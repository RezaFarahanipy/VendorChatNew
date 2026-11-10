package com.example.vendorchat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.vendorchat.ui.AppTheme
import com.example.vendorchat.ui.ChatScreen
import com.example.vendorchat.ui.LockScreen

class MainActivity : ComponentActivity() {
    private val chatVm: ChatViewModel by viewModels()
    private var locked by mutableStateOf(false)
    private var lastStop = 0L
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // نوار وضعیت روی هدر گرادیانی است، پس آیکن‌ها همیشه روشن باشند
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        locked = AppLock.enabled(this)
        setContent {
            AppTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Box(Modifier.fillMaxSize().imePadding()) {
                        if (locked) LockScreen(onUnlock = { unlock() }) else ChatScreen(chatVm)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (AppLock.enabled(this)) {
            if (!locked && lastStop != 0L && System.currentTimeMillis() - lastStop > 20_000L) locked = true
            if (locked) unlock()
        } else {
            locked = false
        }
    }

    override fun onStop() {
        super.onStop()
        lastStop = System.currentTimeMillis()
    }

    private fun unlock() {
        if (prompting) return
        prompting = true
        AppLock.prompt(
            this, "باز کردن دستیار وندور",
            onOk = {
                prompting = false
                locked = false
            },
            onFail = { prompting = false }
        )
    }
}
