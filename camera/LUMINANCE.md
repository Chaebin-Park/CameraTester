## 1. 서론: 조명 상태 분석의 필요성 및 Y-평면 접근법

### 1.1. 얼굴 인식 성공의 전제 조건: 조명 품질

Android 환경에서 전면 카메라를 사용한 얼굴 인식 솔루션, 특히 Google ML Kit과 같은 라이브러리의 성공률은 입력되는 이미지 프레임의 품질에 절대적으로 의존합니다.1 조명 조건은 이미지 품질을 결정하는 가장 중요한 변수입니다.

극단적인 조명 환경은 두 가지 주요 실패 시나리오를 야기합니다. 첫째, **저조도(Underexposure)** 환경(예: 50 lux 미만)은 이미지 센서의 노이즈를 증가시키고, 얼굴의 고유한 특징(feature)을 소실시켜 인식 성능을 치명적으로 저하시킵니다.2 둘째, **과다 노출(Overexposure)** 또는 '빛번짐'은 피사체를 조명으로 포화(saturate)시켜 얼굴의 핵심 특징(눈, 코, 입 등)이 하얗게 날아가는 '클리핑(clipping)' 현상을 발생시킵니다.3 이 경우, 특징 추출 자체가 불가능해집니다.3

ML Kit의 공식 가이드라인은 정확한 감지를 위해 '충분한 픽셀 데이터'와 '좋은 초점'을 명시적으로 요구하며 1, 최소 100x100 픽셀 크기의 얼굴 영역을 권장합니다.1 극단적인 조명 조건은 이러한 기본 전제 조건을 근본적으로 훼손합니다.

### 1.2. 물리적 조도 센서(TYPE_LIGHT)의 한계와 이미지 기반 접근의 당위성

사용자의 요구사항에서 명시되었듯이, 모든 Android 기기가 물리적 조도 센서(`Sensor.TYPE_LIGHT`)를 탑재하고 있지는 않습니다. 이는 하드웨어 기반의 환경 센서로, 기기 제조사의 선택 사항입니다.

설사 센서가 존재하더라도, 이 센서는 기기 주변의 '주변광(ambient light)'을 측정할 뿐, 카메라 렌즈가 실제로 향하고 있는 '피사체(얼굴)의 조명'을 보장하지 않습니다. 예를 들어, 사용자의 얼굴은 그늘에 있지만 스마트폰은 밝은 햇빛 아래 있는 경우, 조도 센서는 높은 lux 값을 반환하지만 실제 얼굴 인식용 프레임은 매우 어두울 수 있습니다. 따라서 카메라가 _실제로_ 수신하는 이미지 프레임 자체를 분석하는 것이 얼굴 인식 성공 여부를 판단하는 가장 직접적이고 신뢰할 수 있는 접근 방식입니다.

### 1.3. 핵심 데이터 소스: Y-평면 (Luminance Plane)

현대 Android 카메라 API(CameraX 및 Camera2)는 고성능 실시간 처리를 위해 `ImageFormat.YUV_420_888` 포맷을 표준으로 사용합니다. 이 포맷은 이미지를 3개의 개별적인 평면(plane)으로 분리하여 제공합니다:

1. **Y-평면 (Luminance):** 이미지의 휘도, 즉 '밝기' 정보만을 담고 있습니다.

2. **U/V-평면 (Chrominance):** 이미지의 색차, 즉 '색상' 정보를 담고 있습니다.


사용자의 요구사항인 '밝기 측정', '빛번짐 감지', '저조도/과다노출 판별'은 모두 `planes`에 해당하는 **Y-평면** 데이터만으로 완벽하게 수행할 수 있습니다.

이 접근 방식의 핵심 이점은 불필요하고 리소스 집약적인 YUV-to-RGB 색 공간 변환을 완전히 피할 수 있다는 것입니다. CameraX의 `ImageAnalysis` 유스케이스 내 `analyze()` 함수에서 매 프레임마다 발생하는 RGB 변환은 심각한 성능 저하를 유발할 수 있으므로, Y-평면을 직접 분석하는 것은 실시간 얼굴 인식을 위한 필수적인 최적화 전략입니다.

## 2. 통합 구현의 초석: CameraX ImageProxy와 OpenCV Mat 연동

안정적인 조명 분석을 위해서는 CameraX로부터 받은 Y-평면 데이터를 효율적으로 처리할 수 있는 OpenCV(Open Source Computer Vision Library) `Mat` 객체로 변환해야 합니다. 이 과정에는 기기 호환성과 직결되는 중요한 함정이 존재합니다.

### 2.1. CameraX ImageAnalysis 유스케이스 설정

먼저, CameraX의 `ImageAnalysis` 유스케이스를 설정해야 합니다. 실시간 처리를 위해 `ImageAnalysis.Builder`에 `setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)`를 설정하는 것이 표준적입니다. 이 전략은 분석 파이프라인이 처리 속도보다 빠르게 프레임을 수신할 경우, 중간 프레임을 폐기하고 가장 최신의 프레임만 분석가(analyzer)에게 전달하여 지연 시간(latency)을 최소화합니다.

분석 로직은 `ImageAnalysis.Analyzer` 인터페이스를 구현하고 `analyze(ImageProxy image)` 함수를 오버라이드하여 작성합니다.

### 2.2. Y-Plane ByteBuffer의 함정: Stride의 이해

`analyze` 함수 내에서 `image.getPlanes().getBuffer()`를 호출하면 Y-평면의 원시 데이터가 담긴 `ByteBuffer`에 접근할 수 있습니다. 하지만 이 버퍼는 단순한 `width * height` 크기의 1차원 배열이 아닐 수 있습니다.

`Image.Plane` 인터페이스는 두 가지 중요한 메모리 레이아웃 속성을 정의합니다: `getPixelStride()`와 `getRowStride()`.

- `pixelStride`: Y-평면의 경우, 픽셀 간의 바이트 간격이며 거의 항상 1입니다.

- `rowStride`: **핵심 변수**로, 한 행(row)의 시작부터 다음 행의 시작까지의 바이트 수입니다. 하드웨어 최적화(예: 16바이트 또는 32바이트 메모리 정렬)를 위해, `rowStride`는 실제 이미지의 `width`보다 클 수 있습니다. `rowStride`가 `width`보다 크다는 것은 각 행의 끝에 사용되지 않는 '패딩(padding)' 바이트가 존재함을 의미합니다.


### 2.3. Stride를 무시한 접근의 치명적 결함

만약 `rowStride`를 무시하고 `ByteBuffer`를 `width * height` 크기의 단순 배열로 취급하면(예:의 `yp++` 방식), 패딩 바이트를 실제 픽셀 데이터로 잘못 읽게 됩니다. 이는 두 가지 심각한 문제를 야기합니다:

1. **데이터 오염:** 모든 통계 계산(평균, 히스토그램)이 패딩 바이트(쓰레기 값)에 의해 오염됩니다.

2. **이미지 왜곡:** 이미지가 행마다 몇 픽셀씩 밀리는 현상이 발생하여, `connectedComponentsWithStats`와 같은 공간 분석 알고리즘이 완전히 실패합니다.


이는 일부 기기(예: Xiaomi Mi A2, Samsung Tab E)에서 실제로 보고된 심각한 호환성 문제로, 이미지가 잘못 정렬되는 결과를 초래합니다.

### 2.4. 안정적인 Y-Plane to OpenCV Mat 변환 로직

따라서 모든 Android 기기에서 안정적으로 동작하기 위해서는 `rowStride`를 고려한 견고한(robust) `Mat` 변환 로직이 필수적입니다. 이는 'Fast-Path'와 'Safe-Path'로 분기됩니다.

1. `ImageProxy`에서 Y-평면의 속성들을 가져옵니다.

   Java

    ```
    Image.Plane yPlane = image.getPlanes();
    ByteBuffer yBuffer = yPlane.getBuffer();
    int width = image.getWidth();
    int height = image.getHeight();
    int rowStride = yPlane.getRowStride();
    ```

2. 'Fast-Path' (제로-카피)와 'Safe-Path' (수동-카피)로 분기합니다.

    - **Fast-Path (패딩 없음):** `rowStride`가 `width`와 같다면 패딩이 없으므로, 메모리를 복사할 필요 없이 `ByteBuffer`를 직접 참조하는 '제로-카피(zero-copy)' `Mat` 헤더를 생성합니다. 이는 최고의 성능을 보장합니다.

      Java

        ```
        Mat yMat;
        if (rowStride == width) {
            // Fast-Path: 제로-카피
            yMat = new Mat(height, width, CvType.CV_8UC1, yBuffer);
        } else {
            // Safe-Path: 패딩 존재
        ```


    ...
    
    ```
    
    (참고: 일부 OpenCV 구현에서는 Mat 생성자에 rowStride를 직접 전달하여 패딩을 인지시킬 수 있습니다: new Mat(height, width, CvType.CV_8UC1, yBuffer, rowStride). 이는 JNI 구현에 따라 다를 수 있습니다.)
    
    - **Safe-Path (패딩 있음):** `rowStride`가 `width`와 다르다면 제로-카피가 불가능합니다. 이 경우, `width` 크기만큼만 데이터를 수동으로 복사하여 패딩을 제거한 새로운 조밀한(dense) `Mat`을 생성해야 합니다.
        
    
    ...
    
    } else {
    
    // Safe-Path: 패딩 존재. 수동 복사.
    
    yMat = new Mat(height, width, CvType.CV_8UC1);
    
    byte rowData = new byte[width];
    
    for (int y = 0; y < height; y++) {
    
    yBuffer.position(y * rowStride);
    
    yBuffer.get(rowData, 0, width);
    
    yMat.put(y, 0, rowData);
    
    }
    
    }
    
    ```


이후 모든 조명 분석(평균, 히스토그램, 빛번짐)은 이렇게 생성된 안정적인 `yMat`을 대상으로 수행되어야 합니다.

## 3. 기본 알고리즘: 평균 휘도(Mean Luminance) 계산

### 3.1. 알고리즘 원리

이미지의 전반적인 밝기를 측정하는 가장 간단하고 빠른 방법은 Y-평면 모든 픽셀 값의 산술 평균(mean)을 계산하는 것입니다. `yMat`이 준비되었다면, OpenCV의 `Core.mean(yMat)` 함수를 통해 이 값을 매우 효율적으로 얻을 수 있습니다.

### 3.2. 임계값 설정 및 한계

단순히 평균값을 기준으로 임계값(예: `meanLuminance < 40`는 "너무 어두움", `meanLuminance > 220`는 "너무 밝음")을 설정할 수 있습니다.

그러나 이 방식은 **치명적인 한계**를 가집니다. 평균값은 이미지의 국소적인(local) 문제를 감지하지 못합니다. 예를 들어, 피사체 뒤에 밝은 창문이 있는 **강한 역광(backlight)** 상황을 가정해 보겠습니다. 배경 픽셀은 255(과다 노출)에 근접하고, 정작 중요한 얼굴 영역은 0(그림자)에 가까울 것입니다. 이 두 극단적인 값의 평균은 약 127.5로, '완벽하게 정상적인' 밝기로 잘못 판단될 수 있습니다. 하지만 이 이미지는 얼굴 인식이 불가능한 최악의 조건입니다.

### 3.3. 결론

평균 휘도는 매우 빠르지만 신뢰할 수 없습니다. 전체 프레임이 균일하게 어둡거나 밝은 경우(예: 렌즈가 완전히 가려진 경우)를 걸러내는 '빠른 실패(fast-fail)' 수단으로만 제한적으로 사용해야 합니다.

## 4. 핵심 알고리즘: 휘도 히스토그램(Luminance Histogram) 분석

평균값의 한계를 극복하기 위해, 픽셀 값의 '분포'를 분석하는 휘도 히스토그램(Luminance Histogram)을 사용해야 합니다. 히스토그램은 0(검은색)부터 255(흰색)까지 각 밝기 수준에 얼마나 많은 픽셀이 분포하는지 보여주는 그래프입니다.5

### 4.1. 히스토그램 생성: OpenCV Imgproc.calcHist

`yMat`이 준비되었다면(Sec 2.4), OpenCV의 `Imgproc.calcHist` 함수를 사용하여 Y-평면(1채널)의 히스토그램을 계산합니다.5

Java

```
import org.opencv.core.Mat;
import org.opencv.core.MatOfInt;
import org.opencv.core.MatOfFloat;
import org.opencv.imgproc.Imgproc;
import java.util.Collections;
import java.util.List;

//... yMat 준비...

Mat yHist = new Mat();
List<Mat> yMatList = Collections.singletonList(yMat);
MatOfInt channels = new MatOfInt(0); // Y-평면 (0번 채널)
MatOfInt histSize = new MatOfInt(256); // 0~255까지 256개의 빈(bin)
MatOfFloat ranges = new MatOfFloat(0f, 256f); // 0.0부터 255.99까지

Imgproc.calcHist(yMatList, channels, new Mat(), yHist, histSize, ranges);

// yHist는 이제 256x1 크기의 Mat이며, 각 인덱스(0~255)에 픽셀 수가 저장됨.
```

### 4.2. 저조도(Underexposure) 감지: '왼쪽 쏠림' 정량화

**징후:** 저조도 이미지는 히스토그램의 픽셀 분포가 왼쪽(0에 가까운 어두운 영역)으로 극단적으로 치우칩니다.6

**판단 로직:** 이 '왼쪽 쏠림'을 정량화합니다.6

1. 이미지의 전체 픽셀 수를 계산합니다: `long totalPixels = yMat.total();`

2. 히스토그램에서 극도로 어두운 영역(예: 0~50)에 속하는 픽셀의 총합을 구합니다.

   Java

    ```
    long darkPixels = 0;
    for (int i = 0; i < 50; i++) {
        darkPixels += yHist.get(i, 0);
    }
    ```

3. 전체 픽셀 대비 어두운 픽셀의 비율을 계산합니다: `double darknessRatio = (double) darkPixels / totalPixels;`

4. 이 비율이 설정한 임계값(예: 75%)을 초과하는지 확인합니다.

   if (darknessRatio > 0.75) { return "너무 어둡습니다"; }


이 로직은 평균값보다 월등히 강력합니다. 평균 밝기가 60이더라도, 픽셀의 75%가 50 미만에 몰려있다면 이는 ML Kit가 요구하는 '충분한 픽셀 데이터'가 없는, 콘트라스트가 극히 낮은 어두운 환경임을 의미합니다. 이는 조도 50 lux 미만에서 얼굴 인식 성능이 3배 저하된다는 보고 2와 일치하는 현상입니다.

### 4.3. 과다 노출(Overexposure) 감지: '클리핑(Clipping)' 분석

**징후:** '너무 밝은' 환경의 핵심 문제는 단순한 밝음이 아니라, 센서가 수용할 수 있는 빛의 한계를 넘어 정보가 '소실'되는 **클리핑(clipping)**입니다.7 8비트 이미지에서 이는 픽셀 값이 최대치인 **255**로 포화(saturate)되는 현상으로 나타납니다.

히스토그램 분석 시, 이는 다른 모든 빈(bin)과 무관하게 *오직 255번 빈(bin)*에 픽셀이 비정상적으로 집중되는 것으로 관찰됩니다.7

**판단 로직:** 255번 빈의 픽셀 비율만 확인합니다.

1. 전체 픽셀 수를 계산합니다: `long totalPixels = yMat.total();`

2. 히스토그램에서 정확히 255 값을 가진 픽셀의 수를 가져옵니다.

   long clippedPixels = (long) yHist.get(255, 0);

3. 전체 픽셀 대비 클리핑된 픽셀의 비율을 계산합니다: `double clippingRatio = (double) clippedPixels / totalPixels;`

4. 이 비율이 설정한 임계값(예: 10%)을 초과하는지 확인합니다.

   if (clippingRatio > 0.10) { return "너무 밝아 얼굴 특징이 소실되었습니다"; }


이 방식은 `mean > 220`보다 훨씬 정확합니다. 이미지의 10%가 255에 몰려있다는 것은, 해당 영역의 정보가 영구적으로 손실되었음을 의미합니다. 이것이 얼굴 영역에서 발생하면 인식은 실패합니다.3 이 방법은 역광이나 얼굴에 직접 비추는 조명으로 인한 부분적 과다 노출을 효과적으로 감지합니다.

## 5. 고급 알고리즘: '빛번짐(Glare)' 및 포화 영역(Saturated Regions) 감지

사용자가 요청한 '빛번짐(glare)'은 4.3절의 전반적인 '과다 노출'과는 다릅니다. 빛번짐은 창문, 조명, 태양과 같은 강한 광원에 의해 이미지의 _특정 영역_이 국소적으로 포화되는 현상입니다.

### 5.1. '빛번짐'의 공간적 군집 정의

핵심은 255에 가까운 픽셀이 _얼마나 많이_ 있느냐가 아니라, 그 픽셀들이 _공간적으로 어떻게 군집(cluster)을 이루고 있느냐_입니다. 얼굴 인식을 방해하는 것은 고립된 '핫 픽셀(hot pixel)'이 아니라, 얼굴 특징을 가릴 만큼 충분히 큰 '패치(patch)' 또는 '블롭(blob)'입니다.

이러한 공간적 군집을 감지하기 위해 3단계 접근법을 사용합니다.

### 5.2. 1단계: 포화 영역 이진화(Binarization)

먼저, `yMat`에서 빛번짐으로 간주할 매우 밝은 픽셀(예: 250 이상)만 추출하여 이진(binary) 마스크 이미지를 생성합니다.

Java

```
Mat saturatedMask = new Mat();
Imgproc.threshold(yMat, saturatedMask, 250.0, 255.0, Imgproc.THRESH_BINARY);
// saturatedMask는 이제 250 이상인 픽셀만 255(흰색)이고, 나머지는 0(검은색)임.
```

_(참고: S41은 더 정교한 감지를 위해 감마 보정(gamma correction)을 제안하지만, 실시간 전면 카메라 분석에는 단순 임계값이 더 효율적입니다.)_

### 5.3. 2단계: OpenCV '연결 요소 분석(Connected Component Analysis)'

`saturatedMask`는 이제 포화된 영역이 흰색 '블롭(blob)'으로 표시된 이미지입니다. `Imgproc.connectedComponentsWithStats`는 이 블롭들을 찾아내고 각각에 고유한 레이블(번호)을 매긴 뒤, 각 블롭의 통계 정보(크기, 위치, 바운딩 박스)를 반환하는 강력한 함수입니다.

Java

```
Mat labels = new Mat();
Mat stats = new Mat(); // 각 블롭의 통계 정보 (x, y, width, height, area)
Mat centroids = new Mat();

// 8-way 연결성(connectivity)을 사용하여 블롭 분석
int numLabels = Imgproc.connectedComponentsWithStats(saturatedMask, labels, stats, centroids, 8, CvType.CV_32S);
```

### 5.4. 3단계: '블롭(Blob)' 면적(Area) 기반 필터링

`stats` `Mat`은 각 레이블(블롭)의 통계 정보를 행렬로 담고 있습니다. `i`번째 블롭의 면적(area)은 `stats.get(i, Imgproc.CC_STAT_AREA)`를 통해 접근할 수 있습니다.

**판단 로직:** 인식에 방해가 될 만큼 '충분히 큰' 블롭이 있는지 검사합니다.

1. numLabels만큼 반복합니다. 단, i = 0은 배경 레이블이므로 건너뜁니다.

   for (int i = 1; i < numLabels; i++) {... }

2. i번째 블롭의 면적을 가져옵니다.

   double area = stats.get(i, Imgproc.CC_STAT_AREA);

3. 면적이 이미지 전체 면적의 특정 비율(예: 5%)을 초과하는지 확인합니다.

   long totalPixels = yMat.total();

   if (area > (totalPixels * 0.05)) {

   return "강한 빛번짐이 감지됩니다"; // 5% 초과하는 큰 블롭 발견

   }

4. 반복문이 종료될 때까지 큰 블롭이 없으면 `null` (정상)을 반환합니다.


이 로직은 작은 반사광이나 노이즈는 무시하고, 얼굴 인식을 방해할 만큼 의미 있는 크기의 실제 '빛번짐' 또는 '강한 조명 영역'만을 정확하게 필터링합니다.

## 6. 보조 전략: 자동 노출(AE) 메타데이터 활용

픽셀 분석과 더불어, 카메라 하드웨어 자체가 현재 조명 조건을 어떻게 판단하고 있는지 확인하는 보조 전략을 병행할 수 있습니다.

### 6.1. 자동 노출(AE) 메타데이터의 의미

물리적 조도 센서가 없더라도, 카메라의 **자동 노출(Auto-Exposure, AE) 알고리즘** 자체가 매우 정교한 _능동적 조도 센서_ 역할을 합니다. AE 알고리즘은 프레임이 어둡다고 판단하면, 더 많은 빛을 받아들이기 위해 두 가지 핵심 파라미터를 능동적으로 높입니다:

1. **`SENSOR_SENSITIVITY` (ISO):** 센서의 빛 민감도를 높입니다. 값이 높을수록(예: 1600, 3200) 빛이 부족함을 의미하며, 이미지 노이즈가 증가합니다.

2. **`SENSOR_EXPOSURE_TIME` (셔터 속도):** 센서가 빛을 수집하는 시간을 늘립니다. 값이 길수록(예: 1/30초, 1/15초) 빛이 매우 부족함을 의미하며, 모션 블러(motion blur)가 발생하기 쉽습니다.


이 메타데이터를 읽으면, OpenCV로 픽셀을 분석하기도 전에 카메라 하드웨어가 "현재 매우 어둡다"고 판단했는지 알 수 있습니다.

### 6.2. CameraX에서 AE 메타데이터 접근하기 (Camera2Interop)

Camera2 API에서는 `onCaptureCompleted` 콜백의 `TotalCaptureResult` 객체에서 이 값들을 직접 얻을 수 있습니다.8 CameraX의 `ImageAnalysis` 유스케이스에서 이 콜백을 받으려면 `Camera2Interop.Extender`를 사용해야 합니다.9

Kotlin

```
// Kotlin 예제
val analysisBuilder = ImageAnalysis.Builder()

// Camera2Interop.Extender 생성
val interopExtender = Camera2Interop.Extender(analysisBuilder)

// Camera2의 CaptureCallback 설정
interopExtender.setSessionCaptureCallback(object : CameraCaptureSession.CaptureCallback() {
    override fun onCaptureCompleted(
        session: CameraCaptureSession,
        request: CaptureRequest,
        result: TotalCaptureResult
    ) {
        // 메타데이터 접근
        val iso = result.get(CaptureResult.SENSOR_SENSITIVITY)
        val exposureTimeNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME)

        // 이 값들을 ViewModel이나 콜백으로 전달하여 상태 업데이트
        // 예: viewModel.updateAeMetadata(iso, exposureTimeNs)
    }
})

// 빌더로부터 ImageAnalysis 유스케이스 생성
val imageAnalysis = analysisBuilder.build()
imageAnalysis.setAnalyzer(...) // 픽셀 분석기 설정
```

### 6.3. 판단 로직

`onCaptureCompleted` 콜백에서 수신한 값을 기반으로 저조도 상태를 추론할 수 있습니다.

- `if (iso > 3200 && exposureTimeNs > 66_000_000L)` (약 1/15초)


위와 같은 조건이 충족되면, 픽셀 분석(히스토그램) 결과와 관계없이 "너무 어두워 모션 블러 또는 노이즈가 심각한" 상태로 간주할 수 있습니다. 이 방법은 CPU 비용이 거의 들지 않으므로, 픽셀 분석을 보완하는 강력한 교차 검증 수단을 제공합니다.

## 7. 통합 구현 전략 및 권장 임계값

### 7.1. 단계별 필터링 파이프라인

지금까지 논의된 알고리즘들은 각각 다른 비용(CPU)과 정확도를 가집니다. 효율적인 실시간 처리를 위해 다음과 같은 단계별 필터링 파이프라인을 구축하는 것이 좋습니다.

1. **(매 프레임) 1단계: AE 메타데이터 검사 (Sec 6):** `Camera2Interop` 콜백에서 수신한 ISO/셔터 속도를 확인합니다. 극단적인 저조도(예: ISO > 6400) 시, 픽셀 분석을 건너뛰고 즉시 "너무 어두움" 플래그를 설정합니다. (성능 비용: 매우 낮음)

2. **(매 프레임) 2단계: 평균 휘도 검사 (Sec 3):** `yMat` 변환 후(Sec 2.4) `Core.mean(yMat)`을 계산합니다. 극단적인 값(예: 30 미만 또는 230 초과)은 즉시 필터링합니다. (성능 비용: 낮음)

3. **(1/2단계 통과 시) 3단계: 히스토그램 분석 (Sec 4):** `Imgproc.calcHist`를 실행합니다. 4.2절의 '왼쪽 쏠림' 비율과 4.3절의 '클리핑' 비율을 계산하여 저조도 및 과다 노출을 정밀하게 판단합니다. (성능 비용: 중간)

4. **(3단계 통과 시) 4단계: 빛번짐 분석 (Sec 5):** `Imgproc.threshold`와 `Imgproc.connectedComponentsWithStats`를 실행합니다. 5.4절의 로직에 따라 임계 크기를 초과하는 포화 영역(블롭)이 있는지 검사합니다. (성능 비용: 높음)


### 7.2. 조명 품질 분석 알고리즘 비교

각 알고리즘의 목적과 비용-효과 분석은 다음 표와 같습니다.

**표 1: 조명 품질 분석 알고리즘 비교**

|**알고리즘**|**검출 대상 (사용자 쿼리)**|**핵심 방법론 (OpenCV/CameraX)**|**성능 비용 (CPU)**|**정확도/신뢰성**|
|---|---|---|---|---|
|**평균 휘도** (Sec 3)|전반적인 밝기 (1차)|`Core.mean(yMat)`|매우 낮음|낮음 (국소적 문제 감지 불가)|
|**AE 메타데이터** (Sec 6)|너무 어두운 장소 (추론)|`Camera2Interop` + `CaptureResult` 9|매우 낮음|높음 (저조도 판단에 강력)|
|**히스토그램 (저조도)** (Sec 4.2)|너무 어두운 장소|`Imgproc.calcHist` + 왼쪽 쏠림 분석 6|중간|매우 높음 (인식 실패와 직결)|
|**히스토그램 (과다노출)** (Sec 4.3)|너무 밝은 장소|`Imgproc.calcHist` + 클리핑(255) 분석 7|중간|매우 높음 (특징 소실 감지 3)|
|**빛번짐 (연결 요소)** (Sec 5)|빛번짐 (국소적)|`threshold` + `connectedComponentsWithStats`|높음|매우 높음 (특정 문제 전용)|

### 7.3. 최종 판단 로직 (의사 코드)

다음은 파이프라인을 적용한 최종 판단 로직의 의사 코드 예시입니다.

Kotlin

```
// AeMetadata는 Sec 6의 콜백을 통해 비동기적으로 업데이트되는 데이터 클래스
fun analyzeLighting(image: ImageProxy, metadata: AeMetadata): String? {

    // 1. AE 메타데이터 확인 (Sec 6)
    if (metadata.isExtremelyDark()) { // (예: iso > 6400)
        return "너무 어둡습니다. 장소를 밝게 해주세요."
    }

    // 2. 견고한 Y-Mat 변환 (Sec 2.4)
    val yMat = robustYPlaneToMat(image)
    val totalPixels = yMat.total()

    // 3. 평균 휘도 빠른 실패 (Sec 3)
    val mean = Core.mean(yMat).`val`
    if (mean < 30) {
        yMat.release()
        return "너무 어둡습니다. 장소를 밝게 해주세요."
    }
    if (mean > 230) {
        yMat.release()
        return "너무 밝습니다. 역광을 피해주세요."
    }

    // 4. 히스토그램 분석 (Sec 4)
    val histogram = calculateHistogram(yMat) // Sec 4.1
    
    // 4.2. 저조도 검사
    val darknessRatio = getDarknessRatio(histogram, totalPixels, 50) // 0~50 범위
    if (darknessRatio > 0.75) { // 75% 이상이 50 미만
        histogram.release()
        yMat.release()
        return "너무 어둡습니다. 장소를 밝게 해주세요."
    }
    
    // 4.3. 과다 노출(클리핑) 검사
    val clippingRatio = getClippingRatio(histogram, totalPixels) // 255 값
    if (clippingRatio > 0.10) { // 10% 이상이 255
        histogram.release()
        yMat.release()
        return "너무 밝아 얼굴이 하얗게 날아갔습니다. 역광을 피해주세요."
    }
    
    histogram.release()

    // 5. 빛번짐 분석 (Sec 5) - 비용이 가장 높으므로 마지막에 수행
    val glareAreaThreshold = totalPixels * 0.05 // 5%
    val hasGlare = checkForGlare(yMat, glareAreaThreshold) // Sec 5.2 ~ 5.4
    if (hasGlare) {
        yMat.release()
        return "강한 빛번짐이 감지됩니다. 카메라 각도를 조절해주세요."
    }

    // 모든 테스트 통과
    yMat.release()
    return null // '정상' 상태, 얼굴 인식 진행
}
```

## 8. 결론: 안정적인 얼굴 인식을 위한 조명 품질 보증 프레임워크

본 보고서는 물리적 조도 센서의 부재라는 제약 조건 하에서, Android 카메라 프레임 분석만으로 '너무 어둡거나', '너무 밝거나', '빛번짐이 있는' 환경을 정확하게 필터링하는 다층적 알고리즘 프레임워크를 제시했습니다.

핵심은 `ImageFormat.YUV_420_888` 포맷의 Y-평면을 `rowStride` 호환성 문제를 고려하여 OpenCV `Mat`으로 안전하게 변환(Sec 2.4)하는 것에서 출발합니다.

이후, 단순 평균값(Sec 3)의 한계를 지적하고, 픽셀 분포 전체를 분석하는 히스토그램(Sec 4)을 도입했습니다. 특히, 저조도 판단을 위한 '왼쪽 쏠림' 6 분석과 과다 노출 판단을 위한 '클리핑(255)' 7 비율 분석은 조명으로 인한 정보 소실을 정량적으로 측정하는 핵심 기법입니다.

나아가, 사용자의 '빛번짐' 요구를 '포화된 픽셀의 공간적 군집'으로 재정의하고, 이를 `Imgproc.connectedComponentsWithStats`로 감지하는 고급 기법(Sec 5)을 제안했습니다.

마지막으로, `Camera2Interop` 9을 통해 AE 메타데이터(ISO, 셔터 속도)를 활용하는 보조 전략(Sec 6)은, 픽셀 분석 없이도 저조도 환경을 판별하는 강력하고 효율적인 교차 검증 수단이 됩니다.

표 1과 최종 판단 로직(Sec 7.3)에서 요약된 바와 같이, 이러한 알고리즘들을 비용-효과에 따라 파이프라인으로 구축하고 사용자에게 적절한 안내 메시지를 제공함으로써, 개발자는 ML Kit와 같은 얼굴 인식 솔루션이 최적의 조건에서만 실행되도록 보장하고 안정적인 사용자 경험을 제공할 수 있습니다.