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
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN parentId TEXT DEFAULT NULL")
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
        ).addMigrations(MIGRATION_1_2).build()
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
    fun provideTagDao(db: QuantifyDatabase) = db.tagDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
        return context.dataStore
    }
}
