package com.kii.camera

/**
 * Comprehensive luminance (brightness) analysis result for a camera frame.
 *
 * This class provides detailed information about the lighting conditions of a frame,
 * including histogram data, exposure metrics, and overall lighting quality assessment.
 *
 * Based on the Y-plane (luminance) of YUV_420_888 format, this analysis avoids
 * expensive RGB conversion and provides real-time lighting quality feedback.
 *
 * @property brightness Average brightness value (0.0 to 1.0), where 0.0 is black and 1.0 is white
 * @property histogram 256-bin histogram of Y-plane pixel values (0-255)
 * @property darknessRatio Ratio of very dark pixels (0-50 range) to total pixels (0.0 to 1.0)
 * @property clippingRatio Ratio of clipped pixels (255 value) to total pixels (0.0 to 1.0)
 * @property quality Overall lighting quality assessment
 * @property processingTimeMs Time taken to perform the analysis in milliseconds
 *
 * @see LightingQuality
 * @see FrameAnalysisResult
 */
data class LuminanceAnalysis(
    val brightness: Double,
    val histogram: IntArray,
    val darknessRatio: Double,
    val clippingRatio: Double,
    val quality: LightingQuality,
    val processingTimeMs: Long = 0
) {
    /**
     * Returns true if the frame is underexposed (too dark).
     * Uses histogram analysis: more than 75% of pixels in 0-50 range.
     */
    fun isUnderexposed(): Boolean = darknessRatio > DARKNESS_THRESHOLD

    /**
     * Returns true if the frame is overexposed (too bright).
     * Uses clipping analysis: more than 10% of pixels at maximum (255).
     */
    fun isOverexposed(): Boolean = clippingRatio > CLIPPING_THRESHOLD

    /**
     * Returns true if the frame is backlit (combination of dark and bright extremes).
     */
    fun isBacklit(): Boolean =
        darknessRatio > DARKNESS_THRESHOLD * 0.7 && clippingRatio > CLIPPING_THRESHOLD * 0.5

    /**
     * Returns the peak bin index (most common brightness value) from the histogram.
     * Range: 0-255
     */
    fun getPeakBrightness(): Int {
        return histogram.indices.maxByOrNull { histogram[it] } ?: 0
    }

    /**
     * Returns the median brightness value based on histogram.
     * This is more robust than mean brightness for skewed distributions.
     */
    fun getMedianBrightness(): Int {
        val totalPixels = histogram.sum()
        val halfPixels = totalPixels / 2
        var cumulative = 0

        for (i in histogram.indices) {
            cumulative += histogram[i]
            if (cumulative >= halfPixels) {
                return i
            }
        }
        return 127 // Fallback to middle gray
    }

    /**
     * Returns a human-readable summary of the analysis.
     */
    fun getSummary(): String {
        return buildString {
            append("Lighting: ${quality.getDescription()}")
            append(" | Brightness: ${(brightness * 100).toInt()}%")
            append(" | Darkness: ${(darknessRatio * 100).toInt()}%")
            append(" | Clipping: ${(clippingRatio * 100).toInt()}%")
            if (processingTimeMs > 0) {
                append(" | Time: ${processingTimeMs}ms")
            }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LuminanceAnalysis

        if (brightness != other.brightness) return false
        if (!histogram.contentEquals(other.histogram)) return false
        if (darknessRatio != other.darknessRatio) return false
        if (clippingRatio != other.clippingRatio) return false
        if (quality != other.quality) return false

        return true
    }

    override fun hashCode(): Int {
        var result = brightness.hashCode()
        result = 31 * result + histogram.contentHashCode()
        result = 31 * result + darknessRatio.hashCode()
        result = 31 * result + clippingRatio.hashCode()
        result = 31 * result + quality.hashCode()
        return result
    }

    companion object {
        /**
         * Threshold for darkness ratio to classify as UNDEREXPOSED.
         * Default: 0.75 (75% of pixels in 0-50 range)
         *
         * Based on LUMINANCE.md Section 4.2: "저조도 감지"
         */
        const val DARKNESS_THRESHOLD = 0.75

        /**
         * Threshold for clipping ratio to classify as OVEREXPOSED.
         * Default: 0.10 (10% of pixels at 255)
         *
         * Based on LUMINANCE.md Section 4.3: "과다 노출 감지"
         */
        const val CLIPPING_THRESHOLD = 0.10

        /**
         * Dark pixel range upper bound (inclusive).
         * Pixels with values 0-50 are considered "very dark".
         */
        const val DARK_PIXEL_THRESHOLD = 50

        /**
         * Creates a default/empty analysis with UNKNOWN quality.
         */
        fun createDefault() = LuminanceAnalysis(
            brightness = 0.5,
            histogram = IntArray(256) { 0 },
            darknessRatio = 0.0,
            clippingRatio = 0.0,
            quality = LightingQuality.UNKNOWN,
            processingTimeMs = 0
        )
    }
}
