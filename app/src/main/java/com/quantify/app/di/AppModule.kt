package com.quantify.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.quantify.app.data.local.QuantifyDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN parentId TEXT DEFAULT NULL")
    }
}

private val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS tags")
        db.execSQL("CREATE TABLE transactions_backup (id TEXT NOT NULL, amount REAL NOT NULL, type TEXT NOT NULL, categoryId TEXT NOT NULL, paymentMethodId TEXT, description TEXT, dateEpochSeconds INTEGER NOT NULL, createdAtEpochSeconds INTEGER NOT NULL, recurringRuleId TEXT, PRIMARY KEY(id))")
        db.execSQL("INSERT INTO transactions_backup (id, amount, type, categoryId, paymentMethodId, description, dateEpochSeconds, createdAtEpochSeconds, recurringRuleId) SELECT id, amount, type, categoryId, paymentMethodId, description, dateEpochSeconds, createdAtEpochSeconds, recurringRuleId FROM transactions")
        db.execSQL("DROP TABLE transactions")
        db.execSQL("ALTER TABLE transactions_backup RENAME TO transactions")
        db.execSQL("CREATE TABLE recurring_rules_backup (id TEXT NOT NULL, amount REAL NOT NULL, type TEXT NOT NULL, categoryId TEXT NOT NULL, paymentMethodId TEXT, description TEXT, frequency TEXT NOT NULL, dayOfMonth INTEGER, dayOfWeek INTEGER, nextRunDateEpochSeconds INTEGER NOT NULL, isActive INTEGER NOT NULL, createdAtEpochSeconds INTEGER NOT NULL, PRIMARY KEY(id))")
        db.execSQL("INSERT INTO recurring_rules_backup SELECT id, amount, type, categoryId, paymentMethodId, description, frequency, dayOfMonth, dayOfWeek, nextRunDateEpochSeconds, isActive, createdAtEpochSeconds FROM recurring_rules")
        db.execSQL("DROP TABLE recurring_rules")
        db.execSQL("ALTER TABLE recurring_rules_backup RENAME TO recurring_rules")
    }
}

private val MIGRATION_3_4: Migration = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE wishlist_items (id TEXT NOT NULL, name TEXT NOT NULL, targetAmount REAL NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id))")
    }
}

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): QuantifyDatabase {
        return Room.databaseBuilder(
            context,
            QuantifyDatabase::class.java,
            "quantify_database"
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build()
    }

    @Provides
    fun provideTransactionDao(db: QuantifyDatabase) = db.transactionDao()

    @Provides
    fun provideCategoryDao(db: QuantifyDatabase) = db.categoryDao()

    @Provides
    fun providePaymentMethodDao(db: QuantifyDatabase) = db.paymentMethodDao()

    @Provides
    fun provideBudgetDao(db: QuantifyDatabase) = db.budgetDao()

    @Provides
    fun provideRecurringRuleDao(db: QuantifyDatabase) = db.recurringRuleDao()

    @Provides
    fun provideWishlistItemDao(db: QuantifyDatabase) = db.wishlistItemDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob())
    }
}
