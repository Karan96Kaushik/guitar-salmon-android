package com.guitarsalmon.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/** Lifecycle state of the capture pipeline. */
enum class AudioEngineState {
    Stopped,

    Running,

    /** The stream could not be opened, usually a missing permission or a busy mic. */
    Failed,
}

/**
 * Owns the native engine and the Android-side concerns around it: audio focus,
 * input device changes, and starting/stopping with the UI lifecycle.
 *
 * Detection frames are published as a [StateFlow] refreshed at [POLL_HZ], which
 * matches the DSP thread's frame rate closely enough that no frames are missed
 * perceptually while keeping recomposition cheap.
 *
 * Every call into JNI is confined to a single background thread, so the native
 * engine never sees concurrent access.
 */
class ChordAudioEngine(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)

    /** Single thread that all native calls are confined to. */
    private val nativeExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "GuitarSalmon-JNI").apply { isDaemon = true }
    }
    private val nativeDispatcher = nativeExecutor.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val native = NativeAudioEngine()

    private val _state = MutableStateFlow(AudioEngineState.Stopped)
    val state: StateFlow<AudioEngineState> = _state.asStateFlow()

    private val _detection = MutableStateFlow(ChordDetection.NONE)

    /** Latest detection frame, refreshed while the engine runs. */
    val detection: StateFlow<ChordDetection> = _detection.asStateFlow()

    /** Chord names in the order the native matcher indexes them. */
    val chordNames: List<String> get() = native.chordNames

    private var pollJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null
    private var deviceCallback: AudioDeviceCallback? = null
    private var released = false

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            -> {
                // Something else needs the mic or is about to make noise that would
                // pollute our input, so give up rather than analyse garbage.
                Log.i(TAG, "Audio focus lost ($change); stopping capture")
                scope.launch { stop() }
            }
        }
    }

    /**
     * Requests audio focus, opens the input stream and starts polling.
     *
     * Returns true when capture is running. The caller must already hold the
     * RECORD_AUDIO permission; without it the native stream fails to open and this
     * returns false with [state] set to [AudioEngineState.Failed].
     */
    suspend fun start(): Boolean {
        if (released) return false
        if (_state.value == AudioEngineState.Running) return true

        requestAudioFocus()

        val started = withContext(nativeDispatcher) { native.start() }
        if (!started) {
            Log.w(TAG, "Native engine failed to start")
            abandonAudioFocus()
            _state.value = AudioEngineState.Failed
            return false
        }

        registerDeviceCallback()
        _state.value = AudioEngineState.Running
        startPolling()
        return true
    }

    /** Stops capture and releases audio focus. Safe to call when already stopped. */
    suspend fun stop() {
        pollJob?.cancel()
        pollJob = null
        unregisterDeviceCallback()

        withContext(nativeDispatcher) { native.stop() }

        abandonAudioFocus()
        _detection.value = ChordDetection.NONE
        if (_state.value != AudioEngineState.Failed) _state.value = AudioEngineState.Stopped
    }

    /**
     * Sets the noise gate as an RMS threshold in 0..1. Takes effect on the next
     * analysis frame.
     */
    fun setNoiseGate(rms: Float) {
        if (released) return
        scope.launch(nativeDispatcher) { native.setNoiseGate(rms) }
    }

    /** Releases the native engine. The instance is unusable afterwards. */
    fun release() {
        if (released) return
        released = true
        pollJob?.cancel()
        unregisterDeviceCallback()
        abandonAudioFocus()
        // Run the final teardown on the JNI thread, then shut that thread down.
        nativeExecutor.execute { native.release() }
        nativeExecutor.shutdown()
        scope.cancel()
        _state.value = AudioEngineState.Stopped
    }

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                val frame = withContext(nativeDispatcher) { native.poll() }
                _detection.value = frame

                // If native dropped the stream and could not recover (for example
                // the only mic was unplugged), reflect that in the UI.
                if (!withContext(nativeDispatcher) { native.isRunning() }) {
                    _state.value = AudioEngineState.Failed
                    break
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun requestAudioFocus() {
        val manager = audioManager ?: return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

        // EXCLUSIVE asks other apps to stay silent entirely: anything playing back
        // would be picked up by the mic and detected as chords.
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .build()

        val result = manager.requestAudioFocus(request)
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            focusRequest = request
        } else {
            // Not fatal: recording still works, we just may hear other apps.
            Log.i(TAG, "Audio focus not granted (result=$result); continuing anyway")
        }
    }

    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        focusRequest?.let { manager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private fun registerDeviceCallback() {
        val manager = audioManager ?: return
        if (deviceCallback != null) return

        // The native error callback reopens the stream on the new default device;
        // this callback exists to log the transition and to notice the case where
        // no input device is left at all.
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                Log.i(TAG, "Input devices added: ${addedDevices?.size ?: 0}")
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                Log.i(TAG, "Input devices removed: ${removedDevices?.size ?: 0}")
                val inputs = manager.getDevices(AudioManager.GET_DEVICES_INPUTS)
                if (inputs.isEmpty()) {
                    Log.w(TAG, "No input devices remain; stopping capture")
                    scope.launch { stop() }
                }
            }
        }
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        deviceCallback = callback
    }

    private fun unregisterDeviceCallback() {
        val manager = audioManager ?: return
        deviceCallback?.let { manager.unregisterAudioDeviceCallback(it) }
        deviceCallback = null
    }

    private companion object {
        const val TAG = "ChordAudioEngine"

        /** UI refresh rate for detection frames. */
        const val POLL_HZ = 20
        const val POLL_INTERVAL_MS = 1000L / POLL_HZ
    }
}
