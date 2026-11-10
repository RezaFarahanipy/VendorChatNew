package com.example.vendorchat.ui

import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vendorchat.ChatViewModel
import com.example.vendorchat.ModelState
import com.example.vendorchat.chat.Msg

@Composable
fun ChatScreen(vm: ChatViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val msgs = vm.messages
    val ctx = LocalContext.current
    val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val t = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!t.isNullOrBlank()) input = if (input.isBlank()) t else "$input $t"
    }
    val startVoice = {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "بگو چی می‌خوای...")
        }
        try {
            speech.launch(i)
        } catch (e: Exception) {
            Toast.makeText(ctx, "تشخیص گفتار روی این گوشی در دسترس نیست", Toast.LENGTH_SHORT).show()
        }
    }
    val lastLen = (msgs.lastOrNull() as? Msg.Text)?.text?.length ?: 0

    LaunchedEffect(msgs.size, lastLen) {
        if (msgs.isNotEmpty()) listState.scrollToItem(msgs.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Header(vm, onSettings = { showSettings = true }, onClear = { vm.clearChat() })
        Banner(vm, onModel = { showSettings = true })

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (msgs.isEmpty()) {
                item {
                    Welcome(
                        onList = { vm.send("لیست وندورها") },
                        onAdd = { input = "وندور ... رو اضافه کن، تلفن ... شهر ..." },
                        onAsk = { input = "اطلاعات " },
                        onTools = { showSettings = true }
                    )
                }
            }
            items(msgs.size) { idx ->
                when (val m = msgs[idx]) {
                    is Msg.Text -> if (m.fromUser) UserBubble(m.text) else BotBubble(m.text)
                    is Msg.Cards -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        m.vendors.forEach { v ->
                            VendorCard(
                                v,
                                onEdit = { input = "برای «${v.name}» " },
                                onDelete = { vm.send("وندور «${v.name}» رو حذف کن") }
                            )
                        }
                    }
                    is Msg.Chips -> NameChips(m.title, m.names) { vm.onChip(it) }
                    is Msg.Action -> ActionCard(
                        m, enabled = !vm.busy,
                        onConfirm = { vm.confirm() }, onCancel = { vm.cancel() }
                    )
                }
            }
        }

        InputBar(
            value = input,
            onChange = { input = it },
            busy = vm.busy,
            onMic = { startVoice() },
            onSend = {
                vm.send(input)
                input = ""
            }
        )
    }

    if (showSettings) SettingsSheet(vm) { showSettings = false }
}

@Composable
private fun Header(vm: ChatViewModel, onSettings: () -> Unit, onClear: () -> Unit) {
    val (statusText, dot) = when (vm.modelState) {
        ModelState.READY -> "آماده برای گفتگو" to Color(0xFF4ADE80)
        ModelState.LOADING -> "در حال بارگذاری مدل..." to Pal.Amber
        ModelState.ERROR -> "خطا در مدل" to Color(0xFFFF8A8F)
        ModelState.NONE -> "مدل اضافه نشده" to Color(0xFFCBD5E1)
    }
    Row(
        Modifier.fillMaxWidth()
            .background(Pal.Brand, RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp))
            .statusBarsPadding()
            .padding(start = 16.dp, end = 6.dp, top = 12.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape).background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(Ic.SmartToy, Color.White, 28.dp) }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "دستیار وندور", color = Color.White,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.size(6.dp))
                Text(statusText, color = Color(0xDDFFFFFF), style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = onClear, enabled = !vm.busy && vm.messages.isNotEmpty()) {
            Icon(
                Ic.Delete, contentDescription = "پاک کردن گفتگو",
                tint = Color.White.copy(alpha = if (!vm.busy && vm.messages.isNotEmpty()) 1f else 0.4f)
            )
        }
        IconButton(onClick = onSettings) {
            Icon(Ic.Settings, contentDescription = "تنظیمات", tint = Color.White)
        }
    }
}

@Composable
private fun Banner(vm: ChatViewModel, onModel: () -> Unit) {
    val (text, color) = when (vm.modelState) {
        ModelState.NONE -> "مدل زبانی اضافه نشده؛ بدون آن ثبت و گفتگوی هوشمند ممکن نیست. برای افزودن بزن." to Pal.Amber
        ModelState.ERROR -> ((vm.error ?: "خطا در مدل") + " (برای تنظیمات بزن)") to Pal.Danger
        ModelState.LOADING -> return
        ModelState.READY -> return
    }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.14f))
            .clickable { onModel() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        GlyphIcon(Ic.Warning, color, 22.dp)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomEnd = 22.dp, bottomStart = 6.dp))
                .background(Pal.BrandDiag)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            SelectionContainer {
                Text(text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun BotBubble(text: String) {
    val shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 22.dp, bottomEnd = 6.dp)
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            Modifier.widthIn(max = 300.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.7f), shape)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            if (text.isEmpty()) {
                TypingDots()
            } else {
                SelectionContainer {
                    Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Spacer(Modifier.size(8.dp))
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(Pal.Mint),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(Ic.AutoAwesome, Color.White, 18.dp) }
    }
}

@Composable
private fun Welcome(onList: () -> Unit, onAdd: () -> Unit, onAsk: () -> Unit, onTools: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(92.dp).clip(CircleShape).background(Pal.Brand),
            contentAlignment = Alignment.Center
        ) { GlyphIcon(Ic.SmartToy, Color.White, 52.dp) }
        Text(
            "سلام! من دستیار وندورم",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
        )
        Text(
            "هر کاری با وندورها داری با چت بگو؛ ثبت، ویرایش، حذف و جستجو.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        SuggestionRow(Ic.FormatListBulleted, Color(0xFF3B5BDB), "لیست وندورها", "همهٔ وندورهای ثبت‌شده", onList)
        SuggestionRow(Ic.AddCircle, Color(0xFF16A34A), "افزودن وندور جدید", "مثلاً: وندور فولاد اصفهان رو اضافه کن، تلفن ...", onAdd)
        SuggestionRow(Ic.PersonSearch, Color(0xFFEA580C), "دیدن اطلاعات یک وندور", "مثلاً: اطلاعات فولاد اصفهان", onAsk)
        SuggestionRow(Ic.UploadFile, Color(0xFF7C3AED), "ورود از اکسل و پشتیبان‌گیری", "داده‌ها را وارد کن یا نسخهٔ پشتیبان بگیر", onTools)
    }
}

@Composable
private fun SuggestionRow(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconChip(icon, tint, box = 44.dp, glyph = 24.dp)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun InputBar(value: String, onChange: (String) -> Unit, busy: Boolean, onMic: () -> Unit, onSend: () -> Unit) {
    val canSend = !busy && value.isNotBlank()
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextField(
            value = value,
            onValueChange = onChange,
            enabled = !busy,
            placeholder = { Text(if (busy) "در حال پردازش..." else "پیام بنویس...") },
            maxLines = 4,
            shape = RoundedCornerShape(26.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onMic, enabled = !busy) {
            Icon(Ic.Mic, contentDescription = "ورودی صوتی", tint = MaterialTheme.colorScheme.primary)
        }
        Box(
            Modifier.size(52.dp).clip(CircleShape)
                .background(
                    if (canSend) Pal.BrandDiag
                    else Brush.linearGradient(listOf(Color(0xFFB4BBD0), Color(0xFFB4BBD0)))
                )
                .clickable(enabled = canSend) { onSend() },
            contentAlignment = Alignment.Center
        ) { GlyphIcon(Ic.ArrowUpward, Color.White, 26.dp) }
    }
}
