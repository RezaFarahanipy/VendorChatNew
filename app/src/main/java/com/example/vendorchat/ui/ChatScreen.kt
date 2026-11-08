package com.example.vendorchat.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.vendorchat.ChatMsg
import com.example.vendorchat.ChatViewModel
import com.example.vendorchat.ModelState

@Composable
fun ChatScreen(vm: ChatViewModel) {
    var input by rememberSaveable { mutableStateOf("") }
    var showModel by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val msgs = vm.messages
    val lastLen = msgs.lastOrNull()?.text?.length ?: 0

    LaunchedEffect(msgs.size, lastLen) {
        if (msgs.isNotEmpty()) listState.scrollToItem(msgs.lastIndex)
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "دستیار وندور",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(8.dp).weight(1f)
            )
            TextButton(onClick = { showModel = true }) { Text("مدل") }
            if (msgs.isNotEmpty()) {
                TextButton(onClick = { vm.clearChat() }, enabled = !vm.busy) { Text("پاک کردن") }
            }
        }

        when (vm.modelState) {
            ModelState.NONE -> Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("مدل زبانی هنوز اضافه نشده؛ بدون آن فقط جستجو کار می‌کند و ثبت/ویرایش/حذف ممکن نیست.")
                    TextButton(onClick = { showModel = true }) { Text("افزودن مدل") }
                }
            }
            ModelState.LOADING -> Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CircularProgressIndicator(Modifier.size(22.dp))
                Text("در حال بارگذاری مدل (ممکن است چند ده ثانیه طول بکشد)...")
            }
            ModelState.ERROR -> Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text(vm.error ?: "خطا در مدل", color = MaterialTheme.colorScheme.error)
                    Row {
                        TextButton(onClick = { vm.loadModel() }) { Text("تلاش دوباره") }
                        TextButton(onClick = { showModel = true }) { Text("تنظیمات مدل") }
                    }
                }
            }
            ModelState.READY -> {}
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (msgs.isEmpty()) {
                item {
                    Text(
                        "همه‌چیز با چت انجام می‌شود. مثلاً بگو:\n" +
                            "• وندور فولاد اصفهان رو اضافه کن، تلفن ۰۳۱۱۲۲۳۳۴۴۴، شهر اصفهان\n" +
                            "• موبایل فولاد اصفهان رو بکن ۰۹۱۲۱۲۳۴۵۶۷\n" +
                            "• تلفن فولاد اصفهان چنده؟\n" +
                            "• ایمیل فولاد اصفهان رو پاک کن\n" +
                            "• وندور فولاد اصفهان رو حذف کن\n" +
                            "• لیست وندورها"
                    )
                }
            }
            items(msgs.size) { idx -> Bubble(msgs[idx]) }
        }

        if (vm.pending != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { vm.confirm() }, enabled = !vm.busy) { Text("تأیید") }
                OutlinedButton(onClick = { vm.cancel() }, enabled = !vm.busy) { Text("لغو") }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                enabled = !vm.busy,
                placeholder = { Text(if (vm.busy) "در حال پردازش..." else "پیام بنویس") },
                maxLines = 4,
                modifier = Modifier.weight(1f)
            )
            Button(
                enabled = !vm.busy && input.isNotBlank(),
                onClick = {
                    vm.send(input)
                    input = ""
                }
            ) { Text("ارسال") }
        }
    }

    if (showModel) ModelDialog(vm) { showModel = false }
}

@Composable
private fun Bubble(m: ChatMsg) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (m.fromUser) Arrangement.Start else Arrangement.End
    ) {
        Surface(
            color = if (m.fromUser) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            SelectionContainer {
                Text(if (m.text.isEmpty()) "…" else m.text, modifier = Modifier.padding(10.dp))
            }
        }
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
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    when (vm.modelState) {
                        ModelState.NONE -> "وضعیت: مدل اضافه نشده"
                        ModelState.LOADING -> "وضعیت: در حال بارگذاری..."
                        ModelState.READY -> "وضعیت: آماده"
                        ModelState.ERROR -> "وضعیت: خطا"
                    }
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
