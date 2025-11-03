package com.kii.camera

import android.util.Size
import androidx.camera.core.AspectRatio

/**
 * 카메라 프리셋 설정
 *
 * @author chaebin
 * @since 10/31/25
 */
data class CameraPreset(
    val name: String,
    val targetResolution: Size? = null,
    val targetAspectRatio: Int = AspectRatio.RATIO_16_9,
    val targetFrameRate: Int = 30,
    val imageQuality: Int = 90,
    val videoQuality: VideoQuality = VideoQuality.HD,
    val adaptToHardware: Boolean = true
) {
    companion object {
        /**
         * 저화질 프리셋 (저사양 디바이스, 배터리 절약)
         */
        val LOW = CameraPreset(
            name = "LOW",
            targetResolution = Size(640, 480),
            targetAspectRatio = AspectRatio.RATIO_4_3,
            targetFrameRate = 24,
            imageQuality = 70,
            videoQuality = VideoQuality.SD,
            adaptToHardware = true
        )

        /**
         * 중간 화질 프리셋 (일반적인 사용)
         */
        val MEDIUM = CameraPreset(
            name = "MEDIUM",
            targetResolution = Size(1280, 720),
            targetAspectRatio = AspectRatio.RATIO_16_9,
            targetFrameRate = 30,
            imageQuality = 80,
            videoQuality = VideoQuality.HD,
            adaptToHardware = true
        )

        /**
         * 고화질 프리셋 (고품질 촬영)
         */
        val HIGH = CameraPreset(
            name = "HIGH",
            targetResolution = Size(1920, 1080),
            targetAspectRatio = AspectRatio.RATIO_16_9,
            targetFrameRate = 30,
            imageQuality = 90,
            videoQuality = VideoQuality.FULL_HD,
            adaptToHardware = true
        )

        /**
         * 최고화질 프리셋 (4K, 고사양 디바이스)
         */
        val ULTRA = CameraPreset(
            name = "ULTRA",
            targetResolution = Size(3840, 2160),
            targetAspectRatio = AspectRatio.RATIO_16_9,
            targetFrameRate = 30,
            imageQuality = 95,
            videoQuality = VideoQuality.UHD_4K,
            adaptToHardware = true
        )

        /**
         * 고프레임 프리셋 (60fps, 부드러운 영상)
         */
        val HIGH_FPS = CameraPreset(
            name = "HIGH_FPS",
            targetResolution = Size(1920, 1080),
            targetAspectRatio = AspectRatio.RATIO_16_9,
            targetFrameRate = 60,
            imageQuality = 85,
            videoQuality = VideoQuality.FULL_HD,
            adaptToHardware = true
        )

        /**
         * 커스텀 프리셋 빌더
         */
        fun custom(block: Builder.() -> Unit): CameraPreset {
            return Builder().apply(block).build()
        }
    }

    /**
     * 커스텀 프리셋 빌더
     */
    class Builder {
        var name: String = "CUSTOM"
        var targetResolution: Size? = null
        var targetAspectRatio: Int = AspectRatio.RATIO_16_9
        var targetFrameRate: Int = 30
        var imageQuality: Int = 90
        var videoQuality: VideoQuality = VideoQuality.HD
        var adaptToHardware: Boolean = true

        fun build() = CameraPreset(
            name = name,
            targetResolution = targetResolution,
            targetAspectRatio = targetAspectRatio,
            targetFrameRate = targetFrameRate,
            imageQuality = imageQuality,
            videoQuality = videoQuality,
            adaptToHardware = adaptToHardware
        )
    }

    /**
     * 영상 화질 등급
     */
    enum class VideoQuality(val width: Int, val height: Int, val bitrate: Int) {
        SD(640, 480, 2_000_000),           // 2 Mbps
        HD(1280, 720, 5_000_000),          // 5 Mbps
        FULL_HD(1920, 1080, 10_000_000),   // 10 Mbps
        UHD_4K(3840, 2160, 20_000_000);    // 20 Mbps

        fun toSize(): Size = Size(width, height)
    }
}
