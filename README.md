# Salvacibo

App Android (Kotlin + Jetpack Compose) per non buttare più il cibo scaduto.

## Cosa fa

1. **Scansiona**: inquadri il codice a barre → l'app trova nome, marca, foto e categoria
   (prima tra i prodotti che hai già comprato, poi su Open Food Facts).
2. Poi inquadri la **data di scadenza** → la legge da sola (OCR sul telefono), capisce se è
   "da consumarsi entro" o "preferibilmente entro" e, se c'è scritto, anche
   "una volta aperto consumare entro X giorni".
3. Tocchi **Salva** nella schermata di conferma.
4. **Notifiche**: ogni giorno all'ora scelta, da N giorni prima della scadenza fino al giorno stesso
   (es. pollo che scade il 22 con 2 giorni di preavviso → avvisi il 20, il 21 e il 22).
5. **"L'ho aperto"**: parte il conto alla rovescia (es. marmellata 30 giorni). La data che conta
   diventa la più vicina tra quella stampata e quella dell'apertura, con le notifiche.
6. Quando lo finisci: **Consumato** o **Buttato** (le statistiche del mese sono in home).

## Pubblicità e "Offrimi un caffè"

- Banner AdMob in fondo alla home, con il modulo di consenso privacy di Google (UMP) per gli utenti UE.
- Schermata **Supporta e info** (cuore in alto nella home): caffè 0,99 €, pizza, cena tramite Google Play.
  Qualsiasi acquisto toglie le pubblicità per sempre (anche reinstallando l'app).
- In debug gli annunci sono sempre di PROVA. Gli ID veri vanno in `gradle.properties`
  (`ADMOB_APP_ID`, `ADMOB_BANNER_ID`); email e link privacy in `AppConfig.kt`.
- Per pubblicare senza annunci o senza acquisti: `SALVACIBO_ADS_ENABLED=false` / `SALVACIBO_BILLING_ENABLED=false`.

## Materiali per il Play Store

- `store/` → icona 512 px, grafica in evidenza 1024×500, testi della scheda
- `docs/privacy-policy.html` → informativa privacy da pubblicare (es. su Google Sites)

## Come aprirlo

1. Android Studio → **File › Open** → scegli la cartella `Salvacibo`.
2. Attendi la sincronizzazione di Gradle (la prima volta scarica le librerie, qualche minuto).
3. Collega il telefono (con il debug USB attivo) e premi **Run ▶**.
   La fotocamera nell'emulatore funziona male: per la scansione meglio un telefono vero.

Requisiti: Android 8.0+ sul telefono; Android Studio Meerkat (2024.3.1) o più recente con SDK 36
(Android 16) installato: Android Studio propone di scaricarlo al primo Sync.

## Struttura

```
app/src/main/java/com/davide/salvacibo/
├── domain/          logica pura (testata): date dall'etichetta, categorie, calcolo scadenze, testi notifiche
│   ├── LabelParser.kt     legge date tipo 22/10/26, 12 OTT 2026, 10/2027, 221026, "dopo l'apertura entro 3 giorni"
│   ├── Category.kt        categorie con icona e durata tipica dopo l'apertura
│   └── Expiry.kt          scadenza effettiva, urgenza (semaforo), regole delle notifiche
├── data/            database Room, impostazioni, Open Food Facts
├── notifications/   WorkManager (controllo giornaliero), notifiche con pulsante "Consumato"
├── monetization/    pubblicità AdMob + consenso, acquisti Google Play, richiesta recensione
└── ui/              schermate Compose: home, scansione, conferma, dettaglio, impostazioni, supporto
```

I test della logica sono in `app/src/test` (tasto destro sulla cartella → *Run Tests*).

## Idee per dopo

- Se l'OCR sbaglia spesso su certe etichette: inviare la foto a un modello di IA (serve un piccolo server).
- Widget nella schermata home con i prodotti in scadenza.
- Lista della spesa con i prodotti consumati.
