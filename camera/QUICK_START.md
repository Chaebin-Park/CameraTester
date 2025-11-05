# Quick Start Guide

가장 빠르게 시작할 수 있는 기본 사용 예제입니다.

## 1️⃣ 가장 기본적인 카메라 프리뷰

최소한의 코드로 카메라 프리뷰만 표시:

```kotlin
@Composable
fun BasicCameraScreen() {
    SimpleCameraPreview(
        config = CameraConfig(
            preset = CameraPreset.MEDIUM
        ),
        modifier = Modifier.fillMaxSize()
    )
}
```

**이게 전부입니다!** 3줄의 코드로 카메라가 작동합니다.

---

## 2️⃣ 전면/후면 카메라 전환

```kotlin
@Composable
fun CameraWithSwitch() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraManager = remember {
        CameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            config = CameraConfig(preset = CameraPreset.MEDIUM)
        )
    }

    LaunchedEffect(cameraManager) {
        cameraManager.startCamera()
    }

    DisposableEffect(cameraManager) {
        onDispose { cameraManager.release() }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    CoroutineScope(Dispatchers.Main).launch {
                        cameraManager.toggleCamera()
                    }
                }
            ) {
                Icon(Icons.Default.Refresh, "Switch Camera")
            }
        }
    ) { padding ->
        CameraPreview(
            cameraManager = cameraManager,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            autoStart = false
        )
    }
}
```

---

## 3️⃣ 사진 캡처

```kotlin
@Composable
fun CameraWithCapture() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraManager = remember {
        CameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            config = CameraConfig(
                preset = CameraPreset.HIGH,
                enableImageCapture = true
            )
        )
    }

    LaunchedEffect(cameraManager) {
        cameraManager.startCamera()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val outputDir = context.getExternalFilesDir(null)!!
                            val imageInfo = cameraManager.capturePhoto(outputDir)
                            Log.d("Camera", "Captured: ${imageInfo.fileName}")
                        } catch (e: Exception) {
                            Log.e("Camera", "Capture failed", e)
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Add, "Capture")
            }
        }
    ) { padding ->
        CameraPreview(
            cameraManager = cameraManager,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            autoStart = false
        )
    }
}
```

---

## 4️⃣ 실시간 프레임 분석 (자동)

선명도와 밝기를 자동으로 분석:

```kotlin
@Composable
fun CameraWithAutoAnalysis() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 자동 프레임 분석 활성화
    val cameraManager = remember {
        CameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            config = CameraConfig(
                preset = CameraPreset.MEDIUM,
                frameAnalysisConfig = FrameAnalysisConfig.HIGH_PERFORMANCE
            )
        )
    }

    var sharpness by remember { mutableStateOf<Double?>(null) }
    var brightness by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(cameraManager) {
        cameraManager.startCamera()
    }

    // 분석 결과 자동 수신
    LaunchedEffect(cameraManager) {
        cameraManager.frameAnalysisFlow.collect { result ->
            sharpness = result.sharpness
            brightness = result.brightness
            result.imageProxy.close()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CameraPreview(
            cameraManager = cameraManager,
            modifier = Modifier.fillMaxSize(),
            autoStart = false
        )

        // 분석 결과 표시
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Text(
                "Sharpness: ${sharpness?.let { "%.2f".format(it) } ?: "-"}",
                color = Color.White
            )
            Text(
                "Brightness: ${brightness?.let { "%.2f".format(it) } ?: "-"}",
                color = Color.White
            )
        }
    }
}
```

---

## 5️⃣ 프리셋 사용

미리 정의된 프리셋으로 간편하게:

```kotlin
// 저화질, 빠른 처리
CameraConfig(preset = CameraPreset.LOW)

// 중화질 (기본값)
CameraConfig(preset = CameraPreset.MEDIUM)

// 고화질
CameraConfig(preset = CameraPreset.HIGH)

// 초고화질
CameraConfig(preset = CameraPreset.VERY_HIGH)

// 최대 화질
CameraConfig(preset = CameraPreset.ULTRA)
```

---

## 6️⃣ 권한 처리

AndroidManifest.xml:
```xml
<uses-feature android:name="android.hardware.camera" />
<uses-permission android:name="android.permission.CAMERA" />
```

Activity:
```kotlin
class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            // 권한 승인됨
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PermissionHelper.checkCameraPermission(this)) {
            permissionLauncher.launch(PermissionHelper.getCameraPermissions())
        }

        setContent {
            BasicCameraScreen()
        }
    }
}
```

---

## 📱 실행 화면

앱을 실행하면:
- **Preview 탭**: 가장 기본적인 카메라 프리뷰
- **Simple 탭**: 선명도 측정 + 사진 캡처
- **Custom 탭**: 커스텀 UI
- **Shapes 탭**: 다양한 프리뷰 모양
- **Analysis 탭**: 자동 프레임 분석

---

## 🚀 다음 단계

- [USAGE.md](USAGE.md): 상세한 사용 가이드
- [README.md](README.md): 전체 기능 소개
- [IMAGE_FORMATS.md](../docs/IMAGE_FORMATS.md): 이미지 처리 최적화 가이드

---

**이제 시작할 준비가 되었습니다!** 🎉
