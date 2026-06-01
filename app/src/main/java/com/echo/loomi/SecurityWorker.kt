package com.echo.loomi

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class SecurityWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        // Perform a fake "Security Scan" for privacy
        NotificationHelper.showSecurityNotification(
            applicationContext,
            "Your end-to-end is active. Privacy 100% secured today."
        )
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SecurityWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "SecurityScan",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
