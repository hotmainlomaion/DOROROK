package com.dororok.keyboard.sticker

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 스티커 파일 + 메타데이터 인덱스 저장소 (로컬 전용).
 *
 * - 파일: `filesDir/stickers/{id}.png`
 * - 인덱스: `filesDir/stickers/index.json`
 * - 서버·클라우드 동기화 없음. 백업 대상에서도 제외된다(res/xml/backup_rules.xml).
 *
 * 슬롯 정책(무료 10칸 → 보상형 광고로 확장)은 [capacity] 로만 노출하고,
 * 실제 해금 로직은 4주차 결제/광고 모듈에서 채운다.
 */
class StickerStore(private val dir: File) {

    private val indexFile: File get() = dir.resolve("index.json")

    fun ensureDir() {
        if (!dir.exists()) dir.mkdirs()
    }

    fun all(): List<Sticker> {
        ensureDir()
        if (!indexFile.exists()) return emptyList()
        val array = JSONArray(indexFile.readText())
        return (0 until array.length()).map { i ->
            val json = array.getJSONObject(i)
            Sticker(
                id = json.getString("id"),
                filePath = json.getString("filePath"),
                caption = json.optString("caption").ifBlank { null },
                createdAt = json.optLong("createdAt"),
            )
        }.sortedByDescending { it.createdAt }
    }

    fun add(sticker: Sticker) {
        ensureDir()
        val list = all().toMutableList()
        list.add(0, sticker)
        write(list)
    }

    fun remove(id: String) {
        val list = all().filterNot { it.id == id }
        write(list)
        dir.resolve("$id.png").delete()
    }

    /** 무료 슬롯. 보상형 광고 시청 시 늘어난 값을 SharedPreferences 에서 읽어 더한다. */
    var capacity: Int = DEFAULT_FREE_SLOTS

    fun isFull(): Boolean = all().size >= capacity

    private fun write(list: List<Sticker>) {
        val array = JSONArray()
        list.forEach { s ->
            array.put(
                JSONObject()
                    .put("id", s.id)
                    .put("filePath", s.filePath)
                    .put("caption", s.caption ?: "")
                    .put("createdAt", s.createdAt),
            )
        }
        indexFile.writeText(array.toString())
    }

    companion object {
        const val DEFAULT_FREE_SLOTS = 10
        const val SLOTS_PER_REWARDED_AD = 3
    }
}
