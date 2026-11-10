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
import com.example.vendorchat.chat.ActionDsl
import com.example.vendorchat.chat.Commands
import com.example.vendorchat.chat.Msg
import com.example.vendorchat.chat.NameMatch
import com.example.vendorchat.chat.Prompts
import com.example.vendorchat.chat.Tone
import com.example.vendorchat.chat.VendorAction
import com.example.vendorchat.data.Backup
import com.example.vendorchat.data.Db
import com.example.vendorchat.data.Fa
import com.example.vendorchat.data.ImportPlan
import com.example.vendorchat.data.Importer
import com.example.vendorchat.data.Sheet
import com.example.vendorchat.data.Template
import com.example.vendorchat.data.VendorData
import com.example.vendorchat.data.XlsxWriter
import com.example.vendorchat.llm.LlmManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class ModelState { NONE, LOADING, READY, ERROR }

/** کارهای گروهی منتظر تأیید (ورود از اکسل / بازیابی پشتیبان). */
sealed class PendingTask {
    data class Import(val plan: ImportPlan) : PendingTask()
    data class Restore(val vendors: List<VendorData>) : PendingTask()
}

private data class Resolved(val vendorId: Long?, val name: String, val action: VendorAction, val isNew: Boolean)
private data class PendingBatch(val items: List<Resolved>)
private data class UndoEntry(val createdId: Long?, val snapshot: VendorData?)

class ChatViewModel(private val app: Application) : AndroidViewModel(app) {
    private val db by lazy { Db(app) }
    private val llmLazy = lazy { LlmManager(app) }
    private val llm: LlmManager get() = llmLazy.value
    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val messages = mutableStateListOf<Msg>()

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
    var tone by mutableStateOf(Tone.clamp(prefs.getInt("tone", Tone.CASUAL)))
        private set

    private var batch: PendingBatch? = null
    private var task: PendingTask? = null
    private var lastUndo: List<UndoEntry>? = null
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

    fun changeTone(t: Int) {
        tone = Tone.clamp(t)
        prefs.edit().putInt("tone", tone).apply()
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
            batch = null
            task = null
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

    private fun hasPending() = batch != null || task != null

    // ---------------- ارسال و تأیید ----------------
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

    /** انتخاب یک چیپ (اسم وندور یا «برگردان»). */
    fun onChip(name: String) {
        if (name.startsWith("↩")) send("برگردان") else send("اطلاعات «$name»")
    }

    fun confirm() {
        if (busy || !hasPending()) return
        val b = batch
        val t = task
        batch = null
        task = null
        closeAction(1)
        busy = true
        viewModelScope.launch {
            try {
                if (t != null) runTask(t) else if (b != null) execute(b)
            } catch (e: Exception) {
                dropTyping()
                addText("خطا: " + (e.message ?: e.toString()))
            } finally {
                busy = false
            }
        }
    }

    fun cancel() {
        if (!hasPending()) return
        batch = null
        task = null
        closeAction(2)
        addText(Tone.cancelled(tone))
    }

    private suspend fun handle(raw: String) {
        val n = Fa.normalize(raw)

        // ۱) تأیید/لغو متنی
        if (hasPending()) {
            val b = batch
            val t = task
            batch = null
            task = null
            if (Commands.isYes(n)) {
                closeAction(1)
                if (t != null) runTask(t) else if (b != null) execute(b)
                return
            }
            if (Commands.isNo(n)) {
                closeAction(2)
                addText(Tone.cancelled(tone))
                return
            }
            closeAction(2) // پیام جدید: درخواست قبلی لغو می‌شود
        }

        // ۲) برگردان
        if (Commands.wantsUndo(n)) {
            doUndo()
            return
        }

        // ۳) لیست و تعداد (بدون مدل، دقیق)
        if (Commands.wantsList(n)) {
            val all = withContext(Dispatchers.IO) { db.names() }
            if (all.isEmpty()) addText(Tone.emptyDb(tone))
            else messages.add(Msg.Chips("${all.size} وندور ثبت شده؛ برای دیدن اطلاعات روی نام بزن", all.take(40).map { it.name }))
            return
        }
        if (Commands.wantsCount(n)) {
            addText("تعداد وندورهای ثبت‌شده: ${withContext(Dispatchers.IO) { db.count() }}")
            return
        }

        // ۴) نمایش سریع کارت (بدون مدل)
        if (Commands.wantsShow(n) && showCards(raw)) return

        // ۵) گفتگو (مدل تصمیم می‌گیرد)
        if (modelState != ModelState.READY) {
            offline(raw, n)
            return
        }
        converse(raw, n)
    }

    // ---------------- پیدا کردن وندور ----------------
    private suspend fun findVendors(raw: String, limit: Int = 3): List<Long> = withContext(Dispatchers.IO) {
        val all = db.names()
        val byName = NameMatch.rank(raw, all).filter { it.score >= 2 }
        if (byName.isNotEmpty()) {
            val top = byName.first().score
            byName.filter { it.score == top }.take(limit).map { it.id }
        } else {
            db.search(raw, limit).map { it.id }
        }
    }

    private suspend fun showIds(ids: List<Long>, title: String): Boolean {
        if (ids.isEmpty()) return false
        if (ids.size <= 3) {
            val list = withContext(Dispatchers.IO) { ids.mapNotNull { db.get(it) } }
            if (list.isEmpty()) return false
            setCurrent(list.first().id, list.first().name)
            messages.add(Msg.Cards(list))
        } else {
            val names = withContext(Dispatchers.IO) { db.names().filter { it.id in ids }.map { it.name } }
            messages.add(Msg.Chips(title, names))
        }
        return true
    }

    private suspend fun showCards(raw: String): Boolean {
        var ids = findVendors(raw, 12)
        val last = lastVendorId
        if (ids.isEmpty() && Fa.tokens(raw).isEmpty() && last != null) ids = listOf(last)
        return showIds(ids, "چند وندور پیدا شد؛ روی نام بزن")
    }

    private suspend fun offline(raw: String, n: String) {
        val guess = ActionDsl.guess(raw)
        if (guess != null && lastVendorId != null && !Commands.isQuestion(raw, n)) {
            prepare(listOf(guess))
            return
        }
        val ids = findVendors(raw)
        if (ids.isNotEmpty() && showIds(ids, "چند وندور پیدا شد؛ روی نام بزن")) return
        addText(if (modelState == ModelState.LOADING) Tone.loading(tone) else Tone.noModel(tone))
    }

    // ---------------- گفتگوی اصلی ----------------
    private suspend fun converse(raw: String, n: String) {
        var ids = findVendors(raw)
        val last = lastVendorId
        if (ids.isEmpty() && last != null) ids = listOf(last)
        val withSecret = Commands.wantsSecret(n)
        val ctx = withContext(Dispatchers.IO) {
            val d = ids.mapNotNull { db.get(it) }.joinToString("\n\n") { Prompts.block(it, withSecret) }
            Triple(d, db.names().map { it.name }, db.count())
        }
        val system = Prompts.chatSystem(tone, ctx.third, ctx.second, lastVendorName, ctx.first, recentHistory())

        typing()
        var acc = ""
        try {
            llm.ask(system, raw, 0.3).collect { chunk ->
                acc += chunk
                setLastText(ActionDsl.visible(acc))
            }
        } catch (e: Exception) {
            setLastText("خطا: " + (e.message ?: e.toString()))
            return
        }

        val parsed = ActionDsl.parse(acc)
        var actions = parsed.actions
        val reply = parsed.visible

        // مرحلهٔ کمکی: اگر مدل بلوک ننوشت ولی پیام شبیه دادهٔ قابل ثبت است
        if (actions.isEmpty() && Commands.looksLikeData(raw, n)) {
            val names = withContext(Dispatchers.IO) { db.names().map { it.name } }
            val out = try {
                llm.complete(Prompts.extractSystem(names, lastVendorName), "پیام: $raw")
            } catch (e: Exception) {
                ""
            }
            if (!out.trim().startsWith("NONE")) {
                var found = ActionDsl.parse(out).actions
                if (found.isEmpty()) found = ActionDsl.parseBlock(out)
                actions = found
            }
            if (actions.isEmpty()) {
                val g = ActionDsl.guess(raw)
                if (g != null && lastVendorId != null && !Commands.isQuestion(raw, n)) actions = listOf(g)
            }
        }

        val mutating = actions.any { it.mutating }
        if (reply.isBlank()) {
            // فقط بلوک عمل بود
            if (mutating) setLastText(leadFor(tone)) else dropTyping()
        } else if (actions.isEmpty() && ActionDsl.claimsSaved(reply)) {
            setLastText(reply + "\n\n(ولی هنوز چیزی ذخیره نشده؛ اگه می‌خوای ثبت بشه، یه بار واضح‌تر بگو.)")
        } else {
            setLastText(reply)
        }
        if (actions.isNotEmpty()) dispatch(actions)
    }

    private fun leadFor(t: Int) = when (Tone.clamp(t)) {
        Tone.FORMAL -> "موارد زیر آماده شد؛ لطفاً تأیید کنید."
        Tone.FRIENDLY -> "این‌ها رو آماده کردم، تأیید کن 👇"
        else -> "آماده‌ش کردم، یه نگاه بنداز و تأیید کن 👇"
    }

    // ---------------- اجرای درخواست‌های مدل ----------------
    private suspend fun dispatch(actions: List<VendorAction>) {
        for (a in actions.filter { !it.mutating }) runRead(a)
        val mut = actions.filter { it.mutating }
        if (mut.isNotEmpty()) prepare(mut)
    }

    private suspend fun runRead(a: VendorAction) {
        val show = a.show
        val search = a.search
        if (show != null) {
            val ids: List<Long> = if (show == "@current") {
                listOfNotNull(lastVendorId)
            } else {
                val all = withContext(Dispatchers.IO) { db.names() }
                val r = NameMatch.rank(show, all).filter { it.score >= 2 }
                if (r.isNotEmpty()) r.filter { it.score == r.first().score }.take(12).map { it.id } else findVendors(show, 12)
            }
            if (!showIds(ids, "چند وندور شبیه این هست؛ روی نام بزن")) addText(Tone.noMatch(tone))
        } else if (search != null) {
            val ids = withContext(Dispatchers.IO) { db.search(search, 30).map { it.id } }
            if (ids.isEmpty()) addText(Tone.noMatch(tone))
            else showIds(ids.take(30), "${ids.size} وندور پیدا شد؛ روی نام بزن")
        }
    }

    /** وندور هدف هر تغییر را پیدا می‌کند و کارت تأیید را نشان می‌دهد. */
    private suspend fun prepare(mut: List<VendorAction>) {
        val names = withContext(Dispatchers.IO) { db.names() }
        val items = ArrayList<Resolved>()
        for (a in mut) {
            val target = a.vendor
            if (target.isEmpty()) {
                val id = lastVendorId
                val nm = lastVendorName
                if (id != null && nm != null && names.any { it.id == id }) {
                    items.add(Resolved(id, nm, a, false))
                    continue
                }
                addText(Tone.askVendor(tone))
                return
            }
            if (a.create) {
                val exact = NameMatch.rank(target, names).firstOrNull { it.score >= 3 }
                if (exact != null) items.add(Resolved(exact.id, exact.name, a.copy(create = false), false))
                else items.add(Resolved(null, target, a, true))
                continue
            }
            when (val r = NameMatch.resolve(target, names)) {
                is NameMatch.Resolved.One -> items.add(Resolved(r.hit.id, r.hit.name, a, false))
                is NameMatch.Resolved.Many -> {
                    messages.add(Msg.Chips("چند وندور شبیه «$target» هست؛ کدوم رو می‌گی؟ (اسمش رو دقیق‌تر بنویس)", r.hits.map { it.name }))
                    return
                }
                is NameMatch.Resolved.None -> {
                    if (a.delete) {
                        addText("وندوری با نام «$target» پیدا نشد.")
                        return
                    }
                    items.add(Resolved(null, target, a.copy(create = true), true))
                }
            }
        }
        if (items.isEmpty()) return
        startPending(PendingBatch(items))
    }

    private fun startPending(b: PendingBatch) {
        if (hasPending()) {
            batch = null
            task = null
            closeAction(2)
        }
        batch = b
        val items = b.items
        val allDelete = items.all { it.action.delete }
        val kind = if (allDelete) "delete" else if (items.size == 1 && items[0].isNew) "add" else "update"
        val title = if (items.size == 1) {
            val r = items[0]
            when {
                r.action.delete -> "حذف «${r.name}»"
                r.isNew -> "ثبت وندور جدید: «${r.name}»"
                else -> "ویرایش «${r.name}»"
            }
        } else "${items.size} تغییر روی وندورها"
        val lines = ArrayList<String>()
        for (r in items) {
            if (items.size > 1) lines.add("— ${r.name}${if (r.isNew) " (جدید)" else ""} —")
            lines.addAll(ActionDsl.describe(r.action).ifEmpty { listOf("بدون تغییر") })
        }
        val shown = if (lines.size > 16) lines.take(16) + listOf("...") else lines
        messages.add(Msg.Action(kind, title, shown, 0))
    }

    private suspend fun execute(b: PendingBatch) {
        class Out(val undo: List<UndoEntry>, val touched: List<VendorData>, val c: Int, val u: Int, val d: Int)
        val out = withContext(Dispatchers.IO) {
            val undo = ArrayList<UndoEntry>()
            val touched = ArrayList<Long>()
            var c = 0
            var u = 0
            var d = 0
            for (r in b.items) {
                val vid = r.vendorId
                val a = r.action
                if (a.delete && vid != null) {
                    val snap = db.get(vid)
                    db.delete(vid)
                    if (snap != null) undo.add(UndoEntry(null, snap))
                    d++
                } else if (vid == null) {
                    val id = db.create(r.name)
                    db.apply(id, a)
                    undo.add(UndoEntry(id, null))
                    touched.add(id)
                    c++
                } else {
                    val snap = db.get(vid)
                    db.apply(vid, a)
                    if (snap != null) undo.add(UndoEntry(null, snap))
                    touched.add(vid)
                    u++
                }
            }
            Out(undo, touched.distinct().mapNotNull { db.get(it) }, c, u, d)
        }
        lastUndo = out.undo.ifEmpty { null }
        val parts = ArrayList<String>()
        if (out.c > 0) parts.add("${out.c} وندور جدید")
        if (out.u > 0) parts.add("${out.u} وندور به‌روز")
        if (out.d > 0) parts.add("${out.d} وندور حذف")
        addText("✅ " + Tone.done(tone).removeSuffix(" ✅") + (if (parts.isNotEmpty()) " (" + parts.joinToString("، ") + ")" else ""))
        if (out.touched.isNotEmpty()) {
            setCurrent(out.touched.last().id, out.touched.last().name)
            messages.add(Msg.Cards(out.touched))
        } else if (out.d > 0 && lastVendorId != null) {
            lastVendorId = null
            lastVendorName = null
        }
        if (lastUndo != null) messages.add(Msg.Chips("اشتباه شد؟", listOf("↩ برگردان")))
    }

    private suspend fun doUndo() {
        val u = lastUndo
        if (u == null || u.isEmpty()) {
            addText(Tone.nothingToUndo(tone))
            return
        }
        lastUndo = null
        val restored = withContext(Dispatchers.IO) {
            val ids = ArrayList<Long>()
            for (e in u.reversed()) {
                val created = e.createdId
                val snap = e.snapshot
                if (created != null) {
                    db.delete(created)
                } else if (snap != null) {
                    val exists = db.get(snap.id) != null
                    ids.add(db.restoreSnapshot(snap, if (exists) snap.id else null))
                }
            }
            ids.mapNotNull { db.get(it) }
        }
        addText(Tone.undone(tone))
        if (restored.isNotEmpty()) {
            setCurrent(restored.last().id, restored.last().name)
            messages.add(Msg.Cards(restored))
        } else {
            lastVendorId = null
            lastVendorName = null
        }
    }

    // ---------------- ورود از اکسل، پشتیبان‌گیری ----------------
    private fun launchOp(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                dropTyping()
                addText("خطا: " + (e.message ?: e.toString()))
            } finally {
                busy = false
            }
        }
    }

    private fun newTaskCard(kind: String, title: String, lines: List<String>, t: PendingTask) {
        if (hasPending()) {
            batch = null
            task = null
            closeAction(2)
        }
        task = t
        lastUndo = null
        messages.add(Msg.Action(kind, title, lines, 0))
    }

    /** چند وندور جدید و چند وندور موجود (هم‌نام) است. */
    private fun predict(names: List<String>): Pair<Int, Int> {
        val known = db.names().map { Fa.normalize(it.name) }.toHashSet()
        var created = 0
        var updated = 0
        for (nm in names.map { Fa.normalize(it) }.distinct()) {
            if (nm in known) updated++ else created++
        }
        return created to updated
    }

    /** ادغام: هم‌نام‌ها به‌روز می‌شوند، بقیه ساخته می‌شوند. */
    private fun merge(items: List<Pair<String, VendorAction>>): Pair<Int, Int> {
        val index = HashMap<String, Long>()
        db.names().forEach { index[Fa.normalize(it.name)] = it.id }
        var created = 0
        var updated = 0
        for ((name, action) in items) {
            val key = Fa.normalize(name)
            val existing = index[key]
            val id = existing ?: db.create(name).also { index[key] = it }
            if (existing == null) created++ else updated++
            db.apply(id, action)
        }
        return created to updated
    }

    fun importSheet(uri: Uri) = launchOp {
        typing()
        val plan = withContext(Dispatchers.IO) {
            val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) null else Importer.plan(Sheet.read(bytes))
        }
        dropTyping()
        if (plan == null) {
            addText("فایل خالی است یا قابل خواندن نیست. ردیف اول باید عنوان ستون‌ها باشد و از ردیف دوم هر ردیف یک وندور. قالب آماده را از تنظیمات (دریافت قالب اکسل) بگیر.")
            return@launchOp
        }
        val (created, updated) = withContext(Dispatchers.IO) { predict(plan.rows.map { it.name }) }
        val lines = ArrayList<String>()
        lines.add("ستون‌های شناسایی‌شده: " + plan.columns.take(14).joinToString("، ") + if (plan.columns.size > 14) " ..." else "")
        lines.add("$created وندور جدید ثبت می‌شود")
        if (updated > 0) lines.add("$updated وندور هم‌نام موجود است؛ اطلاعاتش ادغام و به‌روز می‌شود")
        if (plan.skipped > 0) lines.add("${plan.skipped} ردیف بدون نام نادیده گرفته شد")
        lines.add("نمونه: " + plan.rows.take(3).joinToString("، ") { it.name })
        newTaskCard("import", "ورود از اکسل: ${plan.rows.size} وندور", lines, PendingTask.Import(plan))
    }

    fun writeTemplate(uri: Uri) = launchOp {
        withContext(Dispatchers.IO) {
            val out = app.contentResolver.openOutputStream(uri, "wt") ?: error("نوشتن فایل ممکن نیست.")
            out.use { it.write(XlsxWriter.build(Template.sheets())) }
        }
        addText("✅ قالب اکسل ساخته شد. توش یک برگهٔ «وندورها» (با دو ردیف نمونه) و یک برگهٔ «راهنما» هست. ردیف‌های نمونه رو پاک کن، وندورهات رو بنویس و از تنظیمات ← «ورود از اکسل» بده به من.")
    }

    fun writeExcel(uri: Uri) = launchOp {
        typing()
        val n = withContext(Dispatchers.IO) {
            val vendors = db.all()
            val bytes = XlsxWriter.build(Backup.toTable(vendors))
            val out = app.contentResolver.openOutputStream(uri, "wt") ?: error("نوشتن فایل ممکن نیست.")
            out.use { it.write(bytes) }
            vendors.size
        }
        dropTyping()
        addText("✅ فایل اکسل ساخته شد ($n وندور). اطلاعات بانکی برای امنیت در اکسل نیست؛ برای آن‌ها از «پشتیبان‌گیری رمزدار» استفاده کن.")
    }

    fun writeBackup(uri: Uri, password: String) = launchOp {
        typing()
        val n = withContext(Dispatchers.IO) {
            val vendors = db.all()
            val bytes = Backup.encrypt(Backup.toJson(vendors), password.toCharArray())
            val out = app.contentResolver.openOutputStream(uri, "wt") ?: error("نوشتن فایل ممکن نیست.")
            out.use { it.write(bytes) }
            vendors.size
        }
        dropTyping()
        addText("✅ پشتیبان رمزدار ساخته شد ($n وندور).\nرمز را جای امنی نگه دار؛ بدون آن فایل قابل بازیابی نیست.")
    }

    fun readBackup(uri: Uri, password: String) = launchOp {
        typing()
        val vendors = withContext(Dispatchers.IO) {
            val bytes = app.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            val json = if (bytes == null) null else Backup.decrypt(bytes, password.toCharArray())
            if (json == null) null else Backup.fromJson(json)
        }
        dropTyping()
        if (vendors == null) {
            addText("❌ رمز اشتباه است یا فایل پشتیبان معتبر نیست.")
            return@launchOp
        }
        val (created, updated) = withContext(Dispatchers.IO) { predict(vendors.map { it.name }) }
        val lines = listOf(
            "${vendors.size} وندور داخل فایل پشتیبان هست",
            "$created وندور جدید ساخته می‌شود",
            "$updated وندور هم‌نام ادغام و به‌روز می‌شود",
            "چیزی از اطلاعات فعلی حذف نمی‌شود"
        )
        newTaskCard("restore", "بازیابی از پشتیبان", lines, PendingTask.Restore(vendors))
    }

    private suspend fun runTask(t: PendingTask) {
        typing()
        val text = withContext(Dispatchers.IO) {
            when (t) {
                is PendingTask.Import -> {
                    val (c, u) = merge(t.plan.rows.map { it.name to it.action })
                    "✅ ورود از اکسل انجام شد: $c وندور جدید، $u وندور به‌روز شد."
                }
                is PendingTask.Restore -> {
                    val (c, u) = merge(t.vendors.map { v -> v.name to Backup.toAction(v) })
                    "✅ بازیابی انجام شد: $c وندور جدید، $u وندور به‌روز شد."
                }
            }
        }
        dropTyping()
        addText(text)
    }

    override fun onCleared() {
        if (llmLazy.isInitialized()) llm.close()
    }
}
