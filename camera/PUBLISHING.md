# KII Camera Library 배포 가이드

이 문서는 KII Camera 라이브러리를 빌드하고 배포하는 방법을 설명합니다.

## 📦 라이브러리 정보

- **그룹 ID**: `com.kii`
- **아티팩트 ID**: `camera`
- **현재 버전**: `1.0.0`
- **최소 SDK**: 24 (Android 7.0)
- **타겟 SDK**: 34

## 🔨 로컬 빌드

### 1. AAR 파일 생성

```bash
./gradlew :camera:assembleRelease
```

생성된 AAR 파일 위치:
```
camera/build/outputs/aar/camera-release.aar
```

### 2. 로컬 Maven 저장소에 배포

```bash
./gradlew :camera:publishToMavenLocal
```

배포 위치:
```
~/.m2/repository/com/kii/camera/1.0.0/
```

### 3. 프로젝트 로컬 저장소에 배포

```bash
./gradlew :camera:publish
```

배포 위치:
```
camera/build/repo/com/kii/camera/1.0.0/
```

## 📱 라이브러리 사용 방법

### 로컬 Maven 저장소에서 사용

`settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal() // 추가
    }
}
```

`app/build.gradle.kts`:
```kotlin
dependencies {
    implementation("com.kii:camera:1.0.0")
}
```

### AAR 파일 직접 사용

1. `libs` 폴더에 AAR 복사
```bash
mkdir -p app/libs
cp camera/build/outputs/aar/camera-release.aar app/libs/
```

2. `app/build.gradle.kts`:
```kotlin
dependencies {
    implementation(files("libs/camera-release.aar"))

    // 필수 의존성 추가
    implementation("androidx.camera:camera-core:1.3.1")
    implementation("androidx.camera:camera-camera2:1.3.1")
    implementation("androidx.camera:camera-lifecycle:1.3.1")
    implementation("androidx.camera:camera-view:1.3.1")

    implementation("androidx.compose.ui:ui:1.6.0")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
}
```

## 🌐 GitHub Packages 배포

### 설정

`build.gradle.kts`의 주석 처리된 GitHub Packages 설정 활성화:

```kotlin
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/yourusername/kii-camera")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

### GitHub Token 설정

`~/.gradle/gradle.properties`:
```properties
gpr.user=your-github-username
gpr.key=your-github-personal-access-token
```

또는 환경 변수:
```bash
export GITHUB_ACTOR=your-github-username
export GITHUB_TOKEN=your-github-personal-access-token
```

### 배포 실행

```bash
./gradlew :camera:publish
```

### 사용 방법

`settings.gradle.kts`:
```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/yourusername/kii-camera")
            credentials {
                username = project.findProperty("gpr.user") as String?
                password = project.findProperty("gpr.key") as String?
            }
        }
    }
}
```

## 📋 버전 관리

버전 업데이트 시 `camera/build.gradle.kts` 수정:

```kotlin
publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.kii"
            artifactId = "camera"
            version = "1.0.1" // 버전 변경
            // ...
        }
    }
}
```

### 버전 네이밍 규칙

- **Major (1.x.x)**: Breaking changes
- **Minor (x.1.x)**: 새로운 기능 추가 (하위 호환)
- **Patch (x.x.1)**: 버그 수정

## 🔍 배포 전 체크리스트

- [ ] 모든 테스트 통과
- [ ] ProGuard 규칙 확인 (`consumer-rules.pro`)
- [ ] 문서 업데이트 (README.md, USAGE.md, QUICK_START.md)
- [ ] 버전 번호 업데이트
- [ ] CHANGELOG 작성
- [ ] 샘플 앱에서 AAR 테스트

## 📝 빌드 산출물

각 빌드는 다음 파일들을 생성합니다:

```
camera-1.0.0.aar           # Android Archive (라이브러리 바이너리)
camera-1.0.0.pom           # Maven POM (의존성 정보)
camera-1.0.0-sources.jar   # 소스 코드 (선택사항)
camera-1.0.0-javadoc.jar   # Javadoc (선택사항)
```

## 🚀 Maven Central 배포 (향후)

Maven Central 배포를 위해서는:

1. Sonatype 계정 생성
2. 도메인 소유권 증명 (com.kii)
3. GPG 키 생성 및 서명
4. build.gradle.kts에 signing 플러그인 추가

자세한 내용은 [Maven Central Guide](https://central.sonatype.org/publish/publish-guide/) 참고

## 🔧 트러블슈팅

### Native 라이브러리 누락 오류

AAR에 .so 파일이 포함되었는지 확인:
```bash
unzip -l camera-release.aar | grep .so
```

예상 출력:
```
jni/armeabi-v7a/libcamera_native.so
jni/arm64-v8a/libcamera_native.so
jni/x86/libcamera_native.so
jni/x86_64/libcamera_native.so
```

### ProGuard 관련 오류

`consumer-rules.pro`에 필요한 규칙이 모두 포함되었는지 확인

### 의존성 충돌

앱에서 사용하는 CameraX 버전이 라이브러리와 호환되는지 확인
