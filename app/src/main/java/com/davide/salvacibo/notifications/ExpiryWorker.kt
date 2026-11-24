package com.davide.salvacibo.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.davide.salvacibo.SalvaciboApp
import com.davide.salvacibo.data.categoryEnum
import com.davide.salvacibo.data.expiryInfo
import com.davide.salvacibo.data.type
import com.davide.salvacibo.domain.NotificationRules
import java.time.LocalDate

/**
 * Gira una volta al giorno (all'ora scelta nelle impostazioni) e manda una notifica
 * per ogni prodotto vicino alla scadenza: es. pollo che scade il 22 → avvisi il 20, 21 e 22.
 */
class ExpiryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as SalvaciboApp
        val settings = app.prefs.settings.value
        if (!settings.notificationsEnabled) return Result.success()

        val today = LocalDate.now()
        val force = inputData.getBoolean(KEY_FORCE, false)
        // evita doppioni se il sistema esegue il controllo due volte nello stesso giorno
        if (!force && app.prefs.lastCheckEpochDay == today.toEpochDay()) return Result.success()

        for (product in app.repository.getActive()) {
            val info = product.expiryInfo(settings, today)
            val msg = NotificationRules.message(product.name, product.categoryEnum.emoji, info, product.type)
                ?: continue
            Notifier.showProduct(applicationContext, product.id, msg.title, msg.body)
        }
        app.prefs.lastCheckEpochDay = today.toEpochDay()
        return Result.success()
    }

    companion object {
        const val KEY_FORCE = "force"
    }
}
