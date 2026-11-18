package com.dororok.keyboard.sticker

/**
 * 휠에 실리는 스티커 한 장.
 *
 * [filePath] 는 이미 흰 테두리 + 드롭 섀도우가 합성된 투명 PNG 다.
 * 카카오톡·인스타 DM 은 투명 PNG 를 "사진 파일"로 보내기 때문에,
 * 합성은 전송이 아니라 저장 시점에 끝나 있어야 한다 (docs/ARCHITECTURE.md §4).
 */
data class Sticker(
    val id: String,
    val filePath: String,
    val caption: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
