package com.davide.salvacibo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ExpiryAndCategoryTest {

    private val settings = Settings(warnDaysUseBy = 2, warnDaysBestBefore = 3, warnDaysOpened = 2)

    /** L'esempio del pollo: scade il 22, avvisi il 20, 21 e 22. */
    @Test
    fun polloNotificaDueGiorniPrimaFinoAlGiornoStesso() {
        val expiry = LocalDate.of(2026, 10, 22)
        val notified = (15..24).filter { day ->
            val info = ExpiryCalc.info(expiry, ExpiryType.USE_BY, null, null, LocalDate.of(2026, 10, day), settings)
            NotificationRules.message("Pollo", "🍗", info, ExpiryType.USE_BY) != null
        }
        // 20, 21, 22 + un ultimo avviso "scaduto ieri" il 23
        assertEquals(listOf(20, 21, 22, 23), notified)
    }

    /** Marmellata che scade nel 2028: aperta oggi con 30 giorni, la data che conta è tra 30 giorni. */
    @Test
    fun marmellataApertaUsaIlContoAllaRovescia() {
        val today = LocalDate.of(2026, 10, 22)
        val info = ExpiryCalc.info(LocalDate.of(2028, 5, 1), ExpiryType.BEST_BEFORE, today, 30, today, settings)
        assertTrue(info.limitedByOpening)
        assertEquals(LocalDate.of(2026, 11, 21), info.effectiveExpiry)
        assertEquals(30L, info.daysLeft)
        assertEquals(Urgency.OK, info.urgency)

        val later = LocalDate.of(2026, 11, 20)
        val infoLater = ExpiryCalc.info(LocalDate.of(2028, 5, 1), ExpiryType.BEST_BEFORE, today, 30, later, settings)
        assertEquals(Urgency.SOON, infoLater.urgency)
        val msg = NotificationRules.message("Marmellata", "🍯", infoLater, ExpiryType.BEST_BEFORE)
        assertNotNull(msg)
        assertTrue(msg!!.title.contains("aperto"))
    }

    @Test
    fun seLaScadenzaStampataArrivaPrimaVinceQuella() {
        val today = LocalDate.of(2026, 10, 22)
        val info = ExpiryCalc.info(LocalDate.of(2026, 10, 24), ExpiryType.USE_BY, today, 5, today, settings)
        assertEquals(LocalDate.of(2026, 10, 24), info.effectiveExpiry)
        assertTrue(!info.limitedByOpening)
    }

    @Test
    fun nienteNotificheLontanoDallaScadenza() {
        val today = LocalDate.of(2026, 10, 22)
        val info = ExpiryCalc.info(LocalDate.of(2026, 11, 22), ExpiryType.USE_BY, null, null, today, settings)
        assertNull(NotificationRules.message("Latte", "🥛", info, ExpiryType.USE_BY))
    }

    @Test
    fun categorie() {
        assertEquals(Category.CARNE, CategoryGuesser.guess("Petto di pollo a fette").category)
        assertEquals(Category.CONSERVE, CategoryGuesser.guess("Tonno all'olio di oliva").category)
        assertEquals(Category.PESCE, CategoryGuesser.guess("Filetti di merluzzo").category)
        assertEquals(Category.BEVANDE, CategoryGuesser.guess("Latte di soia bio").category)
        assertEquals(Category.LATTICINI, CategoryGuesser.guess("Latte fresco intero").category)
        assertEquals(Category.FORMAGGI, CategoryGuesser.guess("Mozzarella di bufala").category)
        assertEquals(2, CategoryGuesser.guess("Mozzarella di bufala").daysAfterOpening)
        assertEquals(Category.MARMELLATE, CategoryGuesser.guess("Confettura extra di albicocche").category)
        assertEquals(30, CategoryGuesser.guess("Confettura extra di albicocche").daysAfterOpening)
        assertEquals(Category.SUGHI, CategoryGuesser.guess("Passata di pomodoro").category)
        assertEquals(5, CategoryGuesser.guess("Pesto alla Genovese").daysAfterOpening)
        assertEquals(Category.VERDURA, CategoryGuesser.guess("Insalata mista in busta").category)
        assertEquals(Category.PRONTI, CategoryGuesser.guess("Insalata di riso").category)
        assertEquals(Category.DOLCI, CategoryGuesser.guess("Biscotti al cioccolato").category)
        assertEquals(Category.SALUMI, CategoryGuesser.guess("Prosciutto cotto alta qualità").category)
        assertEquals(Category.CONSERVE, CategoryGuesser.guess("Rio Mare tonno", listOf("en:canned-foods", "en:canned-fishes")).category)
        assertEquals(Category.MARMELLATE, CategoryGuesser.guess("Bonne Maman", listOf("en:spreads", "en:jams")).category)
        assertEquals(Category.ALTRO, CategoryGuesser.guess("Xyz 123").category)
    }
}
