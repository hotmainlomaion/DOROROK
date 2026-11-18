package com.dororok.keyboard

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dororok.keyboard.cutout.CutoutException
import com.dororok.keyboard.cutout.StickerComposer
import com.dororok.keyboard.sticker.Sticker
import com.dororok.keyboard.ui.theme.DororokTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 스티커 스튜디오 — 사진 고르기 → 누끼 → 스티커 저장.
 *
 * 저장 시점에 흰 테두리·섀도우·문구를 **미리 합성**한다.
 * 카카오톡이 투명 PNG 를 사진으로 보내기 때문에, 전송 순간이 아니라
 * 여기서 모양이 완성되어 있어야 1탭 전송이 즉시 끝난다.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as DororokApp
        setContent {
            DororokTheme {
                StudioScreen(app = app)
            }
        }
    }
}

@Composable
private fun StudioScreen(app: DororokApp) {
    var subject by remember { mutableStateOf<Bitmap?>(null) }
    var sourceLabel by remember { mutableStateOf<String?>(null) }
    var caption by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val saved = remember { mutableStateListOf<Sticker>().apply { addAll(app.stickerStore.all()) } }
    val scope = rememberCoroutineScope()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        error = null
        scope.launch {
            try {
                val cut = withContext(Dispatchers.Default) {
                    val bitmap = app.contentResolver.openInputStream(uri)?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    } ?: throw CutoutException("사진을 읽지 못했어요.")
                    app.cutoutEngine.cutout(bitmap)
                }
                subject = cut
                sourceLabel = uri.lastPathSegment
            } catch (e: CutoutException) {
                error = e.message
            } catch (e: Exception) {
                error = "누끼 처리 중 문제가 생겼어요 (${e.javaClass.simpleName})"
            } finally {
                busy = false
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Text("스티커 스튜디오", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "사진 한 장이면 끝. 누끼는 기기 안에서만 처리돼요.",
                fontSize = 12.sp,
                color = Color(0xFF7A7A72),
                modifier = Modifier.padding(top = 4.dp),
            )

            Spacer(Modifier.height(18.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(Color(0xFFEDEDE7), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    busy -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Text("누끼 따는 중…", fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                    subject != null -> Image(
                        bitmap = subject!!.asImageBitmap(),
                        contentDescription = "누끼 결과",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                    )
                    else -> Text(
                        "사진 고르기를 눌러 시작",
                        fontSize = 13.sp,
                        color = Color(0xFF9A9A93),
                    )
                }
            }

            if (error != null) {
                Text(
                    error.orEmpty(),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = caption,
                onValueChange = { caption = it.take(12) },
                label = { Text("한 줄 문구 (선택)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEDEDE7),
                        contentColor = Color(0xFF1A1A18),
                    ),
                    modifier = Modifier.weight(1f),
                ) { Text("사진 고르기") }

                Button(
                    onClick = {
                        val cut = subject ?: return@Button
                        busy = true
                        scope.launch {
                            try {
                                val sticker = withContext(Dispatchers.Default) {
                                    val composed = StickerComposer.compose(
                                        subject = cut,
                                        outlinePx = (cut.width * 0.035f).toInt().coerceAtLeast(6),
                                        caption = caption.ifBlank { null },
                                    )
                                    val id = UUID.randomUUID().toString().take(8)
                                    val dir = File(app.filesDir, "stickers").apply { mkdirs() }
                                    val file = File(dir, "$id.png")
                                    file.outputStream().use { out ->
                                        composed.compress(Bitmap.CompressFormat.PNG, 100, out)
                                    }
                                    Sticker(id = id, filePath = file.absolutePath, caption = caption.ifBlank { null })
                                }
                                app.stickerStore.add(sticker)
                                saved.add(0, sticker)
                                subject = null
                                caption = ""
                            } catch (e: Exception) {
                                error = "저장 실패: ${e.message}"
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = subject != null && !busy,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.weight(1f),
                ) { Text("스티커로 저장") }
            }

            Spacer(Modifier.height(22.dp))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("내 스티커 ${saved.size}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "무료 슬롯 ${app.stickerStore.capacity}칸",
                    fontSize = 12.sp,
                    color = Color(0xFF9A9A93),
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                saved.take(6).forEach { sticker ->
                    val bmp = remember(sticker.filePath) {
                        android.graphics.BitmapFactory.decodeFile(sticker.filePath)?.asImageBitmap()
                    }
                    if (bmp != null) {
                        Image(
                            bitmap = bmp,
                            contentDescription = sticker.caption ?: "저장된 스티커",
                            modifier = Modifier
                                .size(52.dp)
                                .background(Color(0xFFEDEDE7), RoundedCornerShape(12.dp)),
                        )
                    }
                }
            }
        }
    }
}
