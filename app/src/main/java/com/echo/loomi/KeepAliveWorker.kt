package com.echo.loomi

import android.content.Context
import android.content.Intent
import androidx.work.*
import java.util.concurrent.TimeUnit

class KeepAliveWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val serviceIntent = Intent(applicationContext, MessageListenerService::class.java)
        try {
            applicationContext.startService(serviceIntent)
        } catch (e: Exception) {
            // Might fail in background on newer Android versions
        }
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<KeepAliveWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "LoomiKeepAlive",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
