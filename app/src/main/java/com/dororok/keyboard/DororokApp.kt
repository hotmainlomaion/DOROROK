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

    val cutoutEngine: SubjectCutoutEngine by lazy { SubjectCutoutEngine() }
    val stickerStore: StickerStore by lazy { StickerStore(filesDir.resolve("stickers")) }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: DororokApp
            private set
    }
}
