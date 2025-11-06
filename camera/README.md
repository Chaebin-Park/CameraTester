# 📷 KII Camera Library

프리셋 기반의 고성능 Android CameraX 라이브러리

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Min SDK](https://img.shields.io/badge/Min%20SDK-24-green.svg)](https://developer.android.com/about/versions/nougat)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)](https://kotlinlang.org/)

## ✨ 주요 기능

- ✅ **프리셋 기반 설정**: 5가지 화질 프리셋 (LOW ~ ULTRA)
- ✅ **고성능 프레임 분석**: Native C++ 기반 (1-3ms 처리 시간)
- ✅ **자동 프레임 스트리밍**: Flow API로 실시간 선명도/밝기 측정
- ✅ **조명 품질 분석**: 히스토그램 기반 저조도/과다노출/역광 감지 🆕
- ✅ **Jetpack Compose UI**: 커스터마이징 가능한 카메라 프리뷰
- ✅ **Java/XML 완벽 지원**: 레거시 프로젝트에서도 사용 가능
- ✅ **이미지 캡처**: 간단한 API로 고화질 사진 저장
- ✅ **ROI 최적화**: 관심 영역 처리로 94% 메모리 절감
- ✅ **전면/후면 카메라 전환**
- ✅ **카메라 상태 관리**: StateFlow 기반

## 📋 요구사항

- **minSdk**: 24 (Android 7.0)
- **compileSdk**: 34
- **언어**: Kotlin / **Java 8+** ✅
- **UI**: Jetpack Compose / **XML View** ✅
- **지원 아키텍처**: armeabi-v7a, arm64-v8a, x86, x86_64

## 📦 설치

### 방법 1: 로컬 Maven 저장소

라이브러리를 로컬에 배포:

```bash
./gradlew :camera:publishToMavenLocal
```

프로젝트 설정:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal() // 추가
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.kii:camera:1.0.0")
}
```

### 방법 2: AAR 파일 직접 사용

AAR 빌드:

```bash
./gradlew :camera:assembleRelease
# 출력: camera/build/outputs/aar/camera-release.aar
```

프로젝트에 추가:

```kotlin
dependencies {
    implementation(files("libs/camera-release.aar"))

    // 필수 의존성
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")
}
```

### 방법 3: 모듈 의존성 (개발 시)

```kotlin
// settings.gradle.kts
include(":camera")

// app/build.gradle.kts
dependencies {
    implementation(project(":camera"))
}
```

자세한 배포 가이드는 [PUBLISHING.md](PUBLISHING.md) 참고

## 🚀 빠른 시작

### 권한 설정

`AndroidManifest.xml`:
```xml
<uses-feature android:name="android.hardware.camera" />
<uses-permission android:name="android.permission.CAMERA" />
```

### 기본 카메라 프리뷰

```kotlin
@Composable
fun MyCameraScreen() {
    SimpleCameraPreview(
        config = CameraConfig(preset = CameraPreset.MEDIUM),
        modifier = Modifier.fillMaxSize()
    )
}
```

**이게 전부입니다!** 🎉

### 더 많은 예제

6가지 기본 사용 예제는 [QUICK_START.md](QUICK_START.md)에서 확인하세요:
- 기본 프리뷰
- 카메라 전환
- 사진 캡처
- 자동 프레임 분석
- 프리셋 사용
- 권한 처리

## 🎯 자동 프레임 분석

라이브러리의 핵심 기능 - 실시간 선명도/밝기 자동 측정:

```kotlin
val cameraManager = remember(context, lifecycleOwner) {
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

// 자동 분석 결과 수신
LaunchedEffect(Unit) {
    cameraManager.frameAnalysisFlow.collect { result ->
        sharpness = result.sharpness
        brightness = result.brightness
        result.imageProxy.close()
    }
}
```

**성능**:
- 처리 시간: 1-3ms (Native C++ + ROI)
- 메모리 사용: 0.5MB (ROI 사용 시)
- 프레임 샘플링: 자동 최적화

## 📸 이미지 캡처

```kotlin
val outputDir = context.getExternalFilesDir(null)!!
val imageInfo = cameraManager.capturePhoto(outputDir)

println("파일: ${imageInfo.fileName}")
println("크기: ${imageInfo.width}x${imageInfo.height}")
println("용량: ${imageInfo.fileSizeBytes / 1024}KB")
```

## 🎨 프리셋

| 프리셋 | 해상도 | 용도 |
|--------|--------|------|
| `LOW` | 640×480 | 프리뷰만, 최고 성능 |
| `MEDIUM` | 1280×720 | 일반적인 사용 (기본값) |
| `HIGH` | 1920×1080 | 고품질 캡처 |
| `VERY_HIGH` | 2560×1440 | 초고화질 |
| `ULTRA` | 3840×2160 | 4K |

## ⚡ 성능

### 프레임 분석 최적화

- **초기 구현**: 55ms
- **JPEG 우회**: 13ms (4.2배 향상)
- **Native C++**: 8ms (1.6배 향상)
- **ROI 처리**: 4ms (2배 향상)
- **Bitmap 우회**: **2-3ms** (최종, **18-27배 향상**)

### 메모리 최적화

- 전체 프레임: 8.3MB (1280×720 YUV)
- ROI (중앙 50%): **0.5MB** (**94% 절감**)

### 실시간 처리

- 선명도 계산: 1-3ms
- 밝기 계산: 1-2ms
- 프레임 샘플링: 10프레임당 1개 (설정 가능)

자세한 최적화 과정은 [IMAGE_FORMATS.md](../docs/IMAGE_FORMATS.md) 참고

## 🛠️ 커스텀 프레임 처리

자동 분석 대신 직접 프레임을 처리하려면:

```kotlin
LaunchedEffect(Unit) {
    cameraManager.frameFlow.collect { imageProxy ->
        // YUV → ByteArray 직접 추출 (고성능)
        val yPlaneBytes = imageProxy.toYPlaneByteArray()

        if (yPlaneBytes != null) {
            // Native 선명도 계산 (ROI 지원)
            val sharpness = FrameProcessor.calculateSharpnessDirect(
                pixelData = yPlaneBytes,
                width = imageProxy.width,
                height = imageProxy.height,
                sampleRate = 4,
                roi = ROI.CENTER_50
            )

            // Native 밝기 계산
            val brightness = FrameProcessor.calculateBrightnessDirect(
                pixelData = yPlaneBytes,
                width = imageProxy.width,
                height = imageProxy.height,
                sampleRate = 4,
                roi = ROI.CENTER_50
            )
        }

        imageProxy.close() // 반드시 호출!
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

## 🎨 UI 커스터마이징

### 다양한 모양 프리뷰

```kotlin
// 원형 프리뷰
SimpleCameraPreview(
    config = CameraConfig(preset = CameraPreset.MEDIUM),
    modifier = Modifier
        .size(300.dp)
        .clip(CircleShape)
        .border(4.dp, Color.White, CircleShape)
)

// 둥근 모서리
SimpleCameraPreview(
    config = CameraConfig(preset = CameraPreset.MEDIUM),
    modifier = Modifier
        .size(400.dp, 600.dp)
        .clip(RoundedCornerShape(24.dp))
)
```

## 🎛️ 카메라 제어

```kotlin
// 카메라 전환 (전면/후면)
cameraManager.toggleCamera()

// 줌 제어 (0.0 ~ 1.0)
cameraManager.setZoomRatio(0.5f)

// 플래시 제어
cameraManager.setFlashMode(CameraConfig.FlashMode.ON)

// 프리셋 변경
cameraManager.updatePreset(CameraPreset.HIGH)
```

## 📚 문서

- [QUICK_START.md](QUICK_START.md) - 빠른 시작 가이드 (6가지 기본 예제, Kotlin)
- [JAVA_USAGE.md](JAVA_USAGE.md) - **Java/XML 레거시 프로젝트 가이드**
- [USAGE.md](USAGE.md) - 상세 API 가이드 및 고급 사용법
- [IMAGE_FORMATS.md](../docs/IMAGE_FORMATS.md) - 이미지 처리 최적화 가이드
- [PUBLISHING.md](PUBLISHING.md) - 라이브러리 빌드 및 배포 가이드

## 🔧 기술 스택

- **Android CameraX**: 최신 카메라 API
- **Jetpack Compose**: 선언적 UI
- **Kotlin Coroutines**: 비동기 처리 및 Flow API
- **Native C++ (JNI)**: 고성능 이미지 처리
- **ARM NEON**: SIMD 최적화 지원 (준비됨)
- **CMake**: 크로스 플랫폼 C++ 빌드

## 📦 빌드 및 배포

### AAR 빌드

```bash
./gradlew :camera:assembleRelease
# 출력: camera/build/outputs/aar/camera-release.aar
```

### Maven 로컬 배포

```bash
./gradlew :camera:publishToMavenLocal
```

자세한 내용은 [PUBLISHING.md](PUBLISHING.md) 참고

## 💡 조명 품질 분석 (Luminance Analysis) 🆕

히스토그램 기반의 고급 조명 품질 분석 기능을 제공합니다.

### 주요 기능

- **실시간 조명 품질 판정**: OPTIMAL, ACCEPTABLE, UNDEREXPOSED, OVEREXPOSED, BACKLIT
- **히스토그램 분석**: Y-plane 직접 분석으로 RGB 변환 오버헤드 제거
- **정밀한 노출 감지**:
  - **darknessRatio**: 어두운 픽셀(0-50) 비율 분석
  - **clippingRatio**: 과다 노출(255) 픽셀 비율 분석
- **Native C++ 구현**: 3-5ms 처리 시간 (720p 기준)

### 사용 예제

#### 1. 자동 분석 설정

```kotlin
val cameraManager = CameraManager(
    context = context,
    lifecycleOwner = lifecycleOwner,
    config = CameraConfig(
        preset = CameraPreset.MEDIUM,
        frameAnalysisConfig = FrameAnalysisConfig.HIGH_PERFORMANCE.copy(
            enableLuminance = true  // 조명 품질 분석 활성화
        )
    )
)

// 분석 결과 수집
lifecycleScope.launch {
    cameraManager.frameAnalysisFlow.collect { result ->
        result.luminanceAnalysis?.let { analysis ->
            Log.d("Camera", "Lighting Quality: ${analysis.quality}")
            Log.d("Camera", "Brightness: ${(analysis.brightness * 100).toInt()}%")
            Log.d("Camera", "Darkness Ratio: ${(analysis.darknessRatio * 100).toInt()}%")
            Log.d("Camera", "Clipping Ratio: ${(analysis.clippingRatio * 100).toInt()}%")

            // 사용자 피드백 표시
            when (analysis.quality) {
                LightingQuality.UNDEREXPOSED ->
                    showToast("조명이 너무 어둡습니다. 밝은 곳으로 이동하세요.")
                LightingQuality.OVEREXPOSED ->
                    showToast("조명이 너무 밝습니다. 직접 조명을 피하세요.")
                LightingQuality.BACKLIT ->
                    showToast("역광입니다. 카메라 각도를 조절하세요.")
                else -> { /* 정상 */ }
            }
        }

        result.imageProxy.close()
    }
}
```

#### 2. 수동 분석

```kotlin
// ImageProxy 직접 분석
cameraManager.frameFlow.collect { imageProxy ->
    val analysis = imageProxy.analyzeLuminance(roi = ROI.CENTER_50)

    println(analysis.getSummary())
    // Output: "Lighting: Optimal lighting | Brightness: 65% | Darkness: 12% | Clipping: 2% | Time: 4ms"

    if (!analysis.quality.isSuitable()) {
        println("Warning: ${analysis.quality.getDescription()}")
        println("Suggestion: ${analysis.quality.getSuggestedAction()}")
    }

    imageProxy.close()
}

// 히스토그램만 계산
val histogram = imageProxy.calculateHistogram()
val analysis = FrameProcessor.analyzeLuminanceQuality(histogram)
```

#### 3. 얼굴 인식 전 조명 체크

```kotlin
suspend fun captureFacePhoto(): Result<Bitmap> {
    // 조명 품질 확인
    val analysis = getCurrentLuminanceAnalysis()

    if (!analysis.quality.isSuitable()) {
        return Result.failure(
            Exception("Poor lighting: ${analysis.quality.getSuggestedAction()}")
        )
    }

    // 조명이 적합하면 촬영 진행
    return capturePhoto()
}
```

### 임계값 설정

기본 임계값은 `LuminanceAnalysis` companion object에 정의되어 있습니다:

```kotlin
LuminanceAnalysis.DARKNESS_THRESHOLD  // 0.75 (75% 이상 어두움)
LuminanceAnalysis.CLIPPING_THRESHOLD  // 0.10 (10% 이상 과다노출)
LuminanceAnalysis.DARK_PIXEL_THRESHOLD  // 50 (0-50 범위를 어두움으로 판정)
```

### 성능 특징

| 항목 | 값 |
|------|-----|
| 처리 시간 (720p) | 3-5ms |
| 처리 시간 (1080p) | 6-8ms |
| 메모리 오버헤드 | ~1KB/frame |
| CPU 사용률 증가 | +10-15% |
| 실시간 가능 여부 | ✅ 30fps 여유 있음 |

### 기술 배경

조명 품질 분석은 LUMINANCE.md에 문서화된 방법론을 기반으로 구현되었습니다:

- **Section 4.2**: 저조도 감지 - 왼쪽 쏠림 분석
- **Section 4.3**: 과다노출 감지 - 클리핑 분석
- **Section 7.3**: 최종 판단 로직

자세한 내용은 [LUMINANCE.md](LUMINANCE.md) 참고

## 🤝 기여

이슈 및 Pull Request 환영합니다!

## 📄 라이선스

Apache License 2.0

---

Made with ❤️ using Jetpack Compose & CameraX