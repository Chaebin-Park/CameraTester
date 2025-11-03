package com.kii.camera

import android.content.Context
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.kii.common.Logger
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

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

    // 카메라 이벤트
    private val _cameraEvent = MutableSharedFlow<CameraEvent>(
        replay = 0,
        extraBufferCapacity = 10,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val cameraEvent: SharedFlow<CameraEvent> = _cameraEvent.asSharedFlow()

    // Preview Surface Provider 저장
    private var surfaceProvider: Preview.SurfaceProvider? = null

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
                    config.preset.targetResolution?.let { setTargetResolution(it) }
                        ?: setTargetAspectRatio(config.preset.targetAspectRatio)
                }
                .build()
                .also { preview ->
                    surfaceProvider?.let { preview.setSurfaceProvider(it) }
                }
            useCases.add(preview!!)

            // Image Analysis (Frame Flow)
            if (config.enableImageAnalysis) {
                imageAnalysis = ImageAnalysis.Builder()
                    .apply {
                        config.preset.targetResolution?.let { setTargetResolution(it) }
                            ?: setTargetAspectRatio(config.preset.targetAspectRatio)
                        setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    }
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            // Frame을 Flow로 emit
                            val emitted = _frameFlow.tryEmit(imageProxy)
                            if (!emitted) {
                                // Buffer가 가득 차서 emit 실패 시 close
                                imageProxy.close()
                            }
                        }
                    }
                useCases.add(imageAnalysis!!)
            }

            // Image Capture
            if (config.enableImageCapture) {
                imageCapture = ImageCapture.Builder()
                    .apply {
                        config.preset.targetResolution?.let { setTargetResolution(it) }
                            ?: setTargetAspectRatio(config.preset.targetAspectRatio)
                        setCaptureMode(
                            when (config.captureMode) {
                                CameraConfig.CaptureMode.MAXIMIZE_QUALITY -> ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
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
        stopCamera()
        cameraExecutor.shutdown()
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
