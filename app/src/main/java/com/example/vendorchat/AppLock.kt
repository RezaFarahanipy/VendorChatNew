package com.example.vendorchat

import android.app.Activity
import android.content.Context
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.CancellationSignal

/** قفل برنامه با اثر انگشت یا پین/الگوی گوشی (بدون کتابخانهٔ اضافه). */
object AppLock {
    private const val KEY = "lock"
    private const val AUTH = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun enabled(ctx: Context): Boolean =
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(ctx: Context, v: Boolean) {
        ctx.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean(KEY, v).commit()
    }

    /** آیا گوشی پین/الگو/اثر انگشت دارد؟ */
    fun available(ctx: Context): Boolean {
        val bm = ctx.getSystemService(BiometricManager::class.java) ?: return false
        return bm.canAuthenticate(AUTH) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun prompt(activity: Activity, title: String, onOk: () -> Unit, onFail: (String) -> Unit) {
        try {
            val p = BiometricPrompt.Builder(activity)
                .setTitle(title)
                .setAllowedAuthenticators(AUTH)
                .build()
            p.authenticate(
                CancellationSignal(),
                activity.mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        onOk()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        onFail(errString.toString())
                    }
                }
            )
        } catch (e: Exception) {
            onFail(e.message ?: "خطا")
        }
    }
}
