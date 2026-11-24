package com.davide.salvacibo

import android.app.Application
import com.davide.salvacibo.data.AppDatabase
import com.davide.salvacibo.data.Prefs
import com.davide.salvacibo.data.ProductRepository
import com.davide.salvacibo.monetization.BillingManager
import com.davide.salvacibo.notifications.Notifier
import com.davide.salvacibo.notifications.WorkScheduler
import java.time.LocalDate
import java.time.LocalTime

class SalvaciboApp : Application() {

    lateinit var repository: ProductRepository
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var billing: BillingManager
        private set

    override fun onCreate() {
        super.onCreate()
        repository = ProductRepository(AppDatabase.create(this).productDao())
        prefs = Prefs(this)
        billing = BillingManager(this, prefs)
        Notifier.createChannel(this)

        val settings = prefs.settings.value
        WorkScheduler.scheduleDaily(this, settings.notifyHour, replace = false)
        // Se apri l'app dopo l'ora degli avvisi e oggi il controllo non è ancora partito, fallo ora.
        if (LocalTime.now().hour >= settings.notifyHour &&
            prefs.lastCheckEpochDay != LocalDate.now().toEpochDay()
        ) {
            WorkScheduler.runNow(this, force = false)
        }
    }
}
