package com.quantify.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.quantify.app.data.local.dao.*
import com.quantify.app.data.local.entity.*

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        PaymentMethodEntity::class,
        BudgetEntity::class,
        RecurringRuleEntity::class,
        TagEntity::class
    ],
    version = 2,
    exportSchema = false,
    autoMigrations = []
)
abstract class QuantifyDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun tagDao(): TagDao

    companion object {
        @Volatile
        private var INSTANCE: QuantifyDatabase? = null

        fun getInstance(context: Context): QuantifyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    QuantifyDatabase::class.java,
                    "quantify_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
