# Java/XML 레거시 프로젝트 사용 가이드

KII Camera 라이브러리를 Java 및 XML 기반 레거시 프로젝트에서 사용하는 방법입니다.

## ✅ 호환성

- **Java 8+**: 완벽 지원
- **Java 7**: 부분 지원 (람다 사용 불가)
- **XML Layout**: 완벽 지원
- **View 시스템**: 완벽 지원

## 📦 의존성

```gradle
dependencies {
    implementation("com.kii:camera:1.0.0")

    // CameraX, Compose는 자동으로 포함됨 (추가 불필요)
}
```

## 🎨 XML 레이아웃에서 사용

### 1. XML 레이아웃 정의

`activity_main.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <!-- 카메라 프리뷰 -->
    <com.kii.camera.CameraPreviewView
        android:id="@+id/cameraPreviewView"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />

    <!-- 캡처 버튼 -->
    <Button
        android:id="@+id/btnCapture"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom|center_horizontal"
        android:layout_margin="16dp"
        android:text="Capture" />

    <!-- 카메라 전환 버튼 -->
    <Button
        android:id="@+id/btnToggle"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top|end"
        android:layout_margin="16dp"
        android:text="Switch" />

    <!-- 정보 텍스트 -->
    <TextView
        android:id="@+id/tvInfo"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="top|start"
        android:layout_margin="16dp"
        android:background="#80000000"
        android:padding="8dp"
        android:textColor="#FFFFFF"
        android:text="Initializing..." />
</FrameLayout>
```

### 2. Java Activity 코드

`MainActivity.java`:
```java
package com.example.myapp;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.kii.camera.CameraConfig;
import com.kii.camera.CameraManager;
import com.kii.camera.CameraPreset;
import com.kii.camera.CameraPreviewView;
import com.kii.camera.CapturedImageInfo;
import com.kii.camera.FrameAnalysisCallback;
import com.kii.camera.FrameAnalysisConfig;
import com.kii.camera.FrameAnalysisResult;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private CameraPreviewView cameraPreviewView;
    private CameraManager cameraManager;
    private TextView tvInfo;

    private final ActivityResultLauncher<String> permissionLauncher =
        registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            isGranted -> {
                if (isGranted) {
                    setupCamera();
                } else {
                    Toast.makeText(this, "카메라 권한이 필요합니다", Toast.LENGTH_SHORT).show();
                }
            }
        );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // View 초기화
        cameraPreviewView = findViewById(R.id.cameraPreviewView);
        Button btnCapture = findViewById(R.id.btnCapture);
        Button btnToggle = findViewById(R.id.btnToggle);
        tvInfo = findViewById(R.id.tvInfo);

        // 버튼 클릭 리스너
        btnCapture.setOnClickListener(v -> capturePhoto());
        btnToggle.setOnClickListener(v -> toggleCamera());

        // 권한 확인 및 카메라 시작
        checkPermissionAndStart();
    }

    private void checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            setupCamera();
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void setupCamera() {
        // CameraConfig 생성
        CameraConfig config = new CameraConfig(
            CameraPreset.MEDIUM,                    // 프리셋
            androidx.camera.core.CameraSelector.LENS_FACING_BACK,  // 후면 카메라
            true,                                   // enableImageAnalysis
            true,                                   // enableImageCapture
            false,                                  // enableVideoCapture
            CameraConfig.FlashMode.OFF,             // flashMode
            CameraConfig.CaptureMode.MAXIMIZE_QUALITY,  // captureMode
            FrameAnalysisConfig.HIGH_PERFORMANCE    // frameAnalysisConfig
        );

        // CameraManager 생성 및 바인딩
        cameraManager = cameraPreviewView.setupCamera(this, this, config);

        // 프레임 분석 콜백 설정
        cameraManager.setFrameAnalysisCallback(new FrameAnalysisCallback() {
            @Override
            public void onFrameAnalyzed(@NonNull FrameAnalysisResult result) {
                // UI 업데이트 (메인 스레드에서)
                runOnUiThread(() -> {
                    String info = String.format(
                        "Sharpness: %.2f (%s)\nBrightness: %.2f (%s)\nTime: %dms",
                        result.getSharpness(),
                        result.getSharpnessLevel(),
                        result.getBrightness(),
                        result.getBrightnessLevel(),
                        result.getProcessingTimeMs()
                    );
                    tvInfo.setText(info);
                });

                // 반드시 close() 호출!
                result.getImageProxy().close();
            }

            @Override
            public void onError(@NonNull Exception error) {
                Log.e(TAG, "Frame analysis error", error);
            }
        });

        // 카메라 시작
        cameraManager.startCameraSync(
            () -> {
                // 성공
                Log.d(TAG, "Camera started successfully");
            },
            error -> {
                // 실패
                Log.e(TAG, "Failed to start camera", error);
                runOnUiThread(() ->
                    Toast.makeText(this, "카메라 시작 실패", Toast.LENGTH_SHORT).show()
                );
            }
        );
    }

    private void capturePhoto() {
        if (cameraManager == null) return;

        File outputDir = getExternalFilesDir(null);
        if (outputDir == null) {
            outputDir = getFilesDir();
        }

        cameraManager.capturePhotoSync(
            outputDir,
            imageInfo -> {
                // 성공
                Log.d(TAG, "Photo captured: " + imageInfo.getFileName());
                runOnUiThread(() ->
                    Toast.makeText(
                        this,
                        "사진 저장: " + imageInfo.getFileName(),
                        Toast.LENGTH_SHORT
                    ).show()
                );
            },
            error -> {
                // 실패
                Log.e(TAG, "Failed to capture photo", error);
                runOnUiThread(() ->
                    Toast.makeText(this, "사진 캡처 실패", Toast.LENGTH_SHORT).show()
                );
            }
        );
    }

    private void toggleCamera() {
        if (cameraManager == null) return;

        cameraManager.toggleCameraSync(
            () -> {
                // 성공
                Log.d(TAG, "Camera toggled");
            },
            error -> {
                // 실패
                Log.e(TAG, "Failed to toggle camera", error);
            }
        );
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraPreviewView != null) {
            cameraPreviewView.release();
        }
    }
}
```

## 🚀 Java 8+ 람다 사용

Java 8 이상에서는 더 간단하게 사용할 수 있습니다:

```java
// 프레임 분석 콜백 (람다)
cameraManager.setFrameAnalysisCallback(result -> {
    Log.d(TAG, "Sharpness: " + result.getSharpness());
    Log.d(TAG, "Brightness: " + result.getBrightness());
    result.getImageProxy().close();
});

// 사진 캡처 (람다)
cameraManager.capturePhotoSync(
    outputDir,
    imageInfo -> Log.d(TAG, "Saved: " + imageInfo.getFileName()),
    error -> Log.e(TAG, "Error", error)
);

// 카메라 전환 (람다)
cameraManager.toggleCameraSync(
    () -> Log.d(TAG, "Toggled"),
    error -> Log.e(TAG, "Error", error)
);
```

## 📋 AndroidManifest.xml

권한 추가:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-feature android:name="android.hardware.camera" />
    <uses-permission android:name="android.permission.CAMERA" />

    <application>
        <!-- ... -->
    </application>
</manifest>
```

## 🎯 Native 함수 직접 사용

프레임 분석 없이 Native 함수만 사용하는 경우:

```java
import com.kii.camera.FrameProcessor;
import com.kii.camera.ROI;

public class ImageAnalyzer {

    public void analyzeImage(byte[] yPlaneData, int width, int height) {
        // 선명도 계산 (Native C++)
        double sharpness = FrameProcessor.calculateSharpnessDirect(
            yPlaneData,
            width,
            height,
            4,              // sampleRate
            ROI.CENTER_50   // ROI
        );

        // 밝기 계산 (Native C++)
        double brightness = FrameProcessor.calculateBrightnessDirect(
            yPlaneData,
            width,
            height,
            4,              // sampleRate
            ROI.CENTER_50   // ROI
        );

        Log.d("Analyzer", "Sharpness: " + sharpness);
        Log.d("Analyzer", "Brightness: " + brightness);
    }
}
```

## 🔧 고급 사용법

### 커스텀 프리셋 사용

```java
// 커스텀 CameraConfig
CameraConfig customConfig = new CameraConfig(
    CameraPreset.HIGH,
    androidx.camera.core.CameraSelector.LENS_FACING_FRONT,
    true,
    true,
    false,
    CameraConfig.FlashMode.AUTO,
    CameraConfig.CaptureMode.MINIMIZE_LATENCY,
    new FrameAnalysisConfig(
        true,           // enableSharpness
        true,           // enableBrightness
        4,              // sampleRate
        10,             // frameSamplingRate
        ROI.CENTER_70,  // roi
        true            // useNativeProcessing
    )
);
```

### 카메라 상태 모니터링

```java
// StateFlow 구독 (Kotlin Coroutines 필요)
// 권장: 콜백 방식 사용

// 간단한 방법: getter 사용
CameraState state = cameraManager.getCameraState().getValue();
CameraConfig config = cameraManager.getConfigState().getValue();
```

## ⚠️ 주의사항

1. **ImageProxy.close() 필수**
   ```java
   result.getImageProxy().close(); // 반드시 호출!
   ```

2. **UI 업데이트는 메인 스레드에서**
   ```java
   runOnUiThread(() -> {
       textView.setText("Updated");
   });
   ```

3. **Lifecycle 관리**
   ```java
   @Override
   protected void onDestroy() {
       super.onDestroy();
       cameraPreviewView.release();
   }
   ```

## 📱 최소 요구사항

- **Java**: 8+ (권장), 7 (제한적)
- **Android SDK**: 24+ (Android 7.0)
- **Build Tools**: Gradle 8.0+

## 🆚 Kotlin vs Java 비교

| 기능 | Kotlin | Java |
|------|--------|------|
| UI 컴포넌트 | `@Composable` 함수 | `CameraPreviewView` (XML) |
| 프레임 분석 | `Flow` | `Callback` |
| 비동기 작업 | `suspend fun` | `*Sync()` 메서드 |
| 람다 지원 | 기본 | Java 8+ |
| 타입 안정성 | Null-safe | Nullable |

## 💡 추가 예제

완전한 샘플 프로젝트는 `app` 모듈의 Java 예제를 참고하세요.

---

**Java 프로젝트에서도 Native C++ 성능을 그대로 활용할 수 있습니다!** ⚡
