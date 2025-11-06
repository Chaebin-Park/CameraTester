package com.kii.camera

/**
 * Represents the quality of lighting conditions for camera frames.
 *
 * Based on histogram analysis and exposure metadata, this enum categorizes
 * the lighting environment to help determine if conditions are suitable for
 * face recognition or other computer vision tasks.
 *
 * @see LuminanceAnalysis
 */
enum class LightingQuality {
    /**
     * Optimal lighting conditions.
     * Frame has good brightness distribution with no extreme exposure issues.
     * Best for face recognition and image analysis.
     */
    OPTIMAL,

    /**
     * Acceptable lighting conditions.
     * Frame quality is sufficient but not ideal. May have slight exposure issues
     * but recognition should still work with reduced accuracy.
     */
    ACCEPTABLE,

    /**
     * Underexposed frame - too dark.
     * More than 75% of pixels are in very dark range (0-50).
     * Face recognition will likely fail due to insufficient pixel data.
     * User should be advised to increase lighting or move to a brighter location.
     */
    UNDEREXPOSED,

    /**
     * Overexposed frame - too bright.
     * More than 10% of pixels are clipped at maximum brightness (255).
     * Face features may be "blown out" and lost.
     * User should be advised to avoid direct lighting or backlight.
     */
    OVEREXPOSED,

    /**
     * Backlit condition - combination of dark and bright extremes.
     * Both underexposed and overexposed regions detected.
     * Typically occurs when subject is in front of bright background (e.g., window).
     * Face recognition will likely fail.
     */
    BACKLIT,

    /**
     * Unknown lighting quality.
     * Analysis has not been performed or insufficient data.
     */
    UNKNOWN;

    /**
     * Returns true if the lighting quality is suitable for face recognition.
     */
    fun isSuitable(): Boolean = this == OPTIMAL || this == ACCEPTABLE

    /**
     * Returns a human-readable description of the lighting quality.
     */
    fun getDescription(): String = when (this) {
        OPTIMAL -> "Optimal lighting"
        ACCEPTABLE -> "Acceptable lighting"
        UNDEREXPOSED -> "Too dark - please increase lighting"
        OVEREXPOSED -> "Too bright - please avoid direct light"
        BACKLIT -> "Backlit - please adjust camera angle"
        UNKNOWN -> "Unknown lighting condition"
    }

    /**
     * Returns a suggested user action for improving lighting.
     * Returns null if no action needed (OPTIMAL/ACCEPTABLE).
     */
    fun getSuggestedAction(): String? = when (this) {
        UNDEREXPOSED -> "Move to a brighter location or turn on lights"
        OVEREXPOSED -> "Avoid direct lighting or move away from bright sources"
        BACKLIT -> "Adjust camera angle to avoid backlight (e.g., window behind you)"
        OPTIMAL, ACCEPTABLE, UNKNOWN -> null
    }
}
