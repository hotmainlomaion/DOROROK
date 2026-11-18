package com.dororok.keyboard.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dororok.keyboard.sticker.Sticker
import com.dororok.keyboard.wheel.StickerWheel
import com.dororok.keyboard.wheel.TickHaptics

/**
 * 키보드 전체 뷰 — 상단 스티커 휠 + 하단 자판.
 *
 * 두 탭으로 나뉜다.
 * - 휠: 갤러리 스티커를 돌려서 1탭 전송 (이 앱의 간판)
 * - 자판: 일반 타이핑. MVP 에서는 AOSP 한글 IME 포크가 그리는 영역을
 *   이 컴포저블의 [ScaffoldKeyboardRows] 자리에 끼운다. 지금은 영문 문자만
 *   커밋하는 플레이스홀더라, 키보드를 켜고 기본 동작을 먼저 확인할 수 있다.
 */
@Composable
fun DororokKeyboard(
    stickers: List<Sticker>,
    haptics: TickHaptics?,
    canSendImages: Boolean,
    onText: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
    onOpenStudio: () -> Unit,
    onStickerSelected: (Sticker?) -> Unit,
    onSendSticker: (Sticker) -> Unit,
) {
    var tab by remember { mutableIntStateOf(TAB_WHEEL) }
    var notice by remember { mutableStateOf<String?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row {
                    TabChip("스티커", selected = tab == TAB_WHEEL) { tab = TAB_WHEEL }
                    Spacer(Modifier.width(6.dp))
                    TabChip("자판", selected = tab == TAB_KEYS) { tab = TAB_KEYS }
                }
                Text(
                    text = "도로록",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickable { onOpenStudio() }
                        .padding(end = 6.dp),
                )
            }

            if (tab == TAB_WHEEL) {
                if (stickers.isEmpty()) {
                    EmptyStickerHint(onOpenStudio = onOpenStudio)
                } else {
                    StickerWheel(
                        stickers = stickers,
                        haptics = haptics,
                        modifier = Modifier.fillMaxWidth(),
                        onCenterChange = onStickerSelected,
                        onSend = { sticker ->
                            if (canSendImages) onSendSticker(sticker) else notice = "이 앱은 이미지 전송을 지원하지 않아요"
                        },
                    )
                    if (notice != null) {
                        Text(
                            text = notice.orEmpty(),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 6.dp, top = 2.dp),
                        )
                    }
                }
            } else {
                ScaffoldKeyboardRows(onText = onText, onBackspace = onBackspace, onEnter = onEnter)
            }
        }
    }
}

@Composable
private fun TabChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary else Color(0xFFEDEDE8)
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else Color(0xFF6B6B66)
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}

@Composable
private fun EmptyStickerHint(onOpenStudio: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(72.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("아직 스티커가 없어요", fontSize = 13.sp, color = Color(0xFF6B6B66))
        Text(
            "눌러서 사진 한 장을 스티커로 만들어 보세요",
            fontSize = 11.sp,
            color = Color(0xFF9A9A93),
            modifier = Modifier
                .clickable { onOpenStudio() }
                .padding(top = 4.dp),
        )
    }
}

/**
 * 자판 플레이스홀더 — AOSP 한글 IME 포크가 들어올 자리.
 * 지금은 영문 문자·백스페이스·엔터만 커밋한다.
 */
@Composable
private fun ScaffoldKeyboardRows(
    onText: (String) -> Unit,
    onBackspace: () -> Unit,
    onEnter: () -> Unit,
) {
    val rows = listOf(
        "qwertyuiop",
        "asdfghjkl",
        "zxcvbnm",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        rows.forEach { row ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                row.forEach { char ->
                    KeyCap(
                        label = char.uppercase(),
                        modifier = Modifier.weight(1f),
                        onClick = { onText(char.toString()) },
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            KeyCap("⌫", Modifier.weight(1.4f), onBackspace)
            KeyCap("space", Modifier.weight(4f), { onText(" ") })
            KeyCap("⏎", Modifier.weight(1.4f), onEnter)
        }
    }
}

@Composable
private fun KeyCap(
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(42.dp)
            .background(Color(0xFFF2F2EE), RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 15.sp, color = Color(0xFF1A1A18))
    }
}

private const val TAB_WHEEL = 0
private const val TAB_KEYS = 1
