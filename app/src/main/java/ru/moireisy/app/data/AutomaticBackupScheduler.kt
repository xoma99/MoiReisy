package ru.moireisy.app.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object AutomaticBackupScheduler {
    private const val UNIQUE_NAME = "moireisy_daily_backup"

    fun setEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        prefs.edit().putBoolean(AutomaticBackupWorker.KEY_ENABLED, enabled).apply()
        if (enabled) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .build()
            val request = PeriodicWorkRequestBuilder<AutomaticBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_NAME)
        }
    }

    fun sync(context: Context) {
        val enabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(AutomaticBackupWorker.KEY_ENABLED, false)
        setEnabled(context, enabled)
    }
}
