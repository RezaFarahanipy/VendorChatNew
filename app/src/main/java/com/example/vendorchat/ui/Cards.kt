package com.example.vendorchat.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.vendorchat.chat.Msg
import com.example.vendorchat.data.AddressData
import com.example.vendorchat.data.VendorData

private val order = listOf(
    "تلفن", "موبایل", "ایمیل", "وبسایت", "آدرس", "شهر", "استان", "کد پستی", "مسئول", "سمت",
    "کالا", "شناسه ملی", "شماره ثبت", "کد اقتصادی", "بانک", "شماره حساب", "شبا", "توضیحات"
)

/** آیکن و رنگ هر نوع اطلاعات */
private fun iconFor(k: String): Pair<ImageVector, Color> = when (k) {
    "تلفن" -> Ic.Call to Color(0xFF16A34A)
    "موبایل" -> Ic.Smartphone to Color(0xFF0D9488)
    "ایمیل" -> Ic.Mail to Color(0xFF2563EB)
    "وبسایت" -> Ic.Language to Color(0xFF0891B2)
    "آدرس" -> Ic.LocationOn to Color(0xFFEA580C)
    "شهر" -> Ic.LocationCity to Color(0xFFF97316)
    "استان" -> Ic.Map to Color(0xFFF97316)
    "کد پستی" -> Ic.MarkunreadMailbox to Color(0xFFDB2777)
    "مسئول" -> Ic.Person to Color(0xFF7C3AED)
    "سمت" -> Ic.Work to Color(0xFF6D28D9)
    "کالا" -> Ic.Inventory2 to Color(0xFFD97706)
    "شناسه ملی" -> Ic.Badge to Color(0xFF4F46E5)
    "شماره ثبت" -> Ic.ReceiptLong to Color(0xFF4F46E5)
    "کد اقتصادی" -> Ic.Sell to Color(0xFF4F46E5)
    "بانک" -> Ic.AccountBalance to Color(0xFF0F766E)
    "شماره حساب", "شبا" -> Ic.CreditCard to Color(0xFF0F766E)
    "توضیحات" -> Ic.Notes to Color(0xFF64748B)
    else -> Ic.Info to Color(0xFF3B5BDB)
}

private fun copyText(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("vendor", text))
    Toast.makeText(ctx, "کپی شد", Toast.LENGTH_SHORT).show()
}

private fun openValue(ctx: Context, key: String, value: String) {
    try {
        val intent: Intent? = when (key) {
            "تلفن", "موبایل" ->
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + value.filter { it.isDigit() || it == '+' }))
            "ایمیل" -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$value"))
            "وبسایت" ->
                Intent(Intent.ACTION_VIEW, Uri.parse(if (value.startsWith("http")) value else "https://$value"))
            else -> null
        }
        if (intent != null) ctx.startActivity(intent) else copyText(ctx, value)
    } catch (e: Exception) {
        copyText(ctx, value)
    }
}

@Composable
fun GlyphIcon(icon: ImageVector, tint: Color, size: Dp = 20.dp) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

/** دایرهٔ رنگی کم‌رنگ با آیکن داخلش */
@Composable
fun IconChip(icon: ImageVector, tint: Color, box: Dp = 38.dp, glyph: Dp = 20.dp) {
    Box(
        Modifier.size(box).clip(CircleShape).background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center
    ) { GlyphIcon(icon, tint, glyph) }
}

@Composable
fun Avatar(letter: String, size: Dp, brush: Brush = Pal.BrandDiag, fontSize: TextUnit = 18.sp) {
    Box(Modifier.size(size).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
        Text(letter, color = Color.White, fontWeight = FontWeight.Bold, fontSize = fontSize)
    }
}

private fun shareText(v: VendorData): String = buildString {
    appendLine("🏢 ${v.name}")
    val g = LinkedHashMap<String, MutableList<String>>()
    v.attrs.filter { !it.secret }.forEach {
        g.getOrPut(it.key) { mutableListOf() }.add(if (it.label.isNullOrEmpty()) it.value else "${it.value} (${it.label})")
    }
    g.forEach { (k, vals) -> appendLine("$k: ${vals.joinToString("، ")}") }
    v.addresses.forEach { a ->
        append("📍 آدرس ${a.kind}: " + listOfNotNull(a.province, a.city, a.street).joinToString("، "))
        a.postal?.let { append(" | کد پستی $it") }
        appendLine()
        if (a.lat != null && a.lng != null) appendLine("   نقشه: https://maps.google.com/?q=${a.lat},${a.lng}")
    }
    v.people.forEach { p ->
        appendLine("👤 ${p.name}" + listOfNotNull(p.role, p.mobile, p.phone, p.email).let { if (it.isEmpty()) "" else " — " + it.joinToString(" • ") })
    }
}.trim()

private fun shareVendor(ctx: Context, v: VendorData) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, shareText(v))
    }
    try {
        ctx.startActivity(Intent.createChooser(i, "ارسال اطلاعات وندور"))
    } catch (e: Exception) {
        copyText(ctx, shareText(v))
    }
}

private fun openMap(ctx: Context, a: AddressData, name: String) {
    val uri = if (a.lat != null && a.lng != null) {
        Uri.parse("geo:${a.lat},${a.lng}?q=${a.lat},${a.lng}(${Uri.encode(name)})")
    } else {
        Uri.parse("geo:0,0?q=" + Uri.encode(listOfNotNull(a.province, a.city, a.street).joinToString(" ")))
    }
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Exception) {
        try {
            val q = if (a.lat != null && a.lng != null) "${a.lat},${a.lng}" else Uri.encode(listOfNotNull(a.city, a.street).joinToString(" "))
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/search/?api=1&query=$q")))
        } catch (e2: Exception) {
            Toast.makeText(ctx, "برنامهٔ نقشه پیدا نشد", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
private fun SectionLabel(icon: ImageVector, tint: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlyphIcon(icon, tint, 18.dp)
        Text(text, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium, color = tint)
    }
}

/** یک سطر اطلاعات با آیکن، عنوان کوچک، مقدار و دکمهٔ کپی */
@Composable
private fun InfoLine(
    icon: ImageVector, tint: Color, title: String, value: String,
    visible: Boolean = true, onOpen: (() -> Unit)? = null, onCopy: (() -> Unit)? = null
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        IconChip(icon, tint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = muted)
            Text(
                if (visible) value else "•••• •••• ••••",
                modifier = Modifier.clickable(enabled = visible && onOpen != null) { onOpen?.invoke() }.padding(vertical = 2.dp),
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium
            )
        }
        if (visible && onCopy != null) {
            Box(Modifier.clip(CircleShape).clickable { onCopy() }.padding(8.dp)) { GlyphIcon(Ic.ContentCopy, muted, 16.dp) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VendorCard(v: VendorData, onEdit: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var reveal by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val primary = MaterialTheme.colorScheme.primary

    fun vals(key: String) = v.attrs.filter { it.key == key && !it.secret }
    val activity = vals("نوع فعالیت").map { it.value }
    val tags = vals("دسته‌بندی").map { it.value }
    val fixedKeys = setOf("نوع فعالیت", "دسته‌بندی", "تلفن", "موبایل", "ایمیل", "وبسایت", "شناسه ملی", "شماره ثبت", "کد اقتصادی", "کالا", "توضیحات")
    val others = v.attrs.filter { it.key !in fixedKeys }
    val hasBankSecret = v.banks.any { it.account != null || it.sheba != null || it.holder != null } || v.attrs.any { it.secret }
    val total = v.attrs.size + v.addresses.size + v.people.size + v.banks.size

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.fillMaxWidth().background(Pal.avatar(v.name)).padding(horizontal = 16.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Color(0x33FFFFFF)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(v.name.trim().take(1), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text(v.name, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        if (activity.isNotEmpty()) activity.joinToString(" • ") else "$total مورد اطلاعات",
                        color = Color(0xCCFFFFFF), style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(Ic.Storefront, contentDescription = null, tint = Color(0x66FFFFFF), modifier = Modifier.size(34.dp))
            }
        }

        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (total == 0) {
                Text("هنوز اطلاعاتی ثبت نشده. مثلاً بگو «تلفنش ... است».", color = muted, style = MaterialTheme.typography.bodyMedium)
            }
            if (tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { t ->
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) { Text("#$t", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer) }
                    }
                }
            }

            // تماس
            vals("تلفن").forEach { a ->
                InfoLine(Ic.Call, Color(0xFF16A34A), if (a.label.isNullOrEmpty()) "تلفن" else "تلفن • ${a.label}", a.value,
                    onOpen = { openValue(ctx, "تلفن", a.value) }, onCopy = { copyText(ctx, a.value) })
            }
            vals("موبایل").forEach { a ->
                InfoLine(Ic.Smartphone, Color(0xFF0D9488), if (a.label.isNullOrEmpty()) "موبایل" else "موبایل • ${a.label}", a.value,
                    onOpen = { openValue(ctx, "موبایل", a.value) }, onCopy = { copyText(ctx, a.value) })
            }
            vals("ایمیل").forEach { a ->
                InfoLine(Ic.Mail, Color(0xFF2563EB), if (a.label.isNullOrEmpty()) "ایمیل" else "ایمیل • ${a.label}", a.value,
                    onOpen = { openValue(ctx, "ایمیل", a.value) }, onCopy = { copyText(ctx, a.value) })
            }
            vals("وبسایت").forEach { a ->
                InfoLine(Ic.Language, Color(0xFF0891B2), "وبسایت", a.value,
                    onOpen = { openValue(ctx, "وبسایت", a.value) }, onCopy = { copyText(ctx, a.value) })
            }

            // مشخصات ثبتی
            listOf("شناسه ملی" to Ic.Badge, "شماره ثبت" to Ic.ReceiptLong, "کد اقتصادی" to Ic.Sell).forEach { (k, ic) ->
                vals(k).forEach { a ->
                    InfoLine(ic, Color(0xFF4F46E5), k, a.value, onCopy = { copyText(ctx, a.value) })
                }
            }
            val goods = vals("کالا").map { it.value }
            if (goods.isNotEmpty()) {
                InfoLine(Ic.Inventory2, Color(0xFFD97706), "کالا و خدمات", goods.joinToString("، "), onCopy = { copyText(ctx, goods.joinToString("، ")) })
            }

            // آدرس‌ها
            if (v.addresses.isNotEmpty()) {
                SectionLabel(Ic.LocationOn, Color(0xFFEA580C), "آدرس‌ها")
                v.addresses.forEach { a ->
                    val place = listOfNotNull(a.province, a.city, a.street).joinToString("، ")
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFEA580C).copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 3.dp)
                            ) { Text(a.kind, style = MaterialTheme.typography.labelMedium, color = Color(0xFFEA580C), fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.weight(1f))
                            if (a.lat != null && a.lng != null) {
                                Text("موقعیت ثبت شده", style = MaterialTheme.typography.labelSmall, color = Pal.Ok)
                            }
                        }
                        if (place.isNotEmpty()) Text(place, style = MaterialTheme.typography.bodyLarge)
                        a.postal?.let { Text("کد پستی: $it", style = MaterialTheme.typography.bodySmall, color = muted) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { openMap(ctx, a, v.name) }, shape = RoundedCornerShape(12.dp)) {
                                GlyphIcon(Ic.Directions, primary, 18.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("نقشه")
                            }
                            OutlinedButton(onClick = { copyText(ctx, place) }, shape = RoundedCornerShape(12.dp)) {
                                GlyphIcon(Ic.ContentCopy, primary, 16.dp)
                                Spacer(Modifier.width(6.dp))
                                Text("کپی")
                            }
                        }
                    }
                }
            }

            // پرسنل
            if (v.people.isNotEmpty()) {
                SectionLabel(Ic.Groups, Color(0xFF7C3AED), "پرسنل")
                v.people.forEach { p ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Avatar(p.name.trim().take(1), 36.dp, brush = Pal.avatar(p.name), fontSize = 16.sp)
                            Column {
                                Text(p.name, fontWeight = FontWeight.Bold)
                                p.role?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                            }
                        }
                        p.mobile?.let { m ->
                            InfoLine(Ic.Smartphone, Color(0xFF0D9488), "موبایل", m, onOpen = { openValue(ctx, "موبایل", m) }, onCopy = { copyText(ctx, m) })
                        }
                        p.phone?.let { m ->
                            InfoLine(Ic.Call, Color(0xFF16A34A), "تلفن", m, onOpen = { openValue(ctx, "تلفن", m) }, onCopy = { copyText(ctx, m) })
                        }
                        p.email?.let { m ->
                            InfoLine(Ic.Mail, Color(0xFF2563EB), "ایمیل", m, onOpen = { openValue(ctx, "ایمیل", m) }, onCopy = { copyText(ctx, m) })
                        }
                        p.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
                    }
                }
            }

            // حساب بانکی
            if (v.banks.isNotEmpty()) {
                SectionLabel(Ic.AccountBalance, Color(0xFF0F766E), "حساب‌های بانکی")
                v.banks.forEach { b ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            listOfNotNull(b.bank, b.branch?.let { "شعبه $it" }).joinToString(" • ").ifEmpty { "حساب" },
                            fontWeight = FontWeight.Bold
                        )
                        b.holder?.let { InfoLine(Ic.Person, Color(0xFF0F766E), "صاحب حساب", it, visible = reveal, onCopy = { copyText(ctx, it) }) }
                        b.account?.let { InfoLine(Ic.CreditCard, Color(0xFF0F766E), "شماره حساب", it, visible = reveal, onCopy = { copyText(ctx, it) }) }
                        b.sheba?.let { InfoLine(Ic.CreditCard, Color(0xFF0F766E), "شبا", it, visible = reveal, onCopy = { copyText(ctx, it) }) }
                    }
                }
            }

            // توضیحات و مشخصه‌های دلخواه
            vals("توضیحات").forEach { a -> InfoLine(Ic.Notes, Color(0xFF64748B), "توضیحات", a.value, onCopy = { copyText(ctx, a.value) }) }
            others.forEach { a ->
                InfoLine(Ic.Info, primary, a.key, a.value, visible = !a.secret || reveal, onCopy = { copyText(ctx, a.value) })
            }

            if (hasBankSecret) {
                TextButton(onClick = { reveal = !reveal }) {
                    GlyphIcon(if (reveal) Ic.VisibilityOff else Ic.Visibility, primary, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(if (reveal) "پنهان کردن اطلاعات بانکی" else "نمایش اطلاعات بانکی")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onEdit) {
                GlyphIcon(Ic.Edit, primary, 18.dp)
                Spacer(Modifier.width(6.dp))
                Text("ویرایش")
            }
            TextButton(onClick = { shareVendor(ctx, v) }) {
                GlyphIcon(Ic.Share, primary, 18.dp)
                Spacer(Modifier.width(6.dp))
                Text("ارسال")
            }
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = Pal.Danger)) {
                GlyphIcon(Ic.Delete, Pal.Danger, 18.dp)
                Spacer(Modifier.width(6.dp))
                Text("حذف")
            }
        }
    }
}

@Composable
fun ActionCard(m: Msg.Action, enabled: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val accent = if (m.kind == "delete") Pal.Danger else MaterialTheme.colorScheme.primary
    val icon = when (m.kind) {
        "delete" -> Ic.Delete
        "add" -> Ic.AddCircle
        "import" -> Ic.UploadFile
        "restore" -> Ic.SettingsBackupRestore
        else -> Ic.Edit
    }
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().background(accent.copy(alpha = 0.10f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconChip(icon, accent, box = 36.dp, glyph = 20.dp)
            Text(
                m.title, fontWeight = FontWeight.Bold, color = accent,
                style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)
            )
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            m.lines.forEach { line ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.padding(top = 9.dp).size(6.dp).clip(CircleShape).background(accent))
                    Text(line, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(4.dp))
            when (m.state) {
                0 -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = onConfirm, enabled = enabled,
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                    ) {
                        GlyphIcon(Ic.CheckCircle, Color.White, 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("تأیید")
                    }
                    OutlinedButton(
                        onClick = onCancel, enabled = enabled,
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                    ) {
                        GlyphIcon(Ic.Close, MaterialTheme.colorScheme.onSurface, 18.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("لغو")
                    }
                }
                1 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Ic.CheckCircle, Pal.Ok, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("انجام شد", color = Pal.Ok, fontWeight = FontWeight.Bold)
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Ic.Cancel, MaterialTheme.colorScheme.onSurfaceVariant, 18.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("لغو شد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NameChips(title: String, names: List<String>, onPick: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconChip(Ic.FormatListBulleted, MaterialTheme.colorScheme.primary, box = 34.dp, glyph = 18.dp)
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                names.forEach { n -> SuggestionChip(onClick = { onPick(n) }, label = { Text(n) }) }
            }
        }
    }
}

@Composable
fun TypingDots() {
    val t = rememberInfiniteTransition(label = "dots")
    val color = MaterialTheme.colorScheme.primary
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        for (i in 0..2) {
            val a by t.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(600, delayMillis = i * 150), RepeatMode.Reverse),
                label = "d$i"
            )
            Box(Modifier.size(8.dp).alpha(a).background(color, CircleShape))
        }
    }
}
