package com.davide.salvacibo.domain

import java.text.Normalizer
import java.util.Locale

/**
 * Categorie di alimenti. Ogni categoria ha un'icona stilizzata (emoji) e una durata
 * tipica dopo l'apertura, usata come valore predefinito quando l'etichetta non la indica.
 * [defaultDaysAfterOpening] = null significa "non si applica" (es. frutta, uova).
 */
enum class Category(
    val label: String,
    val emoji: String,
    val defaultDaysAfterOpening: Int?,
) {
    CARNE("Carne", "🍗", 1),
    PESCE("Pesce", "🐟", 1),
    SALUMI("Salumi", "🥓", 4),
    FORMAGGI("Formaggi", "🧀", 7),
    LATTICINI("Latte e yogurt", "🥛", 3),
    UOVA("Uova", "🥚", null),
    FRUTTA("Frutta", "🍎", null),
    VERDURA("Verdura", "🥦", 2),
    PANE("Pane", "🍞", 3),
    PASTA_FRESCA("Pasta fresca", "🥟", 2),
    PRONTI("Piatti pronti", "🥡", 2),
    SUGHI("Sughi", "🍅", 4),
    SALSE("Salse", "🧂", 60),
    CONSERVE("Conserve", "🥫", 3),
    MARMELLATE("Marmellate e creme", "🍯", 30),
    DOLCI("Dolci e snack", "🍪", 30),
    DISPENSA("Dispensa", "🍝", 180),
    BEVANDE("Bevande", "🧃", 3),
    SURGELATI("Surgelati", "🧊", null),
    ALTRO("Altro", "🛒", 5);

    companion object {
        fun fromName(name: String?): Category =
            entries.firstOrNull { it.name == name } ?: ALTRO
    }
}

/** Risultato dell'indovina-categoria: categoria + giorni dopo l'apertura suggeriti. */
data class CategoryGuess(val category: Category, val daysAfterOpening: Int?)

/**
 * Indovina la categoria dal nome del prodotto (e, se disponibili, dalle categorie
 * di Open Food Facts). Vince la parola chiave più lunga trovata nel nome, così
 * "tonno all'olio" (conserve) batte "tonno" (pesce) e "latte di soia" batte "latte".
 */
object CategoryGuesser {

    private class Rule(val keyword: String, val category: Category, val days: Int? = null)

    private val nameRules: List<Rule> = buildList {
        fun add(cat: Category, vararg words: String, days: Int? = null) =
            words.forEach { add(Rule(it, cat, days)) }

        // Carne
        add(Category.CARNE, "pollo", "petto", "cosce", "sovracosce", "fusi", "alette", "manzo", "bovino",
            "vitello", "vitellone", "maiale", "suino", "tacchino", "agnello", "coniglio", "hamburger",
            "burger", "macinato", "salsiccia", "salsicce", "fettine", "bistecca", "bistecche", "spiedini",
            "arrosto", "carne", "cotoletta", "cotolette", "polpette", "scaloppine", "lonza", "costine",
            "filetto", "straccetti")
        // Pesce
        add(Category.PESCE, "pesce", "salmone", "merluzzo", "orata", "branzino", "spigola", "tonno",
            "gamberi", "gamberetti", "calamari", "seppie", "polpo", "cozze", "vongole", "sogliola",
            "platessa", "trota", "surimi", "acciughe", "alici", "sardine", "baccalà", "pesce spada")
        add(Category.PESCE, "salmone affumicato", "pesce spada affumicato", days = 3)
        // Salumi
        add(Category.SALUMI, "prosciutto", "prosciutto crudo", "prosciutto cotto", "salame", "mortadella",
            "speck", "bresaola", "pancetta", "coppa", "lardo", "affettato", "affettati", "wurstel", "würstel", "salamino", "porchetta", "cubetti di pancetta")
        // Formaggi (con durate specifiche per i freschi)
        add(Category.FORMAGGI, "formaggio", "formaggi", "pecorino", "gorgonzola", "fontina", "asiago",
            "emmental", "provolone", "taleggio", "scamorza", "provola", "feta", "brie", "camembert",
            "edamer", "fontal", "caciotta", "groviera", "leerdammer")
        add(Category.FORMAGGI, "grana", "grana padano", "parmigiano", "parmigiano reggiano",
            "formaggio grattugiato", days = 30)
        add(Category.FORMAGGI, "mozzarella", "fior di latte", "bocconcini", "ciliegine", days = 2)
        add(Category.FORMAGGI, "burrata", "stracciatella", days = 1)
        add(Category.FORMAGGI, "ricotta", "mascarpone", "stracchino", "crescenza", "squacquerone",
            "robiola", days = 3)
        add(Category.FORMAGGI, "philadelphia", "formaggio spalmabile", "spalmabile", days = 7)
        // Latte, yogurt, panna, burro
        add(Category.LATTICINI, "latte", "latte fresco", "latte intero", "latte parzialmente scremato",
            "yogurt", "yoghurt", "kefir", "panna", "panna da cucina", "panna fresca", "skyr", days = 3)
        add(Category.LATTICINI, "latte uht", "latte a lunga conservazione", days = 4)
        add(Category.LATTICINI, "burro", days = 30)
        // Uova
        add(Category.UOVA, "uova", "uovo")
        // Frutta
        add(Category.FRUTTA, "frutta", "mele", "mela", "pere", "banane", "arance", "mandarini", "kiwi",
            "uva", "fragole", "ciliegie", "pesche", "albicocche", "mirtilli", "lamponi", "ananas",
            "melone", "anguria", "limoni", "avocado")
        add(Category.FRUTTA, "macedonia", "frutta tagliata", days = 1)
        add(Category.FRUTTA, "frutti di bosco", days = 2)
        // Verdura
        add(Category.VERDURA, "verdura", "verdure", "zucchine", "carote", "pomodori", "pomodorini",
            "peperoni", "melanzane", "patate", "cipolle", "broccoli", "cavolfiore", "finocchi", "funghi",
            "champignon", "sedano", "cetrioli", "asparagi", "fagiolini", "minestrone", "zuppa")
        add(Category.VERDURA, "insalata", "insalatina", "lattuga", "rucola", "valeriana", "songino",
            "misticanza", "germogli", days = 1)
        add(Category.VERDURA, "spinaci", "bietole", days = 2)
        // Pane
        add(Category.PANE, "pane", "panini", "pane in cassetta", "focaccia", "piadina", "piadine",
            "baguette", "ciabatta", "rosetta", "bruschette")
        add(Category.PANE, "pancarrè", "pan carrè", "tramezzino", "tortillas", "tortilla", "pan bauletto",
            days = 5)
        // Pasta fresca e impasti
        add(Category.PASTA_FRESCA, "pasta fresca", "gnocchi", "tortellini", "ravioli", "cappelletti",
            "agnolotti", "lasagne fresche", "tagliatelle fresche", "pasta sfoglia", "sfoglia",
            "pasta brisè", "pasta brisée", "pasta frolla", "pasta pizza", "impasto", "base pizza")
        // Piatti pronti
        add(Category.PRONTI, "piatto pronto", "piatti pronti", "insalata di riso", "insalata di pollo",
            "hummus", "guacamole", "tzatziki", "sushi", "gastronomia", "lasagna", "parmigiana", "pizza")
        // Sughi
        add(Category.SUGHI, "sugo", "sughi", "passata", "passata di pomodoro", "polpa di pomodoro",
            "pomodori pelati", "pelati", "ragù", "ragu", "pesto", "pesto alla genovese", "salsa di pomodoro",
            "pomodoro", "concentrato")
        add(Category.SUGHI, "pesto", "pesto alla genovese", days = 5)
        // Salse
        add(Category.SALSE, "maionese", "ketchup", "senape", "salsa", "salse", "salsa barbecue",
            "salsa rosa", "salsa tonnata", days = 60)
        add(Category.SALSE, "salsa di soia", "aceto", "aceto balsamico", "olio", days = 180)
        // Conserve
        add(Category.CONSERVE, "tonno all'olio", "tonno in scatola", "tonno al naturale",
            "sgombro all'olio", "salmone in scatola", days = 2)
        add(Category.CONSERVE, "ceci", "fagioli", "lenticchie", "piselli", "mais", "legumi", "borlotti",
            "cannellini", days = 3)
        add(Category.CONSERVE, "olive", "carciofini", "sottoli", "sott'olio", "funghi trifolati",
            "giardiniera", days = 15)
        add(Category.CONSERVE, "sottaceti", "capperi", "cetriolini", days = 30)
        // Marmellate e creme
        add(Category.MARMELLATE, "marmellata", "confettura", "composta", "crema di frutta", days = 30)
        add(Category.MARMELLATE, "crema spalmabile", "crema alle nocciole", "nutella", "crema di pistacchio",
            "burro di arachidi", days = 90)
        add(Category.MARMELLATE, "miele", days = 365)
        // Dolci e snack
        add(Category.DOLCI, "torta", "crostata", "tiramisù", "tiramisu", "dolce", "pasticcini",
            "panna cotta", "budino", "budini", "mousse", "cheesecake", days = 3)
        add(Category.DOLCI, "merendine", "merendina", "brioche", "cornetti", "croissant", "plumcake",
            days = 7)
        add(Category.DOLCI, "biscotti", "frollini", "wafer", "cioccolato", "cioccolata", "fette biscottate",
            "crackers", "cracker", "grissini", "taralli", "patatine", "snack", "barrette", "cereali",
            "muesli", "corn flakes", "gallette", days = 30)
        // Dispensa
        add(Category.DISPENSA, "pasta", "spaghetti", "penne", "fusilli", "rigatoni", "riso", "farina",
            "zucchero", "sale", "caffè", "orzo", "farro", "couscous", "polenta", "legumi secchi", "tè",
            "camomilla", "lievito", "pangrattato", "spezie", days = 180)
        // Bevande
        add(Category.BEVANDE, "succo", "succhi", "spremuta", "tè freddo", "the freddo", "aranciata",
            "cola", "bibita", "acqua", "vino", "birra", "frullato", "smoothie", days = 3)
        add(Category.BEVANDE, "latte di soia", "latte di riso", "latte di mandorla", "latte d'avena",
            "bevanda di soia", "bevanda di riso", "bevanda di avena", "bevanda all'avena",
            "bevanda vegetale", "drink di soia", days = 5)
        // Surgelati
        add(Category.SURGELATI, "surgelato", "surgelati", "surgelata", "surgelate", "gelato", "gelati",
            "ghiaccioli", "bastoncini", "sofficini", "frozen", "congelato")
        add(Category.SURGELATI, "gelato", "gelati", days = 30)
    }

    /** Mappa delle categorie di Open Food Facts (tag in inglese) → nostra categoria. */
    private val offRules: List<Pair<String, Category>> = listOf(
        "frozen" to Category.SURGELATI,
        "ice-cream" to Category.SURGELATI,
        "plant-based-milk" to Category.BEVANDE,
        "plant-milk" to Category.BEVANDE,
        "juices" to Category.BEVANDE,
        "beverages" to Category.BEVANDE,
        "drinks" to Category.BEVANDE,
        "jams" to Category.MARMELLATE,
        "marmalades" to Category.MARMELLATE,
        "spreads" to Category.MARMELLATE,
        "honey" to Category.MARMELLATE,
        "pestos" to Category.SUGHI,
        "tomato-sauces" to Category.SUGHI,
        "pasta-sauces" to Category.SUGHI,
        "tomato" to Category.SUGHI,
        "mayonnaise" to Category.SALSE,
        "ketchup" to Category.SALSE,
        "mustards" to Category.SALSE,
        "sauces" to Category.SALSE,
        "condiments" to Category.SALSE,
        "canned" to Category.CONSERVE,
        "legumes" to Category.CONSERVE,
        "pickles" to Category.CONSERVE,
        "olives" to Category.CONSERVE,
        "fresh-pasta" to Category.PASTA_FRESCA,
        "stuffed-pasta" to Category.PASTA_FRESCA,
        "gnocchi" to Category.PASTA_FRESCA,
        "doughs" to Category.PASTA_FRESCA,
        "pastas" to Category.DISPENSA,
        "rices" to Category.DISPENSA,
        "flours" to Category.DISPENSA,
        "cereals-and-potatoes" to Category.DISPENSA,
        "breakfast-cereals" to Category.DOLCI,
        "biscuits" to Category.DOLCI,
        "cakes" to Category.DOLCI,
        "chocolates" to Category.DOLCI,
        "snacks" to Category.DOLCI,
        "desserts" to Category.DOLCI,
        "breads" to Category.PANE,
        "eggs" to Category.UOVA,
        "cheeses" to Category.FORMAGGI,
        "mozzarella" to Category.FORMAGGI,
        "yogurts" to Category.LATTICINI,
        "milks" to Category.LATTICINI,
        "creams" to Category.LATTICINI,
        "butters" to Category.LATTICINI,
        "dairies" to Category.LATTICINI,
        "hams" to Category.SALUMI,
        "salamis" to Category.SALUMI,
        "prepared-meats" to Category.SALUMI,
        "sausages" to Category.SALUMI,
        "poultries" to Category.CARNE,
        "chickens" to Category.CARNE,
        "meats" to Category.CARNE,
        "fishes" to Category.PESCE,
        "seafood" to Category.PESCE,
        "fruits" to Category.FRUTTA,
        "vegetables" to Category.VERDURA,
        "salads" to Category.VERDURA,
        "meals" to Category.PRONTI,
    )

    fun guess(name: String?, offCategoryTags: List<String> = emptyList()): CategoryGuess {
        val tags = offCategoryTags.map { it.lowercase(Locale.ROOT).substringAfter(':') }
        val normalized = normalize(name ?: "")
        if (normalized.isNotBlank()) {
            val padded = " $normalized "
            val best = nameRules
                .filter { padded.contains(" ${normalize(it.keyword)} ") }
                .maxWithOrNull(compareBy<Rule> { it.keyword.length }.thenBy { if (it.days != null) 1 else 0 })
            if (best != null) {
                // Il nome dice "tonno" ma OFF dice "in scatola"/"surgelato": vince il tipo di confezione.
                val fresh = best.category in setOf(Category.CARNE, Category.PESCE, Category.VERDURA, Category.FRUTTA)
                if (fresh && tags.any { it.contains("frozen") }) {
                    return CategoryGuess(Category.SURGELATI, Category.SURGELATI.defaultDaysAfterOpening)
                }
                if (fresh && tags.any { it.contains("canned") }) {
                    return CategoryGuess(Category.CONSERVE, Category.CONSERVE.defaultDaysAfterOpening)
                }
                return CategoryGuess(best.category, best.days ?: best.category.defaultDaysAfterOpening)
            }
        }
        // I tag OFF vanno dal più generico al più specifico: partiamo dall'ultimo.
        for (t in tags.asReversed()) {
            val hit = offRules.firstOrNull { (key, _) -> t.contains(key) }
            if (hit != null) return CategoryGuess(hit.second, hit.second.defaultDaysAfterOpening)
        }
        return CategoryGuess(Category.ALTRO, Category.ALTRO.defaultDaysAfterOpening)
    }

    /** minuscolo, senza accenti, solo lettere/numeri/apostrofo separati da spazi singoli */
    internal fun normalize(s: String): String {
        val noAccents = Normalizer.normalize(s.lowercase(Locale.ITALIAN), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noAccents.replace('’', '\'')
            .replace(Regex("[^a-z0-9']+"), " ")
            .trim()
    }
}
