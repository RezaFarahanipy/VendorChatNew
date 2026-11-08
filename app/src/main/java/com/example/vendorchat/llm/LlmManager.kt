package com.example.vendorchat.llm

import android.app.Application
import android.net.Uri
import android.provider.OpenableColumns
import com.google.ai.edge.litertlm.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/** مدیریت فایل مدل .litertlm و موتور LiteRT-LM. */
class LlmManager(private val app: Application) {
    private var engine: Engine? = null

    private val modelFile: File get() = File(app.filesDir, "model.litertlm")

    fun hasModel(): Boolean = modelFile.exists() && modelFile.length() > 50_000_000L

    /** کپی فایل انتخاب‌شده به حافظهٔ داخلی اپ (موتور به مسیر فایل نیاز دارد). */
    suspend fun importModel(uri: Uri, onProgress: (Float) -> Unit) {
        withContext(Dispatchers.IO) {
            val cr = app.contentResolver
            var total = -1L
            cr.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) total = c.getLong(0)
            }
            if (total > 0 && app.filesDir.usableSpace < total + 100_000_000L) {
                error("فضای خالی گوشی کافی نیست.")
            }
            val tmp = File(app.filesDir, "model.tmp")
            val input = cr.openInputStream(uri) ?: error("فایل قابل خواندن نیست.")
            input.use { ins ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(1 shl 20)
                    var done = 0L
                    while (true) {
                        val n = ins.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) onProgress(done.toFloat() / total)
                    }
                }
            }
            engine?.close()
            engine = null
            if (modelFile.exists()) modelFile.delete()
            if (!tmp.renameTo(modelFile)) error("ذخیرهٔ مدل ناموفق بود.")
        }
    }

    suspend fun load(useGpu: Boolean): Result<Unit> = withContext(Dispatchers.Default) {
        runCatching {
            engine?.close()
            engine = null
            engine = if (useGpu) {
                try {
                    create(Backend.GPU())
                } catch (t: Throwable) {
                    create(Backend.CPU())
                }
            } else {
                create(Backend.CPU())
            }
        }
    }

    private fun create(backend: Backend): Engine {
        val e = Engine(EngineConfig(modelPath = modelFile.absolutePath, backend = backend))
        e.initialize()
        return e
    }

    /** هر پرسش یک Conversation تازه با system prompt می‌گیرد (حافظهٔ مدل کوچک است). */
    fun ask(system: String, question: String, temperature: Double = 0.3): Flow<String> = flow {
        val e = engine ?: error("مدل بارگذاری نشده است.")
        val conv = e.createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(system),
                samplerConfig = SamplerConfig(topK = 10, topP = 0.95, temperature = temperature)
            )
        )
        try {
            conv.sendMessageAsync(question).collect { emit(it.toString()) }
        } finally {
            conv.close()
        }
    }.flowOn(Dispatchers.Default)

    /** پاسخ کامل در یک رشته (برای تبدیل پیام به دستور). */
    suspend fun complete(system: String, question: String, temperature: Double = 0.1): String {
        val sb = StringBuilder()
        ask(system, question, temperature).collect { sb.append(it) }
        return sb.toString()
    }

    fun deleteModel() {
        engine?.close()
        engine = null
        modelFile.delete()
    }

    fun close() {
        engine?.close()
        engine = null
    }
}
