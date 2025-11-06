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
