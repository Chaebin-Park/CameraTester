package com.kii.camera

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner

/**
 * XML 레이아웃에서 사용할 수 있는 카메라 프리뷰 View
 *
 * Java/XML 기반 레거시 프로젝트를 위한 커스텀 View
 *
 * 사용 예시 (XML):
 * ```xml
 * <com.kii.camera.CameraPreviewView
 *     android:id="@+id/cameraPreviewView"
 *     android:layout_width="match_parent"
 *     android:layout_height="match_parent" />
 * ```
 *
 * 사용 예시 (Java):
 * ```java
 * CameraPreviewView previewView = findViewById(R.id.cameraPreviewView);
 * CameraManager cameraManager = new CameraManager(
 *     this,                    // context
 *     this,                    // lifecycleOwner
 *     CameraConfig.DEFAULT,
 *     null
 * );
 * previewView.bindCameraManager(cameraManager);
 * cameraManager.startCamera();
 * ```
 *
 * @author chaebin
 * @since 11/05/25
 */
class CameraPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val previewView: PreviewView = PreviewView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        implementationMode = PreviewView.ImplementationMode.PERFORMANCE
    }

    private var cameraManager: CameraManager? = null

    init {
        addView(previewView)
    }

    /**
     * CameraManager를 바인딩합니다.
     *
     * @param manager CameraManager 인스턴스
     */
    fun bindCameraManager(manager: CameraManager) {
        this.cameraManager = manager
        manager.setSurfaceProvider(previewView.surfaceProvider)
    }

    /**
     * 프리뷰 구현 모드 설정
     *
     * @param mode PERFORMANCE 또는 COMPATIBLE
     */
    fun setImplementationMode(mode: PreviewView.ImplementationMode) {
        previewView.implementationMode = mode
    }

    /**
     * 프리뷰 스케일 타입 설정
     *
     * @param scaleType 스케일 타입
     */
    fun setScaleType(scaleType: PreviewView.ScaleType) {
        previewView.scaleType = scaleType
    }

    /**
     * 카메라 매니저 해제 (액티비티 종료 시 호출)
     */
    fun release() {
        cameraManager?.release()
        cameraManager = null
    }

    /**
     * Java에서 쉽게 사용하기 위한 헬퍼 메서드
     *
     * @param context Context
     * @param lifecycleOwner LifecycleOwner (Activity, Fragment)
     * @param config CameraConfig
     * @return CameraManager 인스턴스
     */
    @JvmOverloads
    fun setupCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        config: CameraConfig = CameraConfig.DEFAULT
    ): CameraManager {
        val manager = CameraManager(context, lifecycleOwner, config)
        bindCameraManager(manager)
        return manager
    }
}
