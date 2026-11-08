package com.example.vendorchat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var showModel by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val msgs = vm.messages
    val lastLen = (msgs.lastOrNull() as? Msg.Text)?.text?.length ?: 0

    LaunchedEffect(msgs.size, lastLen) {
        if (msgs.isNotEmpty()) listState.scrollToItem(msgs.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Header(vm, onModel = { showModel = true }, onClear = { vm.clearChat() })
        Banner(vm, onModel = { showModel = true })

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
                        onAsk = { input = "اطلاعات " }
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
                    is Msg.Chips -> NameChips(m.title, m.names) { vm.send("اطلاعات «$it»") }
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
            onSend = {
                vm.send(input)
                input = ""
            }
        )
    }

    if (showModel) ModelDialog(vm) { showModel = false }
}

@Composable
private fun Header(vm: ChatViewModel, onModel: () -> Unit, onClear: () -> Unit) {
    val (statusText, dot) = when (vm.modelState) {
        ModelState.READY -> "آماده برای گفتگو" to Pal.Ok
        ModelState.LOADING -> "در حال بارگذاری مدل..." to Pal.Amber
        ModelState.ERROR -> "خطا در مدل" to Pal.Danger
        ModelState.NONE -> "مدل اضافه نشده" to Color(0xFF9CA3AF)
    }
    Row(
        Modifier.fillMaxWidth()
            .background(Pal.Brand, RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .statusBarsPadding()
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(Color(0x33FFFFFF)),
            contentAlignment = Alignment.Center
        ) { Text("🤖", fontSize = 24.sp) }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "دستیار وندور", color = Color.White,
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
                Spacer(Modifier.size(6.dp))
                Text(statusText, color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = onModel) { Text("⚙️", fontSize = 20.sp) }
        IconButton(onClick = onClear, enabled = !vm.busy && vm.messages.isNotEmpty()) {
            Text("🗑️", fontSize = 20.sp)
        }
    }
}

@Composable
private fun Banner(vm: ChatViewModel, onModel: () -> Unit) {
    val (text, color) = when (vm.modelState) {
        ModelState.NONE -> "مدل زبانی اضافه نشده؛ بدون آن ثبت و گفتگوی هوشمند ممکن نیست. برای افزودن بزن." to Pal.Amber
        ModelState.ERROR -> (vm.error ?: "خطا در مدل") + " (برای تنظیمات بزن)" to Pal.Danger
        ModelState.LOADING -> return
        ModelState.READY -> return
    }
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.14f))
            .clickable { onModel() }
            .padding(12.dp)
    ) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun UserBubble(text: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = 6.dp))
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
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            Modifier.widthIn(max = 300.dp)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp))
                .background(MaterialTheme.colorScheme.surface)
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
        Avatar("V", 30.dp, brush = Pal.TealGrad, fontSize = 14.sp)
    }
}

@Composable
private fun Welcome(onList: () -> Unit, onAdd: () -> Unit, onAsk: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            Modifier.size(84.dp).clip(CircleShape).background(Pal.BrandDiag),
            contentAlignment = Alignment.Center
        ) { Text("🤖", fontSize = 40.sp) }
        Text(
            "سلام! من دستیار وندورم 👋",
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold
        )
        Text(
            "هر کاری با وندورها داری با چت بگو؛ ثبت، ویرایش، حذف و جستجو.",
            style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        SuggestionRow("📋", "لیست وندورها", "همهٔ وندورهای ثبت‌شده", onList)
        SuggestionRow("➕", "افزودن وندور جدید", "مثلاً: وندور فولاد اصفهان رو اضافه کن، تلفن ...", onAdd)
        SuggestionRow("🔎", "دیدن اطلاعات یک وندور", "مثلاً: اطلاعات فولاد اصفهان", onAsk)
    }
}

@Composable
private fun SuggestionRow(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Text(emoji, fontSize = 20.sp) }
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
private fun InputBar(value: String, onChange: (String) -> Unit, busy: Boolean, onSend: () -> Unit) {
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
            shape = RoundedCornerShape(24.dp),
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
        Box(
            Modifier.size(50.dp).clip(CircleShape)
                .background(if (canSend) Pal.BrandDiag else Brush.linearGradient(listOf(Color(0xFFB8BBD0), Color(0xFFB8BBD0))))
                .clickable(enabled = canSend) { onSend() },
            contentAlignment = Alignment.Center
        ) { Text("↑", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun ModelDialog(vm: ChatViewModel, onClose: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }
    val progress = vm.importProgress
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("مدل زبانی (Gemma 4 E2B)") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    when (vm.modelState) {
                        ModelState.NONE -> "وضعیت: مدل اضافه نشده"
                        ModelState.LOADING -> "وضعیت: در حال بارگذاری..."
                        ModelState.READY -> "وضعیت: آماده"
                        ModelState.ERROR -> "وضعیت: خطا"
                    },
                    fontWeight = FontWeight.Bold
                )
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("در حال کپی فایل مدل: ${(progress * 100).toInt()}٪ (چند دقیقه طول می‌کشد)")
                }
                vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { launcher.launch(arrayOf("*/*")) }, enabled = progress == null) {
                    Text("انتخاب فایل مدل (.litertlm)")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("استفاده از GPU (سریع‌تر؛ اگر ناپایدار بود خاموش کن)", modifier = Modifier.weight(1f))
                    Switch(checked = vm.useGpu, onCheckedChange = { vm.setGpu(it) })
                }
                if (vm.modelState != ModelState.NONE) {
                    OutlinedButton(onClick = { vm.deleteModel() }, enabled = progress == null) {
                        Text("حذف مدل از حافظهٔ اپ")
                    }
                }
                Text(
                    "فایل gemma-4-E2B-it.litertlm (حدود ۲٫۶ گیگابایت) را از huggingface.co/litert-community/gemma-4-E2B-it-litert-lm " +
                        "دانلود کن و اینجا انتخاب کن. حداقل ۶ گیگابایت فضای خالی لازم است.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("بستن") } }
    )
}
