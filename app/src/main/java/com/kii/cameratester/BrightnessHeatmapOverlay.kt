package com.kii.cameratester

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.kii.camera.SpatialBrightnessAnalysis
import kotlin.math.min

/**
 * 밝기 히트맵 오버레이 컴포저블
 *
 * 카메라 프레임 위에 그리드 기반 밝기 분석 결과를 시각화합니다.
 * - 각 셀을 밝기에 따라 색상으로 표시 (어두움→밝음: 파랑→초록→노랑→빨강)
 * - 클리핑 영역에 경고 표시
 * - 역광 감지 시 중앙/가장자리 표시
 *
 * @param analysis 공간적 밝기 분석 결과
 * @param modifier Modifier
 * @param showGrid 그리드 라인 표시 여부
 * @param showClipping 클리핑 경고 표시 여부
 * @param opacity 히트맵 투명도 (0.0 ~ 1.0)
 */
@Composable
fun BrightnessHeatmapOverlay(
    analysis: SpatialBrightnessAnalysis?,
    modifier: Modifier = Modifier,
    showGrid: Boolean = true,
    showClipping: Boolean = true,
    opacity: Float = 0.5f
) {
    if (analysis == null) return

    Canvas(modifier = modifier.fillMaxSize()) {
        val cellWidth = size.width / analysis.gridCols
        val cellHeight = size.height / analysis.gridRows

        // 1. 각 셀의 밝기를 색상으로 표시
        for (row in 0 until analysis.gridRows) {
            for (col in 0 until analysis.gridCols) {
                val brightness = analysis.getBrightness(row, col)
                val color = brightnessToColor(brightness).copy(alpha = opacity)

                drawRect(
                    color = color,
                    topLeft = Offset(col * cellWidth, row * cellHeight),
                    size = Size(cellWidth, cellHeight)
                )
            }
        }

        // 2. 그리드 라인 표시
        if (showGrid) {
            drawGridLines(analysis.gridRows, analysis.gridCols, cellWidth, cellHeight)
        }

        // 3. 클리핑 경고 표시
        if (showClipping) {
            val totalPixelsPerCell = (size.width * size.height / (analysis.gridRows * analysis.gridCols)).toInt()
            val glareZones = analysis.getGlareZones(totalPixelsPerCell, threshold = 0.05)

            for ((row, col) in glareZones) {
                drawClippingWarning(row, col, cellWidth, cellHeight)
            }
        }

        // 4. 역광 감지 시 중앙/가장자리 표시
        if (analysis.isBacklit) {
            drawBacklitIndicator()
        }
    }
}

/**
 * 밝기 값을 히트맵 색상으로 변환
 *
 * 0.0 (어두움) → 파랑
 * 0.25 → 초록
 * 0.5 → 노랑
 * 0.75 → 주황
 * 1.0 (밝음) → 빨강
 */
private fun brightnessToColor(brightness: Double): Color {
    return when {
        brightness < 0.25 -> {
            // 0.0 ~ 0.25: 검정 → 파랑
            val ratio = (brightness / 0.25).toFloat()
            Color(0f, 0f, ratio, 1f)
        }
        brightness < 0.5 -> {
            // 0.25 ~ 0.5: 파랑 → 초록
            val ratio = ((brightness - 0.25) / 0.25).toFloat()
            Color(0f, ratio, 1f - ratio, 1f)
        }
        brightness < 0.75 -> {
            // 0.5 ~ 0.75: 초록 → 노랑
            val ratio = ((brightness - 0.5) / 0.25).toFloat()
            Color(ratio, 1f, 0f, 1f)
        }
        else -> {
            // 0.75 ~ 1.0: 노랑 → 빨강
            val ratio = ((brightness - 0.75) / 0.25).toFloat()
            Color(1f, 1f - ratio, 0f, 1f)
        }
    }
}

/**
 * 그리드 라인 그리기
 */
private fun DrawScope.drawGridLines(
    gridRows: Int,
    gridCols: Int,
    cellWidth: Float,
    cellHeight: Float
) {
    val gridColor = Color.White.copy(alpha = 0.3f)
    val strokeWidth = 2f

    // 수직선
    for (col in 1 until gridCols) {
        val x = col * cellWidth
        drawLine(
            color = gridColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = strokeWidth
        )
    }

    // 수평선
    for (row in 1 until gridRows) {
        val y = row * cellHeight
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokeWidth
        )
    }
}

/**
 * 클리핑 경고 표시 (X 표시)
 */
private fun DrawScope.drawClippingWarning(
    row: Int,
    col: Int,
    cellWidth: Float,
    cellHeight: Float
) {
    val centerX = col * cellWidth + cellWidth / 2
    val centerY = row * cellHeight + cellHeight / 2
    val warningSize = min(cellWidth, cellHeight) * 0.3f
    val warningColor = Color.Red.copy(alpha = 0.8f)
    val strokeWidth = 4f

    // X 표시 그리기
    drawLine(
        color = warningColor,
        start = Offset(centerX - warningSize, centerY - warningSize),
        end = Offset(centerX + warningSize, centerY + warningSize),
        strokeWidth = strokeWidth
    )
    drawLine(
        color = warningColor,
        start = Offset(centerX + warningSize, centerY - warningSize),
        end = Offset(centerX - warningSize, centerY + warningSize),
        strokeWidth = strokeWidth
    )

    // 경고 원
    drawCircle(
        color = warningColor,
        radius = warningSize,
        center = Offset(centerX, centerY),
        style = Stroke(width = strokeWidth)
    )
}

/**
 * 역광 표시 (중앙 영역 강조)
 */
private fun DrawScope.drawBacklitIndicator() {
    val centerX = size.width / 2
    val centerY = size.height / 2
    val indicatorColor = Color.Yellow.copy(alpha = 0.6f)
    val strokeWidth = 6f

    // 중앙 영역 강조 (사각형)
    val boxSize = min(size.width, size.height) * 0.4f
    drawRect(
        color = indicatorColor,
        topLeft = Offset(centerX - boxSize / 2, centerY - boxSize / 2),
        size = Size(boxSize, boxSize),
        style = Stroke(width = strokeWidth)
    )

    // "BACKLIT" 텍스트 표시 (간단한 사각형으로 대체)
    val textBoxWidth = size.width * 0.3f
    val textBoxHeight = size.height * 0.08f
    drawRect(
        color = Color.Yellow.copy(alpha = 0.8f),
        topLeft = Offset(centerX - textBoxWidth / 2, 20f),
        size = Size(textBoxWidth, textBoxHeight)
    )
}

/**
 * 밝기 히트맵 범례 컴포저블
 *
 * 히트맵 색상의 의미를 설명하는 범례
 */
@Composable
fun BrightnessHeatmapLegend(
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val barWidth = size.width
        val barHeight = size.height
        val steps = 100

        // 그라디언트 바 그리기
        for (i in 0 until steps) {
            val brightness = i / steps.toDouble()
            val color = brightnessToColor(brightness)
            val x = (i / steps.toFloat()) * barWidth

            drawRect(
                color = color,
                topLeft = Offset(x, 0f),
                size = Size(barWidth / steps, barHeight)
            )
        }

        // 테두리
        drawRect(
            color = Color.White,
            topLeft = Offset(0f, 0f),
            size = Size(barWidth, barHeight),
            style = Stroke(width = 2f)
        )
    }
}
