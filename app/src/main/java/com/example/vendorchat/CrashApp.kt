package com.example.vendorchat

import android.app.Application
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** ثبت متن کرش‌های Java در فایل تا صفحهٔ تشخیص بتواند نشانش بدهد. */
class CrashApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val sw = StringWriter()
                e.printStackTrace(PrintWriter(sw))
                File(filesDir, "crash.txt").writeText("thread: ${t.name}\n$sw")
            } catch (_: Throwable) {
            }
            old?.uncaughtException(t, e)
        }
    }
}
