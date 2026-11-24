package com.davide.salvacibo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.davide.salvacibo.monetization.Ads
import com.davide.salvacibo.notifications.Notifier
import com.davide.salvacibo.ui.AppNavHost
import com.davide.salvacibo.ui.theme.SalvaciboTheme

class MainActivity : ComponentActivity() {

    private val app get() = application as SalvaciboApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // se l'app è stata aperta da una notifica, mostra quel prodotto (solo al primo avvio, non dopo una rotazione)
        val fromNotification = if (savedInstanceState == null) {
            intent.getLongExtra(Notifier.EXTRA_PRODUCT_ID, -1L).takeIf { it > 0 }
        } else null

        // pubblicità: prima il consenso privacy (UE), poi gli annunci. Chi ha offerto un caffè non le vede.
        if (BuildConfig.ADS_ENABLED && !app.billing.state.value.isSupporter) {
            Ads.gatherConsent(this)
        }

        setContent {
            SalvaciboTheme {
                AppNavHost(openProductId = fromNotification)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // aggiorna acquisti (es. pagamento in attesa confermato mentre l'app era chiusa)
        app.billing.start()
    }
}
