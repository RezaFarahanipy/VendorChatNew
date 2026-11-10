package com.example.vendorchat.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import android.widget.Toast
import com.example.vendorchat.AppLock
import com.example.vendorchat.ChatViewModel
import com.example.vendorchat.chat.Tone
import com.example.vendorchat.ModelState

private const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

private const val DEV_NAME = "رضا فراهانی"
private const val DEV_EMAIL = "Rezafarahani2008@gmail.com"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: ChatViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val progress = vm.importProgress

    val modelPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importModel(uri)
    }
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.importSheet(uri)
            onClose()
        }
    }
    val excelSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri != null) {
            vm.writeExcel(uri)
            onClose()
        }
    }
    var backupPass by remember { mutableStateOf("") }
    val backupSaver = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            vm.writeBackup(uri, backupPass)
            backupPass = ""
            onClose()
        }
    }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) restoreUri = uri
    }
    var askBackupPass by remember { mutableStateOf(false) }
    val templateSaver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
        if (uri != null) {
            vm.writeTemplate(uri)
            onClose()
        }
    }
    var lockOn by remember { mutableStateOf(AppLock.enabled(ctx)) }

    val version = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("تنظیمات", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            // ---------- مدل ----------
            SectionTitle("مدل زبانی")
            SheetCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconChip(Ic.SmartToy, Color(0xFF3B5BDB))
                        Column(Modifier.weight(1f)) {
                            Text("Gemma 4 E2B", fontWeight = FontWeight.Bold)
                            Text(
                                when (vm.modelState) {
                                    ModelState.NONE -> "اضافه نشده"
                                    ModelState.LOADING -> "در حال بارگذاری..."
                                    ModelState.READY -> "آماده"
                                    ModelState.ERROR -> "خطا"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            "در حال کپی فایل مدل: ${(progress * 100).toInt()}٪ (چند دقیقه طول می‌کشد)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    Button(onClick = { modelPicker.launch(arrayOf("*/*")) }, enabled = progress == null) {
                        GlyphIcon(Ic.FolderZip, Color.White, 18.dp)
                        Spacer(Modifier.size(8.dp))
                        Text("انتخاب فایل مدل (.litertlm)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "استفاده از GPU (سریع‌تر؛ اگر ناپایدار بود خاموش کن)",
                            modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(checked = vm.useGpu, onCheckedChange = { vm.setGpu(it) })
                    }
                    if (vm.modelState != ModelState.NONE) {
                        OutlinedButton(onClick = { vm.deleteModel() }, enabled = progress == null) {
                            Text("حذف مدل از حافظهٔ اپ")
                        }
                    }
                    Text(
                        "فایل gemma-4-E2B-it.litertlm (حدود ۲٫۶ گیگابایت) را از huggingface.co/litert-community/gemma-4-E2B-it-litert-lm دانلود کن. حداقل ۶ گیگابایت فضای خالی لازم است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------- لحن ----------
            SectionTitle("لحن گفتگو")
            SheetCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Tone.names.forEachIndexed { i, nm ->
                            FilterChip(selected = vm.tone == i, onClick = { vm.changeTone(i) }, label = { Text(nm) })
                        }
                    }
                    Box(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)).padding(12.dp)
                    ) { Text("«" + Tone.sample(vm.tone) + "»", style = MaterialTheme.typography.bodyMedium) }
                    Text(
                        "لحن روی نحوهٔ حرف زدن هوش مصنوعی اثر می‌گذارد. «خودمونی» محاوره‌ای و صمیمی است.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ---------- امنیت ----------
            SectionTitle("امنیت")
            SheetCard {
                Row(
                    Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    IconChip(Ic.Fingerprint, Color(0xFFDB2777), box = 42.dp, glyph = 22.dp)
                    Column(Modifier.weight(1f)) {
                        Text("قفل برنامه", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "با اثر انگشت یا پین/الگوی گوشی؛ هر بار که برنامه را باز می‌کنی",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = lockOn, onCheckedChange = { want ->
                        val act = ctx.findActivity()
                        if (act != null) {
                            if (want && !AppLock.available(ctx)) {
                                Toast.makeText(ctx, "اول در تنظیمات گوشی یک پین، الگو یا اثر انگشت بگذار.", Toast.LENGTH_LONG).show()
                            } else {
                                AppLock.prompt(
                                    act, if (want) "تأیید برای فعال‌سازی قفل" else "تأیید برای غیرفعال‌سازی قفل",
                                    onOk = {
                                        AppLock.setEnabled(ctx, want)
                                        lockOn = want
                                    },
                                    onFail = { Toast.makeText(ctx, "تأیید هویت انجام نشد", Toast.LENGTH_SHORT).show() }
                                )
                            }
                        }
                    })
                }
            }

            // ---------- داده‌ها ----------
            SectionTitle("داده‌ها")
            SheetCard {
                Column {
                    ToolRow(
                        Ic.TableView, Color(0xFF3B5BDB), "دریافت قالب اکسل",
                        "فایل آمادهٔ xlsx با ستون‌ها، دو ردیف نمونه و برگهٔ راهنما"
                    ) { templateSaver.launch("VendorChat-template.xlsx") }
                    RowDivider()
                    ToolRow(
                        Ic.UploadFile, Color(0xFF16A34A), "ورود از اکسل یا CSV",
                        "فایلی که با قالب (یا هر جدولی با عنوان ستون) پر کرده‌ای؛ هر ردیف یک وندور"
                    ) { importPicker.launch(arrayOf("*/*")) }
                    RowDivider()
                    ToolRow(
                        Ic.TableView, Color(0xFF0891B2), "خروجی اکسل",
                        "همهٔ وندورها در یک فایل xlsx (بدون اطلاعات بانکی)"
                    ) { excelSaver.launch("VendorChat-vendors.xlsx") }
                    RowDivider()
                    ToolRow(
                        Ic.Backup, Color(0xFF7C3AED), "پشتیبان‌گیری رمزدار",
                        "همهٔ اطلاعات (حتی بانکی) در یک فایل رمزگذاری‌شده"
                    ) { askBackupPass = true }
                    RowDivider()
                    ToolRow(
                        Ic.SettingsBackupRestore, Color(0xFFEA580C), "بازیابی از پشتیبان",
                        "فایل .vcb و رمزش را بده؛ اطلاعات با موجودی ادغام می‌شود"
                    ) { restorePicker.launch(arrayOf("*/*")) }
                }
            }

            // ---------- درباره ----------
            SectionTitle("درباره برنامه")
            SheetCard {
                Column(
                    Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Pal.Brand),
                        contentAlignment = Alignment.Center
                    ) { GlyphIcon(Ic.ChatBubble, Color.White, 34.dp) }
                    Text("دستیار وندور", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (version.isNotEmpty()) {
                        Text(
                            "نسخهٔ $version", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "مدیریت وندورها با چت؛ هوش مصنوعی روی خود گوشی اجرا می‌شود و اطلاعات از گوشی بیرون نمی‌رود.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        IconChip(Ic.Person, Color(0xFF7C3AED), box = 34.dp, glyph = 18.dp)
                        Column {
                            Text("توسعه‌دهنده", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(DEV_NAME, fontWeight = FontWeight.Bold)
                        }
                    }
                    Row(
                        Modifier.clip(RoundedCornerShape(14.dp)).clickable {
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$DEV_EMAIL")))
                            } catch (e: Exception) {
                            }
                        }.padding(vertical = 4.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        IconChip(Ic.Mail, Color(0xFF2563EB), box = 34.dp, glyph = 18.dp)
                        Column {
                            Text("ایمیل", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(DEV_EMAIL, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                    Text(
                        "Gemma 4 E2B • LiteRT-LM",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (askBackupPass) {
        PasswordDialog(
            title = "رمز پشتیبان", okLabel = "ادامه", needConfirm = true,
            onDismiss = { askBackupPass = false }
        ) { pass ->
            askBackupPass = false
            backupPass = pass
            backupSaver.launch("VendorChat-backup.vcb")
        }
    }
    val ru = restoreUri
    if (ru != null) {
        PasswordDialog(
            title = "رمز فایل پشتیبان", okLabel = "بازیابی", needConfirm = false,
            onDismiss = { restoreUri = null }
        ) { pass ->
            vm.readBackup(ru, pass)
            restoreUri = null
            onClose()
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp, start = 4.dp)
    )
}

@Composable
private fun SheetCard(content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) { content() }
}

@Composable
private fun RowDivider() {
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    )
}

@Composable
private fun ToolRow(icon: ImageVector, tint: Color, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        IconChip(icon, tint, box = 42.dp, glyph = 22.dp)
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PasswordDialog(
    title: String,
    okLabel: String,
    needConfirm: Boolean,
    onDismiss: () -> Unit,
    onOk: (String) -> Unit
) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    val ok = p1.length >= 4 && (!needConfirm || p1 == p2)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = p1, onValueChange = { p1 = it }, label = { Text("رمز") },
                    singleLine = true, visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (needConfirm) {
                    OutlinedTextField(
                        value = p2, onValueChange = { p2 = it }, label = { Text("تکرار رمز") },
                        singleLine = true, visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (p2.isNotEmpty() && p1 != p2) {
                        Text("دو رمز یکی نیست", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        "رمز حداقل ۴ حرف باشد. آن را فراموش نکن؛ بدون رمز، فایل پشتیبان قابل بازیابی نیست.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(enabled = ok, onClick = { onOk(p1) }) { Text(okLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } }
    )
}
