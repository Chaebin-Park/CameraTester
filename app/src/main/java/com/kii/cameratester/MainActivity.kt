package com.kii.cameratester

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kii.camera.CameraConfig
import com.kii.camera.CameraManager
import com.kii.camera.CameraPreset
import com.kii.camera.CameraPreview
import com.kii.camera.FrameProcessor
import com.kii.camera.SimpleCameraPreview
import com.kii.camera.mapToBitmap
import com.kii.camera.mapToYuvBitmap
import com.kii.camera.toYuvBitmap
import com.kii.cameratester.ui.theme.CameraTesterTheme
import com.kii.common.Logger
import com.kii.common.PermissionHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Logger.d("MainActivity", "Camera permissions granted")
            Toast.makeText(this, "카메라 권한이 승인되었습니다", Toast.LENGTH_SHORT).show()
        } else {
            Logger.w("MainActivity", "Camera permissions denied: $permissions")
            Toast.makeText(
                this,
                "카메라 권한이 필요합니다. 설정에서 권한을 허용해주세요.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 권한 요청
        if (!PermissionHelper.checkCameraPermission(this)) {
            permissionLauncher.launch(PermissionHelper.getCameraPermissions())
        }

        setContent {
            CameraTesterTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CameraExampleApp()
                }
            }
        }
    }
}

@Composable
fun CameraExampleApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Simple", "Custom", "Shapes", "Frame")

    Scaffold(
        topBar = {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> SimpleCameraExample()
                1 -> CustomCameraExample()
                2 -> ShapesCameraExample()
                3 -> FrameProcessingExample()
            }
        }
    }
}

@Composable
fun SimpleCameraExample() {
    val context = LocalContext.current
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var sharpness by remember { mutableStateOf<Double?>(null) }
//    var brightness by remember { mutableStateOf<Double?>(null) }
    var sharpnessTime by remember { mutableStateOf<Long?>(null) }
//    var brightnessTime by remember { mutableStateOf<Long?>(null) }
    var capturedImageInfo by remember { mutableStateOf<com.kii.camera.CapturedImageInfo?>(null) }
    var showCaptureInfo by remember { mutableStateOf(false) }

    // 선명도 측정 (완전히 백그라운드에서 처리)
    LaunchedEffect(cameraManager) {
        var frameCount = 0
        cameraManager?.frameFlow?.collect { imageProxy ->
            frameCount++

            // 10프레임 중 1개만 처리 (성능 최적화)
            if (frameCount % 10 == 0) {
                // 백그라운드 코루틴으로 완전히 분리
                launch(Dispatchers.IO) {
                    try {
                        // YUV → Bitmap 변환
                        val bitmap = imageProxy.toYuvBitmap()

                        if (bitmap != null) {
                            // 선명도 측정 시간
                            val sharpnessStart = System.nanoTime()
                            val calculatedSharpness = FrameProcessor.calculateSharpness(bitmap)
                            val sharpnessEnd = System.nanoTime()
                            val sharpnessElapsed = (sharpnessEnd - sharpnessStart) / 1_000_000 // ms

                            Logger.d("SimpleCameraExample",
                                "Frame #$frameCount - Sharpness: ${sharpnessElapsed}ms")

                            withContext(Dispatchers.Main) {
                                sharpness = calculatedSharpness
                                sharpnessTime = sharpnessElapsed
                            }

                            // Bitmap 메모리 해제
                            bitmap.recycle()
                        }
                    } finally {
                        // 메모리 누수 방지
                        imageProxy.close()
                    }
                }
            } else {
                // 처리하지 않는 프레임은 즉시 close
                imageProxy.close()
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        SimpleCameraPreview(
            modifier = Modifier
                .fillMaxWidth(),
            onCameraManagerCreated = { manager ->
                cameraManager = manager
            },
            onError = { error ->
                // 에러 처리
            }
        )

        // 카메라 전환 버튼 (우상단)
        CameraSwitchButton(
            onSwitch = { cameraManager?.toggleCamera() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        // 캡처 버튼 (우하단)
        CaptureButton(
            onClick = {
                cameraManager?.let { manager ->
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            val outputDir = context.getExternalFilesDir(null) ?: context.filesDir
                            val info = manager.capturePhoto(outputDir)
                            capturedImageInfo = info
                            showCaptureInfo = true
                            Logger.d("MainActivity", "Photo captured: $info")
                        } catch (e: Exception) {
                            Logger.e("MainActivity", "Failed to capture photo", e)
                        }
                    }
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )

        // 카메라 정보 (좌하단)
        CameraInfoOverlay(
            cameraManager = cameraManager,
            sharpness = sharpness,
//            brightness = brightness,
            sharpnessTime = sharpnessTime,
//            brightnessTime = brightnessTime,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )

        // 캡처된 이미지 정보 다이얼로그
        if (showCaptureInfo && capturedImageInfo != null) {
            CapturedImageInfoDialog(
                imageInfo = capturedImageInfo!!,
                onDismiss = { showCaptureInfo = false }
            )
        }
    }
}

@Composable
fun CustomCameraExample() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var currentPreset by remember { mutableStateOf(CameraPreset.MEDIUM) }
    var sharpness by remember { mutableStateOf<Double?>(null) }
    var brightness by remember { mutableStateOf<Double?>(null) }

    val cameraManager = remember {
        CameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            config = CameraConfig(preset = CameraPreset.MEDIUM)
        )
    }

    // Preset 변경 시 카메라 업데이트
    LaunchedEffect(currentPreset) {
        cameraManager.updatePreset(currentPreset)
    }

    // 선명도 및 밝기 측정 (프레임 샘플링 적용)
    LaunchedEffect(Unit) {
        var frameCount = 0
        cameraManager.frameFlow
            .mapToBitmap()
            .collect { bitmap ->
                bitmap?.let {
                    // 10프레임 중 1개만 처리 (성능 최적화)
                    if (frameCount % 10 == 0) {
                        withContext(Dispatchers.Default) {
                            val calculatedSharpness = FrameProcessor.calculateSharpness(it)
                            val calculatedBrightness = FrameProcessor.calculateHistogramBrightness(it)
                            withContext(Dispatchers.Main) {
                                sharpness = calculatedSharpness
                                brightness = calculatedBrightness
                            }
                        }
                    }
                    frameCount++
                }
            }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 프리셋 선택 버튼들
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { currentPreset = CameraPreset.LOW }) {
                    Text("LOW")
                }
                Button(onClick = { currentPreset = CameraPreset.MEDIUM }) {
                    Text("MEDIUM")
                }
                Button(onClick = { currentPreset = CameraPreset.HIGH }) {
                    Text("HIGH")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Box {
                CameraPreview(
                    cameraManager = cameraManager,
                    modifier = Modifier
                        .fillMaxWidth()
                )

                // 카메라 전환 버튼 (우상단)
                CameraSwitchButton(
                    onSwitch = { cameraManager.toggleCamera() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )

                // 카메라 정보 (좌하단)
                CameraInfoOverlay(
                    cameraManager = cameraManager,
                    sharpness = sharpness,
                    brightness = brightness,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun ShapesCameraExample() {
    var isCircleShape by remember { mutableStateOf(true) }
    var cameraManager by remember { mutableStateOf<CameraManager?>(null) }
    var sharpness by remember { mutableStateOf<Double?>(null) }
    var brightness by remember { mutableStateOf<Double?>(null) }

    // 선명도 및 밝기 측정 (프레임 샘플링 적용)
    LaunchedEffect(cameraManager) {
        var frameCount = 0
        cameraManager?.frameFlow
            ?.mapToYuvBitmap()
            ?.collect { bitmap ->
                bitmap?.let {
                    // 10프레임 중 1개만 처리 (성능 최적화)
                    if (frameCount % 10 == 0) {
                        withContext(Dispatchers.Default) {
                            val calculatedSharpness = FrameProcessor.calculateSharpness(it)
                            val calculatedBrightness = FrameProcessor.calculateHistogramBrightness(it)
                            withContext(Dispatchers.Main) {
                                sharpness = calculatedSharpness
                                brightness = calculatedBrightness
                            }
                        }
                    }
                    frameCount++
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // 형태 전환 버튼
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { isCircleShape = true }) {
                    Text("Circle")
                }
                Button(onClick = { isCircleShape = false }) {
                    Text("Rounded Rectangle")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box {
                // 선택된 형태의 카메라
                Card(
                    modifier = Modifier.padding(8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isCircleShape) "Circle Shape" else "Rounded Rectangle",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (isCircleShape) {
                            // 원형 카메라
                            SimpleCameraPreview(
                                modifier = Modifier
                                    .size(300.dp)
                                    .clip(CircleShape)
                                    .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                onCameraManagerCreated = { manager ->
                                    cameraManager = manager
                                }
                            )
                        } else {
                            // 둥근 모서리 카메라
                            SimpleCameraPreview(
                                modifier = Modifier
                                    .size(width = 350.dp, height = 250.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .border(
                                        4.dp,
                                        MaterialTheme.colorScheme.secondary,
                                        RoundedCornerShape(24.dp)
                                    ),
                                onCameraManagerCreated = { manager ->
                                    cameraManager = manager
                                }
                            )
                        }
                    }
                }

                // 카메라 전환 버튼 (우상단)
                CameraSwitchButton(
                    onSwitch = { cameraManager?.toggleCamera() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                )

                // 카메라 정보 (좌하단)
                CameraInfoOverlay(
                    cameraManager = cameraManager,
                    sharpness = sharpness,
                    brightness = brightness,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun FrameProcessingExample() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var frameInfo by remember { mutableStateOf("No frames yet") }
    var sharpness by remember { mutableStateOf<Double?>(null) }
    var brightness by remember { mutableStateOf<Double?>(null) }

    val cameraManager = remember {
        CameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            config = CameraConfig(preset = CameraPreset.MEDIUM)
        )
    }

    // 프레임 처리 (프레임 샘플링 적용)
    LaunchedEffect(Unit) {
        var frameCount = 0
        cameraManager.frameFlow
            .mapToBitmap()
            .collect { bitmap ->
                bitmap?.let {
                    frameCount++
                    frameInfo = "Frame #$frameCount"

                    // 10프레임 중 1개만 처리 (성능 최적화)
                    if (frameCount % 10 == 0) {
                        withContext(Dispatchers.Default) {
                            val calculatedSharpness = FrameProcessor.calculateSharpness(it)
                            val calculatedBrightness = FrameProcessor.calculateHistogramBrightness(it)
                            withContext(Dispatchers.Main) {
                                sharpness = calculatedSharpness
                                brightness = calculatedBrightness
                            }
                        }
                    }
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        CameraPreview(
            cameraManager = cameraManager,
            modifier = Modifier
                .fillMaxWidth()
        )

        // 프레임 정보 표시
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(16.dp)
        ) {
            Text(
                text = frameInfo,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        // 카메라 전환 버튼 (우상단)
        CameraSwitchButton(
            onSwitch = { cameraManager.toggleCamera() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        )

        // 카메라 정보
        CameraInfoOverlay(
            cameraManager = cameraManager,
            sharpness = sharpness,
            brightness = brightness,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        )
    }
}

/**
 * 카메라 전환 아이콘 버튼
 */
@Composable
fun CameraSwitchButton(
    onSwitch: suspend () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = {
            CoroutineScope(Dispatchers.Main).launch {
                onSwitch()
            }
        },
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = "Switch Camera",
            tint = MaterialTheme.colorScheme.primary
        )
    }
}

/**
 * 카메라 정보 오버레이
 */
@Composable
fun CameraInfoOverlay(
    cameraManager: CameraManager?,
    modifier: Modifier = Modifier,
    sharpness: Double? = null,
    brightness: Double? = null,
    sharpnessTime: Long? = null,
    brightnessTime: Long? = null,
    extraInfo: String = ""
) {
    val config = cameraManager?.configState?.collectAsState()?.value
    val cameraState = cameraManager?.cameraState?.collectAsState()?.value

    val infoText = buildString {
        config?.let {
            appendLine("Lens: ${if (it.lensFacing == androidx.camera.core.CameraSelector.LENS_FACING_BACK) "Back" else "Front"}")
            appendLine("Preset: ${it.preset.name}")
            appendLine("Resolution: ${it.preset.targetResolution?.width ?: "Auto"}x${it.preset.targetResolution?.height ?: "Auto"}")
            appendLine("FPS: ${it.preset.targetFrameRate}")
            appendLine("Quality: ${it.preset.imageQuality}%")
        }
        cameraState?.let { state ->
            val stateName = when (state) {
                is com.kii.camera.CameraState.Idle -> "Idle"
                is com.kii.camera.CameraState.Starting -> "Starting"
                is com.kii.camera.CameraState.Running -> "Running"
                is com.kii.camera.CameraState.Stopping -> "Stopping"
                is com.kii.camera.CameraState.Error -> "Error"
            }
            appendLine("State: $stateName")
        }
        sharpness?.let {
            val quality = FrameProcessor.getSharpnessQuality(it)
            val timeStr = sharpnessTime?.let { " [${it}ms]" } ?: ""
            appendLine("Sharpness: %.1f ($quality)$timeStr".format(it))
        }
        brightness?.let {
            val quality = FrameProcessor.getBrightnessQuality(it)
            val timeStr = brightnessTime?.let { " [${it}ms]" } ?: ""
            appendLine("Brightness: %.2f ($quality)$timeStr".format(it))
        }
        if (extraInfo.isNotEmpty()) {
            append(extraInfo)
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(8.dp)
    ) {
        Text(
            text = infoText,
            color = Color.White,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 10.sp
        )
    }
}

/**
 * 캡처 버튼
 */
@Composable
fun CaptureButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = Icons.Filled.Add,
            contentDescription = "Capture Photo"
        )
    }
}

/**
 * 캡처된 이미지 정보 다이얼로그
 */
@Composable
fun CapturedImageInfoDialog(
    imageInfo: com.kii.camera.CapturedImageInfo,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Captured Image Info")
        },
        text = {
            Column {
                Text("File: ${imageInfo.fileName}")
                Spacer(modifier = Modifier.height(8.dp))
                Text("Resolution: ${imageInfo.width}x${imageInfo.height}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("File Size: ${imageInfo.getFileSizeFormatted()}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Preset: ${imageInfo.preset.name}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Preset Resolution: ${imageInfo.preset.targetResolution?.width ?: "Auto"}x${imageInfo.preset.targetResolution?.height ?: "Auto"}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("FPS: ${imageInfo.preset.targetFrameRate}")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Quality: ${imageInfo.preset.imageQuality}%")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Lens: ${imageInfo.getLensFacingString()}")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Path: ${imageInfo.filePath}",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}
