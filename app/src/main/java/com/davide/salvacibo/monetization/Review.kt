package com.davide.salvacibo.monetization

import android.app.Activity
import com.davide.salvacibo.data.Prefs
import com.google.android.play.core.review.ReviewManagerFactory

/**
 * Chiede una recensione con la finestrella ufficiale di Google Play, una sola volta,
 * quando l'app ha già dimostrato di essere utile (qualche prodotto salvato).
 * Google decide se mostrarla davvero: non c'è modo di forzarla.
 */
object Review {

    private const val MIN_SAVED_PRODUCTS = 3

    fun askIfAppropriate(activity: Activity, prefs: Prefs, savedProducts: Int) {
        if (prefs.reviewAsked || savedProducts < MIN_SAVED_PRODUCTS) return
        prefs.reviewAsked = true
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful) {
                manager.launchReviewFlow(activity, task.result)
            }
        }
    }
}
