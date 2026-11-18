package com.dororok.keyboard.wheel

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.dororok.keyboard.sticker.Sticker
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.abs

/**
 * 가로 롤링 드럼 휠.
 *
 * - LazyRow + `rememberSnapFlingBehavior`: 손을 떼면 가장 가까운 칸에 1.25x 로 자동 정렬.
 * - 각 아이템은 중심에서의 거리(칸 단위)에 따라 `rotationY`(원통 착시)와 scale 을 받는다.
 * - 중앙 칸이 바뀔 때마다 [haptics] 로 틱 1회 → [onCenterChange].
 * - 중앙 스티커를 탭하면 [onSend] (1탭 전송).
 *
 * 최적화 TODO(MVP 이후): 지금은 아이템마다 매 프레임 `layoutInfo` 를 훑는다.
 * 슬롯 30칸 이하에서는 체감 문제가 없지만, 슬롯 무제한 해금 후에는 중심 오프셋을
 * 단일 상태로 끌어올려 아이템당 계산을 없애야 한다.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StickerWheel(
    stickers: List<Sticker>,
    haptics: TickHaptics?,
    modifier: Modifier = Modifier,
    itemSize: Int = 56,
    initialIndex: Int = 0,
    onCenterChange: (Sticker?) -> Unit,
    onSend: (Sticker) -> Unit,
) {
    val density = LocalDensity.current
    val unitPx = with(density) { itemSize.dp.toPx() }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)
    val fling = rememberSnapFlingBehavior(lazyListState = state)

    val centerIndex by remember(stickers.size) {
        derivedStateOf {
            val info = state.layoutInfo
            val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
            info.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2f) - viewportCenter) }
                ?.takeIf { abs((it.offset + it.size / 2f) - viewportCenter) < unitPx / 2f }
                ?.index
        }
    }

    LaunchedEffect(stickers) {
        snapshotFlow { centerIndex }
            .distinctUntilChanged()
            .filterNotNull()
            .collect { index ->
                haptics?.tick(WheelPhysics.tickIntensity(0f))
                onCenterChange(stickers.getOrNull(index))
            }
    }

    BoxWithConstraints(modifier = modifier.height((itemSize + 20).dp)) {
        val horizontalPadding = ((maxWidth - itemSize.dp) / 2).coerceAtLeast(0.dp)

        // 드럼 분위기: 양 끝이 그라데이션으로 사라진다
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color(0x1A000000),
                        0.22f to Color.Transparent,
                        0.5f to Color(0x08000000),
                        0.78f to Color.Transparent,
                        1f to Color(0x1A000000),
                    ),
                ),
        )

        LazyRow(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(stickers.size) { index ->
                val sticker = stickers[index]
                val info = state.layoutInfo
                val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                val cell = info.visibleItemsInfo.firstOrNull { it.index == index }
                val distance = cell
                    ?.let { ((it.offset + it.size / 2f) - viewportCenter) / unitPx }
                    ?.coerceIn(-2.5f, 2.5f)
                    ?: 0f

                StickerCell(
                    sticker = sticker,
                    modifier = Modifier
                        .size(itemSize.dp)
                        .graphicsLayer {
                            rotationY = distance * 22f
                            cameraDistance = 12f * density.density
                            val scale = (1.25f - 0.2f * abs(distance)).coerceIn(0.8f, 1.25f)
                            scaleX = scale
                            scaleY = scale
                            alpha = (1f - 0.3f * abs(distance)).coerceIn(0.25f, 1f)
                        },
                    onClick = { onSend(sticker) },
                )
            }
        }
    }
}

@Composable
private fun StickerCell(
    sticker: Sticker,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bitmap = remember(sticker.filePath) {
        android.graphics.BitmapFactory.decodeFile(sticker.filePath)?.asImageBitmap()
    }
    Box(
        modifier = modifier.clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = sticker.caption ?: "스티커",
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
