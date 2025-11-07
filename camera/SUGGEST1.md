문제점 및 해결 전략
문제: 현재 CameraManager는 저수준 리소스인 ImageProxy를 Flow로 그대로 노출합니다. 이로 인해 소비자(UI)가 비트맵 변환, 스레드 관리, ImageProxy.close() 호출 보장 등 복잡하고 오류가 발생하기 쉬운 책임을 모두 떠안고 있습니다.

해결 전략: 이 모든 복잡한 책임을 CameraManager 라이브러리 레벨로 이동시킵니다. 라이브러리는 소비자에게 사용하기 쉬운 Bitmap 기반의 콜백 인터페이스만 노출합니다.

1. 라이브러리 수정 사항 (CameraManager.kt)
   CameraManager가 ImageProxy를 Bitmap으로 자동 변환하고 리소스를 자동 해제하는 "간편 콜백" 기능을 추가합니다.

1-1. 새 인터페이스 추가 (CameraManager.kt 파일 내부)
ImageProxy 대신 Bitmap과 회전 각도만 전달하는 새 콜백 인터페이스를 정의합니다.

Kotlin

// ... (기존 import)
import android.graphics.Bitmap
import androidx.camera.core.ExperimentalGetImage

/**
* (신규) 간편 비트맵 분석 콜백 인터페이스
* - [onBitmapAnalyzed]는 라이브러리에 의해 백그라운드 스레드에서 호출됩니다.
* - ImageProxy -> Bitmap 변환 및 close()가 자동으로 처리됩니다.
    */
    interface SimpleBitmapAnalysisCallback {
    /**
    * 프레임이 비트맵으로 변환된 후 백그라운드 스레드에서 호출됩니다.
    * @param bitmap 변환된 Bitmap (매번 새로 생성되는 객체)
    * @param rotationDegrees 이미지의 회전 각도 (0, 90, 180, 270)
      */
      fun onBitmapAnalyzed(bitmap: Bitmap, rotationDegrees: Float)

/**
    * 처리 중 에러 발생 시 호출됩니다.
      */
      fun onError(e: Exception)
      }
      1-2. 새 콜백 설정 함수 추가 (CameraManager 클래스 내부)
      소비자가 이 콜백을 등록/해제할 수 있는 함수와, 내부에서 frameAnalysisFlow를 처리하는 로직을 추가합니다.

Kotlin

// In CameraManager class...

// ... (기존 프로퍼티) ...
private var simpleCallbackJob: Job? = null

/**
* (신규) Java/Kotlin 소비자를 위한 간편 비트맵 콜백 설정
*
* 이 함수를 사용하면 소비자는 ImageProxy.close()나 스레드 처리를
* 전혀 신경 쓸 필요 없이, 백그라운드 스레드에서 [Bitmap]을 바로 받을 수 있습니다.
*
* @param callback
  */
  @OptIn(ExperimentalGetImage::class) // ImageProxy.toBitmap()에 필요
  fun setBitmapAnalysisCallback(callback: SimpleBitmapAnalysisCallback?) {
  // 기존의 다른 콜백/Job은 중지
  setFrameAnalysisCallback(null as FrameAnalysisCallback?) // 기존 콜백 중지
  simpleCallbackJob?.cancel()
  simpleCallbackJob = null

  if (callback == null) {
  return // 콜백 제거
  }

  // 새 콜백 Job 시작
  simpleCallbackJob = analysisScope.launch { // 'analysisScope'는 이미 백그라운드
  frameAnalysisFlow.collect { imageProxy ->
  try {
  // (라이브러리가 처리) 1. Bitmap으로 변환
  val bitmap = imageProxy.toBitmap() // YUV -> RGBA 변환
  val rotation = imageProxy.imageInfo.rotationDegrees.toFloat()

               // (라이브러리가 처리) 2. 백그라운드에서 새 콜백 호출
               callback.onBitmapAnalyzed(bitmap, rotation)

           } catch (e: Exception) {
               if (e is android.media.Image.OperationNotSupportedException) {
                   // toBitmap() 실패 (드물게 발생)
                   Logger.w("CameraManager", "Failed to convert ImageProxy to Bitmap", e)
               } else {
                   Logger.e("CameraManager", "Error in simple bitmap callback", e)
                   callback.onError(e)
               }
           } finally {
               // (라이브러리가 처리) 3. ImageProxy.close() 자동 호출 보장
               imageProxy.close()
           }
       }
  }
  }

// ... (기존 release() 함수) ...
// release()가 호출되면 analysisScope.cancel()에 의해
// simpleCallbackJob도 자동으로 취소됩니다.
2. 소비자(UI) 사용 가이드 (Best Practice)
   라이브러리 수정 후, 소비자는 스레드나 ImageProxy를 전혀 신경 쓰지 않아도 됩니다. 모든 무거운 로직은 ViewModel로 위임하는 것이 좋습니다.

2-1. CameraXComponent.kt 수정
기존의 복잡한 LaunchedEffect를 모두 삭제하고, 새 콜백을 등록하는 코드로 대체합니다.

Kotlin

// CameraXComponent.kt

// (수정) 스레드 관련 import 모두 삭제
// import kotlinx.coroutines.Dispatchers
// import kotlinx.coroutines.launch
// import kotlinx.coroutines.withContext

@OptIn(ExperimentalGetImage::class)
@Composable
fun CameraComponent(
// ... (매개변수 동일) ...
) {
// ... (cameraManager 생성 동일) ...

    // (수정) 삭제 - 기존의 복잡한 frameAnalysisFlow 구독 로직
    /*
    LaunchedEffect(Unit) {
        Logger.d("FrameAnalysisExample", "Starting frame analysis collection")
        launch(Dispatchers.IO) { 
            cameraManager.frameAnalysisFlow.collect { result ->
                try {
                    // ... (toBitmap, processBitmap, close ... )
                } finally {
                    result.imageProxy.close()
                }
            }
        }
    }
    */

    // (수정) 추가 - 카메라 시작 및 새 비트맵 콜백 설정
    LaunchedEffect(cameraManager, viewModel) {
        Logger.d("FrameAnalysisExample", "Setting bitmap callback and starting camera")

        // 1. 새 콜백 설정
        cameraManager.setBitmapAnalysisCallback(object : SimpleBitmapAnalysisCallback {
            
            // 이 코드는 '백그라운드 스레드'에서 호출됩니다.
            override fun onBitmapAnalyzed(bitmap: Bitmap, rotationDegrees: Float) {
                // 렉 유발 X, close() 필요 X
                
                // (권장) 모든 로직을 ViewModel로 즉시 위임
                viewModel.processFrame(bitmap, rotationDegrees)
            }

            override fun onError(e: Exception) {
                Logger.e("FrameAnalysisExample", "Bitmap Analysis Error", e)
                // (권장) ViewModel에 에러 상태 전달
                // viewModel.notifyError(e)
            }
        })
        
        // 2. 카메라 시작
        cameraManager.startCamera()
    }

    // (수정) 추가 - 컴포넌트 정리 시 콜백 해제
    DisposableEffect(cameraManager) {
        onDispose {
            Logger.d("FrameAnalysisExample", "Disposing CameraManager")
            // 콜백을 null로 설정하여 Job을 즉시 중지시킵니다.
            cameraManager.setBitmapAnalysisCallback(null)
            cameraManager.release()
        }
    }
    
    // ... (CameraPreview 렌더링은 동일) ...
}

// (수정) processBitmap, manualProcessBitmap, updateUI 등
// Composable 하단에 있던 함수들은 모두 ViewModel로 로직을 이전하거나 삭제합니다.
2-2. 권장 아키텍처: ViewModel의 역할
소비자(UI)가 MutableState를 직접 다루지 않고, ViewModel이 모든 로직과 상태 관리를 담당합니다.

CameraViewModel.kt (예시)

Kotlin

@HiltViewModel
class CameraViewModel @Inject constructor(...) : ViewModel() {

    // 1. UI가 구독할 상태 (메인 스레드에서만 업데이트)
    private val _uiState = MutableStateFlow(CameraUiState(infoText = "얼굴을 보여주세요"))
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    private val detector = KIIEngine.getInstance().createDetector(...)

    /**
     * (신규) 백그라운드 스레드에서 호출될 함수
     * @param bitmap 원본 비트맵
     * @param rotationDegrees 회전 각도
     */
    fun processFrame(bitmap: Bitmap, rotationDegrees: Float) {
        if (!CameraConfig.shared.cameraProcessing) return
        
        // (백그라운드 스레드에서 실행) 무거운 KIIEngine 연산
        val rotatedBitmap = rotateBitmap(bitmap, rotationDegrees) ?: return
        val code = detector.putBitmap(rotatedBitmap)
        
        // (백그라운드 스레드에서 실행) UI 상태 업데이트 준비
        val newText = when (code) {
            KIIEngine.BRIGHTNESS_BAD_CODE -> "너무 어둡거나 밝습니다."
            KIIEngine.LIVE_FACE_CODE -> "촬영중...."
            // ... 모든 케이스
            else -> "얼굴을 보여주세요"
        }

        // 3. UI 상태 업데이트는 'viewModelScope' (메인 스레드)에서 안전하게 실행
        viewModelScope.launch {
            _uiState.update { 
                it.copy(infoText = newText, detectionCode = code) 
            }
            
            if (code == KIIEngine.LIVE_FACE_CODE) {
                 updateCode(KIIEngine.LIVE_FACE_CODE)
                 updateImage(detector.getResultFaceBitmap())
            }
        }
    }
    
    // ... (rotateBitmap 유틸리티 함수) ...
}
CameraXComponent.kt (최종 Composable)

Kotlin

@Composable
fun CameraComponent(
modifier: Modifier,
viewModel: CameraViewModel = hiltViewModel(),
) {
// ... (cameraManager 생성 및 콜백 설정은 위와 동일) ...

    // ViewModel의 상태를 구독하여 UI에 반영
    val uiState by viewModel.uiState.collectAsState()

    CameraPreview(
        cameraManager = cameraManager,
        modifier = modifier
    ) {
        // UI 오버레이
        Text(text = uiState.infoText, color = uiState.trackColor)
        // ...
    }
}
기대 효과
렉(Jank) 완벽 해결: ImageProxy 변환 및 KIIEngine 연산이 메인 스레드를 절대 막지 않습니다.

책임 분리 (SoC):

CameraManager: 하드웨어 제어 및 Bitmap 제공.

ViewModel: 비즈니스 로직(KIIEngine) 및 UI 상태 관리.

Composable: ViewModel의 상태를 화면에 그리기만 함.

코드 단순화: CameraXComponent에서 스레드, try-finally, ImageProxy 등 복잡한 코드가 완전히 사라집니다.