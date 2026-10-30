package com.quantify.app

import android.app.Application
import com.quantify.app.data.seed.SeedData
import com.quantify.app.util.RecurringScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch

@HiltAndroidApp
class QuantifyApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        CoroutineScope(Dispatchers.IO).launch {
            val seedData = SeedData()
            seedData.seedIfEmpty(this@QuantifyApplication)
        }
        RecurringScheduler.schedule(this)
    }
}
