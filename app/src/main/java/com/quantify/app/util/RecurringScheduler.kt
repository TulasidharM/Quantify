package com.quantify.app.util

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.quantify.app.worker.RecurringWorker
import java.util.concurrent.TimeUnit

object RecurringScheduler {

    private const val TAG = "recurring_transaction_worker"

    fun schedule(context: Context) {
        val work = PeriodicWorkRequest.Builder(
            RecurringWorker::class.java,
            6,
            TimeUnit.HOURS
        ).setConstraints(
            Constraints.Builder()
                .build()
        ).build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            work
        )
    }
}
