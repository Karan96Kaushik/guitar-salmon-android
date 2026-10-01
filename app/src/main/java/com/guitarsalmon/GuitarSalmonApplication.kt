package com.guitarsalmon

import android.app.Application
import android.content.Context
import com.guitarsalmon.audio.ChordAudioEngine
import com.guitarsalmon.data.GuitarSalmonDatabase
import com.guitarsalmon.data.PracticeRepository
import com.guitarsalmon.data.SettingsRepository

/**
 * Hand-rolled service locator.
 *
 * The app has a handful of singletons and no need for a DI framework, so they are
 * created lazily here and reached through [GuitarSalmonApplication.container].
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(appContext) }

    val practiceRepository: PracticeRepository by lazy {
        PracticeRepository(GuitarSalmonDatabase.get(appContext).practiceDao())
    }

    /**
     * Shared across screens: there is one microphone, so there is one engine. It
     * also means switching between Live and Practice does not tear the audio stream
     * down and build it back up.
     */
    val audioEngine: ChordAudioEngine by lazy {
        ChordAudioEngine(appContext).also { engine ->
            engine.setNoiseGate(settingsRepository.settings.value.noiseGateRms)
        }
    }
}

class GuitarSalmonApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

/** Convenience accessor for the container from any context. */
val Context.appContainer: AppContainer
    get() = (applicationContext as GuitarSalmonApplication).container
