package com.dororok.keyboard.wheel

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * 틱 햅틱.
 *
 * 5~10ms 의 아주 짧은 진동을 아이템이 중앙을 지날 때마다 울린다.
 * amplitude 를 낮게(≈70/255) 잡아 "딸깍" 하는 다이얼 감각을 만들고,
 * 회전 속도가 빠를수록 진폭을 조금 줄여 손이 저리지 않게 한다.
 *
 * API 31+ 는 `VibratorManager`, 그 아래는 `Vibrator` 를 쓴다.
 */
class TickHaptics(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val hasAmplitudeControl: Boolean =
        vibrator?.hasAmplitudeControl() == true

    var enabled: Boolean = true

    /** 한 칸 통과 시 1회. */
    fun tick(intensity: Float = 1f) {
        if (!enabled) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return

        val durationMs = TICK_MS
        val amplitude = (BASE_AMPLITUDE * intensity).toInt().coerceIn(AMB_MIN, AMB_MAX)

        val effect = if (hasAmplitudeControl) {
            VibrationEffect.createOneShot(durationMs, amplitude)
        } else {
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        v.vibrate(effect)
    }

    /** 스냅 완료 — 살짝 더 또렷한 한 번. */
    fun snap() = tick(intensity = 1.35f)

    companion object {
        private const val TICK_MS = 7L
        private const val BASE_AMPLITUDE = 70
        private const val AMB_MIN = 20
        private const val AMB_MAX = 160
    }
}
