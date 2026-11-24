package com.davide.salvacibo.domain

import java.text.Normalizer
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

/**
 * Legge il testo riconosciuto dall'OCR su un'etichetta e ne estrae:
 *  - la data di scadenza (scegliendo tra più date quella giusta, es. non quella di produzione)
 *  - il tipo: "da consumarsi entro" oppure "preferibilmente entro" (TMC)
 *  - i giorni entro cui consumare dopo l'apertura ("una volta aperto consumare entro 3 giorni")
 *
 * È puro Kotlin, senza dipendenze Android, così si può testare facilmente.
 */
object LabelParser {

    data class DateCandidate(
        val date: LocalDate,
        val score: Int,
        val type: ExpiryType?,
        val raw: String,
    )

    data class Result(
        val expiry: LocalDate?,
        val type: ExpiryType?,
        val daysAfterOpening: Int?,
        /** affidabilità della data scelta: >= 5 vuol dire che c'era una parola chiave tipo "scad." vicino */
        val score: Int,
        val candidates: List<DateCandidate>,
    )

    // ---------- Parole chiave (già normalizzate: maiuscolo, senza accenti) ----------

    private val EXPIRY_KW = listOf(
        "DA CONSUMARSI", "CONSUMARSI", "CONSUMARE", "PREFERIBILMENTE", "SCADENZA", "SCAD", "ENTRO",
        "TMC", "EXPIRY", "EXP", "BEST BEFORE", "BBE", "USE BY", "MHD", "DLC", "DDM", "CONSOMMER", "CONSUMIR",
    )
    private val BEST_BEFORE_KW = listOf(
        "PREFERIBILMENTE", "PREF.", "TMC", "BEST BEFORE", "BBE", "MHD", "DDM", "DE PREFERENCE", "PREFERENTEMENTE",
    )
    private val USE_BY_KW = listOf(
        "DA CONSUMARSI ENTRO", "CONSUMARSI ENTRO", "USE BY", "SCADENZA", "SCAD", "DLC", "CONSOMMER JUSQU",
    )
    private val PRODUCTION_KW = listOf(
        "PRODUZIONE", "PRODOTTO IL", "PROD", "CONFEZIONA", "CONFEZ", "PACKED", "IMBALLA", "LOTTO", "LOT",
        "CONGELATO IL", "CONGELAMENTO", "CONGELAZIONE", "MACELLA", "SEZIONA", "DEPOSIZ", "DATA DI PESCA", "PESCATO IL",
    )

    private val MONTHS: Map<String, Int> = mapOf(
        "GENNAIO" to 1, "GEN" to 1, "JANUARY" to 1, "JAN" to 1,
        "FEBBRAIO" to 2, "FEB" to 2, "FEBRUARY" to 2,
        "MARZO" to 3, "MAR" to 3, "MARCH" to 3,
        "APRILE" to 4, "APR" to 4, "APRIL" to 4,
        "MAGGIO" to 5, "MAG" to 5, "MAY" to 5,
        "GIUGNO" to 6, "GIU" to 6, "JUNE" to 6, "JUN" to 6,
        "LUGLIO" to 7, "LUG" to 7, "JULY" to 7, "JUL" to 7,
        "AGOSTO" to 8, "AGO" to 8, "AUGUST" to 8, "AUG" to 8,
        "SETTEMBRE" to 9, "SETT" to 9, "SET" to 9, "SEPTEMBER" to 9, "SEPT" to 9, "SEP" to 9,
        "OTTOBRE" to 10, "OTT" to 10, "OCTOBER" to 10, "OCT" to 10,
        "NOVEMBRE" to 11, "NOV" to 11, "NOVEMBER" to 11,
        "DICEMBRE" to 12, "DIC" to 12, "DECEMBER" to 12, "DEC" to 12,
    )
    private val MONTH_ALT = MONTHS.keys.sortedByDescending { it.length }.joinToString("|")

    // ---------- Espressioni regolari per le date ----------

    /** 22/10/2026, 22.10.26, 22-10-2026, 22 / 10 / 26 (stesso separatore) */
    private val DMY = Regex("""(?<!\d)(\d{1,2})\s?([./\-,])\s?(\d{1,2})\s?\2\s?(\d{4}|\d{2})(?!\d)""")
    /** 22 10 2026 (stampanti a punti) */
    private val DMY_SPACE = Regex("""(?<![\d./\-,])(\d{1,2}) (\d{1,2}) (\d{4}|\d{2})(?![\d./\-,])""")
    /** 2026-10-22 */
    private val YMD = Regex("""(?<!\d)(20\d{2})([./\-])(\d{1,2})\2(\d{1,2})(?!\d)""")
    /** 221026 oppure 22102026 (solo vicino a una parola chiave) */
    private val COMPACT = Regex("""(?<!\d)(\d{2})(\d{2})(20\d{2}|\d{2})(?!\d)""")
    /** 12 OTT 2026, 12OTT26, OTT 2026, 12/OTT/26 */
    private val DAY_MONTHNAME = Regex(
        """(?<![A-Z0-9])(?:(\d{1,2})\s?[./\-]?\s?)?($MONTH_ALT)(?![A-Z])\.?\s?[./\-]?\s?(\d{4}|\d{2})(?!\d)"""
    )
    /** 10/2026 o 10.26 (fine mese) */
    private val MONTH_YEAR = Regex("""(?<![\d./\-,])(\d{1,2})\s?([./\-])\s?(20\d{2}|\d{2})(?![\d./\-,])""")

    // ---------- Giorni dopo l'apertura ----------

    private val NUMBER_WORDS = mapOf(
        "UN" to 1, "UNO" to 1, "UNA" to 1, "DUE" to 2, "TRE" to 3, "QUATTRO" to 4, "CINQUE" to 5,
        "SEI" to 6, "SETTE" to 7, "OTTO" to 8, "NOVE" to 9, "DIECI" to 10, "DODICI" to 12,
        "QUATTORDICI" to 14, "QUINDICI" to 15, "VENTI" to 20, "TRENTA" to 30,
    )
    private val NUM = "(\\d{1,3}|" + NUMBER_WORDS.keys.sortedByDescending { it.length }.joinToString("|") + ")"
    private const val UNIT =
        "(GIORNI|GIORNO|GG|SETTIMANE|SETTIMANA|MESI|MESE|DAYS|DAY|WEEKS|WEEK|MONTHS|MONTH|JOURS|JOUR|TAGEN|TAGE)"
    private const val OPEN = "(APERTURA|APERTO|APERTA|APERTI|APERTE|OPENING|OPENED|OUVERTURE|OFFNEN)"

    private val OPEN_THEN_NUM = Regex("""\b$OPEN\b.{0,70}?\b$NUM\s?$UNIT\b""")
    private val NUM_THEN_OPEN = Regex("""\b$NUM\s?$UNIT\b.{0,40}?\b$OPEN\b""")

    // ---------- API ----------

    fun parse(rawText: String, today: LocalDate = LocalDate.now()): Result {
        val text = normalize(rawText)
        val candidates = findDates(text, today)
        val best = candidates.maxWithOrNull(compareBy<DateCandidate> { it.score }.thenBy { it.date })
        val type = best?.type ?: detectType(text)
        return Result(
            expiry = best?.date,
            type = type,
            daysAfterOpening = findDaysAfterOpening(text),
            score = best?.score ?: Int.MIN_VALUE,
            candidates = candidates.sortedByDescending { it.score },
        )
    }

    /** maiuscolo, niente accenti, spazi singoli, e correzione degli errori OCR tipici nei numeri (O→0, l→1) */
    fun normalize(raw: String): String {
        val fixedDigits = Regex("""(?<![A-Za-z])[0-9OoIl|./\-,]+(?![A-Za-z])""").replace(raw) { m ->
            val token = m.value
            if (token.count { it.isDigit() } >= 2) {
                token.map {
                    when (it) {
                        'O', 'o' -> '0'
                        'I', 'l', '|' -> '1'
                        else -> it
                    }
                }.joinToString("")
            } else token
        }
        val noAccents = Normalizer.normalize(fixedDigits, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return noAccents.uppercase(Locale.ROOT)
            .replace('’', '\'').replace('`', '\'').replace('´', '\'')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun findDaysAfterOpening(normalizedText: String): Int? {
        val matches = (OPEN_THEN_NUM.findAll(normalizedText).map { it to (2 to 3) } +
            NUM_THEN_OPEN.findAll(normalizedText).map { it to (1 to 2) })
            .sortedBy { (m, _) -> m.value.length }
        for ((m, idx) in matches) {
            val numText = m.groupValues[idx.first]
            val unit = m.groupValues[idx.second]
            val n = numText.toIntOrNull() ?: NUMBER_WORDS[numText] ?: continue
            val days = when {
                unit.startsWith("SETTIMAN") || unit.startsWith("WEEK") -> n * 7
                unit.startsWith("MES") || unit.startsWith("MONTH") -> n * 30
                else -> n
            }
            if (days in 1..730) return days
        }
        return null
    }

    private fun detectType(text: String): ExpiryType? = when {
        BEST_BEFORE_KW.any { text.contains(it) } -> ExpiryType.BEST_BEFORE
        USE_BY_KW.any { text.contains(it) } -> ExpiryType.USE_BY
        else -> null
    }

    // ---------- Estrazione e punteggio delle date ----------

    private fun findDates(text: String, today: LocalDate): List<DateCandidate> {
        val out = mutableListOf<DateCandidate>()
        val taken = mutableListOf<IntRange>()

        fun overlaps(r: IntRange) = taken.any { it.first <= r.last && r.first <= it.last }

        fun add(m: MatchResult, date: LocalDate?, trust: Int, needsKeyword: Boolean = false) {
            if (date == null || overlaps(m.range)) return
            if (date.year < today.year - 3 || date.year > today.year + 12) return
            val ctx = context(text, m.range.first)
            if (needsKeyword && !ctx.hasExpiryKeyword) return
            var score = trust + ctx.keywordScore
            score += if (date.isBefore(today.minusDays(1))) -4 else 1
            out += DateCandidate(date, score, ctx.type, m.value)
            taken += m.range
        }

        for (m in YMD.findAll(text)) {
            val (y, _, mo, d) = m.destructured
            add(m, dateOrNull(y.toInt(), mo.toInt(), d.toInt()), trust = 0)
        }
        for (m in DMY.findAll(text)) {
            val (d, _, mo, y) = m.destructured
            val year = fullYear(y)
            val normal = dateOrNull(year, mo.toInt(), d.toInt())
            if (normal != null) add(m, normal, trust = 0)
            else add(m, dateOrNull(year, d.toInt(), mo.toInt()), trust = -2) // formato americano MM/DD
        }
        for (m in DAY_MONTHNAME.findAll(text)) {
            val day = m.groupValues[1]
            val month = MONTHS[m.groupValues[2]] ?: continue
            val year = fullYear(m.groupValues[3])
            if (day.isNotEmpty()) {
                add(m, dateOrNull(year, month, day.toInt()), trust = 0)
            } else {
                add(m, endOfMonth(year, month), trust = -1)
            }
        }
        for (m in DMY_SPACE.findAll(text)) {
            val (d, mo, y) = m.destructured
            add(m, dateOrNull(fullYear(y), mo.toInt(), d.toInt()), trust = -1, needsKeyword = y.length == 2)
        }
        for (m in COMPACT.findAll(text)) {
            val (d, mo, y) = m.destructured
            add(m, dateOrNull(fullYear(y), mo.toInt(), d.toInt()), trust = -1, needsKeyword = true)
        }
        for (m in MONTH_YEAR.findAll(text)) {
            val (mo, _, y) = m.destructured
            add(m, endOfMonth(fullYear(y), mo.toInt()), trust = -1, needsKeyword = y.length == 2)
        }
        return out
    }

    private class Ctx(val hasExpiryKeyword: Boolean, val keywordScore: Int, val type: ExpiryType?)

    /** Guarda i ~45 caratteri prima della data: c'è "scad." vicino? O "prod." / "lotto"? */
    private fun context(text: String, start: Int): Ctx {
        val before = text.substring(maxOf(0, start - 45), start)
        val expiry = lastKeyword(before, EXPIRY_KW)
        val prod = lastKeyword(before, PRODUCTION_KW) ?: Regex("""\bL\s?[.:]\s?$""").find(before)?.let { before.length - 1 }
        var score = 0
        val hasExpiry = expiry != null
        if (expiry != null) {
            val distance = before.length - expiry
            score += when {
                distance <= 12 -> 6
                distance <= 25 -> 5
                else -> 3
            }
        }
        // la parola "prod/lotto" più vicina alla data della parola "scad" → probabilmente è la data di produzione
        if (prod != null && (expiry == null || prod > expiry)) score -= 7
        val window = before
        val type = when {
            BEST_BEFORE_KW.any { window.contains(it) } -> ExpiryType.BEST_BEFORE
            USE_BY_KW.any { window.contains(it) } -> ExpiryType.USE_BY
            else -> null
        }
        return Ctx(hasExpiry && (prod == null || prod < expiry!!), score, type)
    }

    /** posizione (fine) dell'ultima parola chiave trovata nel testo, oppure null */
    private fun lastKeyword(s: String, keywords: List<String>): Int? =
        keywords.mapNotNull { kw ->
            val i = s.lastIndexOf(kw)
            if (i >= 0) i + kw.length else null
        }.maxOrNull()

    private fun fullYear(y: String): Int = if (y.length == 2) 2000 + y.toInt() else y.toInt()

    private fun dateOrNull(y: Int, m: Int, d: Int): LocalDate? =
        runCatching { LocalDate.of(y, m, d) }.getOrNull()

    private fun endOfMonth(y: Int, m: Int): LocalDate? =
        runCatching { YearMonth.of(y, m).atEndOfMonth() }.getOrNull()
}
