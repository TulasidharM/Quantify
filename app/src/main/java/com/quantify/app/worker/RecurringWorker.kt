package com.quantify.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.quantify.app.data.repository.RecurringRuleRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class RecurringWorker @JvmOverloads constructor(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            val repo = getRepository()
            repo.processDueRules()
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun getRepository(): RecurringRuleRepository {
        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            RecurringRuleRepositoryEntryPoint::class.java
        )
        return entryPoint.recurringRuleRepository()
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface RecurringRuleRepositoryEntryPoint {
        fun recurringRuleRepository(): RecurringRuleRepository
    }
}
