package com.example.vendorchat

import android.app.Activity
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File

/** صفحهٔ شروع ساده (بدون Compose): اگر اجرای قبلی کرش کرده باشد گزارش نشان می‌دهد، وگرنه مستقیم وارد برنامه می‌شود. */
class DiagActivity : Activity() {

    private fun reasonName(r: Int) = when (r) {
        ApplicationExitInfo.REASON_CRASH -> "CRASH (Java)"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        ApplicationExitInfo.REASON_ANR -> "ANR"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "INIT_FAILURE"
        ApplicationExitInfo.REASON_SIGNALED -> "SIGNALED"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "LOW_MEMORY"
        ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        ApplicationExitInfo.REASON_USER_REQUESTED -> "USER_REQUESTED"
        ApplicationExitInfo.REASON_EXIT_SELF -> "EXIT_SELF"
        else -> "OTHER($r)"
    }

    private fun isBad(r: Int) = r == ApplicationExitInfo.REASON_CRASH ||
        r == ApplicationExitInfo.REASON_CRASH_NATIVE ||
        r == ApplicationExitInfo.REASON_ANR ||
        r == ApplicationExitInfo.REASON_INITIALIZATION_FAILURE ||
        r == ApplicationExitInfo.REASON_SIGNALED ||
        r == ApplicationExitInfo.REASON_LOW_MEMORY ||
        r == ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE ||
        r == ApplicationExitInfo.REASON_DEPENDENCY_DIED

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("diag", Context.MODE_PRIVATE)
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val exits: List<ApplicationExitInfo> = try {
            am.getHistoricalProcessExitReasons(null, 0, 5)
        } catch (e: Throwable) {
            emptyList()
        }
        val seen = prefs.getLong("seen", 0L)
        val bad = exits.filter { it.timestamp > seen && isBad(it.reason) }
        val crashFile = File(filesDir, "crash.txt")

        if (bad.isEmpty() && !crashFile.exists()) {
            goMain()
            return
        }

        val tv = TextView(this).apply {
            text = "در حال جمع‌آوری گزارش..."
            textSize = 11f
            typeface = Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        val copy = Button(this).apply { text = "کپی گزارش" }
        val go = Button(this).apply { text = "ورود به برنامه" }
        val scroll = ScrollView(this).apply { addView(tv) }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(copy)
            addView(go)
            addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        col.setOnApplyWindowInsetsListener { v: View, ins: WindowInsets ->
            val b = ins.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(b.left + 24, b.top + 24, b.right + 24, b.bottom + 24)
            ins
        }
        setContentView(col)

        go.setOnClickListener {
            prefs.edit().putLong("seen", System.currentTimeMillis()).apply()
            crashFile.delete()
            goMain()
        }

        Thread {
            val sb = StringBuilder()
            sb.append("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})\n\n")
            sb.append("== Exit reasons ==\n")
            for (e in exits.take(3)) {
                sb.append("${reasonName(e.reason)} | status=${e.status} | ${e.description}\n")
            }
            if (crashFile.exists()) {
                sb.append("\n== crash.txt ==\n").append(crashFile.readText().take(3000)).append("\n")
            }
            sb.append("\n== logcat ==\n").append(readLog())
            val report = sb.toString()
            runOnUiThread {
                tv.text = report
                copy.setOnClickListener {
                    val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("report", report))
                }
            }
        }.start()
    }

    private fun readLog(): String = try {
        val p = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "800", "-v", "time"))
        val lines = p.inputStream.bufferedReader().readLines()
        p.destroy()
        val idx = lines.indexOfLast { it.contains("FATAL EXCEPTION") || it.contains("Fatal signal") }
        val pick = if (idx >= 0) {
            lines.subList(maxOf(0, idx - 3), minOf(lines.size, idx + 45))
        } else {
            lines.filter { it.contains(" E/") || it.contains(" F/") || it.contains("vendorchat") }.takeLast(60)
        }
        pick.joinToString("\n").take(6000)
    } catch (t: Throwable) {
        "logcat error: $t"
    }
}
