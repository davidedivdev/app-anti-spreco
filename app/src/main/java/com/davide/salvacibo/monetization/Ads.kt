package com.davide.salvacibo.monetization

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import com.davide.salvacibo.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pubblicità AdMob + consenso privacy (GDPR) con la piattaforma UMP di Google.
 * In Europa gli annunci si caricano solo dopo il modulo di consenso.
 */
object Ads {

    private val _canRequestAds = MutableStateFlow(false)
    /** true quando il consenso è stato gestito e si possono chiedere annunci */
    val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _privacyOptionsRequired = MutableStateFlow(false)
    /** true se bisogna offrire il pulsante "Gestisci consenso" (utenti UE) */
    val privacyOptionsRequired: StateFlow<Boolean> = _privacyOptionsRequired.asStateFlow()

    private val initialized = AtomicBoolean(false)

    /** Da chiamare all'avvio dell'activity (solo se l'utente vede le pubblicità). */
    fun gatherConsent(activity: Activity) {
        if (!BuildConfig.ADS_ENABLED) return
        val info = UserMessagingPlatform.getConsentInformation(activity)
        val params = ConsentRequestParameters.Builder().build()
        info.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { _ ->
                    updateState(activity, info)
                }
            },
            { _ -> updateState(activity, info) },
        )
        // se il consenso era già stato dato in un avvio precedente si può partire subito
        updateState(activity, info)
    }

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { _ ->
            updateState(activity, UserMessagingPlatform.getConsentInformation(activity))
        }
    }

    private fun updateState(context: Context, info: ConsentInformation) {
        _privacyOptionsRequired.value =
            info.privacyOptionsRequirementStatus == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
        if (info.canRequestAds()) {
            initialize(context.applicationContext)
            _canRequestAds.value = true
        }
    }

    private fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        CoroutineScope(Dispatchers.IO).launch {
            MobileAds.initialize(context) { }
        }
    }
}

/** Banner adattivo a tutta larghezza. Va mostrato solo se [Ads.canRequestAds] e l'utente non è sostenitore. */
@Composable
fun BannerAd(modifier: Modifier = Modifier) {
    val widthDp = LocalConfiguration.current.screenWidthDp
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = { ctx ->
            AdView(ctx).apply {
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(ctx, widthDp))
                adUnitId = BuildConfig.ADMOB_BANNER_ID
                loadAd(AdRequest.Builder().build())
            }
        },
        onRelease = { it.destroy() },
    )
}
