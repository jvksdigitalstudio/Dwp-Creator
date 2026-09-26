package com.jvk.dwpcreator

import android.app.Application
import android.content.Context.MODE_PRIVATE
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Diagnostic-only: captures ANY uncaught exception (main thread, background
 * thread, or a coroutine inside viewModelScope) and persists its full stack
 * trace to SharedPreferences before letting the crash proceed normally.
 *
 * MainActivity checks for a saved crash on next launch and displays it
 * full-screen instead of the normal UI, so a crash can be diagnosed from a
 * single screenshot without needing adb/logcat access.
 *
 * This is intentionally temporary scaffolding for early development builds
 * -- safe to remove once the app is stable.
 */
class DwpCreatorApplication : Application() {

    companion object {
        private const val PREFS_NAME = "crash_diagnostics"
        private const val KEY_LAST_CRASH = "last_crash"

        fun consumeLastCrash(app: Application): String? {
            val prefs = app.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val crash = prefs.getString(KEY_LAST_CRASH, null)
            if (crash != null) prefs.edit().remove(KEY_LAST_CRASH).apply()
            return crash
        }
    }

    override fun onCreate() {
        super.onCreate()
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAST_CRASH, "Thread: ${thread.name}\n\n$sw")
                    .commit()
            } catch (ignored: Exception) {
                // Never let the diagnostics themselves crash the crash handler.
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
