package com.kii.camera

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager as Camera2Manager
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.kii.common.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * 카메라 매니저
 * CameraX를 래핑하여 편리하게 사용할 수 있도록 제공
 *
 * @author chaebin
 * @since 10/31/25
 */
class CameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private var config: CameraConfig = CameraConfig.DEFAULT
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private var preview: Preview? = null

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    // Coroutine scope for frame analysis
    private val analysisScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val frameCounter = AtomicInteger(0)

    // 카메라 설정 상태
    private val _configState = MutableStateFlow(config)
    val configState: StateFlow<CameraConfig> = _configState.asStateFlow()

    // 카메라 상태
    private val _cameraState = MutableStateFlow<CameraState>(CameraState.Idle)
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    // 프레임 스트림 (Hot Flow - 구독자가 없어도 프레임 발행)
    private val _frameFlow = MutableSharedFlow<ImageProxy>(
        replay = 0,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val frameFlow: SharedFlow<ImageProxy> = _frameFlow.asSharedFlow()

    // 프레임 분석 스트림 (자동으로 분석된 프레임 결과 제공)
    // 주의: 무거운 작업(얼굴 인식 등)을 수행하므로 반드시 백그라운드 스레드에서 collect하세요
    // 예: launch(Dispatchers.Default) { frameAnalysisFlow.collect { ... } }
    private val _frameAnalysisFlow = MutableSharedFlow<FrameAnalysisResult>(
        replay = 0,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val frameAnalysisFlow: SharedFlow<FrameAnalysisResult> = _frameAnalysisFlow.asSharedFlow()

    // 카메라 이벤트
    private val _cameraEvent = MutableSharedFlow<CameraEvent>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val cameraEvent: SharedFlow<CameraEvent> = _cameraEvent.asSharedFlow()

    // Preview Surface Provider 저장
    private var surfaceProvider: Preview.SurfaceProvider? = null

    // 프레임 분석 Job (취소 가능)
    private var frameAnalysisJob: Job? = null

    /**
     * 프레임 자동 분석 시작
     */
    private fun startFrameAnalysis(analysisConfig: FrameAnalysisConfig) {
        // 기존 분석 중지
        stopFrameAnalysis()

        Logger.d("CameraManager", "Starting frame analysis with config: $analysisConfig")

        frameAnalysisJob = analysisScope.launch {
            frameFlow.collect { imageProxy ->
                try {
                    // --- 1. 프레임 샘플링 (공통 로직) ---
                    val currentCount = frameCounter.incrementAndGet()
                    if (currentCount % analysisConfig.frameSamplingRate != 0) {
                        // 샘플링에서 제외된 프레임은 즉시 close
                        imageProxy.close()
                        return@collect
                    }

                    // --- 2. 분석 활성화 여부 확인 ---
                    val analysisEnabled = analysisConfig.enableLuminance ||
                            analysisConfig.enableSharpness ||
                            analysisConfig.enableBrightness ||
                            analysisConfig.enableSpatialAnalysis

                    val result: FrameAnalysisResult

                    if (analysisEnabled) {
                        // --- 3-A. 분석 실행 (기존 if 블록 로직) ---
                        Logger.e(
                            tag = "TEST_FRAME_ANALYSIS",
                            "Frame analysis started with config: $analysisConfig"
                        )

                        val startTime = System.nanoTime()
                        val yPlaneBytes = imageProxy.toYPlaneByteArray()

                        if (yPlaneBytes == null) {
                            Logger.w("CameraManager", "Failed to extract Y plane")
                            imageProxy.close()
                            return@collect
                        }

                        // 선명도 계산
                        var sharpnessTimeMs = 0.0
                        val sharpness = if (analysisConfig.enableSharpness) {
                            val sharpnessStartTime = System.nanoTime()
                            val res = FrameProcessor.calculateSharpnessDirect(
                                pixelData = yPlaneBytes,
                                width = imageProxy.width,
                                height = imageProxy.height,
                                sampleRate = analysisConfig.sampleRate,
                                roi = analysisConfig.roi
                            )
                            sharpnessTimeMs = (System.nanoTime() - sharpnessStartTime) / 1_000_000.0
                            res
                        } else null

                        // 밝기 계산
                        var brightnessTimeMs = 0.0
                        val brightness = if (analysisConfig.enableBrightness) {
                            val brightnessStartTime = System.nanoTime()
                            val res = FrameProcessor.calculateBrightnessDirect(
                                pixelData = yPlaneBytes,
                                width = imageProxy.width,
                                height = imageProxy.height,
                                sampleRate = analysisConfig.sampleRate,
                                roi = analysisConfig.roi
                            )
                            brightnessTimeMs =
                                (System.nanoTime() - brightnessStartTime) / 1_000_000.0
                            res
                        } else null

                        // 조명 품질 분석
                        var luminanceTimeMs = 0.0
                        val luminanceAnalysis = if (analysisConfig.enableLuminance) {
                            val luminanceStartTime = System.nanoTime()
                            val histogram = FrameProcessor.calculateHistogramDirect(
                                yPlaneData = yPlaneBytes,
                                width = imageProxy.width,
                                height = imageProxy.height,
                                sampleRate = analysisConfig.sampleRate,
                                roi = analysisConfig.roi
                            )
                            val res = FrameProcessor.analyzeLuminanceQuality(histogram)
                            luminanceTimeMs = (System.nanoTime() - luminanceStartTime) / 1_000_000.0
                            res.copy(processingTimeMs = luminanceTimeMs)
                        } else null

                        // 공간적 밝기 분석
                        var spatialAnalysisTimeMs = 0.0
                        val spatialAnalysis = if (analysisConfig.enableSpatialAnalysis) {
                            val spatialStartTime = System.nanoTime()
                            val res = FrameProcessor.calculateSpatialBrightnessDirect(
                                yPlaneData = yPlaneBytes,
                                width = imageProxy.width,
                                height = imageProxy.height,
                                gridRows = 3,
                                gridCols = 3,
                                centerRatio = 0.5,
                                sampleRate = analysisConfig.sampleRate
                            )
                            spatialAnalysisTimeMs = (System.nanoTime() - spatialStartTime) / 1_000_000.0
                            res
                        } else null

                        val endTime = System.nanoTime()
                        val processingTimeMs = (endTime - startTime) / 1_000_000.0

                        Logger.d(
                            "CameraManager",
                            "Frame analyzed: sharpness=$sharpness [${"%.2f".format(sharpnessTimeMs)}ms], brightness=$brightness [${
                                "%.2f".format(brightnessTimeMs)
                            }ms], lighting=${luminanceAnalysis?.quality} [${
                                "%.2f".format(
                                    luminanceTimeMs
                                )
                            }ms], spatial=${if (spatialAnalysis != null) "enabled" else "disabled"} [${"%.2f".format(spatialAnalysisTimeMs)}ms], total=${"%.2f".format(processingTimeMs)}ms"
                        )

                        // Bitmap 변환 (항상 수행)
                        val bitmap = try {
                            imageProxy.toBitmap()
                        } catch (e: Exception) {
                            Logger.e("CameraManager", "Failed to convert ImageProxy to Bitmap", e)
                            imageProxy.close()
                            return@collect
                        }

                        // 결과 객체 생성 (분석 데이터 포함)
                        result = FrameAnalysisResult(
                            bitmap = bitmap,
                            rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                            sharpness = sharpness,
                            brightness = brightness,
                            luminanceAnalysis = luminanceAnalysis,
                            spatialAnalysis = spatialAnalysis,
                            processingTimeMs = processingTimeMs,
                            sharpnessTimeMs = sharpnessTimeMs,
                            brightnessTimeMs = brightnessTimeMs,
                            luminanceTimeMs = luminanceTimeMs,
                            spatialAnalysisTimeMs = spatialAnalysisTimeMs,
                            width = imageProxy.width,
                            height = imageProxy.height
                        )

                    } else {
                        // --- 3-B. 분석 비활성화 (기존 else 블록 로직) ---
                        // 샘플링은 통과했으므로, 프레임 정보만 전달
                        Logger.e(
                            tag = "TEST_NO_ANALYSIS",
                            "No analysis enabled, passing frame info. (Sampled)"
                        )

                        // Bitmap 변환 (항상 수행)
                        val bitmap = try {
                            imageProxy.toBitmap()
                        } catch (e: Exception) {
                            Logger.e("CameraManager", "Failed to convert ImageProxy to Bitmap", e)
                            imageProxy.close()
                            return@collect
                        }

                        // 결과 객체 생성 (프레임 정보만 포함)
                        result = FrameAnalysisResult(
                            bitmap = bitmap,
                            rotationDegrees = imageProxy.imageInfo.rotationDegrees,
                            width = imageProxy.width,
                            height = imageProxy.height
                        )
                    }

                    // --- 4. 결과 발행 (공통 로직) ---
                    val emitted = _frameAnalysisFlow.tryEmit(result)
                    if (!emitted) {
                        Logger.w(
                            "CameraManager",
                            "Failed to emit frame analysis result, buffer full"
                        )
                    }

                    // ImageProxy는 라이브러리에서 자동 close (메모리 누수 방지)
                    imageProxy.close()

                } catch (e: Exception) {
                    Logger.e("CameraManager", "Failed to analyze frame", e)
                    imageProxy.close()
                }
            }
        }
    }
    /**
     * 프레임 분석 중지
     */
    private fun stopFrameAnalysis() {
        if (frameAnalysisJob != null) {
            Logger.d("CameraManager", "Stopping frame analysis")
            frameAnalysisJob?.cancel()
            frameAnalysisJob = null
            frameCounter.set(0)
        }
    }

    /**
     * 디바이스가 지원하는 해상도 목록 로깅
     */
    private fun logSupportedResolutions(cameraSelector: CameraSelector) {
        try {
            val camera2Manager = context.getSystemService(Context.CAMERA_SERVICE) as Camera2Manager
            val cameraId = getCameraIdFromSelector(cameraSelector, camera2Manager)

            if (cameraId != null) {
                val characteristics = camera2Manager.getCameraCharacteristics(cameraId)
                val configMap = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

                if (configMap != null) {
                    val outputSizes = configMap.getOutputSizes(android.graphics.ImageFormat.YUV_420_888)

                    if (outputSizes != null && outputSizes.isNotEmpty()) {
                        Logger.d("CameraManager", "===== Supported Resolutions =====")
                        Logger.d("CameraManager", "Total: ${outputSizes.size} resolutions available")
                        Logger.d("CameraManager", "Requested: ${config.preset.targetResolution}")

                        // 해상도를 넓이 기준으로 정렬 (큰 것부터)
                        val sortedSizes = outputSizes.sortedByDescending { it.width * it.height }

                        sortedSizes.forEachIndexed { index, size ->
                            val megapixels = (size.width * size.height) / 1_000_000.0
                            val aspectRatio = size.width.toFloat() / size.height.toFloat()
                            Logger.d(
                                "CameraManager",
                                "  ${index + 1}. ${size.width}x${size.height} " +
                                "(${String.format("%.1f", megapixels)}MP, " +
                                "ratio: ${String.format("%.2f", aspectRatio)})"
                            )
                        }
                        Logger.d("CameraManager", "================================")
                    } else {
                        Logger.w("CameraManager", "No supported resolutions found")
                    }
                } else {
                    Logger.w("CameraManager", "StreamConfigurationMap is null")
                }
            } else {
                Logger.w("CameraManager", "Failed to get camera ID from selector")
            }
        } catch (e: Exception) {
            Logger.e("CameraManager", "Failed to log supported resolutions", e)
        }
    }

    /**
     * CameraSelector로부터 Camera ID 가져오기
     */
    private fun getCameraIdFromSelector(selector: CameraSelector, camera2Manager: Camera2Manager): String? {
        return try {
            val lensFacing = config.lensFacing
            val cameraIds = camera2Manager.cameraIdList

            for (id in cameraIds) {
                val characteristics = camera2Manager.getCameraCharacteristics(id)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)

                val camera2Facing = when (lensFacing) {
                    CameraSelector.LENS_FACING_BACK -> CameraCharacteristics.LENS_FACING_BACK
                    CameraSelector.LENS_FACING_FRONT -> CameraCharacteristics.LENS_FACING_FRONT
                    else -> CameraCharacteristics.LENS_FACING_BACK
                }

                if (facing == camera2Facing) {
                    return id
                }
            }
            null
        } catch (e: Exception) {
            Logger.e("CameraManager", "Failed to get camera ID", e)
            null
        }
    }

    /**
     * 카메라 시작
     */
    suspend fun startCamera() {
        if (_cameraState.value == CameraState.Running) {
            Logger.w("CameraManager", "Camera is already running")
            return
        }

        try {
            _cameraState.value = CameraState.Starting
            Logger.d("CameraManager", "Starting camera with config: $config")
            Logger.d("CameraManager", "FrameAnalysisConfig: ${config.frameAnalysisConfig}")

            val provider = ProcessCameraProvider.getInstance(context).get()
            cameraProvider = provider

            // 기존 바인딩 해제
            provider.unbindAll()

            // 카메라 선택
            val cameraSelector = CameraSelector.Builder()
                .requireLensFacing(config.lensFacing)
                .build()

            // Use cases 설정
            val useCases = mutableListOf<androidx.camera.core.UseCase>()

            // Preview 설정
            preview = Preview.Builder()
                .apply {
                    config.preset.targetResolution?.let { resolution ->
                        val resolutionSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    resolution,
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                )
                            )
                            .build()
                        setResolutionSelector(resolutionSelector)
                    }
                }
                .build()
                .also { preview ->
                    surfaceProvider?.let { preview.setSurfaceProvider(it) }
                }
            useCases.add(preview!!)

            // Image Analysis (Frame Flow) - 항상 활성화
            imageAnalysis = ImageAnalysis.Builder()
                .apply {
                    config.preset.targetResolution?.let { resolution ->
                        val resolutionSelector = ResolutionSelector.Builder()
                            .setResolutionStrategy(
                                ResolutionStrategy(
                                    resolution,
                                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                )
                            )
                            .build()
                        setResolutionSelector(resolutionSelector)
                    }
                    setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                }
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        // 실제 해상도 로깅 (첫 프레임에만)
                        if (_frameFlow.subscriptionCount.value > 0 && frameCounter.get() == 0) {
                            Logger.d(
                                "CameraManager",
                                "ImageAnalysis actual resolution: ${imageProxy.width}x${imageProxy.height} " +
                                "(requested: ${config.preset.targetResolution})"
                            )
                        }

                        // Frame을 Flow로 emit
                        val emitted = _frameFlow.tryEmit(imageProxy)
                        if (!emitted) {
                            // Buffer가 가득 차서 emit 실패 시 close
                            imageProxy.close()
                        }
                    }
                }
            useCases.add(imageAnalysis!!)

            // Image Capture
            if (config.enableImageCapture) {
                imageCapture = ImageCapture.Builder()
                    .apply {
                        // ResolutionSelector를 사용하여 정확한 해상도 제어
                        config.preset.targetResolution?.let { resolution ->
                            val resolutionSelector = ResolutionSelector.Builder()
                                .setResolutionStrategy(
                                    ResolutionStrategy(
                                        resolution,
                                        ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                                    )
                                )
                                .build()
                            setResolutionSelector(resolutionSelector)
                        } ?: run {
                            // targetResolution이 없으면 aspectRatio 사용 (deprecated)
                            setTargetAspectRatio(config.preset.targetAspectRatio)
                        }

                        // CaptureMode: MINIMIZE_LATENCY를 사용하면 해상도 제한이 더 잘 적용됨
                        setCaptureMode(
                            when (config.captureMode) {
                                CameraConfig.CaptureMode.MAXIMIZE_QUALITY -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                                CameraConfig.CaptureMode.MINIMIZE_LATENCY -> ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                            }
                        )
                        setJpegQuality(config.preset.imageQuality)
                    }
                    .build()
                useCases.add(imageCapture!!)
            }

            // 카메라 바인딩
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                *useCases.toTypedArray()
            )

            _cameraState.value = CameraState.Running
            _cameraEvent.emit(CameraEvent.CameraStarted)
            Logger.d("CameraManager", "Camera started successfully")

            // 디바이스가 지원하는 해상도 목록 로깅
            logSupportedResolutions(cameraSelector)

            // SurfaceProvider 재연결 (카메라 전환 시 필요)
            surfaceProvider?.let { provider ->
                preview?.setSurfaceProvider(provider)
                Logger.d("CameraManager", "SurfaceProvider reconnected")
            }

            // 자동 프레임 분석 시작 (카메라가 실행된 후에만)
            config.frameAnalysisConfig?.let { analysisConfig ->
                startFrameAnalysis(analysisConfig)
            }

        } catch (e: Exception) {
            Logger.e("CameraManager", "Failed to start camera", e)
            _cameraState.value = CameraState.Error(e)
            _cameraEvent.emit(CameraEvent.Error(e))
        }
    }

    /**
     * 카메라 정지
     */
    fun stopCamera() {
        if (_cameraState.value != CameraState.Running) {
            Logger.w("CameraManager", "Camera is not running")
            return
        }

        try {
            _cameraState.value = CameraState.Stopping
            Logger.d("CameraManager", "Stopping camera")

            // 프레임 분석 중지
            stopFrameAnalysis()

            cameraProvider?.unbindAll()
            camera = null
            imageAnalysis = null
            imageCapture = null
            preview = null

            _cameraState.value = CameraState.Idle
            Logger.d("CameraManager", "Camera stopped successfully")

        } catch (e: Exception) {
            Logger.e("CameraManager", "Failed to stop camera", e)
            _cameraState.value = CameraState.Error(e)
        }
    }

    /**
     * Preview Surface Provider 설정
     */
    fun setSurfaceProvider(provider: Preview.SurfaceProvider) {
        surfaceProvider = provider
        preview?.setSurfaceProvider(provider)
    }

    /**
     * Preview 객체 가져오기
     */
    fun getPreview(): Preview? = preview

    /**
     * ImageCapture 객체 가져오기
     */
    fun getImageCapture(): ImageCapture? = imageCapture

    /**
     * Camera 객체 가져오기 (줌, 플래시 등 제어)
     */
    fun getCamera(): Camera? = camera

    /**
     * 현재 카메라 설정 가져오기
     */
    fun getCurrentConfig(): CameraConfig = config

    /**
     * 현재 카메라의 노출 상태 가져오기
     * @return CameraExposureState 노출 상태 정보 (ExposureCompensation 등)
     */
    fun getExposureState(): CameraExposureState {
        return try {
            val cameraInfo = camera?.cameraInfo
            if (cameraInfo != null) {
                val exposureState = cameraInfo.exposureState
                val step = exposureState.exposureCompensationStep
                val range = exposureState.exposureCompensationRange
                CameraExposureState(
                    exposureCompensationIndex = exposureState.exposureCompensationIndex,
                    exposureCompensationMin = range.lower,
                    exposureCompensationMax = range.upper,
                    exposureCompensationStep = Rational(step.numerator, step.denominator),
                    isSupported = exposureState.isExposureCompensationSupported
                )
            } else {
                CameraExposureState()
            }
        } catch (e: Exception) {
            Logger.e("CameraManager", "Failed to get exposure state", e)
            CameraExposureState()
        }
    }

    /**
     * 플래시 모드 변경
     */
    fun setFlashMode(mode: CameraConfig.FlashMode) {
        imageCapture?.flashMode = when (mode) {
            CameraConfig.FlashMode.ON -> ImageCapture.FLASH_MODE_ON
            CameraConfig.FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
            CameraConfig.FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        }
    }

    /**
     * 줌 배율 설정 (0.0 ~ 1.0)
     */
    fun setZoomRatio(ratio: Float) {
        camera?.cameraControl?.setLinearZoom(ratio.coerceIn(0f, 1f))
    }

    /**
     * 사진 캡처
     * @return CapturedImageInfo 캡처된 이미지 정보 (파일 경로, 크기, 해상도 등)
     */
    suspend fun capturePhoto(outputDirectory: java.io.File): CapturedImageInfo {
        return suspendCancellableCoroutine { continuation ->
            try {
                if (imageCapture == null) {
                    val error =
                        IllegalStateException("ImageCapture is not enabled. Set enableImageCapture=true in config")
                    Logger.e("CameraManager", "Failed to capture photo", error)
                    continuation.resumeWith(Result.failure(error))
                    return@suspendCancellableCoroutine
                }

                // 파일 생성
                val fileName = "IMG_${System.currentTimeMillis()}.jpg"
                val photoFile = java.io.File(outputDirectory, fileName)

                val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                imageCapture!!.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            try {
                                Logger.d("CameraManager", "Photo saved: ${photoFile.absolutePath}")

                                // 이미지 정보 추출
                                val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                                val fileSize = photoFile.length()

                                val info = CapturedImageInfo(
                                    filePath = photoFile.absolutePath,
                                    fileName = fileName,
                                    width = bitmap?.width ?: 0,
                                    height = bitmap?.height ?: 0,
                                    fileSizeBytes = fileSize,
                                    preset = config.preset,
                                    lensFacing = config.lensFacing,
                                    timestamp = System.currentTimeMillis()
                                )

                                bitmap?.recycle()

                                _cameraEvent.tryEmit(CameraEvent.PhotoCaptured(photoFile.absolutePath))
                                continuation.resumeWith(Result.success(info))
                            } catch (e: Exception) {
                                Logger.e("CameraManager", "Failed to process captured image", e)
                                continuation.resumeWith(Result.failure(e))
                            }
                        }

                        override fun onError(exception: androidx.camera.core.ImageCaptureException) {
                            Logger.e("CameraManager", "Failed to capture photo", exception)
                            _cameraEvent.tryEmit(CameraEvent.Error(exception))
                            continuation.resumeWith(Result.failure(exception))
                        }
                    }
                )
            } catch (e: Exception) {
                Logger.e("CameraManager", "Failed to capture photo", e)
                continuation.resumeWith(Result.failure(e))
            }
        }
    }

    /**
     * 설정 업데이트 (카메라 재시작)
     */
    suspend fun updateConfig(newConfig: CameraConfig) {
        if (config == newConfig) {
            Logger.w("CameraManager", "Config is already the same")
            return
        }

        try {
            Logger.d("CameraManager", "Updating config: $newConfig")

            // 기존 카메라 정지
            val wasRunning = _cameraState.value == CameraState.Running
            if (wasRunning) {
                stopCamera()
            }

            // Config 업데이트
            config = newConfig
            _configState.value = newConfig

            // 카메라 재시작
            if (wasRunning) {
                startCamera()
            }
        } catch (e: Exception) {
            Logger.e("CameraManager", "Failed to update config", e)
            throw e
        }
    }

    /**
     * Preset 변경
     */
    suspend fun updatePreset(preset: CameraPreset) {
        updateConfig(config.copy(preset = preset))
    }

    /**
     * 카메라 렌즈 전환 (전면/후면)
     */
    suspend fun toggleCamera(): Int {
        val newLensFacing = if (config.lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        return switchCamera(newLensFacing)
    }

    /**
     * 특정 카메라로 전환
     */
    suspend fun switchCamera(lensFacing: Int): Int {
        if (config.lensFacing == lensFacing) {
            Logger.w("CameraManager", "Already using camera with lensFacing: $lensFacing")
            return lensFacing
        }

        try {
            Logger.d("CameraManager", "Switching camera to lensFacing: $lensFacing")

            // 기존 카메라 정지
            val wasRunning = _cameraState.value == CameraState.Running
            if (wasRunning) {
                stopCamera()
            }

            // 새 config로 업데이트
            config = config.copy(lensFacing = lensFacing)
            _configState.value = config

            // 카메라 재시작
            if (wasRunning) {
                startCamera()
            }

            return lensFacing
        } catch (e: Exception) {
            Logger.e("CameraManager", "Failed to switch camera", e)
            throw e
        }
    }

    /**
     * 리소스 정리
     */
    fun release() {
        Logger.d("CameraManager", "Releasing camera resources")
        stopFrameAnalysis()
        stopCamera()
        cameraExecutor.shutdown()
        analysisScope.cancel()
    }

    // ==================== Java 친화적인 API ====================

    private var frameAnalysisCallback: FrameAnalysisCallback? = null
    private var callbackJob: Job? = null

    /**
     * Java/레거시 프로젝트를 위한 콜백 기반 프레임 분석
     *
     * 사용 예시 (Java):
     * ```java
     * cameraManager.setFrameAnalysisCallback(new FrameAnalysisCallback() {
     *     @Override
     *     public void onFrameAnalyzed(FrameAnalysisResult result) {
     *         Log.d("Camera", "Sharpness: " + result.getSharpness());
     *         result.getImageProxy().close(); // 반드시 호출!
     *     }
     * });
     * ```
     *
     * @param callback 프레임 분석 콜백
     */
    fun setFrameAnalysisCallback(callback: FrameAnalysisCallback?) {
        // 기존 콜백 제거
        callbackJob?.cancel()
        frameAnalysisCallback = callback

        if (callback != null) {
            // Flow를 콜백으로 변환
            callbackJob = analysisScope.launch {
                frameAnalysisFlow.collect { result ->
                    try {
                        callback.onFrameAnalyzed(result)
                    } catch (e: Exception) {
                        Logger.e("CameraManager", "Error in frame analysis callback", e)
                        callback.onError(e)
                    }
                }
            }
        }
    }

    /**
     * Java 8+ 람다를 위한 간소화 버전
     *
     * 사용 예시 (Java 8+):
     * ```java
     * cameraManager.setFrameAnalysisCallback(result -> {
     *     Log.d("Camera", "Sharpness: " + result.getSharpness());
     *     result.getImageProxy().close();
     * });
     * ```
     */
    fun setFrameAnalysisCallback(callback: SimpleFrameAnalysisCallback?) {
        if (callback != null) {
            setFrameAnalysisCallback(object : FrameAnalysisCallback {
                override fun onFrameAnalyzed(result: FrameAnalysisResult) {
                    callback.onFrameAnalyzed(result)
                }
            })
        } else {
            setFrameAnalysisCallback(null as FrameAnalysisCallback?)
        }
    }

    /**
     * Java에서 사용하기 쉬운 동기식 카메라 시작
     *
     * 백그라운드 스레드에서 호출됩니다.
     */
    @JvmOverloads
    fun startCameraSync(onSuccess: Runnable? = null, onError: ((Exception) -> Unit)? = null) {
        analysisScope.launch {
            try {
                startCamera()
                onSuccess?.run()
            } catch (e: Exception) {
                Logger.e("CameraManager", "Failed to start camera", e)
                onError?.invoke(e)
            }
        }
    }

    /**
     * Java에서 사용하기 쉬운 동기식 사진 캡처
     */
    fun capturePhotoSync(
        outputDirectory: java.io.File,
        onSuccess: (CapturedImageInfo) -> Unit,
        onError: (Exception) -> Unit
    ) {
        analysisScope.launch {
            try {
                val info = capturePhoto(outputDirectory)
                onSuccess(info)
            } catch (e: Exception) {
                Logger.e("CameraManager", "Failed to capture photo", e)
                onError(e)
            }
        }
    }

    /**
     * Java에서 사용하기 쉬운 동기식 카메라 전환
     */
    @JvmOverloads
    fun toggleCameraSync(onSuccess: Runnable? = null, onError: ((Exception) -> Unit)? = null) {
        analysisScope.launch {
            try {
                toggleCamera()
                onSuccess?.run()
            } catch (e: Exception) {
                Logger.e("CameraManager", "Failed to toggle camera", e)
                onError?.invoke(e)
            }
        }
    }

    companion object {
        /**
         * 디바이스에서 사용 가능한 카메라 확인
         */
        fun hasCamera(context: Context, lensFacing: Int): Boolean {
            return try {
                val provider = ProcessCameraProvider.getInstance(context).get()
                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                provider.hasCamera(selector)
            } catch (e: Exception) {
                Logger.e("CameraManager", "Failed to check camera availability", e)
                false
            }
        }

        /**
         * 후면 카메라 존재 여부
         */
        fun hasBackCamera(context: Context): Boolean {
            return hasCamera(context, CameraSelector.LENS_FACING_BACK)
        }

        /**
         * 전면 카메라 존재 여부
         */
        fun hasFrontCamera(context: Context): Boolean {
            return hasCamera(context, CameraSelector.LENS_FACING_FRONT)
        }
    }
}
