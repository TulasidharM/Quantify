package com.quantify.app

import android.app.Application
import com.quantify.app.data.seed.SeedData
import com.quantify.app.util.RecurringScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class QuantifyApplication : Application() {

    @Inject lateinit var seedData: SeedData
    @Inject lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            seedData.seedIfEmpty()
        }
        RecurringScheduler.schedule(this)
    }
}
