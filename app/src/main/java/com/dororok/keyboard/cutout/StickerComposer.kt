package com.dororok.keyboard.cutout

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.Typeface
import kotlin.math.cos
import kotlin.math.sin

/**
 * "굿즈 스티커" 합성 렌더러.
 *
 * 카카오톡은 투명 PNG 를 인라인 스티커가 아니라 사진으로 보낸다.
 * 그래서 누끼 결과에 **도톰한 흰 테두리 + 부드러운 드롭 섀도우**를 미리 입혀
 * 어떤 채팅 배경 위에서도 다이어리에 붙인 스티커처럼 보이게 만든다.
 *
 * ## 테두리 알고리즘
 * 1. 피사체 알파 채널을 흰색 실루엣으로 굽는다.
 * 2. 실루엣을 반지름 [outlinePx] 만큼 원형으로 36방향 오프셋 드로잉 → 외곽선 두께 생성.
 *    (`BlurMaskFilter` 로 흐리는 것보다 경계가 또렷해 카톡 축소 렌더에서도 뭉개지지 않는다)
 * 3. 그 위에 원본 피사체를 그린다.
 * 4. 섀도우는 실루엣을 [shadowPx] 만큼 흐려 아래로 살짝 내려 먼저 깔아 둔다.
 *
 * 모든 좌표 연산은 [scale] 을 곱해 density-independent 하게 유지한다.
 */
object StickerComposer {

    private const val OUTLINE_DIRECTIONS = 36

    /**
     * @param subject 배경 투명 피사체
     * @param outlinePx 흰 테두리 두께(px). 0 이면 테두리 없음
     * @param shadowPx 드롭 섀도우 흐림 반경(px)
     * @param caption 하단 한 줄 문구 ("ㅋㅋㅋ", "퇴근각"). null/빈 값이면 생략
     */
    fun compose(
        subject: Bitmap,
        outlinePx: Int,
        shadowPx: Int = (outlinePx * 0.6f).toInt(),
        caption: String? = null,
    ): Bitmap {
        val pad = outlinePx + shadowPx + SHADOW_OFFSET_PX
        val captionBand = if (caption.isNullOrBlank()) 0 else (subject.height * 0.12f).toInt() + captionPadding()
        val outW = subject.width + pad * 2
        val outH = subject.height + pad * 2 + captionBand

        val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val dst = Rect(pad, pad, pad + subject.width, pad + subject.height)

        val silhouette = subject.silhouette()

        if (shadowPx > 0) {
            val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(Color.argb(90, 0, 0, 0), PorterDuff.Mode.SRC_IN)
                maskFilter = BlurMaskFilter(shadowPx.toFloat(), BlurMaskFilter.Blur.NORMAL)
            }
            canvas.save()
            canvas.translate(0f, SHADOW_OFFSET_PX.toFloat())
            canvas.drawBitmap(silhouette, null, dst, shadowPaint)
            canvas.restore()
        }

        if (outlinePx > 0) {
            val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            }
            for (i in 0 until OUTLINE_DIRECTIONS) {
                val angle = 2.0 * Math.PI * i / OUTLINE_DIRECTIONS
                val dx = (cos(angle) * outlinePx).toFloat()
                val dy = (sin(angle) * outlinePx).toFloat()
                canvas.save()
                canvas.translate(dx, dy)
                canvas.drawBitmap(silhouette, null, dst, outlinePaint)
                canvas.restore()
            }
        }

        canvas.drawBitmap(subject, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))

        if (!caption.isNullOrBlank()) {
            drawCaption(canvas, caption, outW, pad + subject.height + captionBand / 2)
        }

        if (silhouette !== subject) silhouette.recycle()
        return out
    }

    private fun drawCaption(canvas: Canvas, text: String, width: Int, centerY: Int) {
        val size = (width * 0.085f).coerceAtLeast(12f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // 흰 테두리 글씨: 스트로크 먼저, 채움 나중 (카톡 배경 어디서나 읽히게)
        val stroke = Paint(paint).apply {
            style = Paint.Style.STROKE
            strokeWidth = size * 0.18f
            color = Color.WHITE
        }
        val fill = Paint(paint).apply { color = Color.parseColor("#111111") }
        val baseline = centerY + size * 0.35f
        canvas.drawText(text, width / 2f, baseline, stroke)
        canvas.drawText(text, width / 2f, baseline, fill)
    }

    /** 알파만 남긴 흰색 실루엣. */
    private fun Bitmap.silhouette(): Bitmap {
        val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(
            this,
            null,
            Rect(0, 0, width, height),
            Paint().apply { colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN) },
        )
        return result
    }

    private const val SHADOW_OFFSET_PX = 3
    private fun captionPadding() = 8
}
