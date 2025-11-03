package com.kii.camera

import androidx.camera.core.CameraSelector

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
    val captureMode: CaptureMode = CaptureMode.MAXIMIZE_QUALITY
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

        fun build() = CameraConfig(
            preset = preset,
            lensFacing = lensFacing,
            enableImageAnalysis = enableImageAnalysis,
            enableImageCapture = enableImageCapture,
            enableVideoCapture = enableVideoCapture,
            flashMode = flashMode,
            captureMode = captureMode
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
