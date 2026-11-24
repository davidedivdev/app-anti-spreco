package com.davide.salvacibo.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** "Da consumarsi entro" (rigida) oppure "Da consumarsi preferibilmente entro" (TMC, flessibile). */
enum class ExpiryType(val label: String, val short: String) {
    USE_BY("Da consumarsi entro", "Scadenza"),
    BEST_BEFORE("Preferibilmente entro", "TMC");

    companion object {
        fun fromName(name: String?): ExpiryType = entries.firstOrNull { it.name == name } ?: USE_BY
    }
}

enum class Urgency { EXPIRED, TODAY, SOON, OK }

enum class ProductStatus { ACTIVE, CONSUMED, THROWN }

/** Impostazioni che influenzano quando avvisare. */
data class Settings(
    val notificationsEnabled: Boolean = true,
    val notifyHour: Int = 9,
    val warnDaysUseBy: Int = 2,
    val warnDaysBestBefore: Int = 3,
    val warnDaysOpened: Int = 2,
)

/**
 * Stato di scadenza di un prodotto in un dato giorno.
 * La data che conta è la più vicina tra la scadenza stampata e (apertura + giorni dopo l'apertura).
 */
data class ExpiryInfo(
    val printedExpiry: LocalDate,
    val openedUntil: LocalDate?,
    val effectiveExpiry: LocalDate,
    val daysLeft: Long,
    /** true se a decidere è il conto alla rovescia dell'apertura e non la data stampata */
    val limitedByOpening: Boolean,
    val urgency: Urgency,
    val warnDays: Int,
)

object ExpiryCalc {

    fun openedUntil(opened: LocalDate?, daysAfterOpening: Int?): LocalDate? =
        if (opened != null && daysAfterOpening != null) opened.plusDays(daysAfterOpening.toLong()) else null

    fun info(
        printedExpiry: LocalDate,
        type: ExpiryType,
        opened: LocalDate?,
        daysAfterOpening: Int?,
        today: LocalDate,
        settings: Settings,
    ): ExpiryInfo {
        val until = openedUntil(opened, daysAfterOpening)
        val limitedByOpening = until != null && until.isBefore(printedExpiry)
        val effective = if (limitedByOpening) until!! else printedExpiry
        val daysLeft = ChronoUnit.DAYS.between(today, effective)
        val warnDays = when {
            limitedByOpening -> settings.warnDaysOpened
            type == ExpiryType.BEST_BEFORE -> settings.warnDaysBestBefore
            else -> settings.warnDaysUseBy
        }
        val urgency = when {
            daysLeft < 0 -> Urgency.EXPIRED
            daysLeft == 0L -> Urgency.TODAY
            daysLeft <= warnDays -> Urgency.SOON
            else -> Urgency.OK
        }
        return ExpiryInfo(printedExpiry, until, effective, daysLeft, limitedByOpening, urgency, warnDays)
    }

    private val dayFormatter = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN)
    private val shortFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ITALIAN)

    fun formatLong(date: LocalDate): String = dayFormatter.format(date)
    fun formatShort(date: LocalDate): String = shortFormatter.format(date)

    /** "oggi", "domani", "tra 3 giorni", "ieri", "da 4 giorni" */
    fun relative(daysLeft: Long): String = when {
        daysLeft == 0L -> "oggi"
        daysLeft == 1L -> "domani"
        daysLeft == -1L -> "ieri"
        daysLeft > 1 -> "tra $daysLeft giorni"
        else -> "da ${-daysLeft} giorni"
    }

    /** Testo breve per la lista: "Scade tra 2 giorni", "Scaduto ieri", ... */
    fun statusLine(info: ExpiryInfo, type: ExpiryType): String {
        val d = info.daysLeft
        val verb = "Scade"
        return when {
            d > 1 && info.limitedByOpening -> "Da consumare entro $d giorni"
            d > 1 -> "$verb tra $d giorni"
            d == 1L -> "$verb domani"
            d == 0L -> if (info.limitedByOpening) "Da consumare oggi" else "$verb oggi"
            d == -1L -> if (type == ExpiryType.BEST_BEFORE && !info.limitedByOpening) "TMC superato ieri" else "Scaduto ieri"
            else -> if (type == ExpiryType.BEST_BEFORE && !info.limitedByOpening) "TMC superato da ${-d} giorni" else "Scaduto da ${-d} giorni"
        }
    }
}

/** Testi delle notifiche. Restituisce null quando non serve avvisare. */
object NotificationRules {

    data class Message(val title: String, val body: String)

    fun message(name: String, emoji: String, info: ExpiryInfo, type: ExpiryType): Message? {
        val d = info.daysLeft
        // Avvisa ogni giorno da [warnDays] giorni prima fino al giorno stesso, più un ultimo avviso il giorno dopo.
        if (d > info.warnDays || d < -1) return null
        val title = when {
            info.limitedByOpening && d > 1 -> "$emoji $name aperto: consumalo entro $d giorni"
            info.limitedByOpening && d == 1L -> "$emoji $name aperto: consumalo entro domani"
            info.limitedByOpening && d == 0L -> "$emoji $name aperto: consumalo oggi"
            d > 1 -> "$emoji $name scade tra $d giorni"
            d == 1L -> "$emoji $name scade domani"
            d == 0L -> "⚠️ $name scade oggi"
            else -> "$emoji $name è scaduto ieri"
        }
        val body = when {
            d < 0 && type == ExpiryType.BEST_BEFORE && !info.limitedByOpening ->
                "Era \"preferibilmente entro\": controlla aspetto e odore, spesso è ancora buono."
            d < 0 -> "Se l'hai già consumato, segnalo come fatto."
            info.limitedByOpening -> "Una volta aperto va consumato entro il ${ExpiryCalc.formatShort(info.effectiveExpiry)}."
            type == ExpiryType.BEST_BEFORE -> "Da consumarsi preferibilmente entro il ${ExpiryCalc.formatShort(info.effectiveExpiry)}."
            else -> "Da consumarsi entro il ${ExpiryCalc.formatShort(info.effectiveExpiry)}. Mettilo in programma!"
        }
        return Message(title, body)
    }
}
