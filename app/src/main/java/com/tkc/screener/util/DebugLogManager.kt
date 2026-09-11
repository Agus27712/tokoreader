package com.tkc.screener.util

import android.content.Context
import android.os.Build
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

enum class LogCategory {
    ALL,
    CONNECTION,
    USER_ACTION,
    TRADE,
    SYSTEM,
    ERROR
}

data class DebugLogEntry(
    val id: Long = System.currentTimeMillis() + (0..999).random(),
    val timestampMs: Long = System.currentTimeMillis(),
    val tag: String,
    val level: String, // "INFO", "WARN", "ERROR", "DEBUG", "USER", "CONN", "TRADE"
    val category: LogCategory,
    val message: String
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestampMs))
        }

    val formattedFullDateTime: String
        get() {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
            return sdf.format(Date(timestampMs))
        }
}

object DebugLogManager {
    private const val MAX_LOG_ENTRIES = 1500
    private val logQueue = ConcurrentLinkedQueue<DebugLogEntry>()

    private val _logsState = MutableStateFlow<List<DebugLogEntry>>(emptyList())
    val logsState: StateFlow<List<DebugLogEntry>> = _logsState.asStateFlow()

    init {
        // Log initialization
        log(
            tag = "DebugLogManager",
            level = "SYSTEM",
            category = LogCategory.SYSTEM,
            message = "In-App Debug Logger System initialized."
        )
    }

    fun initTimberTree() {
        Timber.plant(object : Timber.Tree() {
            override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
                val levelStr = when (priority) {
                    android.util.Log.VERBOSE -> "DEBUG"
                    android.util.Log.DEBUG -> "DEBUG"
                    android.util.Log.INFO -> "INFO"
                    android.util.Log.WARN -> "WARN"
                    android.util.Log.ERROR -> "ERROR"
                    android.util.Log.ASSERT -> "FATAL"
                    else -> "INFO"
                }

                val category = when {
                    priority >= android.util.Log.WARN || t != null -> LogCategory.ERROR
                    tag?.contains("WebSocket", ignoreCase = true) == true || message.contains("connection", ignoreCase = true) -> LogCategory.CONNECTION
                    message.contains("trade", ignoreCase = true) || message.contains("order", ignoreCase = true) -> LogCategory.TRADE
                    else -> LogCategory.SYSTEM
                }

                val fullMsg = if (t != null) "$message\nException: ${t.localizedMessage}" else message
                log(tag ?: "AppLog", levelStr, category, fullMsg)
            }
        })
    }

    @Synchronized
    fun log(tag: String, level: String, category: LogCategory, message: String) {
        val entry = DebugLogEntry(
            tag = tag,
            level = level,
            category = category,
            message = message
        )
        logQueue.add(entry)
        while (logQueue.size > MAX_LOG_ENTRIES) {
            logQueue.poll()
        }
        _logsState.value = logQueue.toList()
    }

    // Helper logging methods
    fun logConnection(mode: String, details: String) {
        log(
            tag = "KONEKSI",
            level = "CONN",
            category = LogCategory.CONNECTION,
            message = "[$mode] $details"
        )
    }

    fun logUserAction(action: String, details: String = "") {
        val msg = if (details.isNotEmpty()) "$action | $details" else action
        log(
            tag = "USER_ACTION",
            level = "USER",
            category = LogCategory.USER_ACTION,
            message = msg
        )
    }

    fun logTrade(mode: String, pair: String, type: String, price: Double, qty: Double, details: String = "") {
        val msg = "[$mode] $type $pair @ ${PriceFormatter.formatRawDecimal(price)} (Qty: $qty) $details".trim()
        log(
            tag = "TRADE_EXEC",
            level = "TRADE",
            category = LogCategory.TRADE,
            message = msg
        )
    }

    fun logError(tag: String, message: String, throwable: Throwable? = null) {
        val fullMsg = if (throwable != null) "$message (${throwable.localizedMessage})" else message
        log(
            tag = tag,
            level = "ERROR",
            category = LogCategory.ERROR,
            message = fullMsg
        )
    }

    fun clearLogs() {
        logQueue.clear()
        _logsState.value = emptyList()
        logSystem("Log buffer dibersihkan oleh pengguna.")
    }

    fun logSystem(msg: String) {
        log("SYSTEM", "INFO", LogCategory.SYSTEM, msg)
    }

    /**
     * Export log list formatted to SD Card / External Storage / Downloads folder.
     */
    fun exportLogsToStorage(context: Context): Result<File> {
        return try {
            val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "TKCScreener_DebugLog_$timestampStr.txt"

            val headerBuilder = StringBuilder().apply {
                append("=================================================================\n")
                append("          TKCSCREENER DEBUG OUTPUT LOGCAT EXPORT                 \n")
                append("=================================================================\n")
                append("Tanggal Ekspor : ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}\n")
                append("Device Model   : ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})\n")
                append("Total Baris Log: ${logQueue.size}\n")
                append("=================================================================\n\n")
            }

            for (entry in logQueue) {
                headerBuilder.append("[${entry.formattedFullDateTime}] [${entry.level}] [${entry.tag}] ${entry.message}\n")
            }

            val logContent = headerBuilder.toString()

            // Try saving to public Downloads folder first
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            var targetFile = File(downloadsDir, fileName)

            var success = false
            try {
                if (!downloadsDir.exists()) downloadsDir.mkdirs()
                FileOutputStream(targetFile).use { fos ->
                    fos.write(logContent.toByteArray(Charsets.UTF_8))
                }
                success = true
            } catch (_: Exception) {
                // Fallback to app's external files directory on SD Card
                val extFilesDir = context.getExternalFilesDir(null) ?: context.filesDir
                targetFile = File(extFilesDir, fileName)
                FileOutputStream(targetFile).use { fos ->
                    fos.write(logContent.toByteArray(Charsets.UTF_8))
                }
                success = true
            }

            if (success && targetFile.exists()) {
                logSystem("Log berhasil diekspor ke: ${targetFile.absolutePath}")
                Result.success(targetFile)
            } else {
                Result.failure(Exception("Gagal membuat file log di SD Card / Penyimpanan External."))
            }
        } catch (e: Exception) {
            logError("DebugLogManager", "Gagal meng-ekspor log", e)
            Result.failure(e)
        }
    }
}
