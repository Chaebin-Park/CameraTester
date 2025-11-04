package com.kii.camera

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.kii.common.Logger

/**
 * 카메라 프리뷰 Composable
 *
 * @param cameraManager 카메라 매니저 인스턴스
 * @param modifier UI 커스터마이징용 Modifier
 * @param scaleType 프리뷰 스케일 타입
 * @param autoStart 자동으로 카메라 시작 여부 (기본값: true)
 * @param onError 에러 발생 시 콜백
 *
 * @author chaebin
 * @since 10/31/25
 */
@Composable
fun CameraPreview(
    cameraManager: CameraManager,
    modifier: Modifier = Modifier,
    scaleType: PreviewView.ScaleType = PreviewView.ScaleType.FILL_CENTER,
    autoStart: Boolean = true,
    onError: ((Exception) -> Unit)? = null
) {
    val context = LocalContext.current
    val cameraState by cameraManager.cameraState.collectAsState()

    // PreviewView 생성 및 기억
    val previewView = remember {
        PreviewView(context).apply {
            this.scaleType = scaleType
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Surface Provider 항상 설정 (autoStart와 무관)
    LaunchedEffect(previewView) {
        Logger.d("CameraPreview", "Setting surface provider")
        cameraManager.setSurfaceProvider(previewView.surfaceProvider)
    }

    // 카메라 시작 (autoStart=true인 경우에만)
    LaunchedEffect(autoStart) {
        if (autoStart) {
            try {
                cameraManager.startCamera()
            } catch (e: Exception) {
                Logger.e("CameraPreview", "Failed to start camera", e)
                onError?.invoke(e)
            }
        }
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            Logger.d("CameraPreview", "Disposing camera preview")
        }
    }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // 카메라 프리뷰 표시
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // 상태에 따른 UI 표시
        when (cameraState) {
            is CameraState.Idle -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Camera Idle",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            is CameraState.Starting -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            is CameraState.Running -> {
                // 카메라가 실행 중이면 아무것도 표시하지 않음 (프리뷰만 보임)
            }
            is CameraState.Stopping -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            is CameraState.Error -> {
                val error = (cameraState as CameraState.Error).exception
                onError?.invoke(error)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Camera Error: ${error.message}",
                        color = Color.Red,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

/**
 * 간단한 카메라 프리뷰 (CameraManager를 내부에서 생성)
 *
 * @param config 카메라 설정
 * @param modifier UI 커스터마이징용 Modifier
 * @param scaleType 프리뷰 스케일 타입
 * @param onCameraManagerCreated CameraManager 생성 후 콜백
 * @param onError 에러 발생 시 콜백
 */
@Composable
fun SimpleCameraPreview(
    modifier: Modifier = Modifier,
    config: CameraConfig = CameraConfig.DEFAULT,
    scaleType: PreviewView.ScaleType = PreviewView.ScaleType.FILL_CENTER,
    onCameraManagerCreated: ((CameraManager) -> Unit)? = null,
    onError: ((Exception) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cameraManager = remember {
        CameraManager(
            context = context,
            lifecycleOwner = lifecycleOwner,
            config = config
        ).also { manager ->
            onCameraManagerCreated?.invoke(manager)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraManager.release()
        }
    }

    CameraPreview(
        cameraManager = cameraManager,
        modifier = modifier,
        scaleType = scaleType,
        autoStart = true,
        onError = onError
    )
}
