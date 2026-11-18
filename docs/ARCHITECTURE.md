# 도로록(DOROROK) 시스템 아키텍처

문서 상태: v0.1 (MVP 스캐폴딩 기준) · 대상: Android 1차 출시, iOS 2단계 확장
관련 문서: [PRODUCT_SPEC.md](PRODUCT_SPEC.md) · [README](../README.md)

---

## 1. 설계 원칙

1. **키보드는 시스템 컴포넌트다.** IME 는 다른 앱의 입력창 위에 얹히는 특수한 프로세스라, 크래시·렉·메모리 초과가 곧 "타이핑이 안 되는" 사고가 된다. 그래서 기능은 화려하게, 엔진은 보수적으로 간다.
2. **경계를 좁게 자른다.** 자판 오토마타(AOSP 포크) / 휠 UI / 누끼 연산 / 합성 렌더러 / 전송 을 서로 다른 패키지로 분리하고, 경계는 콜백 몇 개로만 연결한다.
3. **데이터는 단말기를 떠나지 않는다.** INTERNET 권한을 선언하지 않는 것이 이 원칙의 기술적 강제 수단이다. 광고·결제 SDK 도입은 4주차에 별도 검토한다.
4. **무거운 것은 미리 한다.** 카톡이 투명 PNG 를 사진으로 보내는 제약 때문에, 테두리·섀도우·문구는 전송 순간이 아니라 **저장 순간**에 합성해 둔다. 전송 경로에서 해야 할 일은 파일 핸들 넘기기 하나뿐이다.

---

## 2. 전체 구조

```text
┌───────────────────────────── 사용자 단말기 (앱 프로세스) ─────────────────────────────┐
│                                                                                       │
│  ┌── 스티커 스튜디오 (Activity) ──┐        ┌──── 도로록 키보드 (IME 서비스) ────┐      │
│  │ MainActivity                  │        │ DororokImeService                  │      │
│  │  · Photo Picker (권한 불필요)  │        │  · onCreateInputView → ComposeView │      │
│  │  · 미리보기 / 문구 입력        │        │  · onEvaluateInputViewShown        │      │
│  └──────────┬────────────────────┘        └───────────────┬────────────────────┘      │
│             │                                             │                            │
│             ▼                                             ▼                            │
│  ┌── cutout ─────────────────┐               ┌── ime/DororokKeyboard (Compose) ──┐    │
│  │ SubjectCutoutEngine       │               │  상단: 탭 칩 + StickerWheel        │    │
│  │  (ML Kit, NPU/GPU 로컬)    │               │  자판: AOSP 포크가 그리는 영역      │    │
│  │ StickerComposer           │               └──────────────┬───────────────────┘    │
│  │  (실루엣 확장 + 섀도우)     │                              │                        │
│  └──────────┬────────────────┘               ┌──────────────▼───────────────────┐    │
│             │                                │ wheel/StickerWheel · WheelPhysics │    │
│             ▼                                │       · TickHaptics               │    │
│  ┌── sticker/StickerStore ───────────────────┴───────────────────────────────────┐   │
│  │  filesDir/stickers/{id}.png  +  index.json                                    │   │
│  └──────────┬────────────────────────────────────────────────────────────────────┘   │
│             │                                                                         │
│             ▼                                                                         │
│  ┌── sticker/StickerSender ──────────────────────────────────────────────────────┐    │
│  │  FileProvider URI → InputConnectionCompat.commitContent → 카톡/인스타 입력창     │    │
│  └───────────────────────────────────────────────────────────────────────────────┘    │
└───────────────────────────────────────────────────────────────────────────────────────┘
                                     │ CommitContent (MIME: image/png)
                                     ▼
                        카카오톡 · 인스타그램 DM (호스트 앱)
```

패키지 의존 방향은 한쪽으로만 흐른다: `ime → wheel, sticker`, `MainActivity → cutout, sticker`, `cutout → (ML Kit)`. 역방향 의존은 없다.

---

## 3. IME 레이어 (`InputMethodService`)

### 3.1 생명주기와 뷰

| 시점 | 하는 일 | 주의 |
| :--- | :--- | :--- |
| `onCreate()` | `TickHaptics` 생성 | 진동자는 서비스 수명 동안 유지 |
| `onCreateInputView()` | `ComposeView` 를 만들어 반환, 스티커 목록 로드 | IME 뷰는 재바인딩마다 새로 만들어질 수 있다 → `ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed` |
| `onStartInputView(info, restarting)` | 스티커 목록 재조회(스튜디오에서 방금 만든 것 반영) | `restarting=true` 는 같은 필드 재진입이므로 상태 보존 |
| `onFinishInput()` | 선택 스티커 상태 초기화 | 다음 입력창에 이전 선택이 새지 않게 |
| `onDestroy()` | ML Kit 클라이언트 `close()`, 진동 취소 | 프로세스가 살아있는 동안 재사용 |

IME 뷰는 **호스트 앱의 윈도우 안에 삽입**된다. 그래서 다음을 지킨다.

- 뷰 트리에 호스트 앱의 테마가 섞이지 않도록 `DororokTheme` 를 Compose 안에서 자체 적용.
- `ComposeView` 에 `LocalConfiguration` 변화가 잦으므로 리컴포지션 비용이 큰 계산(디코딩, 누끼)은 `remember` + 백그라운드 디스패처로 분리.

### 3.2 입력 커밋 경로

```text
Compose 키 탭
   → DororokKeyboard.onText(String)
   → DororokImeService.commitTextToEditor()
   → currentInputConnection.commitText(text, 1)
```

삭제는 선택 영역이 있으면 `commitText("", 1)`, 없으면 `deleteSurroundingText(1, 0)`.
엔터는 `sendKeyEvent(KEYCODE_ENTER)` — 일부 에디터(검색창)는 `commitText("\n")` 을 무시하기 때문이다.

### 3.3 한글 자판 엔진 래핑 전략

기획안의 결정(§3.3)을 그대로 따른다: **자체 오토마타를 짜지 않는다.**

- AOSP 한글 IME(검증된 오픈소스 포크)를 `keyboard/` 모듈로 두고, `KeyboardView` 대신 Compose 로 그리는 얇은 어댑터를 둔다.
- 서비스는 "문자열이 확정되었다"는 콜백만 받는다: `(String) -> Unit`.
- 자모 분리·겹모음·쌍자음·받침 이동 같은 조합 규칙은 전부 엔진 책임이고, 이 저장소의 코드 변경으로 깨질 수 없다.
- **대체 지점**: `DororokKeyboard.kt` 의 `ScaffoldKeyboardRows` 컴포저블. 플레이스홀더는 영문 소문자·스페이스·백스페이스·엔터만 커밋하며, 포크가 들어오면 이 함수 본문만 교체한다.

### 3.4 프라이버시 스위치

```kotlin
override fun onEvaluateInputViewShown(): Boolean =
    super.onEvaluateInputViewShown() && !isSensitiveField()
```

`TYPE_TEXT_VARIATION_PASSWORD`, `VISIBLE_PASSWORD`, `WEB_PASSWORD`, `NUMBER_VARIATION_PASSWORD` 를 감지하면 커스텀 뷰를 띄우지 않는다. OS 가 기본 보안 키보드로 처리하므로, 서드파티 키보드가 비밀번호를 관찰할 수 있는 경로를 열지 않는다.

> 한계: 이 판단은 호스트 앱이 `inputType` 을 정직하게 선언했다는 전제 위에 있다. 금융 앱처럼 자체 보안 키보드를 쓰는 경우는 애초에 IME 가 호출되지 않는다.

---

## 4. 휠 레이어 (Compose)

### 4.1 상태 모델

| 상태 | 소유자 | 설명 |
| :--- | :--- | :--- |
| 스크롤 오프셋 | `LazyListState` | 아이템 인덱스 + 픽셀 오프셋 |
| 중앙 인덱스 | `derivedStateOf` | 뷰포트 중앙에 가장 가까운, 그리고 반 칸 이내에 들어온 아이템 |
| 선택 스티커 | `DororokImeService.lastSelected` | 전송 대상. 중앙 인덱스 변화 시 갱신 |
| 햅틱 상태 | `TickHaptics` | 진폭·활성화 여부(프리미엄 사운드 팩과 연동 예정) |

### 4.2 제스처 → 플링 → 스냅

1. **드래그**: `LazyRow` 가 스크롤 오프셋을 갱신.
2. **플링**: `rememberSnapFlingBehavior` 가 `FlingBehavior` 로 주입되어, 손을 뗀 속도 벡터로 감속 애니메이션을 만들고 **가장 가까운 아이템 경계에 스냅**한다.
3. **감속 모델**: Compose 기본은 `exponentialDecay`. `WheelPhysics.DECAY_FRICTION(4.0)` 수준에서 "한두 칸 더 굴러가는" 관성이 나온다. 값을 키우면 빨리 멈추고, 줄이면 더 미끄러진다.
4. **중앙 정렬 크기**: 중앙 아이템은 `scale = 1.25f`, 거리 1칸에서 `1.05`, 2칸 이상은 `0.85~0.8` 로 감쇠 → "가운데가 도드라지는" 드럼 착시.

```kotlin
// 아이템별 변환 (칸 단위 거리 d)
rotationY      = d * 22°                     // 원통면 회전
scale          = (1.25 - 0.20·|d|).coerceIn(0.8, 1.25)
alpha          = (1.0  - 0.30·|d|).coerceIn(0.25, 1.0)
cameraDistance = 12dp → px                   // 원근에 의한 찌그러짐 방지
```

### 4.3 틱 햅틱

- 발화 조건: `derivedStateOf` 로 계산한 중앙 인덱스가 **정수 단위로 바뀐 순간** (`snapshotFlow { centerIndex }.distinctUntilChanged()`).
- 파형: `VibrationEffect.createOneShot(7ms, amplitude)`.
  - 진폭 제어 지원 기기: `70/255` 기준, 회전 속도에 따라 `20~160` 사이로 가감 (`WheelPhysics.tickIntensity`).
  - 미지원 기기: `DEFAULT_AMPLITUDE` 로 폴백 (진동이 뭉개지는 대신 "딸깍" 은 유지).
- 스냅 완료 시 `TickHaptics.snap()` (진폭 ×1.35) 한 번 더 → "착" 붙는 감각.
- **과발화 방지**: 빠른 플링 중에는 `tickIntensity` 가 0.55 근처로 내려가고, 프레임당 1회로 제한된다(`distinctUntilChanged` + 인덱스 변화만 관찰).

### 4.4 성능 예산

| 항목 | 목표 | 근거 |
| :--- | :--- | :--- |
| 휠 프레임 | 60fps 유지 (16.6ms) | LazyRow 는 화면 밖 아이템을 그리지 않음 |
| 아이템당 계산 | ≤ 3µs | `layoutInfo` 조회 + 산술 연산만 |
| 스티커 디코딩 | 캐시 hit 시 0ms | `remember(filePath)` 로 1회 디코딩, 56dp 썸네일 |
| 슬롯 상한 | 30칸까지 현 구조 유지 | 초과 시 `layoutInfo` 아이템별 조회를 단일 중심 오프셋 상태로 리팩터 (코드 TODO) |

---

## 5. 온디바이스 누끼 파이프라인

### 5.1 흐름

```text
Photo Picker (권한 요청 없음)
  → ContentResolver.openInputStream → Bitmap
  → SubjectCutoutEngine.cutout(bitmap)
        · 최대 변 1024px 로 다운스케일 (연산량·메모리 상한)
        · ML Kit Subject Segmentation (SINGLE_IMAGE 모드)
        · 결과: subject(배경 투명 Bitmap) + foregroundConfidences
        · 5초 타임아웃
  → StickerComposer.compose(subject, outlinePx, caption)
  → PNG 저장 (filesDir/stickers/{id}.png) + index.json 갱신
  → 휠에 등장
```

### 5.2 ML Kit 연동 세부

```kotlin
val segmenter: Segmenter = Segmentation.getClient(
    SubjectSegmenterOptions.builder()
        .setDetectorMode(SubjectSegmenterOptions.SINGLE_IMAGE_MODE)
        .build(),
)
segmenter.process(InputImage.fromBitmap(bitmap, 0))
```

| 결정 | 이유 |
| :--- | :--- |
| `SINGLE_IMAGE_MODE` | 스티커는 정지 사진 1장. 스트림 모드는 불필요한 프레임 파이프라인 |
| 다운스케일 1024px | 스티커 출력은 최대 512px 수준. 1024 이상은 연산만 늘고 결과 차이 없음 |
| `Task` → `suspendCancellableCoroutine` 어댑터 | `kotlinx-coroutines-play-services` 의존성을 추가하지 않으려고 직접 래핑 |
| 타임아웃 5초 | 초기 모델 로딩이 느린 기기에서 무한 대기 방지 → 사용자에게 "다시 시도" 안내 |
| `close()` | IME/앱 프로세스 종료 시 모델 메모리 해제 |

**베타 API 주의**: `play-services-mlkit-subject-segmentation` 은 **16.0.0-beta1** 이고, 시그니처가 바뀔 수 있다. 영향 범위는 `SubjectCutoutEngine.kt` 한 파일로 한정되게 설계했다.

### 5.3 품질 게이트 (예정)

`foregroundConfidences` 는 픽셀별 신뢰도 배열이다. 지금은 사용하지 않지만, 다음 게이트를 3주차 검증에서 붙인다.

- 신뢰도 평균이 임계 이하 → "피사체를 못 찾았어요" 안내 후 재선택 유도.
- 피사체 면적 비율이 너무 작으면(전체의 <3%) 합성 시 최소 테두리 두께를 키워 형태 유지.

---

## 6. 스티커 합성 렌더러 (`StickerComposer`)

### 6.1 왜 저장 시점에 굽는가

카카오톡은 `CommitContent` 로 받은 투명 PNG 를 인라인 스티커로 렌더링하지 않고 **사진 파일로 전송**한다. 즉 투명 영역이 채팅 배경색에 따라 검게/흰게 보이거나, 네모난 사진 박스가 그대로 남는다.
따라서 누끼 결과에 흰 테두리와 그림자를 **미리 입혀** 파일 자체를 "스티커처럼 보이는 이미지"로 만든다. 전송 경로는 무거워지지 않고, 결과는 앱을 바꿔도 유지된다.

### 6.2 알고리즘

```text
입력: subject(알파 있음), outlinePx, shadowPx, caption
1. pad = outlinePx + shadowPx + 3px
   captionBand = (caption 있으면) 높이*0.12 + 8px
   출력 캔버스 = (subject 크기 + 2·pad) × (… + captionBand)
2. silhouette = subject 의 알파를 흰색으로 채운 사본 (PorterDuff SRC_IN)
3. [섀도우] silhouette 을 shadowPx BlurMaskFilter, 검정 35% 로 pad 영역에 그리고 3px 아래로 이동
4. [테두리] silhouette 을 원형 36방향으로 outlinePx 오프셋 드로잉 (흰색)
5. [본체] subject 를 원래 위치에 드로잉
6. [문구] 스트로크(흰색, 0.18em) → 채움(잉크 블랙), 하단 중앙
```

- **왜 36방향 오프셋인가**: `BlurMaskFilter` 로 테두리를 만들면 흐려져서 카톡 축소 렌더 시 회색 띠가 생긴다. 오프셋 드로잉은 경계가 또렷하고, 36방향이면 56dp 썸네일~512px 원본 어디서도 계단이 보이지 않는다(비용: 드로잉 36회, 512px 기준 수 ms).
- **해상도 정책**: 출력 최대 변 512px. 카톡 전송 시 자동 축소되므로 그 이상은 용량만 늘어난다.
- **PNG 강제**: WebP 도 카톡에서 받지만, 알파 경계 품질과 구버전 클라이언트 호환을 위해 PNG 무손실(quality 100)을 기본으로 한다.

### 6.3 문구(캡션)

- 최대 12자, 한 줄. 입력 즉시 미리보기에 반영(향후), 저장 시 합성.
- 폰트: 시스템 기본 Bold. 추후 프리미엄 폰트 팩(1,900원 패스 혜택)으로 확장.

---

## 7. 전송 레이어 (`CommitContent`)

### 7.1 왜 클립보드가 아닌가

| 방식 | 카톡 동작 | 판정 |
| :--- | :--- | :--- |
| 클립보드 복사 + 붙여넣기 | 이미지 붙여넣기 미지원/불안정, 앱마다 다름 | ✗ |
| `commitText` 로 경로 전송 | 텍스트만 감 | ✗ |
| **`CommitContent`** | 에디터가 이미지 MIME 을 선언한 경우 파일 핸들 전달 | ✓ |

### 7.2 흐름

```text
스티커 탭
  → StickerSender.canSendImages(ime)
       EditorInfoCompat.getContentMimeTypes(currentInputEditorInfo)
       에 image/png · image/webp · image/* 중 하나라도 있으면 true
  → false 면 "이 앱은 이미지 전송을 지원하지 않아요" 안내 (전송 시도하지 않음)
  → true 면
       FileProvider.getUriForFile(authority = "${packageName}.stickerfileprovider")
       ClipDescription("dororok-sticker", ["image/png"])
       InputContentInfoCompat(uri, description, null)
       InputConnectionCompat.commitContent(ic, editorInfo, info,
            INPUT_CONTENT_GRANT_READ_URI_PERMISSION, listener)
       listener 안에서 inputContentInfo.requestPermission(grantFlags)
```

- **`requestPermission` 이 핵심**: 카톡·인스타는 URI 를 받은 뒤 권한을 요청해야 실제로 읽는다. 이 호출을 빠뜨리면 전송이 조용히 실패한다.
- **반환값**: `commitContent` 가 `false` 면 에디터가 거부한 것 → 토스트로 폴백. `true` 는 "받았다"이지 "전송 완료"는 아니지만, 입력창에 말풍선이 뜨는 것을 사용자가 즉시 확인한다.
- **파일 노출 범위**: FileProvider 는 `filesDir/stickers/` 만 노출한다(`res/xml/file_paths.xml`). 다른 앱 데이터는 URI 로도 접근 불가.

### 7.3 호스트 앱별 관찰 (3주차 검증 항목)

| 앱 | 관찰 | 대응 |
| :--- | :--- | :--- |
| 카카오톡 | `image/*` 선언. 투명 PNG → 사진 파일로 전송, 인라인 스티커 아님 | 흰 테두리 사전 합성(§6), PNG 무손실 |
| 인스타그램 DM | `image/*` 선언, 전송 후 썸네일 크기로 축소 | 512px 출력이면 충분 |
| 문자/구글 메시지 | MMS 로 전환, 알파 유지 | 동일 경로 사용 |
| 이미지 미지원 에디터 | `commitContent` false | 안내 토스트, 텍스트 입력은 그대로 |

---

## 8. 저장소와 슬롯 정책

```text
filesDir/stickers/
├── 8f3a1c2d.png       # 합성 완료된 투명 PNG
└── index.json         # [{id, filePath, caption, createdAt}, …]  (최신순)
```

- 인덱스는 `JSONArray` (앱 외부와 교환하지 않으므로 스키마 마이그레이션 부담 없음). Room 도입은 슬롯 수십 개·검색이 필요해질 때.
- 슬롯: 무료 10칸(`StickerStore.DEFAULT_FREE_SLOTS`). 보상형 광고 1편 = +3칸 영구(`SLOTS_PER_REWARDED_AD`). 해금 값은 4주차에 `SharedPreferences` 로 영속화.
- 삭제: 인덱스에서 제거 + 파일 삭제. 삭제된 스티커가 휠 캐시에 남지 않도록 `onStartInputView` 에서 목록을 재조회한다.
- 백업: `backup_rules.xml` / `data_extraction_rules.xml` 에서 `stickers/` 제외 — 사용자 사진으로 만든 결과물이 클라우드로 올라가지 않게.

---

## 9. 프라이버시 & 보안

| 위협 | 통제 |
| :--- | :--- |
| 키 입력·이미지의 외부 유출 | `INTERNET` 권한 미선언 → 네트워크 경로 자체가 없음. 광고/결제 SDK 도입 시점에 권한 추가와 함께 재검토 |
| 비밀번호 입력 관찰 | `onEvaluateInputViewShown` 에서 password 계열 variation 차단 |
| 다른 앱의 스티커 파일 접근 | FileProvider 로 `stickers/` 만, `exported=false`, URI 단위 grant |
| 백업을 통한 유출 | 백업 규칙에서 스티커 디렉터리 제외, `allowBackup=false` |
| 누끼 모델의 원격 호출 | ML Kit 온디바이스 모델 (서버 왕복 없음). 최초 모델 다운로드는 Play Services 가 담당 |

**남은 검토**: 4주차 AdMob 도입 시 INTERNET 권한이 필요하다. 그때 "광고 요청 외 네트워크 통신 없음" 을 어떻게 보장·표기할지(예: 네트워크 보안 구성으로 광고 도메인만 허용) 결정해야 한다. → PRODUCT_SPEC §미결 과제.

---

## 10. iOS 2단계 확장 — 메모리 48MB 제약 대응

### 10.1 제약

iOS 키보드 익스텐션은 메모리 사용량이 약 **48MB** 를 넘으면 OS 가 프로세스를 강제 종료한다. JPEG 몇 장과 Vision 모델을 익스텐션 안에서 다루면 즉시 한계에 닿는다.

### 10.2 2트랙 분리

```text
┌────────────── 컨테이너 앱 (RollKey 본체) ──────────────┐      ┌──────── 키보드 익스텐션 ────────┐
│  · 갤러리 접근 (PHPicker, 권한 최소화)                  │      │  · AI 모델 로드 금지             │
│  · Apple Vision Subject Lifting 누끼 연산                │ App  │  · 스티커 팩(경량 썸네일) 렌더링  │
│  · 흰 테두리·섀도우 합성                                  │Group│  · 휠 인터랙션 + 틱 햅틱          │
│  · 스티커 팩을 App Group 공유 저장소에 경량 캐싱          │─────▶│  · 선택 시 UIPasteboard 복사      │
│    (512px WebP + manifest.json)                        │      │  메모리 목표 ≤ 15MB             │
└────────────────────────────────────────────────────────┘      └─────────────────────────────────┘
```

| 결정 | 이유 |
| :--- | :--- |
| 합성을 **본체 앱**에서 수행 | 익스텐션은 렌더링만. CoreGraphics 합성도 메모리를 쓰므로 분리 |
| **WebP 512px** 캐시 | PNG 대비 1/3 용량. 익스텐션은 디코딩 결과만 들고 있으면 됨 |
| `manifest.json` 인덱스 | 익스텐션이 파일 목록을 빠르게 읽고, 썸네일만 지연 로딩 |
| `UIPasteboard` 복사 | iOS 에는 CommitContent 같은 표준 경로가 없어 붙여넣기 방식이 사실상 표준 |
| 휠 렌더링은 UIKit/Compose 대응 구현 | SwiftUI `ScrollView` + 스냅, 햅틱은 `UIImpactFeedbackGenerator(.light)` |

### 10.3 메모리 예산

| 구성 | 목표 |
| :--- | :--- |
| 익스텐션 기본(뷰·오토레이아웃·휠) | ~8MB |
| 가시 스티커 썸네일 3~5장 디코딩 | ~4MB |
| 여유(스파이크·시스템) | ~3MB |
| **합계** | **≤ 15MB** (48MB 한계 대비 3배 여유) |

Android MVP 검증이 끝난 뒤, 합성 결과 파일 포맷(PNG/WebP)과 매니페스트 스키마를 공유해 두 플랫폼이 같은 스티커 팩을 읽도록 맞춘다.

---

## 11. 성능 목표

| 시나리오 | 목표 | 측정 지점 |
| :--- | :--- | :--- |
| 키보드 첫 표시(콜드) | ≤ 400ms | `onCreateInputView` ~ 첫 프레임 |
| 키보드 재표시(웜) | ≤ 120ms | `onStartInputView` ~ 첫 프레임 |
| 키 입력 반응 | ≤ 16ms (1프레임) | 탭 ~ `commitText` |
| 누끼(워밍업 후) | ≤ 500ms | 사진 선택 ~ 합성 완료 |
| 누끼(콜드, 모델 로딩 포함) | ≤ 1.5s | 첫 호출 |
| 휠 스크롤 | 60fps | 프레임 드롭 0 목표 |
| 스티커 전송 | ≤ 150ms | 탭 ~ `commitContent` true |

---

## 12. 검증 계획

1. **IME 기본 동작**: 삼성 인터넷·크롬 주소창, 메모 앱, 카카오톡 입력창에서 텍스트·백스페이스·엔터·한/영 전환.
2. **비밀번호 필드**: 은행 앱·크롬 비밀번호 입력에서 커스텀 뷰가 뜨지 않는지.
3. **누끼 품질**: 인물(얼굴/전신), 반려동물, 사물, 저조도, 복잡 배경 각 5장 → 골든 이미지 비교.
4. **테두리 렌더**: 카톡 라이트/다크, 인스타 DM 다크에서 흰 테두리가 배경과 구분되는지.
5. **전송 호환성**: 카카오톡(최신/1년 전 버전), 인스타 DM, 구글 메시지.
6. **성능**: `Macrobenchmark` 로 키보드 표시 시간, 휠 프레임, 누끼 소요 측정.
7. **메모리**: 익스텐션 대응 전 Android 기준 IME 프로세스 상주 메모리 ≤ 80MB.

---

## 13. 미결 과제 / 리스크

| 항목 | 내용 | 대응 시점 |
| :--- | :--- | :--- |
| AOSP 한글 IME 포크 선정 | 라이선스(Apache-2.0) 확인 + 유지보수 상태 검토. 후보: AOSP LatinIME 계열 한글 포크 | 1주차 |
| ML Kit 베타 API 변경 | 16.0.0-beta1 → 정식. `SubjectCutoutEngine` 내부에 격리 | 3주차 |
| 카톡 클라이언트 업데이트 | CommitContent 처리 방식이 바뀔 수 있음. 버전별 회귀 테스트 필요 | 3주차, 이후 상시 |
| 광고 SDK와 프라이버시 약속 | INTERNET 권한 추가 시 "로컬 처리" 문구의 정합성 | 4주차 |
| EXIF 회전 | 사진 선택 시 회전 정보 미반영 가능 → `ExifInterface` 처리 필요 | 2주차 |
| 휠 성능 (슬롯 무제한) | 아이템별 `layoutInfo` 조회 → 중심 오프셋 단일 상태로 리팩터 | 4주차 이후 |
