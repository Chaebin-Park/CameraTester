# KII Camera Library

프리셋 기반의 CameraX 라이브러리로 실시간 프레임 스트리밍과 커스터마이징 가능한 Compose UI를 제공합니다.

## 주요 기능

- ✅ 간편한 카메라 on/off 제어
- ✅ 프리셋 기반 설정 (LOW, MEDIUM, HIGH, ULTRA, HIGH_FPS)
- ✅ 커스터마이징 가능한 Compose UI (크기, 모양, 외관)
- ✅ 하드웨어 적응형 프리셋
- ✅ Kotlin Flow를 통한 실시간 프레임 스트리밍
- ✅ 프레임 처리 유틸리티 (Bitmap 변환, 회전, 크기 조정, 선명도 측정)
- ✅ 전면/후면 카메라 전환
- ✅ 카메라 상태 관리 (StateFlow)

## 요구사항

- **Gradle**: 8.0 - 8.5
- **Android Gradle Plugin**: 8.2.2
- **Kotlin**: 1.9.22
- **Compose Compiler**: 1.5.8
- **minSdk**: 24 (Android 7.0)
- **compileSdk**: 34

## 설치

### 로컬 Maven 저장소

로컬 Maven 저장소에 빌드 및 배포:

```bash
./gradlew :camera:publishToMavenLocal
```

프로젝트의 `build.gradle.kts`에 추가:

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("com.kii:camera:1.0.0")
}
```

### 직접 모듈 의존성

`camera` 모듈을 프로젝트에 복사하고 추가:

```kotlin
// settings.gradle.kts
include(":camera")

// app/build.gradle.kts
dependencies {
    implementation(project(":camera"))
}
```

## 빠른 시작

### 1. 간단한 카메라 프리뷰

```kotlin
@Composable
fun SimpleCameraScreen() {
    SimpleCameraPreview(
        modifier = Modifier.fillMaxSize()
    )
}
```

### 2. 커스텀 프리셋 사용

```kotlin
@Composable
fun HighQualityCameraScreen() {
    SimpleCameraPreview(
        config = CameraConfig(preset = CameraPreset.HIGH),
        modifier = Modifier.fillMaxSize()
    )
}
```

### 3. 커스텀 모양 프리뷰

```kotlin
@Composable
fun CircleCameraPreview() {
    SimpleCameraPreview(
        config = CameraConfig(preset = CameraPreset.HIGH),
        modifier = Modifier
            .size(300.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
    )
}
```

## 프리셋

### 기본 제공 프리셋

- `CameraPreset.LOW` - 640x480, 24fps, 70% 품질
- `CameraPreset.MEDIUM` - 1280x720, 30fps, 80% 품질
- `CameraPreset.HIGH` - 1920x1080, 30fps, 90% 품질
- `CameraPreset.ULTRA` - 3840x2160 (4K), 30fps, 95% 품질
- `CameraPreset.HIGH_FPS` - 1920x1080, 60fps, 85% 품질

### 커스텀 프리셋

```kotlin
val customPreset = CameraPreset.custom {
    name = "MY_PRESET"
    targetResolution = Size(1280, 720)
    targetFrameRate = 60
    imageQuality = 95
    adaptToHardware = true
}
```

## 실시간 프레임 처리

```kotlin
val cameraManager = remember {
    CameraManager(
        context = context,
        lifecycleOwner = lifecycleOwner,
        config = CameraConfig(preset = CameraPreset.HIGH)
    )
}

LaunchedEffect(Unit) {
    cameraManager.frameFlow
        .mapToBitmap()
        .collect { bitmap ->
            bitmap?.let {
                // 프레임 처리
                val resized = FrameProcessor.resizeBitmap(it, 640, 480)
                val brightness = FrameProcessor.calculateBrightness(it)
                val sharpness = FrameProcessor.calculateSharpness(it)
            }
        }
}
```

## 카메라 제어

### CameraManager 사용

```kotlin
val cameraManager = remember {
    CameraManager(
        context = context,
        lifecycleOwner = lifecycleOwner,
        config = CameraConfig(preset = CameraPreset.MEDIUM)
    )
}

// 카메라 시작/정지
LaunchedEffect(Unit) {
    cameraManager.startCamera()
}

DisposableEffect(Unit) {
    onDispose {
        cameraManager.release()
    }
}

// 줌 제어 (0.0 ~ 1.0)
cameraManager.setZoomRatio(0.5f)

// 플래시 제어
cameraManager.setFlashMode(CameraConfig.FlashMode.ON)

// 카메라 전환 (전면/후면)
cameraManager.toggleCamera()

// 프리셋 변경
cameraManager.updatePreset(CameraPreset.HIGH)
```

### 카메라 상태 관찰

```kotlin
val config = cameraManager.configState.collectAsState()
val cameraState = cameraManager.cameraState.collectAsState()

Text("현재 프리셋: ${config.value.preset.name}")
Text("카메라 상태: ${cameraState.value}")
```

## 프레임 처리 유틸리티

### FrameProcessor

```kotlin
// 선명도 측정 (Laplacian 방법)
val sharpness = FrameProcessor.calculateSharpness(bitmap)
val quality = FrameProcessor.getSharpnessQuality(sharpness)
// 결과: "Excellent", "Good", "Fair", "Poor", "Very Poor"

// 밝기 측정
val brightness = FrameProcessor.calculateBrightness(bitmap)

// 이미지 회전
val rotated = FrameProcessor.rotateBitmap(bitmap, 90f)

// 이미지 크기 조정
val resized = FrameProcessor.resizeBitmap(bitmap, 640, 480)

// 이미지 자르기
val cropped = FrameProcessor.cropBitmap(bitmap, x, y, width, height)
```

### ImageProxy 확장 함수

```kotlin
// ImageProxy를 Bitmap으로 변환
val bitmap = imageProxy.toBitmap()

// YUV 형식의 ImageProxy를 Bitmap으로 변환
val yuvBitmap = imageProxy.toYuvBitmap()

// ByteArray로 변환
val bytes = imageProxy.toByteArray()

// Flow 변환
cameraManager.frameFlow
    .mapToBitmap()
    .collect { bitmap -> /* 처리 */ }

cameraManager.frameFlow
    .mapToYuvBitmap()
    .collect { bitmap -> /* 처리 */ }

cameraManager.frameFlow
    .mapToByteArray()
    .collect { bytes -> /* 처리 */ }
```

## Compose UI 컴포넌트

### SimpleCameraPreview

가장 간단한 카메라 프리뷰 컴포넌트:

```kotlin
SimpleCameraPreview(
    config = CameraConfig(preset = CameraPreset.MEDIUM),
    modifier = Modifier.fillMaxSize(),
    onCameraManagerCreated = { manager ->
        // CameraManager 인스턴스 사용
    },
    onError = { error ->
        // 에러 처리
    }
)
```

### CameraPreview

CameraManager를 직접 제어하는 프리뷰 컴포넌트:

```kotlin
val cameraManager = remember {
    CameraManager(context, lifecycleOwner, CameraConfig(preset = CameraPreset.HIGH))
}

CameraPreview(
    cameraManager = cameraManager,
    modifier = Modifier.fillMaxSize()
)
```

## 권한

`AndroidManifest.xml`에 추가:

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-feature android:name="android.hardware.camera" android:required="false" />
```

카메라 사용 전 권한 확인:

```kotlin
if (PermissionHelper.checkCameraPermission(context)) {
    // 카메라 사용
} else {
    // 권한 요청
    permissionLauncher.launch(PermissionHelper.getCameraPermissions())
}
```

## 카메라 상태

```kotlin
sealed class CameraState {
    object Idle : CameraState()
    object Starting : CameraState()
    object Running : CameraState()
    object Stopping : CameraState()
    data class Error(val exception: Exception) : CameraState()
}
```

## 카메라 이벤트

```kotlin
sealed class CameraEvent {
    object CameraStarted : CameraEvent()
    object CameraStopped : CameraEvent()
    data class Error(val exception: Exception) : CameraEvent()
}
```