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
import com.example.vendorchat.chat.Fallback
import com.example.vendorchat.chat.Msg
import com.example.vendorchat.chat.NameMatch
import com.example.vendorchat.chat.Prompts
import com.example.vendorchat.data.Db
import com.example.vendorchat.data.Fa
import com.example.vendorchat.data.VendorRow
import com.example.vendorchat.llm.LlmManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ModelState { NONE, LOADING, READY, ERROR }

/** دستور منتظر تأیید کاربر. */
data class Pending(val cmd: Cmd, val vendorId: Long?, val vendorName: String)

private data class ExecResult(val text: String, val vendorId: Long?)

class ChatViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db by lazy { Db(app) }
    private val llmLazy = lazy { LlmManager(app) }
    private val llm: LlmManager get() = llmLazy.value
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val messages = mutableStateListOf<Msg>()

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
    private var lastVendorName: String? = null

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

    // ---------------- کمکی‌های پیام ----------------
    private fun addText(t: String) {
        messages.add(Msg.Text(false, t))
    }

    private fun typing() {
        messages.add(Msg.Text(false, ""))
    }

    private fun setLastText(t: String) {
        val i = messages.lastIndex
        if (i >= 0) messages[i] = Msg.Text(false, t)
    }

    private fun dropTyping() {
        val i = messages.lastIndex
        if (i >= 0) {
            val m = messages[i]
            if (m is Msg.Text && !m.fromUser && m.text.isEmpty()) messages.removeAt(i)
        }
    }

    private fun closeAction(state: Int) {
        for (i in messages.indices.reversed()) {
            val m = messages[i]
            if (m is Msg.Action && m.state == 0) {
                messages[i] = m.copy(state = state)
                return
            }
        }
    }

    private fun setCurrent(id: Long, name: String) {
        lastVendorId = id
        lastVendorName = name
    }

    private fun recentHistory(): List<Pair<String, String>> {
        val texts = messages.filterIsInstance<Msg.Text>()
        val past = if (texts.isNotEmpty() && texts.last().fromUser) texts.dropLast(1) else texts
        val out = ArrayList<Pair<String, String>>()
        var u: String? = null
        for (m in past) {
            if (m.fromUser) {
                u = m.text
            } else if (u != null && m.text.isNotBlank()) {
                out.add(u.take(200) to m.text.take(300))
                u = null
            }
        }
        return out.takeLast(2)
    }

    // ---------------- چت ----------------
    fun send(text: String) {
        val q = text.trim()
        if (q.isEmpty() || busy) return
        messages.add(Msg.Text(true, q))
        busy = true
        viewModelScope.launch {
            mark(true)
            try {
                handle(q)
            } catch (e: Exception) {
                dropTyping()
                addText("خطا: " + (e.message ?: e.toString()))
            } finally {
                mark(false)
                busy = false
            }
        }
    }

    /** دکمهٔ «تأیید» روی کارت. */
    fun confirm() {
        val p = pending ?: return
        if (busy) return
        pending = null
        closeAction(1)
        busy = true
        viewModelScope.launch {
            try {
                execute(p)
            } catch (e: Exception) {
                addText("خطا: " + (e.message ?: e.toString()))
            } finally {
                busy = false
            }
        }
    }

    /** دکمهٔ «لغو» روی کارت. */
    fun cancel() {
        if (pending == null) return
        pending = null
        closeAction(2)
        addText("باشه، لغو شد.")
    }

    private suspend fun handle(raw: String) {
        val n = Fa.normalize(raw)

        // ۱) تأیید/لغو متنی
        val p = pending
        if (p != null) {
            pending = null
            if (Commands.isYes(n)) {
                closeAction(1)
                execute(p)
                return
            }
            if (Commands.isNo(n)) {
                closeAction(2)
                addText("باشه، لغو شد.")
                return
            }
            closeAction(2) // پیام جدید: دستور قبلی لغو می‌شود
        }

        // ۲) لیست و تعداد (بدون مدل، دقیق)
        if (Commands.wantsList(n)) {
            val all = withContext(Dispatchers.IO) { db.names() }
            if (all.isEmpty()) addText("هنوز وندوری ثبت نشده. مثلاً بگو: «وندور فولاد اصفهان رو اضافه کن، تلفن ...»")
            else messages.add(Msg.Chips("${all.size} وندور ثبت شده؛ برای دیدن اطلاعات روی نام بزن", all.take(40).map { it.name }))
            return
        }
        if (Commands.wantsCount(n)) {
            addText("تعداد وندورهای ثبت‌شده: ${withContext(Dispatchers.IO) { db.count() }}")
            return
        }

        // ۳) نمایش اطلاعات به صورت کارت (بدون مدل)
        if (!Commands.hasStrong(n) && Commands.wantsShow(n) && showCards(raw)) return

        // ۴) ثبت / ویرایش / حذف
        if (Commands.likelyAction(raw, n)) {
            if (tryAction(raw, n)) return
        }

        // ۵) گفتگوی طبیعی
        converse(raw, n)
    }

    private suspend fun findVendors(raw: String): List<Long> = withContext(Dispatchers.IO) {
        val all = db.names()
        val byName = NameMatch.rank(raw, all).filter { it.score >= 2 }
        if (byName.isNotEmpty()) {
            val top = byName.first().score
            byName.filter { it.score == top }.take(3).map { it.id }
        } else {
            db.search(raw, 3).map { it.id }
        }
    }

    private suspend fun showCards(raw: String): Boolean {
        var ids = findVendors(raw)
        val last = lastVendorId
        if (ids.isEmpty() && Fa.tokens(raw).isEmpty() && last != null) ids = listOf(last)
        if (ids.isEmpty()) return false
        val list = withContext(Dispatchers.IO) { ids.mapNotNull { db.get(it) } }
        if (list.isEmpty()) return false
        setCurrent(list.first().id, list.first().name)
        messages.add(Msg.Cards(list))
        return true
    }

    /** true یعنی پیام به عنوان دستور مدیریت شد، false یعنی باید گفتگوی عادی ادامه پیدا کند. */
    private suspend fun tryAction(raw: String, n: String): Boolean {
        val guess = Fallback.guess(raw)
        val canGuess = guess.isNotEmpty() && lastVendorId != null && !Commands.isQuestion(raw, n)

        if (modelState != ModelState.READY) {
            if (canGuess) {
                prepare(Cmd.Update("", guess, emptyList()), withContext(Dispatchers.IO) { db.names() })
                return true
            }
            if (Commands.hasStrong(n)) {
                addText("برای ثبت، ویرایش و حذف با چت، مدل زبانی باید آماده باشد. از دکمهٔ ⚙️ بالای صفحه مدل را اضافه کن.")
                return true
            }
            return false
        }

        typing()
        val all = withContext(Dispatchers.IO) { db.names() }
        val system = Commands.routerSystem(lastVendorName, all.map { it.name })
        val out = try {
            llm.complete(system, "پیام: $raw")
        } catch (e: Exception) {
            dropTyping()
            addText("خطا در مدل: " + (e.message ?: e.toString()))
            return true
        }
        dropTyping()
        var cmd = Commands.parse(out)
        if ((cmd is Cmd.None || cmd is Cmd.Invalid) && canGuess) {
            cmd = Cmd.Update("", guess, emptyList())
        }
        return when (cmd) {
            is Cmd.None -> false
            is Cmd.Invalid -> {
                if (Commands.hasStrong(n)) {
                    addText(Commands.REPHRASE)
                    true
                } else false
            }
            else -> {
                prepare(cmd, all)
                true
            }
        }
    }

    /** وندور هدف را پیدا می‌کند و کارت تأیید را نشان می‌دهد. */
    private fun prepare(cmd: Cmd, all: List<VendorRow>) {
        when (cmd) {
            is Cmd.Add -> {
                val exact = NameMatch.rank(cmd.vendor, all).firstOrNull { it.score >= 3 }
                if (exact != null) {
                    setCurrent(exact.id, exact.name)
                    addText("وندور «${exact.name}» از قبل ثبت شده. برای تغییرش بگو، مثلاً: «موبایلش رو بکن ...»")
                } else {
                    startPending(Pending(cmd, null, cmd.vendor))
                }
            }
            is Cmd.Update -> resolveThen(cmd, cmd.vendor, all)
            is Cmd.Delete -> resolveThen(cmd, cmd.vendor, all)
            else -> addText(Commands.REPHRASE)
        }
    }

    private fun resolveThen(cmd: Cmd, name: String, all: List<VendorRow>) {
        if (name.isBlank()) {
            val id = lastVendorId
            val nm = lastVendorName
            if (id != null && nm != null && all.any { it.id == id }) {
                startPending(Pending(cmd, id, nm))
            } else {
                addText("منظورت کدوم وندوره؟ اسمش رو بگو.")
            }
            return
        }
        when (val r = NameMatch.resolve(name, all)) {
            is NameMatch.Resolved.One -> startPending(Pending(cmd, r.hit.id, r.hit.name))
            is NameMatch.Resolved.Many -> messages.add(
                Msg.Chips("چند وندور شبیه «$name» هست؛ کدوم رو می‌گی؟", r.hits.map { it.name })
            )
            is NameMatch.Resolved.None ->
                addText("وندوری با نام «$name» پیدا نشد. برای ثبت جدید بگو: «وندور $name رو اضافه کن».")
        }
    }

    private fun startPending(p: Pending) {
        pending = p
        messages.add(Msg.Action(Commands.kind(p.cmd), Commands.title(p.cmd, p.vendorName), Commands.lines(p.cmd), 0))
    }

    private suspend fun execute(p: Pending) {
        val r = withContext(Dispatchers.IO) {
            when (val c = p.cmd) {
                is Cmd.Add -> {
                    val id = db.create(c.vendor)
                    if (c.fields.isNotEmpty()) db.setFields(id, c.fields)
                    ExecResult("✅ وندور «${c.vendor}» ثبت شد.", id)
                }
                is Cmd.Update -> {
                    val id = p.vendorId
                    if (id == null) {
                        ExecResult("خطا: وندور مشخص نیست.", null)
                    } else {
                        if (c.fields.isNotEmpty()) db.setFields(id, c.fields)
                        val removed = if (c.remove.isNotEmpty()) db.removeFields(id, c.remove) else 0
                        ExecResult(
                            "✅ «${p.vendorName}» به‌روز شد." + if (c.remove.isNotEmpty()) " ($removed مورد حذف شد)" else "",
                            id
                        )
                    }
                }
                is Cmd.Delete -> {
                    val id = p.vendorId
                    if (id == null) {
                        ExecResult("خطا: وندور مشخص نیست.", null)
                    } else {
                        db.delete(id)
                        ExecResult("🗑️ وندور «${p.vendorName}» حذف شد.", null)
                    }
                }
                else -> ExecResult("خطا: دستور نامعتبر.", null)
            }
        }
        addText(r.text)
        val vid = r.vendorId
        if (vid != null) {
            val v = withContext(Dispatchers.IO) { db.get(vid) }
            if (v != null) {
                setCurrent(v.id, v.name)
                messages.add(Msg.Cards(listOf(v)))
            }
        } else if (p.cmd is Cmd.Delete && lastVendorId == p.vendorId) {
            lastVendorId = null
            lastVendorName = null
        }
    }

    private suspend fun converse(raw: String, n: String) {
        var ids = findVendors(raw)
        val last = lastVendorId
        if (ids.isEmpty() && last != null && Commands.mentionsField(n)) ids = listOf(last)
        if (ids.isNotEmpty()) {
            val first = ids.first()
            val nm = withContext(Dispatchers.IO) { db.get(first)?.name }
            if (nm != null) setCurrent(first, nm)
        }

        if (modelState != ModelState.READY) {
            if (ids.isNotEmpty()) {
                val list = withContext(Dispatchers.IO) { ids.mapNotNull { db.get(it) } }
                if (list.isNotEmpty()) {
                    messages.add(Msg.Cards(list))
                    return
                }
            }
            addText(
                if (modelState == ModelState.LOADING) "سلام! مدل زبانی در حال بارگذاریه؛ چند لحظه دیگه دوباره بپرس."
                else "سلام! برای گفتگوی هوشمند باید مدل زبانی فعال باشد (دکمهٔ ⚙️ بالای صفحه). تا اون موقع می‌تونی «لیست وندورها» یا «اطلاعات X» رو ببینی."
            )
            return
        }

        val withSecret = Commands.wantsSecret(n)
        val (data, names, total) = withContext(Dispatchers.IO) {
            val d = ids.mapNotNull { db.get(it) }.joinToString("\n\n") { Prompts.block(it, withSecret) }
            Triple(d, db.names().map { it.name }, db.count())
        }
        val system = Prompts.chatSystem(total, names, lastVendorName, data, recentHistory())

        typing()
        var acc = ""
        try {
            llm.ask(system, raw).collect { chunk ->
                acc += chunk
                setLastText(acc)
            }
        } catch (e: Exception) {
            setLastText("خطا: " + (e.message ?: e.toString()))
            return
        }
        if (acc.isBlank()) setLastText("متوجه نشدم، می‌شه یک بار دیگه بگی؟")
    }

    override fun onCleared() {
        if (llmLazy.isInitialized()) llm.close()
    }
}
