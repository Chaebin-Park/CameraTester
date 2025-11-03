# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

CameraTester is an Android application built with Kotlin and Jetpack Compose for testing camera functionality. The project uses a multi-module architecture.

## Module Architecture

The project is organized into three modules:

1. **`:app`** - Main application module
   - Package: `com.kii.cameratester`
   - Uses Jetpack Compose for UI
   - Depends on `:camera` and `:common` modules
   - Entry point: `MainActivity.kt`

2. **`:camera`** - Camera functionality module (standalone library)
   - Package: `com.kii.camera`
   - Contains CameraX implementation with preset-based configuration
   - **Includes integrated common utilities** (from `:common` module)
   - **Standalone library** - can be published and used independently
   - Maven coordinates: `com.kii:camera:1.0.0`
   - Dependencies: CameraX libraries (camera-core, camera2, lifecycle, view)

   **Camera Module Design Goals:**
   - Easy camera on/off control
   - Preset-based camera configuration (resolution, FPS, quality)
   - Customizable Compose UI (size, shape, appearance)
   - Hardware-adaptive presets (auto-adjust to device capabilities)
   - Real-time frame streaming via Flow for post-processing
   - Default presets ensure consistent quality without manual configuration

3. **`:common`** - Shared utilities module (library)
   - Package: `com.kii.common`
   - Contains common utility classes
   - **Note**: Common utilities are integrated into `:camera` module for standalone distribution

## Common Module Utilities

The `:common` module provides essential utilities in `com.kii.common` package:

- **Logger.kt** - Centralized logging with auto-tagging
  - Prefix: "KII"
  - Automatically extracts calling class name as tag
  - Supports all log levels (v, d, i, w, e)

- **Prefs.kt** - SharedPreferences wrapper
  - **Must be initialized in Application.onCreate() with `Prefs.init(context)`**
  - Type-safe storage for primitives and strings

- **ContextExt.kt** - Context extension functions (toast, dp2px, permissions, keyboard, etc.)
- **DateUtils.kt** - Date/time formatting and utilities
- **FileUtils.kt** - File operations and bitmap handling
- **PermissionHelper.kt** - Android permission checks with version-aware logic
- **NetworkUtils.kt** - Network connectivity checks
- **ResourceUtils.kt** - Resource access and conversion helpers
- **CoroutineUtils.kt** - Coroutine/Flow extensions and helpers
- **DeviceUtils.kt** - Device information and detection

## Build Configuration

- **Language**: Kotlin 1.9.22
- **Gradle**: 8.5 (compatible with 8.0 - 8.5)
- **Android Gradle Plugin**: 8.2.2
- **Compose Compiler**: 1.5.8
- **Compile SDK**: 34
- **Min SDK**: 24
- **Target SDK**: 34
- **Java Version**: 11
- **Build System**: Gradle (KTS)

**Note**: The camera library is configured for maximum compatibility with Gradle 8.0+ projects.

## Common Gradle Commands

```bash
# Build the project
./gradlew build

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Run unit tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest

# Run tests for specific module
./gradlew :common:test
./gradlew :camera:test
./gradlew :app:test

# Clean build
./gradlew clean

# Lint checks
./gradlew lint

# Publish camera library to local Maven repository
./gradlew :camera:publishToMavenLocal

# Publish camera library to local build directory
./gradlew :camera:publish
```

## Key Architectural Patterns

### Logging
Always use the `Logger` object from `:common` for logging:
```kotlin
import com.kii.common.Logger

Logger.d("Debug message")  // Auto-tags with class name
Logger.e("Error message", exception)
```

### Preferences Initialization
If using `Prefs`, it must be initialized in a custom Application class:
```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
    }
}
```

### Module Dependencies
- `:app` can depend on `:camera` and `:common`
- `:camera` is standalone - includes integrated common utilities
- `:common` should remain dependency-free (no other module dependencies)

**Note**: The `:camera` module integrates all utilities from `:common` to be a standalone library.

### Permission Handling
Use `PermissionHelper` (available in both `:common` and `:camera` modules) which handles Android version differences:
```kotlin
import com.kii.common.PermissionHelper

PermissionHelper.checkCameraPermission(context)
PermissionHelper.getCameraPermissions()  // Returns version-appropriate permission array
```

## Camera Module Architecture

The `:camera` module provides a preset-based camera system with the following components:

### Core Components

1. **CameraPreset** - Configuration presets for camera settings
   - Resolution (width x height)
   - Frame rate (FPS)
   - Image quality
   - Video quality
   - Predefined presets: LOW, MEDIUM, HIGH, ULTRA
   - Hardware-adaptive mode to match device capabilities

2. **CameraManager** - Main camera controller
   - Handles CameraX lifecycle
   - Applies presets to camera configuration
   - Provides camera on/off control
   - Emits frame data via Flow for real-time processing
   - Thread-safe operations using coroutines

3. **CameraPreview** - Composable UI component
   - Fully customizable (size, shape, modifiers)
   - Integrates with CameraManager
   - Handles lifecycle automatically
   - Provides preview surface for camera feed

### Usage Pattern

```kotlin
// Create camera manager with preset
val cameraManager = CameraManager(
    context = context,
    lifecycleOwner = lifecycleOwner,
    preset = CameraPreset.HIGH
)

// Start camera
cameraManager.startCamera()

// Collect frames for processing
cameraManager.frameFlow.collect { imageProxy ->
    // Process frame
    imageProxy.close()
}

// Use in Compose
CameraPreview(
    cameraManager = cameraManager,
    modifier = Modifier.size(400.dp).clip(CircleShape)
)
```

### Frame Processing Flow

The camera provides real-time frame access through Kotlin Flow:
- Each frame is emitted as `ImageProxy`
- Frames run on background thread (IO dispatcher)
- Consumer must call `imageProxy.close()` to avoid memory leaks
- Flow is hot - emits frames even without collectors

## Technology Stack

- **UI**: Jetpack Compose with Material3
- **Camera**: CameraX 1.3.1
- **Async**: Coroutines + Flow (utilities included)
- **Min Android Version**: Android 7.0 (API 24)

## Publishing the Camera Library

The `:camera` module is configured as a standalone library:

### Local Publishing

```bash
# Publish to local Maven repository (~/.m2/repository)
./gradlew :camera:publishToMavenLocal
```

### Using the Published Library

Add to your project's `build.gradle.kts`:

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("com.kii:camera:1.0.0")
}
```

### Library Information

- **Group ID**: `com.kii`
- **Artifact ID**: `camera`
- **Version**: `1.0.0`
- **Maven Coordinates**: `com.kii:camera:1.0.0`

See `/camera/README.md` for detailed library documentation.
