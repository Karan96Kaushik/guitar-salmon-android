# Native methods are resolved by name; keep the JNI bridge class intact.
-keepclasseswithmembernames,includedescriptorclasses class com.guitarsalmon.audio.NativeAudioEngine {
    native <methods>;
}
