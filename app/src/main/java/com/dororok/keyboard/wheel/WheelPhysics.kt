package com.dororok.keyboard.wheel

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * 가로 드럼 휠의 물리 모델.
 *
 * Compose 의 `exponentialDecay` 와 같은 감속 곡선을 쓰되, 아이템 단위로
 * 스냅 지점을 미리 계산해 두어 "손을 떼는 순간 어느 칸에 멈출지"가
 * 프레임 단위 애니메이션 도중에 흔들리지 않게 한다.
 *
 * 단위는 모두 px. dp 변환은 호출부([StickerWheel])가 density 로 처리한다.
 */
object WheelPhysics {

    /** 아이템 1칸 = 64dp (스티커 미리보기 + 여백) */
    const val UNIT_WIDTH_DP = 64f

    /** 스냅 판정: 중심에서 이 비율 이내면 다음 칸으로 넘긴다 */
    private const val SNAP_THRESHOLD = 0.5f

    /** 감속 계수 — 클수록 빨리 멈춘다. 틱 감각이 살아있는 3.0~4.5 구간. */
    private const val DECAY_FRICTION = 4.0f

    /** 손을 뗀 시점 속도(px/s)로 최종 정지 오프셋을 예측한다 (관성 회전) */
    fun projectedOffset(currentOffsetPx: Float, velocityPxPerSec: Float, unitPx: Float): Int {
        val projectedPx = currentOffsetPx + velocityPxPerSec / DECAY_FRICTION
        return (projectedPx / unitPx).roundToInt()
    }

    /** 가장 가까운 칸. */
    fun nearestIndex(offsetPx: Float, unitPx: Float): Int = (offsetPx / unitPx).roundToInt()

    /** 현재 오프셋이 스냅 임계를 넘었는지 (틱 발생 조건) */
    fun crossedHalfway(offsetPx: Float, unitPx: Float): Boolean {
        val fraction = abs(offsetPx / unitPx) % 1f
        return fraction > SNAP_THRESHOLD
    }

    /**
     * 감속 커브. `v(t) = v0 * e^(-f*t)` 를 프레임 단위로 적분한 근사.
     * Compose 애니메이션에 그대로 넘길 수 있게 "남은 거리 비율"로 돌려준다.
     */
    fun remainingFraction(elapsedMs: Long, velocityPxPerSec: Float, unitPx: Float): Float {
        if (velocityPxPerSec == 0f) return 0f
        val total = abs(velocityPxPerSec) / DECAY_FRICTION
        val decayed = total * kotlin.math.exp((-DECAY_FRICTION * elapsedMs / 1000f))
        return if (total == 0f) 0f else (decayed / total).coerceIn(0f, 1f)
    }

    /**
     * 틱 강도(0~1) — 중심에 가까울수록 강하게, 빠른 회전 중에는 약하게.
     * 햅틱 진폭과 (프리미엄) 기계식 사운드 볼륨에 같이 쓴다.
     */
    fun tickIntensity(velocityPxPerSec: Float): Float {
        val speed = abs(velocityPxPerSec)
        val normalized = (ln(1f + speed / 200f) / ln(1f + 20f)).coerceIn(0f, 1f)
        return (1f - 0.45f * normalized).coerceIn(0.55f, 1f)
    }
}
