package com.kii.camera

/**
 * Java 친화적인 프레임 분석 콜백 인터페이스
 *
 * Kotlin Flow 대신 Java에서 사용하기 쉬운 콜백 방식
 *
 * 사용 예시 (Java):
 * ```java
 * cameraManager.setFrameAnalysisCallback(new FrameAnalysisCallback() {
 *     @Override
 *     public void onFrameAnalyzed(FrameAnalysisResult result) {
 *         Log.d("Camera", "Sharpness: " + result.getSharpness());
 *         Log.d("Camera", "Brightness: " + result.getBrightness());
 *         result.getImageProxy().close(); // 반드시 close() 호출!
 *     }
 *
 *     @Override
 *     public void onError(Exception error) {
 *         Log.e("Camera", "Analysis error", error);
 *     }
 * });
 * ```
 *
 * @author chaebin
 * @since 11/05/25
 */
interface FrameAnalysisCallback {
    /**
     * 프레임 분석 완료 시 호출됩니다.
     *
     * **중요**: 사용 후 반드시 `result.imageProxy.close()`를 호출해야 합니다!
     *
     * @param result 분석 결과
     */
    fun onFrameAnalyzed(result: FrameAnalysisResult)

    /**
     * 분석 중 에러 발생 시 호출됩니다.
     *
     * @param error 에러 정보
     */
    fun onError(error: Exception) {
        // 기본 구현: 아무것도 하지 않음
    }
}

/**
 * Java 8+ 람다를 위한 SAM 인터페이스
 *
 * 사용 예시 (Java 8+):
 * ```java
 * cameraManager.setFrameAnalysisCallback(result -> {
 *     Log.d("Camera", "Sharpness: " + result.getSharpness());
 *     result.getImageProxy().close();
 * });
 * ```
 */
fun interface SimpleFrameAnalysisCallback {
    fun onFrameAnalyzed(result: FrameAnalysisResult)
}
