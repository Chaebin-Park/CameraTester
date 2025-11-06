package com.kii.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageProxy
import com.kii.common.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import androidx.core.graphics.scale

/**
 * 프레임 처리 유틸리티
 *
 * @author chaebin
 * @since 10/31/25
 */

/**
 * ROI (Region of Interest) 설정
 *
 * @param centerX 중심점 X 좌표 비율 (0.0 ~ 1.0, 기본값 0.5 = 중앙)
 * @param centerY 중심점 Y 좌표 비율 (0.0 ~ 1.0, 기본값 0.5 = 중앙)
 * @param widthRatio 폭 비율 (0.0 ~ 1.0, 기본값 0.5 = 50%)
 * @param heightRatio 높이 비율 (0.0 ~ 1.0, 기본값 0.5 = 50%)
 */
data class ROI(
    val centerX: Float = 0.5f,
    val centerY: Float = 0.5f,
    val widthRatio: Float = 0.5f,
    val heightRatio: Float = 0.5f
) {
    companion object {
        /** 중앙 50% 영역 */
        val CENTER_50 = ROI(0.5f, 0.5f, 0.5f, 0.5f)

        /** 중앙 30% 영역 (더 작은 영역) */
        val CENTER_30 = ROI(0.5f, 0.5f, 0.3f, 0.3f)

        /** 중앙 70% 영역 (더 큰 영역) */
        val CENTER_70 = ROI(0.5f, 0.5f, 0.7f, 0.7f)

        /** 전체 영역 (ROI 없음) */
        val FULL = ROI(0.5f, 0.5f, 1.0f, 1.0f)
    }

    /**
     * 실제 픽셀 좌표로 변환
     */
    fun toRect(imageWidth: Int, imageHeight: Int): Rect {
        val roiWidth = (imageWidth * widthRatio).toInt()
        val roiHeight = (imageHeight * heightRatio).toInt()

        val left = ((imageWidth * centerX) - (roiWidth / 2f)).toInt().coerceIn(0, imageWidth - roiWidth)
        val top = ((imageHeight * centerY) - (roiHeight / 2f)).toInt().coerceIn(0, imageHeight - roiHeight)

        return Rect(left, top, left + roiWidth, top + roiHeight)
    }
}

/**
 * ImageProxy를 Bitmap으로 변환
 */
fun ImageProxy.toBitmap(): Bitmap? {
    return try {
        val buffer = planes[0].buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        Logger.e("FrameProcessor", "Failed to convert ImageProxy to Bitmap", e)
        null
    }
}

/**
 * ImageProxy를 YUV Bitmap으로 변환 (YUV 이미지 형식)
 *
 * JPEG 인코딩/디코딩을 거치므로 느림 (30-40ms)
 * 색상 정보가 필요한 경우에만 사용 권장
 */
fun ImageProxy.toYuvBitmap(): Bitmap? {
    return try {
        if (format != ImageFormat.YUV_420_888) {
            Logger.w("FrameProcessor", "Image format is not YUV_420_888")
            return null
        }

        val yBuffer = planes[0].buffer
        val uBuffer = planes[1].buffer
        val vBuffer = planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, width, height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, width, height), 100, out)
        val imageBytes = out.toByteArray()
        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    } catch (e: Exception) {
        Logger.e("FrameProcessor", "Failed to convert YUV ImageProxy to Bitmap", e)
        null
    }
}

/**
 * ImageProxy의 Y plane을 그레이스케일 Bitmap으로 변환
 *
 * JPEG 우회로 매우 빠름 (5-10ms)
 * 선명도, 밝기 측정 등 색상이 불필요한 분석에 최적
 *
 * @return ALPHA_8 형식의 그레이스케일 Bitmap (메모리 1/4)
 */
fun ImageProxy.toGrayscaleBitmap(): Bitmap? {
    return try {
        if (format != ImageFormat.YUV_420_888) {
            Logger.w("FrameProcessor", "Image format is not YUV_420_888")
            return null
        }

        // Y plane만 추출 (밝기 정보)
        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val ySize = yBuffer.remaining()

        // Stride 고려하여 데이터 추출
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        val bitmap: Bitmap

        if (yRowStride == width && yPixelStride == 1) {
            // 연속된 메모리 레이아웃 - 직접 복사 (가장 빠름)
            val yBytes = ByteArray(ySize)
            yBuffer.get(yBytes)

            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
            bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(yBytes))
        } else {
            // Stride/Padding이 있는 경우 - 행 단위 복사
            val yBytes = ByteArray(width * height)

            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                if (yPixelStride == 1) {
                    // 연속된 픽셀 - 행 전체 복사
                    yBuffer.get(yBytes, row * width, width)
                } else {
                    // 픽셀 사이 간격이 있는 경우 - 픽셀 단위 복사
                    for (col in 0 until width) {
                        yBytes[row * width + col] = yBuffer.get(row * yRowStride + col * yPixelStride)
                    }
                }
            }

            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
            bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(yBytes))
        }

        bitmap
    } catch (e: Exception) {
        Logger.e("FrameProcessor", "Failed to convert Y plane to grayscale Bitmap", e)
        null
    }
}

/**
 * ImageProxy의 Y plane을 ByteArray로 직접 추출
 *
 * Bitmap 생성 오버헤드 없이 바로 Native 함수로 전달 가능 (1-2ms)
 * 가장 빠른 방법
 *
 * @return Y plane ByteArray
 */
fun ImageProxy.toYPlaneByteArray(): ByteArray? {
    return try {
        if (format != ImageFormat.YUV_420_888) {
            Logger.w("FrameProcessor", "Image format is not YUV_420_888")
            return null
        }

        val yPlane = planes[0]
        val yBuffer = yPlane.buffer
        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride

        if (yRowStride == width && yPixelStride == 1) {
            // 연속된 메모리 - 직접 복사 (가장 빠름)
            val yBytes = ByteArray(yBuffer.remaining())
            yBuffer.get(yBytes)
            yBytes
        } else {
            // Stride/Padding 제거하여 복사
            val yBytes = ByteArray(width * height)
            for (row in 0 until height) {
                yBuffer.position(row * yRowStride)
                if (yPixelStride == 1) {
                    yBuffer.get(yBytes, row * width, width)
                } else {
                    for (col in 0 until width) {
                        yBytes[row * width + col] = yBuffer.get(row * yRowStride + col * yPixelStride)
                    }
                }
            }
            yBytes
        }
    } catch (e: Exception) {
        Logger.e("FrameProcessor", "Failed to extract Y plane", e)
        null
    }
}

/**
 * ImageProxy ByteBuffer 추출
 */
fun ImageProxy.toByteBuffer(): ByteBuffer {
    return planes[0].buffer
}

/**
 * ImageProxy ByteArray 추출
 */
fun ImageProxy.toByteArray(): ByteArray {
    val buffer = toByteBuffer()
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return bytes
}

/**
 * Frame Flow를 Bitmap Flow로 변환
 * 주의: close()는 자동으로 호출됨
 */
fun Flow<ImageProxy>.mapToBitmap(): Flow<Bitmap?> {
    return this.map { imageProxy ->
        val bitmap = imageProxy.toBitmap()
        imageProxy.close()
        bitmap
    }
}

/**
 * Frame Flow를 YUV Bitmap Flow로 변환
 * 주의: close()는 자동으로 호출됨
 */
fun Flow<ImageProxy>.mapToYuvBitmap(): Flow<Bitmap?> {
    return this.map { imageProxy ->
        val bitmap = imageProxy.toYuvBitmap()
        imageProxy.close()
        bitmap
    }
}

/**
 * Frame Flow를 ByteArray Flow로 변환
 * 주의: close()는 자동으로 호출됨
 */
fun Flow<ImageProxy>.mapToByteArray(): Flow<ByteArray> {
    return this.map { imageProxy ->
        val bytes = imageProxy.toByteArray()
        imageProxy.close()
        bytes
    }
}

/**
 * 프레임 처리 헬퍼 클래스
 */
object FrameProcessor {

    // Native 라이브러리 로드
    private var nativeLibraryLoaded = false

    init {
        try {
            System.loadLibrary("camera_native")
            nativeLibraryLoaded = true
            Logger.d("FrameProcessor", "Native library loaded successfully")
        } catch (e: UnsatisfiedLinkError) {
            Logger.w("FrameProcessor", "Failed to load native library, using Kotlin implementation", e)
            nativeLibraryLoaded = false
        }
    }

    /**
     * Native 라이브러리 사용 가능 여부
     */
    fun isNativeAvailable(): Boolean = nativeLibraryLoaded

    /**
     * Native 선명도 계산 (JNI)
     */
    @JvmStatic
    private external fun calculateSharpnessNative(
        pixelData: ByteArray,
        width: Int,
        height: Int,
        sampleRate: Int
    ): Double

    /**
     * Native 선명도 계산 NEON SIMD 버전 (ARM only)
     */
    @JvmStatic
    private external fun calculateSharpnessNativeNEON(
        pixelData: ByteArray,
        width: Int,
        height: Int,
        sampleRate: Int
    ): Double

    /**
     * Native 밝기 계산 (JNI)
     */
    @JvmStatic
    private external fun calculateBrightnessNative(
        pixelData: ByteArray,
        width: Int,
        height: Int,
        sampleRate: Int
    ): Double

    /**
     * Native ROI 밝기 계산 (JNI)
     */
    @JvmStatic
    private external fun calculateBrightnessNativeROI(
        pixelData: ByteArray,
        width: Int,
        height: Int,
        sampleRate: Int,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int
    ): Double

    /**
     * Native 히스토그램 계산 (JNI)
     *
     * Based on LUMINANCE.md Section 4.1
     *
     * @param yPlaneData Y-plane ByteArray
     * @param width 이미지 폭
     * @param height 이미지 높이
     * @return 256-bin histogram (0-255 brightness levels)
     */
    @JvmStatic
    private external fun calculateHistogramNative(
        yPlaneData: ByteArray,
        width: Int,
        height: Int
    ): IntArray

    /**
     * Native ROI 히스토그램 계산 (JNI)
     *
     * @param yPlaneData Y-plane ByteArray
     * @param width 이미지 폭
     * @param height 이미지 높이
     * @param roiLeft ROI 좌측 시작점
     * @param roiTop ROI 상단 시작점
     * @param roiWidth ROI 폭
     * @param roiHeight ROI 높이
     * @return 256-bin histogram
     */
    @JvmStatic
    private external fun calculateHistogramNativeROI(
        yPlaneData: ByteArray,
        width: Int,
        height: Int,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int
    ): IntArray

    /**
     * 프레임 회전
     */
    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /**
     * 프레임 크기 조정
     */
    fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val ratio = minOf(
            maxWidth.toFloat() / bitmap.width,
            maxHeight.toFloat() / bitmap.height
        )

        val width = (bitmap.width * ratio).toInt()
        val height = (bitmap.height * ratio).toInt()

        return bitmap.scale(width, height)
    }

    /**
     * 프레임 자르기
     */
    fun cropBitmap(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }

    /**
     * 프레임 품질 계산 (간단한 선명도 측정)
     * Laplacian 분산을 사용한 선명도 측정
     *
     * RGB/ARGB 및 그레이스케일(ALPHA_8) 모두 지원
     * Native 구현 사용 가능 시 자동으로 사용 (5-10배 빠름)
     *
     * @param bitmap 측정할 비트맵 (ARGB_8888 또는 ALPHA_8)
     * @param sampleRate 샘플링 비율 (1 = 모든 픽셀, 2 = 2픽셀마다, 기본값 4)
     * @param useNative Native 구현 사용 여부 (기본값 true, 사용 불가 시 Kotlin 구현)
     * @param roi ROI 영역 (null이면 전체 영역)
     * @return 선명도 값 (0~255 범위, 높을수록 선명함)
     */
    fun calculateSharpness(
        bitmap: Bitmap,
        sampleRate: Int = 4,
        useNative: Boolean = true,
        roi: ROI? = null
    ): Double {
        try {
            // ROI 적용 (영역 자르기)
            val targetBitmap = if (roi != null && roi != ROI.FULL) {
                val rect = roi.toRect(bitmap.width, bitmap.height)
                cropBitmap(bitmap, rect.left, rect.top, rect.width(), rect.height())
            } else {
                bitmap
            }

            val result = when (targetBitmap.config) {
                Bitmap.Config.ALPHA_8 -> {
                    // 그레이스케일 - Native 또는 Kotlin 구현
                    if (useNative && nativeLibraryLoaded) {
                        calculateSharpnessGrayscaleNative(targetBitmap, sampleRate)
                    } else {
                        calculateSharpnessGrayscale(targetBitmap, sampleRate)
                    }
                }
                else -> {
                    // RGB/ARGB - 그레이스케일 변환 후 처리
                    calculateSharpnessRGB(targetBitmap, sampleRate)
                }
            }

            // ROI로 자른 Bitmap은 메모리 해제
            if (targetBitmap != bitmap) {
                targetBitmap.recycle()
            }

            return result
        } catch (e: Exception) {
            Logger.e("FrameProcessor", "Failed to calculate sharpness", e)
            return 0.0
        }
    }

    /**
     * ByteArray 기반 선명도 계산 (Bitmap 생성 우회)
     *
     * 가장 빠른 방법 - Bitmap 생성 오버헤드 없음
     *
     * @param pixelData Y plane ByteArray
     * @param width 이미지 폭
     * @param height 이미지 높이
     * @param sampleRate 샘플링 비율
     * @param roi ROI 영역 (null이면 전체 영역)
     * @return 선명도 값
     */
    fun calculateSharpnessDirect(
        pixelData: ByteArray,
        width: Int,
        height: Int,
        sampleRate: Int = 4,
        roi: ROI? = null
    ): Double {
        return try {
            if (nativeLibraryLoaded) {
                if (roi != null && roi != ROI.FULL) {
                    val rect = roi.toRect(width, height)
                    calculateSharpnessNativeROI(
                        pixelData, width, height, sampleRate,
                        rect.left, rect.top, rect.width(), rect.height()
                    )
                } else {
                    calculateSharpnessNative(pixelData, width, height, sampleRate)
                }
            } else {
                // Fallback: Bitmap 생성 후 처리
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
                bitmap.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(pixelData))
                val result = calculateSharpness(bitmap, sampleRate, useNative = false, roi)
                bitmap.recycle()
                result
            }
        } catch (e: Exception) {
            Logger.e("FrameProcessor", "Failed to calculate sharpness direct", e)
            0.0
        }
    }

    /**
     * Native ROI 함수 (JNI)
     */
    @JvmStatic
    private external fun calculateSharpnessNativeROI(
        pixelData: ByteArray,
        width: Int,
        height: Int,
        sampleRate: Int,
        roiLeft: Int,
        roiTop: Int,
        roiWidth: Int,
        roiHeight: Int
    ): Double

    /**
     * ByteArray 기반 밝기 계산 (Bitmap 생성 우회)
     *
     * 가장 빠른 방법 - Bitmap 생성 오버헤드 없음
     *
     * @param pixelData Y plane ByteArray
     * @param width 이미지 폭
     * @param height 이미지 높이
     * @param sampleRate 샘플링 비율 (기본값 4)
     * @param roi ROI 영역 (null이면 전체 영역)
     * @return 밝기 값 (0.0 ~ 1.0)
     */
    fun calculateBrightnessDirect(
        pixelData: ByteArray,
        width: Int,
        height: Int,
        sampleRate: Int = 4,
        roi: ROI? = null
    ): Double {
        return try {
            if (nativeLibraryLoaded) {
                if (roi != null && roi != ROI.FULL) {
                    val rect = roi.toRect(width, height)
                    calculateBrightnessNativeROI(
                        pixelData, width, height, sampleRate,
                        rect.left, rect.top, rect.width(), rect.height()
                    )
                } else {
                    calculateBrightnessNative(pixelData, width, height, sampleRate)
                }
            } else {
                // Fallback: Kotlin 구현
                calculateBrightnessKotlin(pixelData, width, height, sampleRate, roi)
            }
        } catch (e: Exception) {
            Logger.e("FrameProcessor", "Failed to calculate brightness direct", e)
            0.0
        }
    }

    /**
     * Kotlin 기반 밝기 계산 (Native fallback)
     */
    private fun calculateBrightnessKotlin(
        pixelData: ByteArray,
        width: Int,
        height: Int,
        sampleRate: Int = 4,
        roi: ROI? = null
    ): Double {
        val rect = roi?.toRect(width, height)

        val startY = rect?.top ?: 0
        val endY = rect?.bottom ?: height
        val startX = rect?.left ?: 0
        val endX = rect?.right ?: width

        var sum = 0L
        var count = 0

        for (i in startY until endY step sampleRate) {
            for (j in startX until endX step sampleRate) {
                val value = pixelData[i * width + j].toInt() and 0xFF
                sum += value
                count++
            }
        }

        return if (count > 0) (sum / count.toDouble()) / 255.0 else 0.0
    }

    /**
     * 그레이스케일 Bitmap의 선명도 계산 (Native 구현)
     */
    private fun calculateSharpnessGrayscaleNative(bitmap: Bitmap, sampleRate: Int): Double {
        // ALPHA_8 형식을 ByteArray로 변환
        val buffer = java.nio.ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()

        val pixels = ByteArray(bitmap.width * bitmap.height)
        buffer.get(pixels)

        // Native 함수 호출
        return calculateSharpnessNative(pixels, bitmap.width, bitmap.height, sampleRate)
    }

    /**
     * 그레이스케일 Bitmap의 선명도 계산 (최적화)
     */
    private fun calculateSharpnessGrayscale(bitmap: Bitmap, sampleRate: Int): Double {
        var sum = 0.0
        var count = 0

        // ALPHA_8 형식은 ByteBuffer로 직접 접근 가능
        val buffer = java.nio.ByteBuffer.allocate(bitmap.byteCount)
        bitmap.copyPixelsToBuffer(buffer)
        buffer.rewind()

        val pixels = ByteArray(bitmap.width * bitmap.height)
        buffer.get(pixels)

        for (i in sampleRate until bitmap.height - sampleRate step sampleRate) {
            for (j in sampleRate until bitmap.width - sampleRate step sampleRate) {
                val idx = i * bitmap.width + j

                // 그레이스케일 값 (0-255)
                val gray = pixels[idx].toInt() and 0xFF
                val grayUp = pixels[(i - sampleRate) * bitmap.width + j].toInt() and 0xFF
                val grayDown = pixels[(i + sampleRate) * bitmap.width + j].toInt() and 0xFF
                val grayLeft = pixels[i * bitmap.width + (j - sampleRate)].toInt() and 0xFF
                val grayRight = pixels[i * bitmap.width + (j + sampleRate)].toInt() and 0xFF

                // Laplacian 계산
                val laplacian = kotlin.math.abs(4 * gray - grayUp - grayDown - grayLeft - grayRight)
                sum += laplacian
                count++
            }
        }

        return if (count > 0) sum / count else 0.0
    }

    /**
     * RGB Bitmap의 선명도 계산 (그레이스케일 변환 포함)
     */
    private fun calculateSharpnessRGB(bitmap: Bitmap, sampleRate: Int): Double {
        var sum = 0.0
        var count = 0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (i in sampleRate until bitmap.height - sampleRate step sampleRate) {
            for (j in sampleRate until bitmap.width - sampleRate step sampleRate) {
                val idx = i * bitmap.width + j

                // RGB를 그레이스케일로 변환
                val pixel = pixels[idx]
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val gray = (0.299 * r + 0.587 * g + 0.114 * b).toInt()

                // 주변 픽셀의 그레이스케일 값
                val pixelUp = pixels[(i - sampleRate) * bitmap.width + j]
                val grayUp = (0.299 * ((pixelUp shr 16) and 0xFF) +
                             0.587 * ((pixelUp shr 8) and 0xFF) +
                             0.114 * (pixelUp and 0xFF)).toInt()

                val pixelDown = pixels[(i + sampleRate) * bitmap.width + j]
                val grayDown = (0.299 * ((pixelDown shr 16) and 0xFF) +
                               0.587 * ((pixelDown shr 8) and 0xFF) +
                               0.114 * (pixelDown and 0xFF)).toInt()

                val pixelLeft = pixels[i * bitmap.width + (j - sampleRate)]
                val grayLeft = (0.299 * ((pixelLeft shr 16) and 0xFF) +
                               0.587 * ((pixelLeft shr 8) and 0xFF) +
                               0.114 * (pixelLeft and 0xFF)).toInt()

                val pixelRight = pixels[i * bitmap.width + (j + sampleRate)]
                val grayRight = (0.299 * ((pixelRight shr 16) and 0xFF) +
                                0.587 * ((pixelRight shr 8) and 0xFF) +
                                0.114 * (pixelRight and 0xFF)).toInt()

                // Laplacian 계산
                val laplacian = kotlin.math.abs(4 * gray - grayUp - grayDown - grayLeft - grayRight)
                sum += laplacian
                count++
            }
        }

        return if (count > 0) sum / count else 0.0
    }

    /**
     * 선명도 값을 사람이 읽기 쉬운 문자열로 변환
     */
    fun getSharpnessQuality(sharpness: Double): String {
        return when {
            sharpness >= 50.0 -> "Excellent"
            sharpness >= 30.0 -> "Good"
            sharpness >= 15.0 -> "Fair"
            sharpness >= 8.0 -> "Poor"
            else -> "Very Poor"
        }
    }

    /**
     * 프레임 밝기 계산 (픽셀 기반 - 간단한 방법)
     * @deprecated 실제 노출 값을 사용하는 calculateExposureBrightness 사용 권장
     */
    @Deprecated("Use calculateExposureBrightness with ImageProxy for accurate brightness measurement")
    fun calculateBrightness(bitmap: Bitmap): Double {
        var sum = 0.0
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            // 인간의 색 인지를 고려한 밝기 계산
            sum += 0.299 * r + 0.587 * g + 0.114 * b
        }

        return sum / pixels.size
    }

    /**
     * 실제 노출 값(EV)을 기반으로 밝기 계산
     * CameraX ImageProxy의 메타데이터에서 ISO와 노출 시간을 추출하여 계산
     *
     * @param imageProxy 카메라 프레임
     * @return ExposureInfo 노출 정보 (EV, ISO, 노출시간 등)
     */
    fun calculateExposureBrightness(imageProxy: ImageProxy): ExposureInfo {
        return try {
            val imageInfo = imageProxy.imageInfo

            // CameraX ImageInfo에서 노출 정보 추출
            // 참고: ImageInfo.rotationDegrees, timestamp 등은 접근 가능하지만
            // ISO, 노출시간 등은 직접 접근이 제한됨

            // Android Camera2 API를 통한 메타데이터 접근이 필요
            // ImageProxy는 Camera2의 CaptureResult를 직접 제공하지 않으므로
            // CameraControl이나 CameraInfo를 통해 접근해야 함

            // 대안: 히스토그램 기반 밝기 분석
            val bitmap = imageProxy.toBitmap()
            bitmap?.let {
                val histogramBrightness = calculateHistogramBrightness(it)
                ExposureInfo(
                    brightness = histogramBrightness,
                    iso = null,
                    exposureTimeNs = null,
                    ev = null,
                    method = "Histogram"
                )
            } ?: ExposureInfo(0.0, null, null, null, "Error")

        } catch (e: Exception) {
            Logger.e("FrameProcessor", "Failed to calculate exposure brightness", e)
            ExposureInfo(0.0, null, null, null, "Error")
        }
    }

    /**
     * 히스토그램 기반 밝기 계산
     * 픽셀 분포를 분석하여 더 정확한 밝기 측정
     *
     * @return 0.0 (매우 어두움) ~ 1.0 (매우 밝음)
     */
    fun calculateHistogramBrightness(bitmap: Bitmap): Double {
        try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

            // 히스토그램 생성 (0-255 범위)
            val histogram = IntArray(256)

            for (pixel in pixels) {
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                // 밝기 값 계산 (ITU-R BT.601)
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                histogram[luminance.coerceIn(0, 255)]++
            }

            // 가중 평균 계산 (밝은 픽셀에 더 높은 가중치)
            var weightedSum = 0.0
            var totalWeight = 0.0

            for (i in histogram.indices) {
                val count = histogram[i]
                val brightness = i / 255.0
                // 밝은 영역에 더 높은 가중치 부여
                val weight = count * (1.0 + brightness * 0.5)
                weightedSum += brightness * weight
                totalWeight += weight
            }

            return if (totalWeight > 0) weightedSum / totalWeight else 0.0

        } catch (e: Exception) {
            Logger.e("FrameProcessor", "Failed to calculate histogram brightness", e)
            return 0.0
        }
    }

    /**
     * 밝기 값을 사람이 읽기 쉬운 문자열로 변환
     * @param brightness 0.0 ~ 1.0 범위의 밝기 값
     */
    fun getBrightnessQuality(brightness: Double): String {
        return when {
            brightness >= 0.8 -> "Very Bright"
            brightness >= 0.6 -> "Bright"
            brightness >= 0.4 -> "Normal"
            brightness >= 0.2 -> "Dark"
            else -> "Very Dark"
        }
    }

    /**
     * Y-plane 히스토그램 계산 (Direct, 가장 빠름)
     *
     * Based on LUMINANCE.md Section 4.1: "히스토그램 생성"
     * Y-plane을 직접 분석하여 RGB 변환 오버헤드 회피
     *
     * @param yPlaneData Y-plane ByteArray
     * @param width 이미지 폭
     * @param height 이미지 높이
     * @param roi ROI 영역 (null이면 전체 영역)
     * @return 256-bin histogram (index 0-255, value = pixel count)
     */
    fun calculateHistogramDirect(
        yPlaneData: ByteArray,
        width: Int,
        height: Int,
        roi: ROI? = null
    ): IntArray {
        return try {
            if (!nativeLibraryLoaded) {
                // Fallback: Kotlin implementation
                return calculateHistogramKotlin(yPlaneData)
            }

            if (roi != null && roi != ROI.FULL) {
                val rect = roi.toRect(width, height)
                calculateHistogramNativeROI(
                    yPlaneData, width, height,
                    rect.left, rect.top, rect.width(), rect.height()
                )
            } else {
                calculateHistogramNative(yPlaneData, width, height)
            }
        } catch (e: Exception) {
            Logger.e("FrameProcessor", "Failed to calculate histogram", e)
            IntArray(256) { 0 }
        }
    }

    /**
     * Kotlin fallback histogram implementation
     */
    private fun calculateHistogramKotlin(yPlaneData: ByteArray): IntArray {
        val histogram = IntArray(256) { 0 }
        for (byte in yPlaneData) {
            val pixelValue = byte.toInt() and 0xFF
            histogram[pixelValue]++
        }
        return histogram
    }

    /**
     * 히스토그램 기반 조명 품질 분석
     *
     * Based on LUMINANCE.md Section 4: "히스토그램 분석"
     * - Section 4.2: 저조도 감지 (darknessRatio)
     * - Section 4.3: 과다 노출 감지 (clippingRatio)
     *
     * @param histogram 256-bin histogram (from calculateHistogramDirect)
     * @return LuminanceAnalysis with quality assessment
     */
    fun analyzeLuminanceQuality(
        histogram: IntArray,
        processingTimeMs: Long = 0
    ): LuminanceAnalysis {
        require(histogram.size == 256) { "Histogram must have 256 bins" }

        val totalPixels = histogram.sum().toLong()
        if (totalPixels == 0L) {
            return LuminanceAnalysis.createDefault()
        }

        // Calculate darkness ratio (LUMINANCE.md Section 4.2)
        // Count pixels in very dark range (0-50)
        val darkPixels = histogram.sliceArray(0..LuminanceAnalysis.DARK_PIXEL_THRESHOLD).sum()
        val darknessRatio = darkPixels.toDouble() / totalPixels

        // Calculate clipping ratio (LUMINANCE.md Section 4.3)
        // Count pixels at maximum brightness (255)
        val clippedPixels = histogram[255]
        val clippingRatio = clippedPixels.toDouble() / totalPixels

        // Calculate average brightness
        var sum = 0L
        for (i in histogram.indices) {
            sum += histogram[i].toLong() * i
        }
        val brightness = (sum.toDouble() / totalPixels) / 255.0

        // Determine lighting quality
        val quality = determineLightingQuality(darknessRatio, clippingRatio)

        return LuminanceAnalysis(
            brightness = brightness,
            histogram = histogram,
            darknessRatio = darknessRatio,
            clippingRatio = clippingRatio,
            quality = quality,
            processingTimeMs = processingTimeMs
        )
    }

    /**
     * Determine lighting quality based on histogram analysis
     *
     * Logic based on LUMINANCE.md Section 7.3: "최종 판단 로직"
     */
    private fun determineLightingQuality(
        darknessRatio: Double,
        clippingRatio: Double
    ): LightingQuality {
        return when {
            // Backlit: Both underexposed and overexposed regions
            darknessRatio > LuminanceAnalysis.DARKNESS_THRESHOLD * 0.7 &&
            clippingRatio > LuminanceAnalysis.CLIPPING_THRESHOLD * 0.5 ->
                LightingQuality.BACKLIT

            // Underexposed: Too dark
            darknessRatio > LuminanceAnalysis.DARKNESS_THRESHOLD ->
                LightingQuality.UNDEREXPOSED

            // Overexposed: Too bright (clipped)
            clippingRatio > LuminanceAnalysis.CLIPPING_THRESHOLD ->
                LightingQuality.OVEREXPOSED

            // Acceptable: Slight issues but usable
            darknessRatio > 0.5 || clippingRatio > 0.05 ->
                LightingQuality.ACCEPTABLE

            // Optimal: Good lighting conditions
            else -> LightingQuality.OPTIMAL
        }
    }

    /**
     * ImageProxy extension: Full luminance analysis
     *
     * Combines histogram calculation and quality analysis
     *
     * @param roi ROI region (null for full frame)
     * @return Complete luminance analysis result
     */
    fun ImageProxy.analyzeLuminance(roi: ROI? = null): LuminanceAnalysis {
        val startTime = System.currentTimeMillis()

        val histogram = this.calculateHistogram(roi)
        val processingTime = System.currentTimeMillis() - startTime

        return FrameProcessor.analyzeLuminanceQuality(histogram, processingTime)
    }
}

/**
 * 노출 정보 데이터 클래스
 */
data class ExposureInfo(
    val brightness: Double,           // 0.0 ~ 1.0 범위의 밝기
    val iso: Int?,                    // ISO 값 (100, 200, 400 등)
    val exposureTimeNs: Long?,        // 노출 시간 (나노초)
    val ev: Double?,                  // EV (Exposure Value)
    val method: String                // 측정 방법 ("Histogram", "Metadata", "Error")
) {
    /**
     * EV 값 계산 (ISO와 노출시간이 있는 경우)
     * EV = log2(ISO * exposureTime / C)
     * C는 카메라 상수 (일반적으로 12.5~13)
     */
    fun calculateEV(): Double? {
        if (iso == null || exposureTimeNs == null) return null
        val exposureTimeSec = exposureTimeNs / 1_000_000_000.0
        // EV 계산 공식: EV = log2(100 / (exposureTime * ISO))
        return kotlin.math.log2(100.0 / (exposureTimeSec * iso))
    }

    override fun toString(): String {
        return buildString {
            append("Brightness: ${"%.2f".format(brightness)}")
            iso?.let { append(", ISO: $it") }
            exposureTimeNs?.let {
                val ms = it / 1_000_000.0
                append(", Exposure: ${"%.2f".format(ms)}ms")
            }
            calculateEV()?.let { append(", EV: ${"%.1f".format(it)}") }
            append(" ($method)")
        }
    }
}

/**
 * ImageProxy extension: Calculate histogram from Y-plane
 *
 * Convenience function for histogram analysis
 */
fun ImageProxy.calculateHistogram(roi: ROI? = null): IntArray {
    val yPlaneData = this.toYPlaneByteArray()
        ?: return IntArray(256) { 0 }  // Return empty histogram if extraction fails
    return FrameProcessor.calculateHistogramDirect(yPlaneData, width, height, roi)
}
