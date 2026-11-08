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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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

private fun emojiFor(k: String): String = when (k) {
    "تلفن" -> "📞"
    "موبایل" -> "📱"
    "ایمیل" -> "✉️"
    "آدرس" -> "📍"
    "شهر" -> "🏙️"
    "استان" -> "🗺️"
    "کد پستی" -> "📮"
    "شناسه ملی" -> "🆔"
    "شماره ثبت" -> "🧾"
    "کد اقتصادی" -> "🏷️"
    "وبسایت" -> "🌐"
    "مسئول" -> "👤"
    "سمت" -> "💼"
    "کالا" -> "📦"
    "بانک" -> "🏦"
    "شماره حساب", "شبا" -> "💳"
    "توضیحات" -> "📝"
    else -> "🔹"
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
fun Avatar(
    letter: String,
    size: Dp,
    brush: Brush = Pal.BrandDiag,
    textColor: Color = Color.White,
    fontSize: TextUnit = 18.sp
) {
    Box(Modifier.size(size).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
        Text(letter, color = textColor, fontWeight = FontWeight.Bold, fontSize = fontSize)
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(Modifier.fillMaxWidth().background(Pal.Brand).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Avatar(
                    v.name.trim().take(1), 46.dp,
                    brush = Brush.linearGradient(listOf(Color(0x33FFFFFF), Color(0x33FFFFFF))),
                    fontSize = 20.sp
                )
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
            }
        }
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (groups.isEmpty()) {
                Text(
                    "هنوز اطلاعاتی ثبت نشده. مثلاً بگو «شماره‌اش ... است».",
                    color = muted, style = MaterialTheme.typography.bodyMedium
                )
            }
            groups.forEach { (k, list) ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) { Text(emojiFor(k), fontSize = 16.sp) }
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
                                    Text(
                                        "📋",
                                        modifier = Modifier.clickable { copyText(ctx, a.value) }.padding(8.dp),
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (hasSecret) {
                TextButton(onClick = { reveal = !reveal }) {
                    Text(if (reveal) "🙈 پنهان کردن اطلاعات بانکی" else "👁 نمایش اطلاعات بانکی")
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(onClick = onEdit) { Text("✏️ ویرایش") }
            TextButton(
                onClick = onDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = Pal.Danger)
            ) { Text("🗑️ حذف") }
        }
    }
}

@Composable
fun ActionCard(m: Msg.Action, enabled: Boolean, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val accent = if (m.kind == "delete") Pal.Danger else MaterialTheme.colorScheme.primary
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.fillMaxWidth().background(accent.copy(alpha = 0.10f)).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                when (m.kind) {
                    "delete" -> "🗑️"
                    "add" -> "➕"
                    else -> "✏️"
                },
                fontSize = 20.sp
            )
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
                    ) { Text("تأیید") }
                    OutlinedButton(
                        onClick = onCancel, enabled = enabled,
                        shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
                    ) { Text("لغو") }
                }
                1 -> Text("✅ انجام شد", color = Pal.Ok, fontWeight = FontWeight.Bold)
                else -> Text("✖ لغو شد", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NameChips(title: String, names: List<String>, onPick: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
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
