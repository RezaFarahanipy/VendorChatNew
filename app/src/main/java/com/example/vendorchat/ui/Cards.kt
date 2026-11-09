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
import com.example.vendorchat.data.Attr
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

@Composable
fun VendorCard(v: VendorData, onEdit: () -> Unit, onDelete: () -> Unit) {
    val ctx = LocalContext.current
    var reveal by remember { mutableStateOf(false) }
    val groups = remember(v) {
        val m = LinkedHashMap<String, MutableList<Attr>>()
        v.attrs.forEach { m.getOrPut(it.key) { mutableListOf() }.add(it) }
        val keys = m.keys.sortedBy { k -> order.indexOf(k).let { i -> if (i < 0) 999 else i } }
        keys.map { it to m.getValue(it) }
    }
    val hasSecret = v.attrs.any { it.secret }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

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
                    Text(
                        v.name, color = Color.White,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${v.attrs.size} مورد اطلاعات", color = Color(0xCCFFFFFF),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Icon(Ic.Storefront, contentDescription = null, tint = Color(0x66FFFFFF), modifier = Modifier.size(34.dp))
            }
        }
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (groups.isEmpty()) {
                Text(
                    "هنوز اطلاعاتی ثبت نشده. مثلاً بگو «شماره‌اش ... است».",
                    color = muted, style = MaterialTheme.typography.bodyMedium
                )
            }
            groups.forEach { (k, list) ->
                val (icon, tint) = iconFor(k)
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconChip(icon, tint)
                    Column(Modifier.weight(1f)) {
                        Text(k, style = MaterialTheme.typography.bodySmall, color = muted)
                        list.forEach { a ->
                            val visible = !a.secret || reveal
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (visible) a.value else "•••• •••• ••••",
                                    modifier = Modifier.weight(1f)
                                        .clickable(enabled = visible) { openValue(ctx, k, a.value) }
                                        .padding(vertical = 3.dp),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                if (visible) {
                                    Box(
                                        Modifier.clip(CircleShape).clickable { copyText(ctx, a.value) }.padding(8.dp)
                                    ) { GlyphIcon(Ic.ContentCopy, muted, 16.dp) }
                                }
                            }
                        }
                    }
                }
            }
            if (hasSecret) {
                TextButton(onClick = { reveal = !reveal }) {
                    GlyphIcon(if (reveal) Ic.VisibilityOff else Ic.Visibility, MaterialTheme.colorScheme.primary, 18.dp)
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
                GlyphIcon(Ic.Edit, MaterialTheme.colorScheme.primary, 18.dp)
                Spacer(Modifier.width(6.dp))
                Text("ویرایش")
            }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = Pal.Danger)
            ) {
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
