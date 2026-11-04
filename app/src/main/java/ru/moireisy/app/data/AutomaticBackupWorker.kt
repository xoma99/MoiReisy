package ru.moireisy.app.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import java.io.File

/** Daily offline backup. Keeps the seven newest successful archives. */
class AutomaticBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return Result.success()
        return try {
            val dir = File(applicationContext.filesDir, "automatic_backups").apply { mkdirs() }
            val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date())
            val target = File(dir, "MoiReisy_auto_$stamp.zip")
            BackupManager.exportToFile(applicationContext, target)
            dir.listFiles { f -> f.isFile && f.name.endsWith(".zip") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(7)?.forEach { it.delete() }
            prefs.edit().putLong(KEY_LAST_BACKUP, System.currentTimeMillis()).apply()
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val KEY_ENABLED = "automatic_backup_enabled"
        const val KEY_LAST_BACKUP = "automatic_backup_last_at"
    }
}
