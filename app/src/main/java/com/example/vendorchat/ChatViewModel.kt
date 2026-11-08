package com.example.vendorchat

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.vendorchat.chat.Cmd
import com.example.vendorchat.chat.Commands
import com.example.vendorchat.chat.NameMatch
import com.example.vendorchat.chat.Prompts
import com.example.vendorchat.data.Db
import com.example.vendorchat.data.Fa
import com.example.vendorchat.llm.LlmManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ModelState { NONE, LOADING, READY, ERROR }

data class ChatMsg(val fromUser: Boolean, val text: String)

/** دستور منتظر تأیید کاربر. */
data class Pending(val cmd: Cmd, val vendorId: Long?, val vendorName: String)

class ChatViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db by lazy { Db(app) }
    private val llmLazy = lazy { LlmManager(app) }
    private val llm: LlmManager get() = llmLazy.value
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val messages = mutableStateListOf<ChatMsg>()

    var pending by mutableStateOf<Pending?>(null)
        private set
    var modelState by mutableStateOf(ModelState.NONE)
        private set
    var busy by mutableStateOf(false)
        private set
    var importProgress by mutableStateOf<Float?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var useGpu by mutableStateOf(prefs.getBoolean("gpu", false))
        private set

    private var lastVendorId: Long? = null

    private fun mark(on: Boolean) {
        prefs.edit().putBoolean("inflight", on).commit()
    }

    init {
        // اگر اجرای قبلی وسط بارگذاری/پاسخ‌دهی با GPU کرش کرده، GPU را خاموش می‌کنیم
        var note: String? = null
        if (prefs.getBoolean("inflight", false)) {
            if (useGpu) {
                useGpu = false
                prefs.edit().putBoolean("gpu", false).commit()
                note = "اجرای قبلی با GPU ناپایدار بود؛ GPU خاموش شد."
            }
            mark(false)
        }
        if (File(app.filesDir, "model.litertlm").exists()) loadModel()
        if (note != null) error = note
    }

    // ---------------- مدل ----------------
    fun loadModel() {
        modelState = ModelState.LOADING
        error = null
        viewModelScope.launch {
            mark(true)
            val r = llm.load(useGpu)
            mark(false)
            if (r.isSuccess) {
                modelState = ModelState.READY
            } else {
                modelState = ModelState.ERROR
                error = r.exceptionOrNull()?.message ?: "خطا در بارگذاری مدل"
            }
        }
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            error = null
            importProgress = 0f
            try {
                llm.importModel(uri) { importProgress = it }
                importProgress = null
                loadModel()
            } catch (e: Exception) {
                importProgress = null
                modelState = ModelState.NONE
                error = e.message ?: "خطا در کپی مدل"
            }
        }
    }

    fun setGpu(v: Boolean) {
        useGpu = v
        prefs.edit().putBoolean("gpu", v).commit()
        if (File(app.filesDir, "model.litertlm").exists() && importProgress == null) loadModel()
    }

    fun deleteModel() {
        llm.deleteModel()
        modelState = ModelState.NONE
        error = null
    }

    fun clearChat() {
        if (!busy) {
            messages.clear()
            pending = null
        }
    }

    // ---------------- چت ----------------
    private fun reply(text: String) {
        messages.add(ChatMsg(false, text))
    }

    private fun setLast(text: String) {
        if (messages.isNotEmpty()) messages[messages.lastIndex] = ChatMsg(false, text)
    }

    fun confirm() = send("بله")
    fun cancel() = send("لغو")

    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || busy) return
        messages.add(ChatMsg(true, q))
        busy = true
        viewModelScope.launch {
            mark(true)
            try {
                handle(q)
            } catch (e: Exception) {
                reply("خطا: " + (e.message ?: e.toString()))
            } finally {
                mark(false)
                busy = false
            }
        }
    }

    private suspend fun handle(q: String) {
        val n = Fa.normalize(q)

        // ۱) پاسخ به تأیید/لغو دستور منتظر
        val p = pending
        if (p != null) {
            pending = null
            if (Commands.isYes(n)) {
                execute(p)
                return
            }
            if (Commands.isNo(n)) {
                reply("لغو شد.")
                return
            }
            // پیام جدید: دستور قبلی لغو می‌شود و پیام عادی پردازش می‌شود
        }

        // ۲) لیست و تعداد (بدون مدل، دقیق)
        if (Commands.wantsList(n)) {
            val all = withContext(Dispatchers.IO) { db.names() }
            reply(
                if (all.isEmpty()) "هنوز وندوری ثبت نشده."
                else "تعداد وندورها: ${all.size}\n" +
                    all.take(40).mapIndexed { i, v -> "${i + 1}. ${v.name}" }.joinToString("\n") +
                    if (all.size > 40) "\n..." else ""
            )
            return
        }
        if (Commands.wantsCount(n)) {
            reply("تعداد وندورهای ثبت‌شده: ${withContext(Dispatchers.IO) { db.count() }}")
            return
        }

        // ۳) ثبت / ویرایش / حذف
        if (Commands.looksLikeCommand(n)) {
            if (modelState != ModelState.READY) {
                reply("برای ثبت، ویرایش و حذف با چت، مدل زبانی باید آماده باشد. از دکمهٔ «مدل» بالای صفحه مدل را اضافه کن.")
                return
            }
            if (tryCommand(q)) return
        }

        // ۴) پرسش و پاسخ
        answer(q, n)
    }

    /** true یعنی پیام به عنوان دستور مدیریت شد (یا خطا داد)، false یعنی پیام یک سؤال است. */
    private suspend fun tryCommand(q: String): Boolean {
        reply("در حال بررسی دستور...")
        val raw = try {
            llm.complete(Commands.ROUTER_SYSTEM, "پیام: $q")
        } catch (e: Exception) {
            setLast("خطا در مدل: " + (e.message ?: e.toString()))
            return true
        }
        val cmd = Commands.parse(raw)
        if (cmd is Cmd.None) {
            messages.removeAt(messages.lastIndex)
            return false
        }
        if (cmd is Cmd.Invalid) {
            setLast(Commands.REPHRASE)
            return true
        }

        val all = withContext(Dispatchers.IO) { db.names() }
        when (cmd) {
            is Cmd.Add -> {
                val hits = NameMatch.rank(cmd.vendor, all)
                if (hits.any { it.score >= 3 }) {
                    setLast("وندور «${cmd.vendor}» از قبل ثبت شده. برای تغییرش بگو، مثلاً: «موبایل ${cmd.vendor} رو بکن ...»")
                } else {
                    pending = Pending(cmd, null, cmd.vendor)
                    setLast(Commands.summary(cmd, cmd.vendor))
                }
            }
            is Cmd.Update -> resolveThen(cmd, cmd.vendor, all)
            is Cmd.Delete -> resolveThen(cmd, cmd.vendor, all)
            else -> setLast(Commands.REPHRASE)
        }
        return true
    }

    private fun resolveThen(cmd: Cmd, name: String, all: List<com.example.vendorchat.data.VendorRow>) {
        when (val r = NameMatch.resolve(name, all)) {
            is NameMatch.Resolved.One -> {
                pending = Pending(cmd, r.hit.id, r.hit.name)
                setLast(Commands.summary(cmd, r.hit.name))
            }
            is NameMatch.Resolved.Many ->
                setLast("چند وندور شبیه «$name» هست، دقیق‌تر بگو:\n" + r.hits.joinToString("\n") { "• ${it.name}" })
            is NameMatch.Resolved.None ->
                setLast("وندوری با نام «$name» پیدا نشد. برای ثبت جدید بگو: «وندور $name رو اضافه کن».")
        }
    }

    private suspend fun execute(p: Pending) {
        val text = withContext(Dispatchers.IO) {
            when (val c = p.cmd) {
                is Cmd.Add -> {
                    val id = db.create(c.vendor)
                    if (c.fields.isNotEmpty()) db.setFields(id, c.fields)
                    lastVendorId = id
                    "✅ وندور «${c.vendor}» ثبت شد" + if (c.fields.isNotEmpty()) " (${c.fields.size} مورد اطلاعات)." else "."
                }
                is Cmd.Update -> {
                    val id = p.vendorId ?: return@withContext "خطا: وندور مشخص نیست."
                    if (c.fields.isNotEmpty()) db.setFields(id, c.fields)
                    val removed = if (c.remove.isNotEmpty()) db.removeFields(id, c.remove) else 0
                    lastVendorId = id
                    "✅ «${p.vendorName}» به‌روز شد." + if (c.remove.isNotEmpty()) " ($removed مورد حذف شد)" else ""
                }
                is Cmd.Delete -> {
                    val id = p.vendorId ?: return@withContext "خطا: وندور مشخص نیست."
                    db.delete(id)
                    if (lastVendorId == id) lastVendorId = null
                    "🗑️ وندور «${p.vendorName}» حذف شد."
                }
                else -> "خطا: دستور نامعتبر."
            }
        }
        reply(text)
    }

    private suspend fun answer(q: String, n: String) {
        val ids = ArrayList<Long>()
        withContext(Dispatchers.IO) { db.search(q, 3) }.forEach { ids.add(it.id) }
        if (ids.isEmpty()) {
            val last = lastVendorId
            if (Fa.tokens(q).isEmpty() && last != null) {
                ids.add(last)
            } else {
                val total = withContext(Dispatchers.IO) { db.count() }
                reply(
                    if (total == 0) "هنوز وندوری ثبت نشده. مثلاً بگو: «وندور فولاد اصفهان رو اضافه کن، تلفن ...»"
                    else "وندوری مرتبط با پرسش پیدا نشد. بگو «لیست وندورها» تا همهٔ وندورها را ببینی."
                )
                return
            }
        }
        lastVendorId = ids.first()

        val withSecret = Commands.wantsSecret(n)
        val blocks = withContext(Dispatchers.IO) {
            ids.mapNotNull { db.get(it) }.map { Prompts.block(it, withSecret) }
        }
        val data = blocks.joinToString("\n\n")

        if (modelState != ModelState.READY) {
            reply(data + "\n\n(برای پاسخ هوشمند، مدل زبانی را فعال کن.)")
            return
        }

        val total = withContext(Dispatchers.IO) { db.count() }
        val system = Prompts.answerSystem(data, total)
        reply("")
        var acc = ""
        llm.ask(system, q).collect { chunk ->
            acc += chunk
            setLast(acc)
        }
        if (acc.isBlank()) setLast(data)
    }

    override fun onCleared() {
        if (llmLazy.isInitialized()) llm.close()
    }
}
