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
import androidx.compose.material3.CardDefaults
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
import com.kii.camera.CameraState
import com.kii.camera.CapturedImageInfo
import com.kii.camera.FrameAnalysisConfig
import com.kii.camera.FrameAnalysisResult
import com.kii.camera.FrameProcessor
import com.kii.camera.HistogramChart
import com.kii.camera.SimpleCameraPreview
import com.kii.camera.mapToBitmap
import com.kii.camera.mapToYuvBitmap
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
    val tabs = listOf("Preview", "Custom", "Shapes", "Analysis")

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
                0 -> BasicPreviewExample()
                1 -> CustomCameraExample()
                2 -> ShapesCameraExample()
                3 -> FrameAnalysisExample()
            }
        }
    }
}

/**
 * 가장 기본적인 카메라 프리뷰 예제
 * 최소한의 코드로 카메라 프리뷰만 표시
 */
@Composable
fun BasicPreviewExample() {
    SimpleCameraPreview(
        config = CameraConfig(
            preset = CameraPreset.MEDIUM
        ),
        modifier = Modifier.fillMaxSize()
    )
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
                            val calculatedBrightness =
                                FrameProcessor.calculateHistogramBrightness(it)
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
                            val calculatedBrightness =
                                FrameProcessor.calculateHistogramBrightness(it)
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
    sharpnessTime: Double? = null,
    brightnessTime: Double? = null,
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
                CameraState.Idle -> "Idle"
                is CameraState.Starting -> "Starting"
                is CameraState.Running -> "Running"
                is CameraState.Stopping -> "Stopping"
                is CameraState.Error -> "Error"
            }
            appendLine("State: $stateName")
        }
        sharpness?.let { s ->
            val quality = FrameProcessor.getSharpnessQuality(s)
            val timeStr = sharpnessTime?.let { " [${"%.2f".format(it)}ms]" } ?: ""
            appendLine("Sharpness: %.1f ($quality)$timeStr".format(s))
        }
        brightness?.let { b ->
            val quality = FrameProcessor.getBrightnessQuality(b)
            val timeStr = brightnessTime?.let { " [${"%.2f".format(it)}ms]" } ?: ""
            appendLine("Brightness: %.2f ($quality)$timeStr".format(b))
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
    imageInfo: CapturedImageInfo,
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

/**
 * 프레임 자동 분석 예제
 * frameAnalysisFlow를 사용하여 자동으로 분석된 프레임 결과를 받아오는 간단한 예제
 */
@Composable
fun FrameAnalysisExample() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // CameraManager 생성 (자동 프레임 분석 활성화)
    // key를 명확히 지정하여 불필요한 재생성 방지
    val cameraManager = remember(context, lifecycleOwner) {
        Logger.d("FrameAnalysisExample", "Creating CameraManager")
        CameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            config = CameraConfig(
                preset = CameraPreset.LOW,
                frameAnalysisConfig = FrameAnalysisConfig.HIGH_PERFORMANCE
            )
        )
    }

    var sharpness by remember { mutableStateOf<Double?>(null) }
    var brightness by remember { mutableStateOf<Double?>(null) }
    var sharpnessLevel by remember { mutableStateOf<FrameAnalysisResult.SharpnessLevel?>(null) }
    var brightnessLevel by remember { mutableStateOf<FrameAnalysisResult.BrightnessLevel?>(null) }
    var luminanceAnalysis by remember { mutableStateOf<com.kii.camera.LuminanceAnalysis?>(null) }
    var processingTime by remember { mutableStateOf<Double?>(null) }
    var sharpnessTime by remember { mutableStateOf<Double?>(null) }
    var brightnessTime by remember { mutableStateOf<Double?>(null) }
    var luminanceTime by remember { mutableStateOf<Double?>(null) }
    var frameSize by remember { mutableStateOf<String?>(null) }

    // CameraManager 시작 (Unit key로 한 번만 실행)
    LaunchedEffect(Unit) {
        Logger.d("FrameAnalysisExample", "Starting camera")
        cameraManager.startCamera()
    }

    // frameAnalysisFlow로부터 자동 분석 결과 수신 (Unit key로 한 번만 실행)
    LaunchedEffect(Unit) {
        Logger.d("FrameAnalysisExample", "Starting frame analysis collection")
        // 백그라운드 스레드에서 collect (Main 스레드 블로킹 방지)
        launch(Dispatchers.Default) {
            cameraManager.frameAnalysisFlow.collect { result ->
                // 상태 업데이트는 Main 스레드에서
                withContext(Dispatchers.Main) {
                    sharpness = result.sharpness
                    brightness = result.brightness
                    luminanceAnalysis = result.luminanceAnalysis
                    sharpnessLevel = result.getSharpnessLevel()
                    brightnessLevel = result.getBrightnessLevel()
                    processingTime = result.processingTimeMs
                    sharpnessTime = result.sharpnessTimeMs
                    brightnessTime = result.brightnessTimeMs
                    luminanceTime = result.luminanceTimeMs
                    frameSize = "${result.width}x${result.height}"
                }
            }
        }
    }

    // 정리 (Unit key로 컴포넌트 생명주기와 연결)
    DisposableEffect(Unit) {
        onDispose {
            Logger.d("FrameAnalysisExample", "Disposing CameraManager")
            cameraManager.release()
        }
    }

    Scaffold(
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 카메라 전환 버튼
                FloatingActionButton(
                    onClick = {
                        Logger.d("FrameAnalysisExample", "Toggle camera button clicked")
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val newLens = cameraManager.toggleCamera()
                                Logger.d("FrameAnalysisExample", "Camera toggled to: $newLens")
                            } catch (e: Exception) {
                                Logger.e("FrameAnalysisExample", "Failed to toggle camera", e)
                            }
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Switch Camera"
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 카메라 프리뷰
            CameraPreview(
                cameraManager = cameraManager,
                modifier = Modifier.fillMaxSize(),
                autoStart = false // 이미 수동으로 시작함
            )

            // 분석 결과 표시 (상단)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Frame Analysis (Auto)",
                        style = MaterialTheme.typography.titleMedium
                    )

                    frameSize?.let {
                        Text("Frame Size: $it", fontSize = 12.sp)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            sharpness?.let {
                                Text(
                                    "Sharpness: ${"%.2f".format(it)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                sharpnessLevel?.let { level ->
                                    Text(
                                        level.name.replace("_", " "),
                                        fontSize = 11.sp,
                                        color = when (level) {
                                            FrameAnalysisResult.SharpnessLevel.VERY_SHARP -> Color(
                                                0xFF4CAF50
                                            )

                                            FrameAnalysisResult.SharpnessLevel.SHARP -> Color(
                                                0xFF8BC34A
                                            )

                                            FrameAnalysisResult.SharpnessLevel.ACCEPTABLE -> Color(
                                                0xFFFFC107
                                            )

                                            FrameAnalysisResult.SharpnessLevel.SLIGHTLY_BLURRY -> Color(
                                                0xFFFF9800
                                            )

                                            FrameAnalysisResult.SharpnessLevel.BLURRY -> Color(
                                                0xFFF44336
                                            )

                                            else -> Color.Gray
                                        }
                                    )
                                }
                                sharpnessTime?.let { time ->
                                    if (time > 0) {
                                        Text(
                                            "[${"%.2f".format(time)}ms]",
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            } ?: Text("Sharpness: -", style = MaterialTheme.typography.bodyMedium)
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            brightness?.let {
                                Text(
                                    "Brightness: ${"%.2f".format(it)}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                brightnessLevel?.let { level ->
                                    Text(
                                        level.name.replace("_", " "),
                                        fontSize = 11.sp,
                                        color = when (level) {
                                            FrameAnalysisResult.BrightnessLevel.VERY_BRIGHT -> Color(
                                                0xFFFFC107
                                            )

                                            FrameAnalysisResult.BrightnessLevel.BRIGHT -> Color(
                                                0xFF8BC34A
                                            )

                                            FrameAnalysisResult.BrightnessLevel.NORMAL -> Color(
                                                0xFF4CAF50
                                            )

                                            FrameAnalysisResult.BrightnessLevel.DARK -> Color(
                                                0xFFFF9800
                                            )

                                            FrameAnalysisResult.BrightnessLevel.VERY_DARK -> Color(
                                                0xFFF44336
                                            )

                                            else -> Color.Gray
                                        }
                                    )
                                }
                                brightnessTime?.let { time ->
                                    if (time > 0) {
                                        Text(
                                            "[${"%.2f".format(time)}ms]",
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            } ?: Text("Brightness: -", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    // Luminance Analysis
                    luminanceAnalysis?.let { analysis ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                "Lighting Quality: ${analysis.quality.name.replace("_", " ")}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = when (analysis.quality) {
                                    com.kii.camera.LightingQuality.OPTIMAL -> Color(0xFF4CAF50)
                                    com.kii.camera.LightingQuality.ACCEPTABLE -> Color(0xFF8BC34A)
                                    com.kii.camera.LightingQuality.UNDEREXPOSED -> Color(0xFFF44336)
                                    com.kii.camera.LightingQuality.OVEREXPOSED -> Color(0xFFFF9800)
                                    com.kii.camera.LightingQuality.BACKLIT -> Color(0xFFFF5722)
                                    else -> Color.Gray
                                }
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Dark: ${(analysis.darknessRatio * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    "Clip: ${(analysis.clippingRatio * 100).toInt()}%",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                luminanceTime?.let { time ->
                                    if (time > 0) {
                                        Text(
                                            "[${"%.2f".format(time)}ms]",
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                            if (!analysis.quality.isSuitable()) {
                                analysis.quality.getSuggestedAction()?.let { suggestion ->
                                    Text(
                                        "💡 $suggestion",
                                        fontSize = 10.sp,
                                        color = Color(0xFFFF9800)
                                    )
                                }
                            }

                            // 히스토그램 표시
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Histogram",
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            HistogramChart(
                                histogram = analysis.histogram,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }

                    processingTime?.let {
                        Text(
                            "Total: ${"%.2f".format(it)}ms",
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}
