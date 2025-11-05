# KII Camera Library ProGuard Rules
# These rules will be applied to apps that use this library

# Keep all JNI native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep FrameProcessor native methods
-keep class com.kii.camera.FrameProcessor {
    native <methods>;
    public *;
}

# Keep CameraManager public API
-keep public class com.kii.camera.CameraManager {
    public *;
}

# Keep all public data classes and configs
-keep public class com.kii.camera.CameraConfig { *; }
-keep public class com.kii.camera.CameraPreset { *; }
-keep public class com.kii.camera.CameraState { *; }
-keep public class com.kii.camera.FrameAnalysisConfig { *; }
-keep public class com.kii.camera.FrameAnalysisResult { *; }
-keep public class com.kii.camera.CapturedImageInfo { *; }
-keep public class com.kii.camera.ROI { *; }
-keep public class com.kii.camera.SharpnessLevel { *; }

# Keep all public Composable functions
-keep public class com.kii.camera.CameraPreviewKt { *; }
-keep public class com.kii.camera.SimpleCameraPreviewKt { *; }

# Keep Compose-related annotations
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep Kotlin metadata for reflection
-keep class kotlin.Metadata { *; }

# Keep coroutines
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# CameraXî t¯ ê¥ ProGuard ‹YD ÏhX‡ àL
# https://developer.android.com/jetpack/androidx/releases/camera#proguard
