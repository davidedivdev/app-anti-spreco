package com.davide.salvacibo

/**
 * Dati da personalizzare prima di pubblicare.
 * Gli ID dei prodotti devono essere IDENTICI a quelli creati in Play Console
 * (Monetizza › Prodotti › Prodotti in-app).
 */
object AppConfig {

    /**
     * DA COMPILARE: email pubblica di contatto (la stessa della scheda Play Store).
     * Viene mostrata nella schermata "Supporta e info" e inviata a Open Food Facts nello User-Agent,
     * come chiedono le loro regole d'uso. Se resta vuota la riga di contatto non compare.
     */
    const val SUPPORT_EMAIL = ""

    /**
     * DA COMPILARE: indirizzo della pagina pubblica con l'informativa privacy
     * (es. una pagina Google Sites con il testo di docs/privacy-policy.html).
     */
    const val PRIVACY_POLICY_URL = ""

    /** Acquisti "una tantum": ognuno toglie le pubblicità per sempre. */
    val SUPPORT_PRODUCTS = listOf(
        SupportProduct("caffe", "☕", "Un caffè", "Toglie le pubblicità per sempre"),
        SupportProduct("pizza", "🍕", "Una pizza", "Per chi vuole dare una mano in più"),
        SupportProduct("cena", "🍝", "Una cena", "Grazie di cuore: aiuti lo sviluppo"),
    )

    fun playStoreUrl(packageName: String) = "https://play.google.com/store/apps/details?id=$packageName"
}

data class SupportProduct(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
)
