package com.dororok.keyboard.cutout

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentationResult
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 온디바이스 AI 누끼 — ML Kit Subject Segmentation (`play-services-mlkit-subject-segmentation` 16.0.0-beta1).
 *
 * - 기기 안에서만(오프라인) 동작한다. 서버 왕복 0회, 이미지 업로드 0% (INTERNET 권한 미선언).
 * - 첫 호출은 모델 초기화 때문에 느리고, 이후 워밍업 상태에서 목표 0.5초.
 * - 결과 `SubjectSegmentationResult.foregroundBitmap` 이 배경이 투명해진 ARGB 비트맵이다.
 *   `foregroundConfidenceMask` 는 픽셀별 신뢰도(FloatBuffer)로, 지금은 쓰지 않고
 *   "누끼가 애매하면 다시 고르게" 하는 품질 게이트 용도로 남겨 둔다.
 *
 * 베타 API 라 마이너 버전에서 시그니처가 바뀔 수 있다. 영향 범위는 이 파일 하나다.
 */
class SubjectCutoutEngine {

    private val client: SubjectSegmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableForegroundBitmap()          // 배경 투명 피사체 비트맵
                .enableForegroundConfidenceMask()  // 픽셀별 신뢰도(품질 게이트용)
                .build(),
        )
    }

    /**
     * @param source 원본 사진 (내부에서 최대 변 길이 [MAX_EDGE] 로 축소해 처리)
     * @return 배경이 투명한 피사체 비트맵
     * @throws CutoutException 누끼 실패·시간 초과
     */
    suspend fun cutout(source: Bitmap): Bitmap {
        val prepared = source.scaleDown(MAX_EDGE)
        val input = InputImage.fromBitmap(prepared, 0)

        return try {
            val result = withTimeout(TIMEOUT_MS) { client.process(input).awaitResult() }
            result.foregroundBitmap
                ?: throw CutoutException("피사체를 찾지 못했어요. 인물이나 사물이 크게 나온 사진으로 다시 시도해 주세요.")
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            throw CutoutException("누끼 처리가 너무 오래 걸려요.", e)
        }
    }

    fun close() = client.close()

    private fun Bitmap.scaleDown(maxEdge: Int): Bitmap {
        val longest = maxOf(width, height)
        if (longest <= maxEdge) return this
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
    }

    companion object {
        const val MAX_EDGE = 1024
        const val TIMEOUT_MS = 5_000L
    }
}

private suspend fun com.google.android.gms.tasks.Task<SubjectSegmentationResult>.awaitResult(): SubjectSegmentationResult =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { if (cont.isActive) cont.resume(it) }
        addOnFailureListener { if (cont.isActive) cont.resumeWithException(it) }
        addOnCanceledListener { cont.cancel() }
    }

class CutoutException(message: String, cause: Throwable? = null) : Exception(message, cause)
