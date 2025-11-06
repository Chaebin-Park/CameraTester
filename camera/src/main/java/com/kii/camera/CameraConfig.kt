package com.kii.camera

import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageProxy

/**
 * 카메라 설정
 *
 * @author chaebin
 * @since 10/31/25
 */
data class CameraConfig(
    val preset: CameraPreset = CameraPreset.LOW,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val enableImageAnalysis: Boolean = true,
    val enableImageCapture: Boolean = true,
    val enableVideoCapture: Boolean = false,
    val flashMode: FlashMode = FlashMode.OFF,
    val captureMode: CaptureMode = CaptureMode.MAXIMIZE_QUALITY,
    val frameAnalysisConfig: FrameAnalysisConfig? = null
) {
    companion object {
        /**
         * 기본 설정 (후면 카메라, 중간 화질)
         */
        val DEFAULT = CameraConfig()

        /**
         * 전면 카메라 기본 설정
         */
        val FRONT_CAMERA = CameraConfig(
            lensFacing = CameraSelector.LENS_FACING_FRONT
        )

        /**
         * 고화질 촬영 설정
         */
        val HIGH_QUALITY = CameraConfig(
            preset = CameraPreset.HIGH,
            captureMode = CaptureMode.MAXIMIZE_QUALITY
        )

        /**
         * 빠른 처리 설정 (낮은 지연시간)
         */
        val LOW_LATENCY = CameraConfig(
            preset = CameraPreset.MEDIUM,
            captureMode = CaptureMode.MINIMIZE_LATENCY
        )

        /**
         * 영상 녹화 설정
         */
        val VIDEO_RECORDING = CameraConfig(
            preset = CameraPreset.HIGH,
            enableVideoCapture = true,
            captureMode = CaptureMode.MAXIMIZE_QUALITY
        )
    }

    /**
     * 플래시 모드
     */
    enum class FlashMode {
        ON,     // 항상 켜짐
        OFF,    // 항상 꺼짐
        AUTO    // 자동
    }

    /**
     * 캡처 모드
     */
    enum class CaptureMode {
        MAXIMIZE_QUALITY,   // 화질 우선
        MINIMIZE_LATENCY    // 속도 우선
    }

    /**
     * 설정 빌더
     */
    class Builder {
        var preset: CameraPreset = CameraPreset.MEDIUM
        var lensFacing: Int = CameraSelector.LENS_FACING_BACK
        var enableImageAnalysis: Boolean = true
        var enableImageCapture: Boolean = true
        var enableVideoCapture: Boolean = false
        var flashMode: FlashMode = FlashMode.OFF
        var captureMode: CaptureMode = CaptureMode.MAXIMIZE_QUALITY
        var frameAnalysisConfig: FrameAnalysisConfig? = null

        fun build() = CameraConfig(
            preset = preset,
            lensFacing = lensFacing,
            enableImageAnalysis = enableImageAnalysis,
            enableImageCapture = enableImageCapture,
            enableVideoCapture = enableVideoCapture,
            flashMode = flashMode,
            captureMode = captureMode,
            frameAnalysisConfig = frameAnalysisConfig
        )
    }
}

/**
 * 카메라 상태
 */
sealed class CameraState {
    object Idle : CameraState()
    object Starting : CameraState()
    object Running : CameraState()
    object Stopping : CameraState()
    data class Error(val exception: Exception) : CameraState()
}

/**
 * 카메라 이벤트
 */
sealed class CameraEvent {
    object CameraStarted : CameraEvent()
    object CameraStopped : CameraEvent()
    data class PhotoCaptured(val filePath: String) : CameraEvent()
    data class Error(val exception: Exception) : CameraEvent()
}

/**
 * 카메라 노출 상태
 */
data class CameraExposureState(
    val exposureCompensationIndex: Int = 0,
    val exposureCompensationMin: Int = 0,
    val exposureCompensationMax: Int = 0,
    val exposureCompensationStep: Rational? = null,
    val isSupported: Boolean = false
) {
    /**
     * 현재 노출 보정 값 (EV)
     */
    fun getEV(): Float {
        if (exposureCompensationStep == null) return 0f
        return exposureCompensationIndex * exposureCompensationStep.toFloat()
    }

    override fun toString(): String {
        return if (isSupported) {
            "EV: ${"%.1f".format(getEV())} (${exposureCompensationIndex}, range: $exposureCompensationMin~$exposureCompensationMax)"
        } else {
            "Not Supported"
        }
    }
}

/**
 * 분수 표현 (Rational number)
 */
data class Rational(
    val numerator: Int,
    val denominator: Int
) {
    fun toFloat(): Float = numerator.toFloat() / denominator.toFloat()
    fun toDouble(): Double = numerator.toDouble() / denominator.toDouble()
}

/**
 * 캡처된 이미지 정보
 */
data class CapturedImageInfo(
    val filePath: String,
    val fileName: String,
    val width: Int,
    val height: Int,
    val fileSizeBytes: Long,
    val preset: CameraPreset,
    val lensFacing: Int,
    val timestamp: Long
) {
    /**
     * 파일 크기를 읽기 쉬운 형식으로 변환
     */
    fun getFileSizeFormatted(): String {
        return when {
            fileSizeBytes < 1024 -> "$fileSizeBytes B"
            fileSizeBytes < 1024 * 1024 -> "${"%.2f".format(fileSizeBytes / 1024.0)} KB"
            else -> "${"%.2f".format(fileSizeBytes / (1024.0 * 1024.0))} MB"
        }
    }

    /**
     * 렌즈 방향을 문자열로 변환
     */
    fun getLensFacingString(): String {
        return if (lensFacing == CameraSelector.LENS_FACING_BACK) "Back" else "Front"
    }

    override fun toString(): String {
        return buildString {
            appendLine("File: $fileName")
            appendLine("Path: $filePath")
            appendLine("Resolution: ${width}x${height}")
            appendLine("Size: ${getFileSizeFormatted()}")
            appendLine("Preset: ${preset.name}")
            appendLine("Lens: ${getLensFacingString()}")
            appendLine("Timestamp: $timestamp")
        }
    }
}

/**
 * 프레임 분석 설정
 */
data class FrameAnalysisConfig(
    val enableSharpness: Boolean = true,
    val enableBrightness: Boolean = false,
    val enableLuminance: Boolean = false,
    val sampleRate: Int = 4,
    val frameSamplingRate: Int = 10,
    val roi: ROI = ROI.CENTER_50,
    val useNativeProcessing: Boolean = true
) {
    companion object {
        /**
         * 기본 분석 설정 (선명도만, 10프레임당 1회)
         */
        val DEFAULT = FrameAnalysisConfig()

        /**
         * 고성능 분석 (선명도+밝기+조명 품질, Native 처리)
         */
        val HIGH_PERFORMANCE = FrameAnalysisConfig(
            enableSharpness = true,
            enableBrightness = true,
            enableLuminance = true,
            sampleRate = 4,
            frameSamplingRate = 10,
            roi = ROI.CENTER_50,
            useNativeProcessing = true
        )

        /**
         * 전체 영역 분석 (ROI 없음)
         */
        val FULL_FRAME = FrameAnalysisConfig(
            enableSharpness = true,
            enableBrightness = false,
            sampleRate = 4,
            frameSamplingRate = 10,
            roi = ROI.FULL,
            useNativeProcessing = true
        )

        /**
         * 정밀 분석 (샘플링 최소화)
         */
        val PRECISE = FrameAnalysisConfig(
            enableSharpness = true,
            enableBrightness = true,
            sampleRate = 2,
            frameSamplingRate = 5,
            roi = ROI.CENTER_70,
            useNativeProcessing = true
        )
    }
}

/**
 * 프레임 분석 결과
 */
data class FrameAnalysisResult(
    val imageProxy: ImageProxy,
    val sharpness: Double? = null,
    val brightness: Double? = null,
    val luminanceAnalysis: LuminanceAnalysis? = null,
    val processingTimeMs: Long = 0,
    val sharpnessTimeMs: Long = 0,
    val brightnessTimeMs: Long = 0,
    val luminanceTimeMs: Long = 0,
    val width: Int,
    val height: Int,
    val timestamp: Long = System.currentTimeMillis()
) {
    /**
     * 선명도 수준 판정
     */
    fun getSharpnessLevel(): SharpnessLevel {
        return when {
            sharpness == null -> SharpnessLevel.UNKNOWN
            sharpness < 5.0 -> SharpnessLevel.BLURRY
            sharpness < 10.0 -> SharpnessLevel.SLIGHTLY_BLURRY
            sharpness < 15.0 -> SharpnessLevel.ACCEPTABLE
            sharpness < 20.0 -> SharpnessLevel.SHARP
            else -> SharpnessLevel.VERY_SHARP
        }
    }

    /**
     * 밝기 수준 판정
     */
    fun getBrightnessLevel(): BrightnessLevel {
        return when {
            brightness == null -> BrightnessLevel.UNKNOWN
            brightness < 0.2 -> BrightnessLevel.VERY_DARK
            brightness < 0.4 -> BrightnessLevel.DARK
            brightness < 0.6 -> BrightnessLevel.NORMAL
            brightness < 0.8 -> BrightnessLevel.BRIGHT
            else -> BrightnessLevel.VERY_BRIGHT
        }
    }

    override fun toString(): String {
        return buildString {
            appendLine("Frame Analysis Result:")
            appendLine("  Resolution: ${width}x${height}")
            sharpness?.let { appendLine("  Sharpness: ${"%.2f".format(it)} (${getSharpnessLevel()})") }
            brightness?.let { appendLine("  Brightness: ${"%.2f".format(it)} (${getBrightnessLevel()})") }
            luminanceAnalysis?.let {
                appendLine("  Lighting Quality: ${it.quality.getDescription()}")
                appendLine("  Darkness Ratio: ${"%.1f".format(it.darknessRatio * 100)}%")
                appendLine("  Clipping Ratio: ${"%.1f".format(it.clippingRatio * 100)}%")
            }
            appendLine("  Processing Time: ${processingTimeMs}ms")
            appendLine("  Timestamp: $timestamp")
        }
    }

    /**
     * 선명도 수준
     */
    enum class SharpnessLevel {
        UNKNOWN,
        BLURRY,
        SLIGHTLY_BLURRY,
        ACCEPTABLE,
        SHARP,
        VERY_SHARP
    }

    /**
     * 밝기 수준
     */
    enum class BrightnessLevel {
        UNKNOWN,
        VERY_DARK,
        DARK,
        NORMAL,
        BRIGHT,
        VERY_BRIGHT
    }
}
