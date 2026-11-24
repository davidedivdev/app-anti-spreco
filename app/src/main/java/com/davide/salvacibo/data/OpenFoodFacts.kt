package com.davide.salvacibo.data

import com.davide.salvacibo.AppConfig
import com.davide.salvacibo.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Dati base di un prodotto trovati sul database aperto Open Food Facts. */
data class OffProduct(
    val name: String?,
    val brand: String?,
    val categoryTags: List<String>,
    val imageUrl: String?,
)

object OpenFoodFacts {

    private const val FIELDS =
        "product_name,product_name_it,generic_name_it,brands,categories_tags,image_front_url,image_url"

    /** Cerca il codice a barre. Restituisce null se non trovato o senza connessione. */
    suspend fun lookup(barcode: String): OffProduct? = withContext(Dispatchers.IO) {
        val code = barcode.filter { it.isDigit() }
        if (code.length < 6) return@withContext null
        runCatching {
            val url = URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=$FIELDS&lc=it")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.setRequestProperty("User-Agent", userAgent())
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) return@runCatching null
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parse(body)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    /** Open Food Facts chiede "NomeApp/Versione (contatto)". */
    private fun userAgent(): String {
        val contact = AppConfig.SUPPORT_EMAIL.ifBlank { "Android" }
        return "Salvacibo/${BuildConfig.VERSION_NAME} ($contact)"
    }

    private fun parse(body: String): OffProduct? {
        val json = JSONObject(body)
        if (json.optInt("status", 0) != 1) return null
        val p = json.optJSONObject("product") ?: return null
        fun str(key: String) = p.optString(key, "").trim().takeIf { it.isNotEmpty() }
        val tagsArray = p.optJSONArray("categories_tags")
        val tags = buildList {
            if (tagsArray != null) for (i in 0 until tagsArray.length()) add(tagsArray.optString(i))
        }
        val brand = str("brands")?.split(',')?.firstOrNull()?.trim()
        return OffProduct(
            name = str("product_name_it") ?: str("product_name") ?: str("generic_name_it"),
            brand = brand,
            categoryTags = tags,
            imageUrl = str("image_front_url") ?: str("image_url"),
        )
    }
}
