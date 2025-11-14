#include <jni.h>
#include <android/log.h>
#include <cmath>
#include <cstdint>
#include <algorithm>

#ifdef USE_NEON
#include <arm_neon.h>
#endif

#define LOG_TAG "FrameProcessorNative"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

/**
 * 그레이스케일 이미지의 선명도 계산 (Laplacian 방법)
 *
 * @param env JNI 환경
 * @param clazz JNI 객체
 * @param pixelData 그레이스케일 픽셀 데이터 (ByteArray)
 * @param width 이미지 폭
 * @param height 이미지 높이
 * @param sampleRate 샘플링 비율 (1 = 모든 픽셀, 4 = 4픽셀마다)
 * @return 선명도 값 (0~255)
 */
extern "C" JNIEXPORT jdouble JNICALL
Java_com_kii_camera_FrameProcessor_calculateSharpnessNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray pixelData,
    jint width,
    jint height,
    jint sampleRate
) {
    if (pixelData == nullptr) {
        LOGE("pixelData is null");
        return 0.0;
    }

    if (width <= 0 || height <= 0) {
        LOGE("Invalid dimensions: %dx%d", width, height);
        return 0.0;
    }

    if (sampleRate < 1) {
        LOGE("Invalid sampleRate: %d", sampleRate);
        return 0.0;
    }

    // ByteArray를 native 배열로 변환
    jbyte* pixels = env->GetByteArrayElements(pixelData, nullptr);
    if (pixels == nullptr) {
        LOGE("Failed to get byte array elements");
        return 0.0;
    }

    auto* unsignedPixels = reinterpret_cast<uint8_t*>(pixels);

    double sum = 0.0;
    int count = 0;

    // Laplacian 커널: [0 -1 0; -1 4 -1; 0 -1 0]
    // 중심 픽셀 * 4 - 상하좌우 합

    for (int i = sampleRate; i < height - sampleRate; i += sampleRate) {
        for (int j = sampleRate; j < width - sampleRate; j += sampleRate) {
            const int idx = i * width + j;

            // 중심 픽셀
            const int center = unsignedPixels[idx];

            // 상하좌우 픽셀
            const int up = unsignedPixels[(i - sampleRate) * width + j];
            const int down = unsignedPixels[(i + sampleRate) * width + j];
            const int left = unsignedPixels[i * width + (j - sampleRate)];
            const int right = unsignedPixels[i * width + (j + sampleRate)];

            // Laplacian 계산
            const int laplacian = std::abs(4 * center - up - down - left - right);

            sum += laplacian;
            count++;
        }
    }

    // ByteArray 해제
    env->ReleaseByteArrayElements(pixelData, pixels, JNI_ABORT);

    return count > 0 ? sum / count : 0.0;
}

/**
 * ROI 영역의 선명도 계산
 *
 * @param env JNI 환경
 * @param clazz JNI 객체
 * @param pixelData 그레이스케일 픽셀 데이터 (ByteArray)
 * @param width 이미지 폭
 * @param height 이미지 높이
 * @param sampleRate 샘플링 비율
 * @param roiLeft ROI 좌측 시작점
 * @param roiTop ROI 상단 시작점
 * @param roiWidth ROI 폭
 * @param roiHeight ROI 높이
 * @return 선명도 값 (0~255)
 */
extern "C" JNIEXPORT jdouble JNICALL
Java_com_kii_camera_FrameProcessor_calculateSharpnessNativeROI(
    JNIEnv* env,
    jclass clazz,
    jbyteArray pixelData,
    jint width,
    jint height,
    jint sampleRate,
    jint roiLeft,
    jint roiTop,
    jint roiWidth,
    jint roiHeight
) {
    if (pixelData == nullptr || width <= 0 || height <= 0 || sampleRate < 1) {
        LOGE("Invalid parameters");
        return 0.0;
    }

    // ROI 범위 검증
    if (roiLeft < 0 || roiTop < 0 || roiWidth <= 0 || roiHeight <= 0 ||
        roiLeft + roiWidth > width || roiTop + roiHeight > height) {
        LOGE("Invalid ROI: left=%d, top=%d, width=%d, height=%d", roiLeft, roiTop, roiWidth, roiHeight);
        return 0.0;
    }

    jbyte* pixels = env->GetByteArrayElements(pixelData, nullptr);
    if (pixels == nullptr) {
        LOGE("Failed to get byte array elements");
        return 0.0;
    }

    auto* unsignedPixels = reinterpret_cast<uint8_t*>(pixels);

    double sum = 0.0;
    int count = 0;

    // ROI 영역만 처리
    const int roiRight = roiLeft + roiWidth;
    const int roiBottom = roiTop + roiHeight;

    for (int i = roiTop + sampleRate; i < roiBottom - sampleRate; i += sampleRate) {
        for (int j = roiLeft + sampleRate; j < roiRight - sampleRate; j += sampleRate) {
            const int idx = i * width + j;

            // 중심 픽셀
            const int center = unsignedPixels[idx];

            // 상하좌우 픽셀
            const int up = unsignedPixels[(i - sampleRate) * width + j];
            const int down = unsignedPixels[(i + sampleRate) * width + j];
            const int left = unsignedPixels[i * width + (j - sampleRate)];
            const int right = unsignedPixels[i * width + (j + sampleRate)];

            // Laplacian 계산
            const int laplacian = std::abs(4 * center - up - down - left - right);

            sum += laplacian;
            count++;
        }
    }

    env->ReleaseByteArrayElements(pixelData, pixels, JNI_ABORT);

    return count > 0 ? sum / count : 0.0;
}

#ifdef USE_NEON
/**
 * ARM NEON SIMD를 사용한 최적화 버전
 * 간단한 버전 - 추후 최적화 가능
 */
extern "C" JNIEXPORT jdouble JNICALL
Java_com_kii_camera_FrameProcessor_calculateSharpnessNativeNEON(
    JNIEnv* env,
    jobject /* obj */,
    jbyteArray pixelData,
    jint width,
    jint height,
    jint sampleRate
) {
    // 현재는 기본 구현과 동일 (추후 NEON 최적화 가능)
    return Java_com_kii_camera_FrameProcessor_calculateSharpnessNative(
        env, nullptr, pixelData, width, height, sampleRate
    );
}
#endif

/**
 * 밝기 계산 (샘플링 + 단순 평균)
 *
 * @param env JNI 환경
 * @param clazz JNI 객체
 * @param pixelData 그레이스케일 픽셀 데이터
 * @param width 이미지 폭
 * @param height 이미지 높이
 * @param sampleRate 샘플링 비율 (1 = 모든 픽셀, 4 = 4픽셀마다)
 * @return 밝기 값 (0.0 ~ 1.0)
 */
extern "C" JNIEXPORT jdouble JNICALL
Java_com_kii_camera_FrameProcessor_calculateBrightnessNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray pixelData,
    jint width,
    jint height,
    jint sampleRate
) {
    if (pixelData == nullptr || width <= 0 || height <= 0 || sampleRate < 1) {
        return 0.0;
    }

    jbyte* pixels = env->GetByteArrayElements(pixelData, nullptr);
    if (pixels == nullptr) {
        return 0.0;
    }

    auto* unsignedPixels = reinterpret_cast<uint8_t*>(pixels);

    // 단순 평균 계산 (샘플링 적용)
    long long sum = 0;
    int count = 0;

    for (int i = 0; i < height; i += sampleRate) {
        for (int j = 0; j < width; j += sampleRate) {
            sum += unsignedPixels[i * width + j];
            count++;
        }
    }

    env->ReleaseByteArrayElements(pixelData, pixels, JNI_ABORT);

    return count > 0 ? (sum / (double)count) / 255.0 : 0.0;
}

/**
 * ROI 영역의 밝기 계산
 *
 * @param env JNI 환경
 * @param clazz JNI 객체
 * @param pixelData 그레이스케일 픽셀 데이터
 * @param width 이미지 폭
 * @param height 이미지 높이
 * @param sampleRate 샘플링 비율
 * @param roiLeft ROI 좌측 시작점
 * @param roiTop ROI 상단 시작점
 * @param roiWidth ROI 폭
 * @param roiHeight ROI 높이
 * @return 밝기 값 (0.0 ~ 1.0)
 */
extern "C" JNIEXPORT jdouble JNICALL
Java_com_kii_camera_FrameProcessor_calculateBrightnessNativeROI(
    JNIEnv* env,
    jclass clazz,
    jbyteArray pixelData,
    jint width,
    jint height,
    jint sampleRate,
    jint roiLeft,
    jint roiTop,
    jint roiWidth,
    jint roiHeight
) {
    if (pixelData == nullptr || width <= 0 || height <= 0 || sampleRate < 1) {
        return 0.0;
    }

    // ROI 범위 검증
    if (roiLeft < 0 || roiTop < 0 || roiWidth <= 0 || roiHeight <= 0 ||
        roiLeft + roiWidth > width || roiTop + roiHeight > height) {
        LOGE("Invalid ROI for brightness: left=%d, top=%d, width=%d, height=%d",
             roiLeft, roiTop, roiWidth, roiHeight);
        return 0.0;
    }

    jbyte* pixels = env->GetByteArrayElements(pixelData, nullptr);
    if (pixels == nullptr) {
        return 0.0;
    }

    auto* unsignedPixels = reinterpret_cast<uint8_t*>(pixels);

    // ROI 영역만 처리
    const int roiRight = roiLeft + roiWidth;
    const int roiBottom = roiTop + roiHeight;

    long long sum = 0;
    int count = 0;

    for (int i = roiTop; i < roiBottom; i += sampleRate) {
        for (int j = roiLeft; j < roiRight; j += sampleRate) {
            sum += unsignedPixels[i * width + j];
            count++;
        }
    }

    env->ReleaseByteArrayElements(pixelData, pixels, JNI_ABORT);

    return count > 0 ? (sum / (double)count) / 255.0 : 0.0;
}

/**
 * Y-plane 히스토그램 계산 (256-bin)
 *
 * Based on LUMINANCE.md Section 4.1: "히스토그램 생성"
 * Counts the number of pixels at each brightness level (0-255)
 *
 * @param env JNI 환경
 * @param clazz JNI 객체
 * @param yPlaneData Y-plane 픽셀 데이터 (ByteArray)
 * @param width 이미지 폭
 * @param height 이미지 높이
 * @param sampleRate 샘플링 비율 (1 = 모든 픽셀, 4 = 4픽셀마다)
 * @return 256-bin 히스토그램 (IntArray)
 */
extern "C" JNIEXPORT jintArray JNICALL
Java_com_kii_camera_FrameProcessor_calculateHistogramNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray yPlaneData,
    jint width,
    jint height,
    jint sampleRate
) {
    if (yPlaneData == nullptr || width <= 0 || height <= 0 || sampleRate < 1) {
        LOGE("Invalid parameters for histogram");
        return nullptr;
    }

    // ByteArray를 native 배열로 변환
    jbyte* pixels = env->GetByteArrayElements(yPlaneData, nullptr);
    if (pixels == nullptr) {
        LOGE("Failed to get byte array elements for histogram");
        return nullptr;
    }

    auto* unsignedPixels = reinterpret_cast<uint8_t*>(pixels);
    const int totalPixels = width * height;

    // 256-bin 히스토그램 초기화
    int histogram[256] = {0};

    // 샘플링 적용하여 히스토그램 생성
#ifdef USE_NEON
    // NEON 최적화 버전: 8개 픽셀을 동시에 처리
    if (sampleRate == 1) {
        // sampleRate=1일 때만 NEON 사용 (모든 픽셀 처리)
        const int vectorPixels = totalPixels / 8 * 8;  // 8의 배수

        // 8개씩 처리
        for (int i = 0; i < vectorPixels; i += 8) {
            uint8x8_t pixels = vld1_u8(unsignedPixels + i);

            // 각 픽셀을 히스토그램에 추가 (NEON으로는 scatter가 어려워서 scalar로 처리)
            histogram[vget_lane_u8(pixels, 0)]++;
            histogram[vget_lane_u8(pixels, 1)]++;
            histogram[vget_lane_u8(pixels, 2)]++;
            histogram[vget_lane_u8(pixels, 3)]++;
            histogram[vget_lane_u8(pixels, 4)]++;
            histogram[vget_lane_u8(pixels, 5)]++;
            histogram[vget_lane_u8(pixels, 6)]++;
            histogram[vget_lane_u8(pixels, 7)]++;
        }

        // 나머지 픽셀 처리
        for (int i = vectorPixels; i < totalPixels; i++) {
            histogram[unsignedPixels[i]]++;
        }
    } else {
        // Sampling 적용 시 기본 루프
        for (int i = 0; i < totalPixels; i += sampleRate) {
            histogram[unsignedPixels[i]]++;
        }
    }
#else
    // Non-NEON: Loop unrolling for better performance
    const int unrollPixels = totalPixels / (sampleRate * 4) * (sampleRate * 4);

    // 4-way unrolled loop
    for (int i = 0; i < unrollPixels; i += sampleRate * 4) {
        histogram[unsignedPixels[i]]++;
        histogram[unsignedPixels[i + sampleRate]]++;
        histogram[unsignedPixels[i + sampleRate * 2]]++;
        histogram[unsignedPixels[i + sampleRate * 3]]++;
    }

    // 나머지 픽셀 처리
    for (int i = unrollPixels; i < totalPixels; i += sampleRate) {
        histogram[unsignedPixels[i]]++;
    }
#endif

    // ByteArray 해제
    env->ReleaseByteArrayElements(yPlaneData, pixels, JNI_ABORT);

    // Java IntArray 생성 및 데이터 복사
    jintArray result = env->NewIntArray(256);
    if (result == nullptr) {
        LOGE("Failed to create histogram result array");
        return nullptr;
    }

    env->SetIntArrayRegion(result, 0, 256, histogram);

    return result;
}

/**
 * ROI 영역의 히스토그램 계산
 *
 * @param env JNI 환경
 * @param clazz JNI 객체
 * @param yPlaneData Y-plane 픽셀 데이터
 * @param width 이미지 폭
 * @param height 이미지 높이
 * @param sampleRate 샘플링 비율 (1 = 모든 픽셀, 4 = 4픽셀마다)
 * @param roiLeft ROI 좌측 시작점
 * @param roiTop ROI 상단 시작점
 * @param roiWidth ROI 폭
 * @param roiHeight ROI 높이
 * @return 256-bin 히스토그램 (IntArray)
 */
extern "C" JNIEXPORT jintArray JNICALL
Java_com_kii_camera_FrameProcessor_calculateHistogramNativeROI(
    JNIEnv* env,
    jclass clazz,
    jbyteArray yPlaneData,
    jint width,
    jint height,
    jint sampleRate,
    jint roiLeft,
    jint roiTop,
    jint roiWidth,
    jint roiHeight
) {
    if (yPlaneData == nullptr || width <= 0 || height <= 0 || sampleRate < 1) {
        LOGE("Invalid parameters for histogram ROI");
        return nullptr;
    }

    // ROI 범위 검증
    if (roiLeft < 0 || roiTop < 0 || roiWidth <= 0 || roiHeight <= 0 ||
        roiLeft + roiWidth > width || roiTop + roiHeight > height) {
        LOGE("Invalid ROI for histogram: left=%d, top=%d, width=%d, height=%d",
             roiLeft, roiTop, roiWidth, roiHeight);
        return nullptr;
    }

    jbyte* pixels = env->GetByteArrayElements(yPlaneData, nullptr);
    if (pixels == nullptr) {
        LOGE("Failed to get byte array elements for histogram ROI");
        return nullptr;
    }

    auto* unsignedPixels = reinterpret_cast<uint8_t*>(pixels);

    // 256-bin 히스토그램 초기화
    int histogram[256] = {0};

    // ROI 영역을 샘플링하여 처리
    const int roiRight = roiLeft + roiWidth;
    const int roiBottom = roiTop + roiHeight;

    // Loop unrolling for ROI (4-way)
    for (int i = roiTop; i < roiBottom; i += sampleRate) {
        int j = roiLeft;
        const int unrollWidth = roiLeft + (roiWidth / (sampleRate * 4)) * (sampleRate * 4);

        // 4-way unrolled inner loop
        for (; j < unrollWidth; j += sampleRate * 4) {
            histogram[unsignedPixels[i * width + j]]++;
            histogram[unsignedPixels[i * width + j + sampleRate]]++;
            histogram[unsignedPixels[i * width + j + sampleRate * 2]]++;
            histogram[unsignedPixels[i * width + j + sampleRate * 3]]++;
        }

        // 나머지 픽셀 처리
        for (; j < roiRight; j += sampleRate) {
            histogram[unsignedPixels[i * width + j]]++;
        }
    }

    env->ReleaseByteArrayElements(yPlaneData, pixels, JNI_ABORT);

    // Java IntArray 생성 및 데이터 복사
    jintArray result = env->NewIntArray(256);
    if (result == nullptr) {
        LOGE("Failed to create histogram ROI result array");
        return nullptr;
    }

    env->SetIntArrayRegion(result, 0, 256, histogram);

    return result;
}

/**
 * 그리드 기반 밝기 분석
 *
 * 프레임을 gridRows x gridCols 그리드로 나누어 각 셀의 평균 밝기 계산
 *
 * @param env JNI 환경
 * @param clazz JNI 객체
 * @param yPlaneData Y-plane 픽셀 데이터
 * @param width 이미지 폭
 * @param height 이미지 높이
 * @param gridRows 그리드 행 수 (예: 3)
 * @param gridCols 그리드 열 수 (예: 3)
 * @param sampleRate 샘플링 비율 (1 = 모든 픽셀, 4 = 4픽셀마다)
 * @return DoubleArray[gridRows * gridCols] - 각 셀의 평균 밝기 (0.0 ~ 1.0)
 */
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_kii_camera_FrameProcessor_calculateGridBrightnessNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray yPlaneData,
    jint width,
    jint height,
    jint gridRows,
    jint gridCols,
    jint sampleRate
) {
    if (yPlaneData == nullptr || width <= 0 || height <= 0 ||
        gridRows <= 0 || gridCols <= 0 || sampleRate < 1) {
        LOGE("Invalid parameters for grid brightness");
        return nullptr;
    }

    jbyte* pixels = env->GetByteArrayElements(yPlaneData, nullptr);
    if (pixels == nullptr) {
        LOGE("Failed to get byte array elements for grid brightness");
        return nullptr;
    }

    auto* unsignedPixels = reinterpret_cast<uint8_t*>(pixels);

    // 그리드 셀 크기 계산
    const int cellWidth = width / gridCols;
    const int cellHeight = height / gridRows;
    const int gridSize = gridRows * gridCols;

    // 각 그리드 셀의 밝기를 저장할 배열
    auto* gridBrightness = new double[gridSize];

    // 각 그리드 셀별로 밝기 계산
    for (int row = 0; row < gridRows; row++) {
        for (int col = 0; col < gridCols; col++) {
            const int cellStartY = row * cellHeight;
            const int cellStartX = col * cellWidth;
            const int cellEndY = (row == gridRows - 1) ? height : (row + 1) * cellHeight;
            const int cellEndX = (col == gridCols - 1) ? width : (col + 1) * cellWidth;

            long long sum = 0;
            int count = 0;

#ifdef USE_NEON
            // NEON 최적화: 8픽셀씩 처리
            for (int i = cellStartY; i < cellEndY; i += sampleRate) {
                int j = cellStartX;
                const int vectorEnd = cellStartX + ((cellEndX - cellStartX) / 8) * 8;

                // 8픽셀씩 병렬 처리
                for (; j < vectorEnd; j += 8) {
                    uint8x8_t pixels = vld1_u8(unsignedPixels + i * width + j);
                    uint16x8_t pixels16 = vmovl_u8(pixels);
                    uint32x4_t sum_low = vmovl_u16(vget_low_u16(pixels16));
                    uint32x4_t sum_high = vmovl_u16(vget_high_u16(pixels16));

                    uint32_t partial_sum[4];
                    vst1q_u32(partial_sum, sum_low);
                    sum += partial_sum[0] + partial_sum[1] + partial_sum[2] + partial_sum[3];
                    vst1q_u32(partial_sum, sum_high);
                    sum += partial_sum[0] + partial_sum[1] + partial_sum[2] + partial_sum[3];

                    count += 8;
                }

                // 나머지 픽셀 처리
                for (; j < cellEndX; j += sampleRate) {
                    sum += unsignedPixels[i * width + j];
                    count++;
                }
            }
#else
            // Non-NEON: 단순 루프
            for (int i = cellStartY; i < cellEndY; i += sampleRate) {
                for (int j = cellStartX; j < cellEndX; j += sampleRate) {
                    sum += unsignedPixels[i * width + j];
                    count++;
                }
            }
#endif

            const int gridIndex = row * gridCols + col;
            gridBrightness[gridIndex] = count > 0 ? (sum / (double)count) / 255.0 : 0.0;
        }
    }

    env->ReleaseByteArrayElements(yPlaneData, pixels, JNI_ABORT);

    // Java DoubleArray 생성 및 데이터 복사
    jdoubleArray result = env->NewDoubleArray(gridSize);
    if (result == nullptr) {
        delete[] gridBrightness;
        LOGE("Failed to create grid brightness result array");
        return nullptr;
    }

    env->SetDoubleArrayRegion(result, 0, gridSize, gridBrightness);
    delete[] gridBrightness;

    return result;
}

/**
 * 공간별 클리핑 분석
 *
 * 프레임을 그리드로 나누어 각 셀의 하이라이트/섀도우 클리핑 픽셀 수 계산
 *
 * @param env JNI 환경
 * @param clazz JNI 객체
 * @param yPlaneData Y-plane 픽셀 데이터
 * @param width 이미지 폭
 * @param height 이미지 높이
 * @param gridRows 그리드 행 수
 * @param gridCols 그리드 열 수
 * @param sampleRate 샘플링 비율
 * @return IntArray[2 * gridRows * gridCols] - 각 셀의 [highlight, shadow] 클리핑 픽셀 수
 */
extern "C" JNIEXPORT jintArray JNICALL
Java_com_kii_camera_FrameProcessor_calculateSpatialClippingNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray yPlaneData,
    jint width,
    jint height,
    jint gridRows,
    jint gridCols,
    jint sampleRate
) {
    if (yPlaneData == nullptr || width <= 0 || height <= 0 ||
        gridRows <= 0 || gridCols <= 0 || sampleRate < 1) {
        LOGE("Invalid parameters for spatial clipping");
        return nullptr;
    }

    jbyte* pixels = env->GetByteArrayElements(yPlaneData, nullptr);
    if (pixels == nullptr) {
        LOGE("Failed to get byte array elements for spatial clipping");
        return nullptr;
    }

    auto* unsignedPixels = reinterpret_cast<uint8_t*>(pixels);

    // 그리드 셀 크기 계산
    const int cellWidth = width / gridCols;
    const int cellHeight = height / gridRows;
    const int gridSize = gridRows * gridCols;
    const int resultSize = gridSize * 2;  // highlight + shadow per cell

    // 각 그리드 셀의 클리핑 정보를 저장할 배열
    auto* clippingData = new int[resultSize]();  // 0으로 초기화

    // 클리핑 임계값
    const uint8_t HIGHLIGHT_THRESHOLD = 250;
    const uint8_t SHADOW_THRESHOLD = 5;

    // 각 그리드 셀별로 클리핑 픽셀 카운트
    for (int row = 0; row < gridRows; row++) {
        for (int col = 0; col < gridCols; col++) {
            const int cellStartY = row * cellHeight;
            const int cellStartX = col * cellWidth;
            const int cellEndY = (row == gridRows - 1) ? height : (row + 1) * cellHeight;
            const int cellEndX = (col == gridCols - 1) ? width : (col + 1) * cellWidth;

            int highlightCount = 0;
            int shadowCount = 0;

#ifdef USE_NEON
            // NEON 최적화: 8픽셀씩 처리
            const uint8x8_t highlight_thresh = vdup_n_u8(HIGHLIGHT_THRESHOLD);
            const uint8x8_t shadow_thresh = vdup_n_u8(SHADOW_THRESHOLD);

            for (int i = cellStartY; i < cellEndY; i += sampleRate) {
                int j = cellStartX;
                const int vectorEnd = cellStartX + ((cellEndX - cellStartX) / 8) * 8;

                // 8픽셀씩 병렬 처리
                for (; j < vectorEnd; j += 8) {
                    uint8x8_t pixels = vld1_u8(unsignedPixels + i * width + j);

                    // 하이라이트 클리핑 체크 (>= 250)
                    uint8x8_t highlight_mask = vcge_u8(pixels, highlight_thresh);
                    // 섀도우 클리핑 체크 (<= 5)
                    uint8x8_t shadow_mask = vcle_u8(pixels, shadow_thresh);

                    // 마스크를 배열로 변환하여 카운트
                    uint8_t highlight_array[8];
                    uint8_t shadow_array[8];
                    vst1_u8(highlight_array, highlight_mask);
                    vst1_u8(shadow_array, shadow_mask);

                    for (int k = 0; k < 8; k++) {
                        if (highlight_array[k]) highlightCount++;
                        if (shadow_array[k]) shadowCount++;
                    }
                }

                // 나머지 픽셀 처리
                for (; j < cellEndX; j += sampleRate) {
                    const uint8_t pixel = unsignedPixels[i * width + j];
                    if (pixel >= HIGHLIGHT_THRESHOLD) highlightCount++;
                    if (pixel <= SHADOW_THRESHOLD) shadowCount++;
                }
            }
#else
            // Non-NEON: 단순 루프
            for (int i = cellStartY; i < cellEndY; i += sampleRate) {
                for (int j = cellStartX; j < cellEndX; j += sampleRate) {
                    const uint8_t pixel = unsignedPixels[i * width + j];
                    if (pixel >= HIGHLIGHT_THRESHOLD) highlightCount++;
                    if (pixel <= SHADOW_THRESHOLD) shadowCount++;
                }
            }
#endif

            const int gridIndex = row * gridCols + col;
            clippingData[gridIndex * 2] = highlightCount;       // highlight
            clippingData[gridIndex * 2 + 1] = shadowCount;      // shadow
        }
    }

    env->ReleaseByteArrayElements(yPlaneData, pixels, JNI_ABORT);

    // Java IntArray 생성 및 데이터 복사
    jintArray result = env->NewIntArray(resultSize);
    if (result == nullptr) {
        delete[] clippingData;
        LOGE("Failed to create spatial clipping result array");
        return nullptr;
    }

    env->SetIntArrayRegion(result, 0, resultSize, clippingData);
    delete[] clippingData;

    return result;
}

/**
 * 중앙 vs 가장자리 밝기 비교
 *
 * 프레임의 중앙 영역과 가장자리 영역의 평균 밝기를 각각 계산
 * 역광 감지에 유용
 *
 * @param env JNI 환경
 * @param clazz JNI 객체
 * @param yPlaneData Y-plane 픽셀 데이터
 * @param width 이미지 폭
 * @param height 이미지 높이
 * @param centerRatio 중앙 영역 비율 (0.0 ~ 1.0, 예: 0.5 = 중앙 50%)
 * @param sampleRate 샘플링 비율
 * @return DoubleArray[2] - [center, edge] 평균 밝기 (0.0 ~ 1.0)
 */
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_kii_camera_FrameProcessor_calculateCenterEdgeBrightnessNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray yPlaneData,
    jint width,
    jint height,
    jdouble centerRatio,
    jint sampleRate
) {
    if (yPlaneData == nullptr || width <= 0 || height <= 0 ||
        centerRatio <= 0.0 || centerRatio >= 1.0 || sampleRate < 1) {
        LOGE("Invalid parameters for center/edge brightness");
        return nullptr;
    }

    jbyte* pixels = env->GetByteArrayElements(yPlaneData, nullptr);
    if (pixels == nullptr) {
        LOGE("Failed to get byte array elements for center/edge brightness");
        return nullptr;
    }

    auto* unsignedPixels = reinterpret_cast<uint8_t*>(pixels);

    // 중앙 영역 계산
    const int centerWidth = static_cast<int>(width * centerRatio);
    const int centerHeight = static_cast<int>(height * centerRatio);
    const int centerStartX = (width - centerWidth) / 2;
    const int centerStartY = (height - centerHeight) / 2;
    const int centerEndX = centerStartX + centerWidth;
    const int centerEndY = centerStartY + centerHeight;

    long long centerSum = 0;
    long long edgeSum = 0;
    int centerCount = 0;
    int edgeCount = 0;

    // 모든 픽셀을 순회하며 중앙/가장자리 구분
    for (int i = 0; i < height; i += sampleRate) {
        for (int j = 0; j < width; j += sampleRate) {
            const uint8_t pixel = unsignedPixels[i * width + j];

            // 중앙 영역인지 확인
            if (i >= centerStartY && i < centerEndY && j >= centerStartX && j < centerEndX) {
                centerSum += pixel;
                centerCount++;
            } else {
                edgeSum += pixel;
                edgeCount++;
            }
        }
    }

    env->ReleaseByteArrayElements(yPlaneData, pixels, JNI_ABORT);

    // 결과 계산
    double centerBrightness = centerCount > 0 ? (centerSum / (double)centerCount) / 255.0 : 0.0;
    double edgeBrightness = edgeCount > 0 ? (edgeSum / (double)edgeCount) / 255.0 : 0.0;

    // Java DoubleArray 생성
    jdoubleArray result = env->NewDoubleArray(2);
    if (result == nullptr) {
        LOGE("Failed to create center/edge result array");
        return nullptr;
    }

    double resultData[2] = {centerBrightness, edgeBrightness};
    env->SetDoubleArrayRegion(result, 0, 2, resultData);

    return result;
}

/**
 * 밝기 통계 계산 (평균, 분산, 표준편차)
 *
 * 프레임의 밝기 균일도를 측정하기 위한 통계값 계산
 *
 * @param env JNI 환경
 * @param clazz JNI 객체
 * @param yPlaneData Y-plane 픽셀 데이터
 * @param width 이미지 폭
 * @param height 이미지 높이
 * @param sampleRate 샘플링 비율
 * @return DoubleArray[3] - [mean, variance, stddev] (0.0 ~ 1.0 정규화)
 */
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_kii_camera_FrameProcessor_calculateBrightnessStatisticsNative(
    JNIEnv* env,
    jclass clazz,
    jbyteArray yPlaneData,
    jint width,
    jint height,
    jint sampleRate
) {
    if (yPlaneData == nullptr || width <= 0 || height <= 0 || sampleRate < 1) {
        LOGE("Invalid parameters for brightness statistics");
        return nullptr;
    }

    jbyte* pixels = env->GetByteArrayElements(yPlaneData, nullptr);
    if (pixels == nullptr) {
        LOGE("Failed to get byte array elements for brightness statistics");
        return nullptr;
    }

    auto* unsignedPixels = reinterpret_cast<uint8_t*>(pixels);

    // 첫 번째 패스: 평균 계산
    long long sum = 0;
    int count = 0;

#ifdef USE_NEON
    // NEON 최적화: 평균 계산
    uint32x4_t sum_vec = vdupq_n_u32(0);

    for (int i = 0; i < height; i += sampleRate) {
        int j = 0;
        const int vectorEnd = (width / 8) * 8;

        // 8픽셀씩 병렬 처리
        for (; j < vectorEnd; j += 8) {
            uint8x8_t pixels = vld1_u8(unsignedPixels + i * width + j);
            uint16x8_t pixels16 = vmovl_u8(pixels);
            uint32x4_t sum_low = vmovl_u16(vget_low_u16(pixels16));
            uint32x4_t sum_high = vmovl_u16(vget_high_u16(pixels16));
            sum_vec = vaddq_u32(sum_vec, sum_low);
            sum_vec = vaddq_u32(sum_vec, sum_high);
            count += 8;
        }

        // 나머지 픽셀 처리
        for (; j < width; j += sampleRate) {
            sum += unsignedPixels[i * width + j];
            count++;
        }
    }

    // NEON 벡터 결과 합산
    uint32_t partial_sum[4];
    vst1q_u32(partial_sum, sum_vec);
    sum += partial_sum[0] + partial_sum[1] + partial_sum[2] + partial_sum[3];
#else
    // Non-NEON: 단순 루프
    for (int i = 0; i < height; i += sampleRate) {
        for (int j = 0; j < width; j += sampleRate) {
            sum += unsignedPixels[i * width + j];
            count++;
        }
    }
#endif

    const double mean = count > 0 ? sum / (double)count : 0.0;

    // 두 번째 패스: 분산 계산
    double varianceSum = 0.0;

    for (int i = 0; i < height; i += sampleRate) {
        for (int j = 0; j < width; j += sampleRate) {
            const double diff = unsignedPixels[i * width + j] - mean;
            varianceSum += diff * diff;
        }
    }

    env->ReleaseByteArrayElements(yPlaneData, pixels, JNI_ABORT);

    const double variance = count > 0 ? varianceSum / count : 0.0;
    const double stddev = std::sqrt(variance);

    // 정규화 (0.0 ~ 1.0)
    const double normalizedMean = mean / 255.0;
    const double normalizedVariance = variance / (255.0 * 255.0);
    const double normalizedStddev = stddev / 255.0;

    // Java DoubleArray 생성
    jdoubleArray result = env->NewDoubleArray(3);
    if (result == nullptr) {
        LOGE("Failed to create brightness statistics result array");
        return nullptr;
    }

    double resultData[3] = {normalizedMean, normalizedVariance, normalizedStddev};
    env->SetDoubleArrayRegion(result, 0, 3, resultData);

    return result;
}

/**
 * 라이브러리 로드 시 호출
 */
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    LOGD("camera_native library loaded");

#ifdef USE_NEON
    LOGD("NEON SIMD support enabled");
#else
    LOGD("Standard implementation (no SIMD)");
#endif

    return JNI_VERSION_1_6;
}
