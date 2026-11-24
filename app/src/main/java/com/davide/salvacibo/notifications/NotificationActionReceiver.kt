package com.davide.salvacibo.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.davide.salvacibo.SalvaciboApp
import com.davide.salvacibo.domain.ProductStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Gestisce il pulsante "Consumato ✓" direttamente dalla notifica. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONSUMED) return
        val id = intent.getLongExtra(Notifier.EXTRA_PRODUCT_ID, -1L)
        if (id <= 0) return
        val pending = goAsync()
        val app = context.applicationContext as SalvaciboApp
        CoroutineScope(Dispatchers.IO).launch {
            try {
                app.repository.setStatus(id, ProductStatus.CONSUMED)
                Notifier.cancel(context, id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_CONSUMED = "com.davide.salvacibo.CONSUMED"
    }
}
