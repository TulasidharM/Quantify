package com.quantify.app.data.local

import androidx.room.Database
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
        WishlistItemEntity::class
    ],
    version = 4,
    exportSchema = false,
    autoMigrations = []
)
abstract class QuantifyDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun budgetDao(): BudgetDao
    abstract fun recurringRuleDao(): RecurringRuleDao
    abstract fun wishlistItemDao(): WishlistItemDao
}
