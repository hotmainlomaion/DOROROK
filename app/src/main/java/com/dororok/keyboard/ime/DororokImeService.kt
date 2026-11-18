package com.dororok.keyboard.ime

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.dororok.keyboard.DororokApp
import com.dororok.keyboard.R
import com.dororok.keyboard.sticker.Sticker
import com.dororok.keyboard.sticker.StickerSender
import com.dororok.keyboard.ui.theme.DororokTheme
import com.dororok.keyboard.wheel.TickHaptics

/**
 * 도로록 기본 키보드 (IME).
 *
 * ## 구조
 * - `InputMethodService` 가 시스템과의 유일한 접점이다. onCreateInputView() 가 돌려주는
 *   ComposeView 안에 "상단 스티커 휠 + 자판" 을 그린다.
 * - 자판 오토마타(한글 조합)는 이 파일에서 직접 구현하지 않는다. AOSP 한글 IME 포크를
 *   `keyboard/` 패키지로 래핑하고, 여기서는 입력 커밋 콜백만 받는다
 *   (docs/ARCHITECTURE.md §2 참고 — 자모 분리 버그 원천 차단 전략).
 *
 * ## 프라이버시
 * - INTERNET 권한이 없으므로 어떤 키 입력·이미지도 외부로 나갈 수 없다.
 * - 비밀번호 계열 입력 필드에서는 커스텀 뷰를 띄우지 않고 OS 기본 키보드로 넘긴다
 *   ([onEvaluateInputViewShown]).
 */
class DororokImeService : InputMethodService() {

    private lateinit var haptics: TickHaptics
    private var stickers: List<Sticker> = emptyList()
    private var lastSelected: Sticker? = null

    private val app: DororokApp get() = application as DororokApp

    override fun onCreate() {
        super.onCreate()
        haptics = TickHaptics(this)
    }

    override fun onCreateInputView(): View {
        refreshStickers()
        return ComposeView(this).apply {
            // IME 뷰는 화면 회전·재바인딩 때 자주 재생성된다. 풀을 재사용하지 않고 바로 해제.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DororokTheme {
                    DororokKeyboard(
                        stickers = stickers,
                        haptics = haptics,
                        canSendImages = StickerSender.canSendImages(this@DororokImeService),
                        onText = ::commitTextToEditor,
                        onBackspace = ::deleteOneChar,
                        onEnter = ::sendEnter,
                        onOpenStudio = ::openStudioHint,
                        onStickerSelected = { lastSelected = it },
                        onSendSticker = ::sendSticker,
                    )
                }
            }
        }
    }

    /** 입력 대상이 바뀌면 스티커 목록을 새로 읽는다 (스튜디오에서 방금 만든 스티커 반영). */
    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        refreshStickers()
    }

    /**
     * 비밀번호·PIN·OTP 필드에서는 커스텀 키보드를 띄우지 않는다.
     * OS 가 순정 보안 키보드로 처리하도록 넘기는 안전장치.
     */
    override fun onEvaluateInputViewShown(): Boolean {
        return super.onEvaluateInputViewShown() && !isSensitiveField()
    }

    private fun isSensitiveField(): Boolean {
        val inputType = currentInputEditorInfo?.inputType ?: return false
        val variation = inputType and EditorInfo.TYPE_MASK_VARIATION
        val className = inputType and EditorInfo.TYPE_MASK_CLASS
        return when (className) {
            EditorInfo.TYPE_CLASS_TEXT -> variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == EditorInfo.TYPE_TEXT_VARIATION_WEB_PASSWORD
            EditorInfo.TYPE_CLASS_NUMBER -> variation == EditorInfo.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private fun refreshStickers() {
        stickers = runCatching { app.stickerStore.all() }.getOrDefault(emptyList())
    }

    private fun commitTextToEditor(text: String) {
        currentInputConnection?.commitText(text, 1)
        haptics.tick(0.6f)
    }

    private fun deleteOneChar() {
        currentInputConnection?.let { ic ->
            val selected = ic.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic.commitText("", 1)
            } else {
                ic.deleteSurroundingText(1, 0)
            }
        }
    }

    private fun sendEnter() {
        currentInputConnection?.sendKeyEvent(
            android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_ENTER,
            ),
        )
    }

    private fun sendSticker(sticker: Sticker) {
        if (!StickerSender.canSendImages(this)) {
            Toast.makeText(this, "이 앱은 이미지 전송을 지원하지 않아요", Toast.LENGTH_SHORT).show()
            return
        }
        val sent = StickerSender.send(this, sticker)
        if (sent) {
            haptics.snap()
        } else {
            Toast.makeText(this, "스티커를 보내지 못했어요", Toast.LENGTH_SHORT).show()
        }
    }

    /** 스튜디오(본체 앱)로 이동 — 스티커를 새로 만들러 갈 때. */
    private fun openStudioHint() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(null, 0)
        Toast.makeText(
            this,
            getString(R.string.studio_title) + " 앱 아이콘에서 스티커를 만들 수 있어요",
            Toast.LENGTH_SHORT,
        ).show()
    }

    override fun onDestroy() {
        app.cutoutEngine.close()
        super.onDestroy()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            @Suppress("DEPRECATION")
            (getSystemService(VIBRATOR_SERVICE) as? android.os.Vibrator)?.cancel()
        }
    }
}
