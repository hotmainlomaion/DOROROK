# 도로록 DOROROK — 햅틱 물리 휠 & 온디바이스 AI 누끼 커스텀 키보드

> 계획명 **RollKey(롤키)** · 서브 브랜드 **도로록(DOROROK)**
> 갤러리 사진을 0.5초 만에 카톡 스티커로. 손맛은 다이얼처럼, 광고는 키보드에서 사라집니다.

<p>
  <img alt="platform" src="https://img.shields.io/badge/Android-26%2B-3DDC84?logo=android&logoColor=white">
  <img alt="kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7F52FF?logo=kotlin&logoColor=white">
  <img alt="compose" src="https://img.shields.io/badge/Jetpack%20Compose-BOM%202024.10-4285F4?logo=jetpackcompose&logoColor=white">
  <img alt="mlkit" src="https://img.shields.io/badge/ML%20Kit-Subject%20Segmentation-FF6F00">
  <img alt="status" src="https://img.shields.io/badge/status-MVP%20scaffold-FFD400?logoColor=black">
</p>

---

## 한 줄 소개

카카오톡·인스타 DM 에서 **내 갤러리 사진을 스티커처럼 1탭으로 쏘는 안드로이드 키보드**입니다.
상단에는 실제 다이얼을 돌리는 듯한 **가로 롤링 휠**이 있고, 사진에서 피사체만 따는 **누끼는 기기 안에서 0.5초 만에** 끝납니다.

- **왜 필요한가** — 기존 서드파티 키보드는 전면 팝업 광고·타이핑 렉·노후 UI 로 유저 피로가 극심합니다. 반면 Z세대·메신저 헤비 유저는 텍스트보다 "내 사진으로 만든 짤"을 주고받습니다.
- **무엇이 다른가** — ① 손맛(물리 관성 + 틱 햅틱) ② 서버 0원·업로드 0% 로컬 누끼 ③ 카톡에서 네모 사진이 아니라 **굿즈 스티커**로 보이는 흰 테두리 합성 ④ 순정급으로 가벼운 자판.

---

## 핵심 기능

| # | 기능 | 사용자가 느끼는 것 | 구현 포인트 |
| :-: | :--- | :--- | :--- |
| 1 | **물리 관성 휠 & 틱 햅틱** | 휙 돌리면 관성으로 도로록 굴러가다 딸깍, 손을 떼면 중앙에 1.25x 로 착 붙는다 | `LazyRow` + `rememberSnapFlingBehavior`, 아이템 거리 기반 `rotationY` 원통 착시, 중심 통과 시 5~10ms `VibrationEffect` |
| 2 | **0.5초 로컬 NPU 누끼** | 사진 고르고 손 떼기 전에 이미 배경이 사라져 있다 | ML Kit Subject Segmentation (`play-services-mlkit-subject-segmentation`), 오프라인, INTERNET 권한 미선언 |
| 3 | **카카오톡 스마트 스티커 테두리** | 카톡에서도 "사진"이 아니라 다이어리에 붙인 스티커처럼 보인다 | 누끼 알파 실루엣을 36방향 오프셋 드로잉해 흰 테두리 + 드롭 섀도우 합성 (`StickerComposer`) |
| 4 | **초경량 고안정성 AOSP IME** | 순정 키보드처럼 즉시 입력되고, 튕김·자모 깨짐이 없다 | 검증된 AOSP 한글 IME 포크를 `keyboard/` 로 래핑, 자체 오토마타 미구현(리스크 제거), 상단 레이어만 커스터마이징 |

추가로: 스티커 하단 **한 줄 문구**("ㅋㅋㅋ", "퇴근각") 원터치 삽입, 전송은 **CommitContent API** 로 1탭.

---

## 기술 스택

| 영역 | 선택 | 이유 |
| :--- | :--- | :--- |
| 언어 | **Kotlin 2.0.21** (JVM 17) | 코루틴 기반 비동기 누끼 파이프라인 |
| UI | **Jetpack Compose** (BOM 2024.10.01, Material 3) | 휠·스튜디오를 상태 기반으로 선언적 구현, IME 뷰에도 `ComposeView` 로 재사용 |
| 휠 물리 | **Compose Fling** (`rememberSnapFlingBehavior`) + 자체 `WheelPhysics` 감속/스냅 모델 | 스냅 목표를 손 떼는 순간 확정해 애니메이션 중 흔들림 제거 |
| 누끼 | **ML Kit Subject Segmentation** 16.0.0-beta1 | 온디바이스·오프라인·서버 비용 0원 |
| 키보드 | **`InputMethodService`** + AOSP 한글 IME 포크 | 자모 오토마타 리스크 원천 차단 |
| 이미지 전송 | **`CommitContent`** (`androidx.core` `InputConnectionCompat`) | 카톡·인스타 DM 입력창에 파일 핸들 직접 전달 |
| 저장 | 로컬 파일 + `index.json` (`StickerStore`) | 스티커는 단말기 밖으로 나가지 않음. 백업 대상 제외 |
| 빌드 | AGP 8.7.3 · Gradle 8.11.1 · Version Catalog | `gradle/libs.versions.toml` 단일 버전 관리 |

> 광고(AdMob 보상형·네이티브)와 인앱결제(1,900원 광고제거 패스)는 4주차에 붙입니다. **현재 코드베이스에는 광고·결제·네트워크 SDK 가 하나도 없습니다.**

---

## 4주 MVP 로드맵

| 주차 | 목표 | 산출물 |
| :---: | :--- | :--- |
| **1~2주** | 핵심 엔진 구축 (Android) | AOSP 한글 IME 포크 세팅·자모 결합 안정화, Jetpack Compose 가로 물리 롤링 휠 + 틱 햅틱 인터랙션, 스티커 스튜디오 기본 플로우 |
| **3주** | 온디바이스 누끼 & 스마트 스티커 | ML Kit Subject Segmentation 연동(0.5초 목표), 흰 테두리 + 그림자 합성 렌더러, 카카오톡·인스타 DM 전송 호환성 검증 |
| **4주** | 광고/결제 & 스토어 출시 | 1,900원 광고제거 인앱결제, AdMob 보상형/네이티브 연동, 구글 플레이스토어 심사·정식 출시 |
| 5주~ | 숏폼 바이럴 | "1초 스티커 박제", "도로록 ASMR", "광고 키보드 갈아타기" 릴스/쇼츠 3종 포맷 |

상세 일정과 기능 명세는 [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md), 구현 설계는 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) 를 봅니다.

---

## 저장소 구조

```text
DOROROK/
├── app/
│   ├── build.gradle.kts                 # 앱 모듈(Compile SDK 35, minSdk 26)
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml          # IME 서비스 · FileProvider · 권한 최소화
│       ├── java/com/dororok/keyboard/
│       │   ├── MainActivity.kt          # 스티커 스튜디오 (사진 → 누끼 → 저장)
│       │   ├── DororokApp.kt            # 누끼 엔진·스티커 저장소 싱글턴
│       │   ├── ime/
│       │   │   ├── DororokImeService.kt # InputMethodService 진입점
│       │   │   └── DororokKeyboard.kt   # 상단 휠 + 자판 레이아웃
│       │   ├── wheel/
│       │   │   ├── StickerWheel.kt      # Compose 드럼 휠 (Fling + 스냅)
│       │   │   ├── WheelPhysics.kt      # 감속 · 스냅 · 틱 강도 모델
│       │   │   └── TickHaptics.kt       # 5~10ms 틱 진동
│       │   ├── cutout/
│       │   │   ├── SubjectCutoutEngine.kt  # ML Kit Subject Segmentation
│       │   │   └── StickerComposer.kt      # 흰 테두리 + 섀도우 + 문구 렌더러
│       │   ├── sticker/
│       │   │   ├── Sticker.kt · StickerStore.kt
│       │   │   └── StickerSender.kt     # CommitContent 전송
│       │   └── ui/theme/Theme.kt        # 도로록 옐로우/잉크 테마
│       └── res/ (values · xml/method · xml/file_paths · drawable)
├── docs/
│   ├── ARCHITECTURE.md                  # IME·휠·누끼·전송·iOS 2트랙 설계
│   └── PRODUCT_SPEC.md                  # 서비스 기획안 v2.0 전문
├── gradle/libs.versions.toml            # 버전 카탈로그
├── gradlew · gradlew.bat · gradle/wrapper/
└── settings.gradle.kts · build.gradle.kts
```

---

## 빌드 & 실행

```bash
# 1) JDK 17 + Android SDK 35 필요
./gradlew :app:assembleDebug

# 2) 설치
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3) 키보드 등록
#    설정 → 언어 및 입력 → 키보드 관리 → "도로록 키보드" 켜기
#    입력창 길게 눌러 키보드 선택 → 도로록
```

빌드 전 알아둘 점:

- **`keyboard/` (AOSP 한글 IME 포크) 는 아직 저장소에 없습니다.** 1주차에 서브모듈/모듈로 추가하고 `DororokKeyboard` 의 `ScaffoldKeyboardRows` 자리를 대체합니다. 그 전까지 자판 탭은 영문 문자만 커밋하는 플레이스홀더입니다.
- ML Kit Subject Segmentation 은 **베타**라 마이너 버전에서 시그니처가 바뀔 수 있습니다. `SubjectSegmenterOptions` / `SubjectSegmentationResult` 두 타입만 확인하면 됩니다.
- 첫 누끼 호출은 모델 초기화 때문에 느립니다(수백 ms~1초). 0.5초 목표는 워밍업 이후 기준입니다.

---

## 프라이버시

- `AndroidManifest.xml` 에 **INTERNET 권한이 없습니다.** 키 입력·갤러리 이미지가 외부로 나갈 경로 자체가 없습니다.
- 누끼·테두리 합성·저장은 전부 단말기 로컬 연산입니다 (서버 비용 0원, 업로드 0%).
- 비밀번호·PIN·OTP 필드에서는 커스텀 뷰를 띄우지 않고 OS 기본 보안 키보드로 넘깁니다 (`onEvaluateInputViewShown`).
- 스티커 파일은 `filesDir/stickers/` 에만 저장되고 클라우드 백업에서 제외됩니다 (`backup_rules.xml`).

---

## 문서

| 문서 | 내용 |
| :--- | :--- |
| [docs/PRODUCT_SPEC.md](docs/PRODUCT_SPEC.md) | 기획 배경·타겟·기능 명세·수익화·런칭 로드맵 (기획안 v2.0 전문) |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | IME 구조, Compose 휠 레이어, ML Kit 연동, CommitContent/외곽선 렌더러, iOS 2트랙 메모리 분리 |

---

## 라이선스

비공개 검토 단계입니다. AOSP 한글 IME 포크를 포함하게 되므로 **Apache-2.0** 적용을 기본으로 검토 중이며, 포크 대상 확정 후 `LICENSE` 를 추가합니다.
