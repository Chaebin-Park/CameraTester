# Camera Module Usage Guide

카메라 모듈 사용 가이드입니다.

## 기본 사용법

### 1. 간단한 카메라 프리뷰

```kotlin
@Composable
fun SimpleCameraScreen() {
    SimpleCameraPreview(
        config = CameraConfig.DEFAULT,
        modifier = Modifier.fillMaxSize()
    )
}
```

### 2. CameraManager를 사용한 고급 제어

```kotlin
@Composable
fun AdvancedCameraScreen() {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val cameraManager = remember {
        CameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            config = CameraConfig(
                preset = CameraPreset.HIGH,
                lensFacing = CameraSelector.LENS_FACING_BACK
            )
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
        }
    }

    CameraPreview(
        cameraManager = cameraManager,
        modifier = Modifier.fillMaxSize()
    )
}
```

### 3. 커스텀 모양의 카메라 프리뷰

```kotlin
@Composable
fun CircleCameraPreview() {
    SimpleCameraPreview(
        config = CameraConfig.HIGH_QUALITY,
        modifier = Modifier
            .size(300.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White, CircleShape)
    )
}
```

## Preset 사용

### 기본 제공 Preset

```kotlin
// 저화질 (배터리 절약, 저사양 디바이스)
CameraPreset.LOW

// 중간 화질 (일반적인 사용)
CameraPreset.MEDIUM

// 고화질 (고품질 촬영)
CameraPreset.HIGH

// 최고화질 (4K)
CameraPreset.ULTRA

// 고프레임 (60fps)
CameraPreset.HIGH_FPS
```
- `CameraPreset.LOW` - 640x480, 24fps, 70% quality
- `CameraPreset.MEDIUM` - 1280x720, 30fps, 80% quality
- `CameraPreset.HIGH` - 1920x1080, 30fps, 90% quality
- `CameraPreset.ULTRA` - 3840x2160 (4K), 30fps, 95% quality
- `CameraPreset.HIGH_FPS` - 1920x1080, 60fps, 85% quality

### 커스텀 Preset 생성

```kotlin
val customPreset = CameraPreset.custom {
    name = "MY_PRESET"
    targetResolution = Size(1280, 720)
    targetFrameRate = 60
    imageQuality = 95
    adaptToHardware = true
}

SimpleCameraPreview(
    config = CameraConfig(preset = customPreset)
)
```

## 프레임 스트림 처리

### 기본 프레임 수집

```kotlin
val cameraManager = remember { /* ... */ }

LaunchedEffect(Unit) {
    cameraManager.frameFlow.collect { imageProxy ->
        // 프레임 처리
        Log.d("Frame", "Size: ${imageProxy.width}x${imageProxy.height}")

        // 반드시 close() 호출!
        imageProxy.close()
    }
}
```

### Bitmap으로 변환하여 처리

```kotlin
LaunchedEffect(Unit) {
    cameraManager.frameFlow
        .mapToBitmap()
        .collect { bitmap ->
            bitmap?.let {
                // Bitmap 처리
                val processed = FrameProcessor.resizeBitmap(it, 640, 480)
                // ...
            }
        }
}
```

### 프레임 분석 (밝기, 선명도)

```kotlin
LaunchedEffect(Unit) {
    cameraManager.frameFlow
        .mapToBitmap()
        .collect { bitmap ->
            bitmap?.let {
                val brightness = FrameProcessor.calculateBrightness(it)
                val sharpness = FrameProcessor.calculateSharpness(it)

                Log.d("Frame", "Brightness: $brightness, Sharpness: $sharpness")
            }
        }
}
```

## 카메라 제어

### 줌 제어

```kotlin
// 0.0 ~ 1.0 범위
cameraManager.setZoomRatio(0.5f)
```

### 플래시 제어

```kotlin
cameraManager.setFlashMode(CameraConfig.FlashMode.ON)
cameraManager.setFlashMode(CameraConfig.FlashMode.AUTO)
cameraManager.setFlashMode(CameraConfig.FlashMode.OFF)
```

### 카메라 전환 (전면/후면)

```kotlin
// 후면 카메라
val backConfig = CameraConfig(
    lensFacing = CameraSelector.LENS_FACING_BACK
)

// 전면 카메라
val frontConfig = CameraConfig(
    lensFacing = CameraSelector.LENS_FACING_FRONT
)
```

## 카메라 상태 모니터링

```kotlin
val cameraState by cameraManager.cameraState.collectAsState()

when (cameraState) {
    is CameraState.Idle -> Text("Camera Idle")
    is CameraState.Starting -> CircularProgressIndicator()
    is CameraState.Running -> Text("Camera Running")
    is CameraState.Stopping -> Text("Stopping...")
    is CameraState.Error -> {
        val error = (cameraState as CameraState.Error).exception
        Text("Error: ${error.message}", color = Color.Red)
    }
}
```

## 카메라 이벤트 수신

```kotlin
LaunchedEffect(Unit) {
    cameraManager.cameraEvent.collect { event ->
        when (event) {
            is CameraEvent.CameraStarted -> {
                Log.d("Camera", "Camera started")
            }
            is CameraEvent.CameraStopped -> {
                Log.d("Camera", "Camera stopped")
            }
            is CameraEvent.Error -> {
                Log.e("Camera", "Error: ${event.exception.message}")
            }
            else -> {}
        }
    }
}
```

## 실시간 프레임 처리 예제

```kotlin
@Composable
fun FrameProcessingExample() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }

    val cameraManager = remember {
        CameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            config = CameraConfig.HIGH_QUALITY
        )
    }

    LaunchedEffect(Unit) {
        cameraManager.frameFlow
            .mapToBitmap()
            .collect { bitmap ->
                bitmap?.let {
                    // 프레임 처리 (예: 크기 조정)
                    processedBitmap = FrameProcessor.resizeBitmap(it, 640, 480)
                }
            }
    }

    Row {
        // 원본 카메라 프리뷰
        CameraPreview(
            cameraManager = cameraManager,
            modifier = Modifier.weight(1f)
        )

        // 처리된 프레임 표시
        processedBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Processed",
                modifier = Modifier.weight(1f)
            )
        }
    }
}
```

## 권한 처리

카메라를 사용하기 전에 권한을 확인하세요:

```kotlin
// AndroidManifest.xml에 권한 추가 필요
// <uses-permission android:name="android.permission.CAMERA" />

if (PermissionHelper.checkCameraPermission(context)) {
    // 카메라 사용
} else {
    // 권한 요청
}
```

## 주의사항

1. **ImageProxy.close() 필수**: frameFlow에서 받은 ImageProxy는 반드시 `close()`를 호출해야 메모리 누수를 방지할 수 있습니다.

2. **CameraManager 해제**: 사용이 끝난 CameraManager는 `release()`를 호출하여 리소스를 정리해야 합니다.

3. **Lifecycle 관리**: CameraManager는 LifecycleOwner에 바인딩되므로 적절한 lifecycle을 전달해야 합니다.

4. **하드웨어 제약**: `adaptToHardware = true`로 설정하면 디바이스 성능에 맞게 자동으로 설정이 조정됩니다.
