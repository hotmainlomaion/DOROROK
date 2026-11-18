package com.dororok.keyboard.sticker

import android.content.ClipDescription
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.net.Uri
import android.util.Log
import android.view.inputmethod.InputConnection
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import java.io.File

/**
 * 카카오톡·인스타그램 DM 등에 스티커 이미지를 `CommitContent` 로 전송한다.
 *
 * ## 왜 텍스트가 아니라 CommitContent 인가
 * - 입력창에 그냥 붙여넣기(클립보드)하면 앱마다 동작이 달라지고, 카톡에서는
 *   "붙여넣을 수 없습니다" 로 막히는 경우가 있다.
 * - CommitContent 는 에디터가 "이미지 MIME 을 받을 수 있다"고 선언했을 때만
 *   키보드가 파일 핸들을 넘기는 공식 경로다. 유저가 누른 1탭 = 전송 1회.
 * - 앱이 받을 수 없으면 [canSendImages] 가 false 이고, 호출부는
 *   "이 앱은 이미지 전송을 지원하지 않아요" 안내로 폴백한다.
 *
 * ## 카카오톡 특이사항
 * - 카톡은 `image/*` 를 선언하지만 투명 배경 PNG 를 그대로 인라인 스티커로
 *   렌더링하지 않고 사진 파일로 보낸다. 그래서 흰 테두리 합성이 필수다.
 * - `requestPermission()` 을 호출해야 카톡이 URI 를 읽을 수 있는 권한을 받는다.
 */
object StickerSender {

    private const val TAG = "StickerSender"
    private const val MIME_IMAGE_PNG = "image/png"
    private const val MIME_IMAGE_WEBP = "image/webp"

    /** 에디터가 이미지 CommitContent 를 받을 수 있는지. */
    fun canSendImages(ime: InputMethodService): Boolean {
        val info = ime.currentInputEditorInfo ?: return false
        val types = EditorInfoCompat.getContentMimeTypes(info)
        return types.any { it == MIME_IMAGE_PNG || it == MIME_IMAGE_WEBP || it == "image/*" }
    }

    /**
     * 스티커 1장 전송. 성공하면 true.
     * 실패해도 입력 중이던 텍스트 상태는 건드리지 않는다.
     */
    fun send(ime: InputMethodService, sticker: Sticker): Boolean {
        val ic: InputConnection = ime.currentInputConnection ?: return false
        val context: Context = ime
        val file = File(sticker.filePath)
        if (!file.exists()) return false

        val uri: Uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${context.packageName}.stickerfileprovider",
            file,
        )
        val description = ClipDescription(
            "dororok-sticker",
            arrayOf(MIME_IMAGE_PNG),
        )
        val contentInfo = InputContentInfoCompat(uri, description, null)

        val listener = InputConnectionCompat.OnCommitContentListener { inputContentInfo, flags, _ ->
            val readFlag = InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION
            val writeFlag = InputConnectionCompat.INPUT_CONTENT_GRANT_WRITE_URI_PERMISSION
            val grantFlags = flags and (readFlag or writeFlag)
            return@OnCommitContentListener try {
                inputContentInfo.requestPermission(grantFlags)
                true
            } catch (e: Exception) {
                Log.w(TAG, "commit content permission denied", e)
                false
            }
        }

        val consumed = InputConnectionCompat.commitContent(
            ic,
            ime.currentInputEditorInfo,
            contentInfo,
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            listener,
        )
        return consumed
    }
}
