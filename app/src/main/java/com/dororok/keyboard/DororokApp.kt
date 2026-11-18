package com.dororok.keyboard

import android.app.Application
import com.dororok.keyboard.cutout.SubjectCutoutEngine
import com.dororok.keyboard.sticker.StickerStore

/**
 * 앱 전역 싱글턴.
 *
 * 키보드(IME) 프로세스와 스튜디오(Activity)는 같은 애플리케이션 프로세스에서 동작하므로
 * 누끼 엔진(ML Kit 클라이언트)과 스티커 저장소를 한 번만 만들어 공유한다.
 */
class DororokApp : Application() {

    private var engine: SubjectCutoutEngine? = null

    /** 필요할 때 생성. IME 와 스튜디오가 같은 모델 인스턴스를 재사용한다. */
    val cutoutEngine: SubjectCutoutEngine
        get() = engine ?: SubjectCutoutEngine().also { engine = it }

    val stickerStore: StickerStore by lazy { StickerStore(filesDir.resolve("stickers")) }

    /** 모델 메모리 해제 — 프로세스가 계속 살아있으므로 명시적으로 호출할 때만 닫는다. */
    fun closeCutoutEngine() {
        engine?.close()
        engine = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: DororokApp
            private set
    }
}
