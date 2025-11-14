package com.kii.camera

/**
 * 공간적 밝기 분석 결과
 *
 * Y-Plane 프레임을 공간적으로 분석한 결과를 담는 데이터 클래스
 * 그리드 기반 밝기, 클리핑, 중앙/가장자리 비교, 균일도 등의 정보를 포함
 */
data class SpatialBrightnessAnalysis(
    /**
     * 그리드 행 수
     */
    val gridRows: Int,

    /**
     * 그리드 열 수
     */
    val gridCols: Int,

    /**
     * 그리드별 평균 밝기 (0.0 ~ 1.0)
     * 크기: gridRows x gridCols
     * 인덱스: [row * gridCols + col]
     */
    val gridBrightness: DoubleArray,

    /**
     * 그리드별 하이라이트 클리핑 픽셀 수
     * 크기: gridRows x gridCols
     * 하이라이트 클리핑: Y >= 250 (과노출)
     */
    val gridHighlightClipping: IntArray,

    /**
     * 그리드별 섀도우 클리핑 픽셀 수
     * 크기: gridRows x gridCols
     * 섀도우 클리핑: Y <= 5 (저노출)
     */
    val gridShadowClipping: IntArray,

    /**
     * 중앙 영역 평균 밝기 (0.0 ~ 1.0)
     */
    val centerBrightness: Double,

    /**
     * 가장자리 영역 평균 밝기 (0.0 ~ 1.0)
     */
    val edgeBrightness: Double,

    /**
     * 밝기 평균값 (0.0 ~ 1.0)
     */
    val mean: Double,

    /**
     * 밝기 분산 (0.0 ~ 1.0)
     */
    val variance: Double,

    /**
     * 밝기 표준편차 (0.0 ~ 1.0)
     */
    val stddev: Double
) {
    /**
     * 밝기 균일도 (0.0 ~ 1.0, 높을수록 균일)
     * 표준편차가 낮을수록 균일함
     */
    val brightnessUniformity: Double
        get() = 1.0 - stddev.coerceIn(0.0, 1.0)

    /**
     * 역광 여부 판단
     * 가장자리가 중앙보다 훨씬 밝으면 역광으로 판단
     */
    val isBacklit: Boolean
        get() {
            val edgeBrighterThanCenter = edgeBrightness > centerBrightness + 0.2
            val centerDark = centerBrightness < 0.4
            return edgeBrighterThanCenter && centerDark
        }

    /**
     * 빛번짐 발생 그리드 좌표 리스트
     * 하이라이트 클리핑이 해당 셀 전체 픽셀의 5% 이상인 경우 빛번짐으로 판단
     */
    fun getGlareZones(totalPixelsPerCell: Int, threshold: Double = 0.05): List<Pair<Int, Int>> {
        val glareZones = mutableListOf<Pair<Int, Int>>()
        for (row in 0 until gridRows) {
            for (col in 0 until gridCols) {
                val index = row * gridCols + col
                val clippingRatio = gridHighlightClipping[index].toDouble() / totalPixelsPerCell
                if (clippingRatio >= threshold) {
                    glareZones.add(Pair(row, col))
                }
            }
        }
        return glareZones
    }

    /**
     * 특정 그리드 셀의 밝기 반환
     */
    fun getBrightness(row: Int, col: Int): Double {
        require(row in 0 until gridRows && col in 0 until gridCols) {
            "Invalid grid position: ($row, $col)"
        }
        return gridBrightness[row * gridCols + col]
    }

    /**
     * 특정 그리드 셀의 하이라이트 클리핑 픽셀 수 반환
     */
    fun getHighlightClipping(row: Int, col: Int): Int {
        require(row in 0 until gridRows && col in 0 until gridCols) {
            "Invalid grid position: ($row, $col)"
        }
        return gridHighlightClipping[row * gridCols + col]
    }

    /**
     * 특정 그리드 셀의 섀도우 클리핑 픽셀 수 반환
     */
    fun getShadowClipping(row: Int, col: Int): Int {
        require(row in 0 until gridRows && col in 0 until gridCols) {
            "Invalid grid position: ($row, $col)"
        }
        return gridShadowClipping[row * gridCols + col]
    }

    /**
     * 밝기 분포 요약 문자열
     */
    fun getSummary(): String {
        return buildString {
            appendLine("=== 공간적 밝기 분석 ===")
            appendLine("그리드: ${gridRows}x${gridCols}")
            appendLine("중앙 밝기: ${"%.2f".format(centerBrightness * 100)}%")
            appendLine("가장자리 밝기: ${"%.2f".format(edgeBrightness * 100)}%")
            appendLine("평균 밝기: ${"%.2f".format(mean * 100)}%")
            appendLine("균일도: ${"%.2f".format(brightnessUniformity * 100)}%")
            appendLine("역광: ${if (isBacklit) "예" else "아니오"}")
        }
    }

    // Array를 사용하기 때문에 equals/hashCode 오버라이드 필요
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SpatialBrightnessAnalysis

        if (gridRows != other.gridRows) return false
        if (gridCols != other.gridCols) return false
        if (!gridBrightness.contentEquals(other.gridBrightness)) return false
        if (!gridHighlightClipping.contentEquals(other.gridHighlightClipping)) return false
        if (!gridShadowClipping.contentEquals(other.gridShadowClipping)) return false
        if (centerBrightness != other.centerBrightness) return false
        if (edgeBrightness != other.edgeBrightness) return false
        if (mean != other.mean) return false
        if (variance != other.variance) return false
        if (stddev != other.stddev) return false

        return true
    }

    override fun hashCode(): Int {
        var result = gridRows
        result = 31 * result + gridCols
        result = 31 * result + gridBrightness.contentHashCode()
        result = 31 * result + gridHighlightClipping.contentHashCode()
        result = 31 * result + gridShadowClipping.contentHashCode()
        result = 31 * result + centerBrightness.hashCode()
        result = 31 * result + edgeBrightness.hashCode()
        result = 31 * result + mean.hashCode()
        result = 31 * result + variance.hashCode()
        result = 31 * result + stddev.hashCode()
        return result
    }
}
