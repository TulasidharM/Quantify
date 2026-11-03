package com.quantify.app.ui.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.quantify.app.data.repository.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream

class BackupManager(
    private val context: Context,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository,
    private val paymentMethodRepository: PaymentMethodRepository,
    private val budgetRepository: BudgetRepository,
    private val recurringRuleRepository: RecurringRuleRepository
) {
    suspend fun createBackupStream(): OutputStream {
        return withContext(Dispatchers.IO) {
            throw NotImplementedError("Use createBackupIntent() instead")
        }
    }

    fun createBackupIntent(): Intent {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "quantify_backup_${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}.json")
        }
        return intent
    }

    fun createRestoreIntent(): Intent {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        return intent
    }

    fun exportCsvIntent(): Intent {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "quantify_transactions.csv")
        }
        return intent
    }
}
