package com.davide.salvacibo.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LabelParserTest {

    private val today = LocalDate.of(2026, 10, 22)
    private fun parse(s: String) = LabelParser.parse(s, today)

    @Test
    fun dataClassicaDaConsumarsiEntro() {
        val r = parse("Da consumarsi entro il 22/10/2026")
        assertEquals(LocalDate.of(2026, 10, 22), r.expiry)
        assertEquals(ExpiryType.USE_BY, r.type)
    }

    @Test
    fun preferibilmenteConPuntiEAnnoACifreQuattro() {
        val r = parse("Da consumarsi preferibilmente entro: 31.12.2027\nL2345A")
        assertEquals(LocalDate.of(2027, 12, 31), r.expiry)
        assertEquals(ExpiryType.BEST_BEFORE, r.type)
    }

    @Test
    fun soloMeseEAnnoDiventaFineMese() {
        val r = parse("TMC 12/2027")
        assertEquals(LocalDate.of(2027, 12, 31), r.expiry)
        assertEquals(ExpiryType.BEST_BEFORE, r.type)
    }

    @Test
    fun ignoraLaDataDiProduzione() {
        val r = parse("Data di confezionamento 20/10/2026 Da consumarsi entro 26/10/2026")
        assertEquals(LocalDate.of(2026, 10, 26), r.expiry)
    }

    @Test
    fun produzioneDopoLaScadenzaNelTesto() {
        val r = parse("SCAD 30/10/26 PROD 18/10/26")
        assertEquals(LocalDate.of(2026, 10, 30), r.expiry)
    }

    @Test
    fun correggeErroriOcr() {
        val r = parse("scad. 2O/1O/2O26")
        assertEquals(LocalDate.of(2026, 10, 20), r.expiry)
    }

    @Test
    fun dataCompattaVicinoAScad() {
        val r = parse("SCAD 241026 L 1234")
        assertEquals(LocalDate.of(2026, 10, 24), r.expiry)
    }

    @Test
    fun meseScrittoInLettere() {
        assertEquals(LocalDate.of(2026, 11, 12), parse("EXP 12 NOV 2026").expiry)
        assertEquals(LocalDate.of(2027, 10, 31), parse("preferibilmente entro fine OTT 2027").expiry)
        assertEquals(LocalDate.of(2026, 12, 3), parse("03DIC26").expiry)
    }

    @Test
    fun dataConSpaziStampanteAPunti() {
        assertEquals(LocalDate.of(2026, 10, 25), parse("DA CONSUMARSI ENTRO 25 10 26").expiry)
    }

    @Test
    fun formatoIso() {
        assertEquals(LocalDate.of(2026, 11, 5), parse("Best before 2026-11-05").expiry)
    }

    @Test
    fun prezziEPesiNonSonoDate() {
        assertNull(parse("Prezzo 2.50 € — 1,25 kg — 0.75 L").expiry)
    }

    @Test
    fun senzaParoleChiaveSceglieLaDataFuturaPiuLontana() {
        assertEquals(LocalDate.of(2026, 11, 5), parse("20/10/2026 05/11/2026").expiry)
    }

    @Test
    fun lottoNonVieneScambiatoPerScadenza() {
        val r = parse("L.22/10/26 scad 24/11/26")
        assertEquals(LocalDate.of(2026, 11, 24), r.expiry)
    }

    @Test
    fun giorniDopoApertura() {
        assertEquals(3, parse("Una volta aperto conservare in frigorifero e consumare entro 3 giorni").daysAfterOpening)
        assertEquals(60, parse("Dopo l'apertura conservare in frigo e consumare entro 2 mesi").daysAfterOpening)
        assertEquals(30, parse("Consumare entro 30 gg dall’apertura").daysAfterOpening)
        assertEquals(3, parse("CONSUMARE ENTRO TRE GIORNI DALL'APERTURA").daysAfterOpening)
        assertEquals(14, parse("Once opened consume within 2 weeks. After opening").daysAfterOpening)
        assertNull(parse("Conservare in luogo fresco e asciutto").daysAfterOpening)
    }

    @Test
    fun aperturaEScadenzaInsieme() {
        val r = parse("Da consumarsi preferibilmente entro il 15/03/2028. Dopo l'apertura conservare in frigorifero e consumare entro 4 settimane.")
        assertEquals(LocalDate.of(2028, 3, 15), r.expiry)
        assertEquals(ExpiryType.BEST_BEFORE, r.type)
        assertEquals(28, r.daysAfterOpening)
    }
}
