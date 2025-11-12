package com.kii.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

/**
 * 히스토그램 차트
 *
 * 256-bin 히스토그램을 시각화하는 Composable 함수입니다.
 * 단색 막대 그래프로 표시하며, 임계값 위치에 구분선을 그립니다.
 *
 * @param histogram 256개 bins의 히스토그램 데이터 (IntArray)
 * @param modifier Modifier
 * @param showThresholds 임계값 구분선 표시 여부 (기본: true)
 * @param darkThreshold 어두움 임계값 (기본: 50)
 * @param brightThreshold 밝음 임계값 (기본: 200)
 *
 * @author chaebin
 * @since 11/12/25
 */
@Composable
fun HistogramChart(
    histogram: IntArray,
    modifier: Modifier = Modifier,
    showThresholds: Boolean = true,
    darkThreshold: Int = 50,
    brightThreshold: Int = 200
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = modifier
            .height(120.dp)
            .fillMaxWidth()
    ) {
        if (histogram.isEmpty()) return@Canvas

        // 최대값 찾기 (정규화용)
        val maxValue = histogram.maxOrNull()?.toFloat() ?: 1f
        if (maxValue == 0f) return@Canvas

        // 막대 너비 계산
        val barWidth = size.width / 256f

        // 히스토그램 막대 그리기 (단색)
        histogram.forEachIndexed { index, value ->
            val normalizedHeight = (value.toFloat() / maxValue) * size.height

            drawRect(
                color = primaryColor.copy(alpha = 0.7f),
                topLeft = Offset(
                    x = index * barWidth,
                    y = size.height - normalizedHeight
                ),
                size = Size(
                    width = barWidth,
                    height = normalizedHeight
                )
            )
        }

        // 임계값 구분선 그리기
        if (showThresholds) {
            // 어두움 임계값 선 (50)
            val darkLineX = (darkThreshold.toFloat() / 256f) * size.width
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(darkLineX, 0f),
                end = Offset(darkLineX, size.height),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )

            // 밝음 임계값 선 (200)
            val brightLineX = (brightThreshold.toFloat() / 256f) * size.width
            drawLine(
                color = Color.White.copy(alpha = 0.6f),
                start = Offset(brightLineX, 0f),
                end = Offset(brightLineX, size.height),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 베이스라인 그리기 (옵션)
        drawLine(
            color = surfaceVariant,
            start = Offset(0f, size.height),
            end = Offset(size.width, size.height),
            strokeWidth = 1.dp.toPx()
        )
    }
}
