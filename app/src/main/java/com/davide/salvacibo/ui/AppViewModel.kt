package com.davide.salvacibo.ui

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davide.salvacibo.SalvaciboApp
import com.davide.salvacibo.data.OpenFoodFacts
import com.davide.salvacibo.data.Product
import com.davide.salvacibo.data.categoryEnum
import com.davide.salvacibo.data.expiryDate
import com.davide.salvacibo.data.openedDate
import com.davide.salvacibo.data.type
import com.davide.salvacibo.domain.Category
import com.davide.salvacibo.domain.CategoryGuesser
import com.davide.salvacibo.domain.ExpiryType
import com.davide.salvacibo.domain.ProductStatus
import com.davide.salvacibo.domain.Settings
import com.davide.salvacibo.monetization.Ads
import com.davide.salvacibo.monetization.BillingManager
import com.davide.salvacibo.notifications.Notifier
import com.davide.salvacibo.notifications.WorkScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Prodotto "in preparazione": riempito dalla scansione e confermato nella schermata di modifica. */
data class ProductDraft(
    val id: Long = 0,
    val name: String = "",
    val brand: String? = null,
    val category: Category = Category.ALTRO,
    val barcode: String? = null,
    val expiry: LocalDate? = null,
    val expiryType: ExpiryType = ExpiryType.USE_BY,
    val daysAfterOpening: Int? = Category.ALTRO.defaultDaysAfterOpening,
    val opened: LocalDate? = null,
    val imageUrl: String? = null,
    val photoPath: String? = null,
    /** true se l'utente (o la memoria) ha già scelto i giorni dopo l'apertura: non sovrascriverli */
    val daysFromLabel: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SalvaciboApp
    private val repo = app.repository

    val products: StateFlow<List<Product>> =
        repo.active.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<Settings> = app.prefs.settings

    private val monthStart: Long =
        LocalDate.now().withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    val consumedThisMonth: StateFlow<Int> =
        repo.countSince(ProductStatus.CONSUMED, monthStart).stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val thrownThisMonth: StateFlow<Int> =
        repo.countSince(ProductStatus.THROWN, monthStart).stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun observe(id: Long): Flow<Product?> = repo.observe(id)

    // ------------------------------------------------------------------ supporto / pubblicità

    val billing: StateFlow<BillingManager.State> = app.billing.state
    val adsAllowed: StateFlow<Boolean> = Ads.canRequestAds
    val privacyOptionsRequired: StateFlow<Boolean> = Ads.privacyOptionsRequired

    fun buy(activity: Activity, productId: String) = app.billing.buy(activity, productId)
    fun restorePurchases() = app.billing.refresh()
    fun billingMessageShown() = app.billing.messageShown()
    fun showPrivacyOptions(activity: Activity) = Ads.showPrivacyOptions(activity)
    fun askReview(activity: Activity, savedProducts: Int) =
        com.davide.salvacibo.monetization.Review.askIfAppropriate(activity, app.prefs, savedProducts)

    // ------------------------------------------------------------------ bozza / scansione

    var draft by mutableStateOf(ProductDraft())
        private set

    /** true mentre si cerca il prodotto su Open Food Facts */
    var lookingUp by mutableStateOf(false)
        private set

    private var lookupJob: Job? = null

    fun startNewDraft() {
        lookupJob?.cancel()
        lookingUp = false
        draft = ProductDraft()
    }

    fun updateDraft(transform: (ProductDraft) -> ProductDraft) {
        draft = transform(draft)
    }

    fun loadDraftFrom(product: Product) {
        draft = ProductDraft(
            id = product.id,
            name = product.name,
            brand = product.brand,
            category = product.categoryEnum,
            barcode = product.barcode,
            expiry = product.expiryDate,
            expiryType = product.type,
            daysAfterOpening = product.daysAfterOpening,
            opened = product.openedDate,
            imageUrl = product.imageUrl,
            photoPath = product.photoPath,
            daysFromLabel = true,
            createdAt = product.createdAt,
        )
    }

    /** Codice a barre letto: prima guarda se l'hai già comprato, poi chiede a Open Food Facts. */
    fun onBarcode(barcode: String, frame: Bitmap?) {
        draft = draft.copy(barcode = barcode)
        if (frame != null) {
            viewModelScope.launch {
                val path = savePhoto(frame)
                if (path != null) draft = draft.copy(photoPath = path)
            }
        }
        lookupJob?.cancel()
        lookingUp = true
        lookupJob = viewModelScope.launch {
            try {
                val known = repo.lastWithBarcode(barcode)
                if (known != null) {
                    draft = draft.copy(
                        name = known.name,
                        brand = known.brand,
                        category = known.categoryEnum,
                        imageUrl = known.imageUrl,
                        expiryType = if (draft.expiry == null) known.type else draft.expiryType,
                        daysAfterOpening = if (draft.daysFromLabel) draft.daysAfterOpening else known.daysAfterOpening,
                        daysFromLabel = true,
                    )
                    return@launch
                }
                val off = OpenFoodFacts.lookup(barcode)
                if (off != null) {
                    val guess = CategoryGuesser.guess(off.name, off.categoryTags)
                    draft = draft.copy(
                        name = off.name ?: draft.name,
                        brand = off.brand ?: draft.brand,
                        category = guess.category,
                        imageUrl = off.imageUrl,
                        daysAfterOpening = if (draft.daysFromLabel) draft.daysAfterOpening else guess.daysAfterOpening,
                    )
                }
            } finally {
                lookingUp = false
            }
        }
    }

    /** Data (e magari durata dopo l'apertura) letta dall'etichetta. */
    fun onLabelRead(expiry: LocalDate?, type: ExpiryType?, daysAfterOpening: Int?) {
        draft = draft.copy(
            expiry = expiry ?: draft.expiry,
            expiryType = type ?: draft.expiryType,
            daysAfterOpening = daysAfterOpening ?: draft.daysAfterOpening,
            daysFromLabel = draft.daysFromLabel || daysAfterOpening != null,
        )
    }

    fun save(onDone: (Long) -> Unit) {
        val d = draft
        val name = d.name.trim().ifEmpty { d.category.label }
        val product = Product(
            id = d.id,
            name = name.replaceFirstChar { it.uppercase() },
            brand = d.brand?.trim()?.takeIf { it.isNotEmpty() },
            category = d.category.name,
            barcode = d.barcode,
            expiryEpochDay = (d.expiry ?: LocalDate.now().plusDays(7)).toEpochDay(),
            expiryType = d.expiryType.name,
            openedEpochDay = d.opened?.toEpochDay(),
            daysAfterOpening = d.daysAfterOpening,
            imageUrl = d.imageUrl,
            photoPath = d.photoPath,
            createdAt = d.createdAt,
        )
        viewModelScope.launch {
            val id = repo.save(product)
            onDone(id)
        }
    }

    // ------------------------------------------------------------------ azioni sul prodotto

    fun markOpened(product: Product, daysAfterOpening: Int) = viewModelScope.launch {
        repo.save(product.copy(openedEpochDay = LocalDate.now().toEpochDay(), daysAfterOpening = daysAfterOpening))
    }

    fun undoOpened(product: Product) = viewModelScope.launch {
        repo.save(product.copy(openedEpochDay = null))
    }

    fun markConsumed(product: Product) = finish(product, ProductStatus.CONSUMED)
    fun markThrown(product: Product) = finish(product, ProductStatus.THROWN)

    /** Ultimo prodotto segnato consumato/buttato: serve per il pulsante "Annulla" in home. */
    data class Finished(val product: Product, val status: ProductStatus)

    var lastFinished by mutableStateOf<Finished?>(null)
        private set

    private fun finish(product: Product, status: ProductStatus) = viewModelScope.launch {
        repo.setStatus(product.id, status)
        Notifier.cancel(app, product.id)
        lastFinished = Finished(product, status)
    }

    fun undoFinish(finished: Finished) = viewModelScope.launch {
        repo.save(finished.product.copy(status = ProductStatus.ACTIVE.name, finishedAt = null))
        lastFinished = null
    }

    fun clearFinished() {
        lastFinished = null
    }

    fun delete(product: Product) = viewModelScope.launch {
        repo.delete(product)
        Notifier.cancel(app, product.id)
    }

    // ------------------------------------------------------------------ impostazioni

    fun updateSettings(transform: (Settings) -> Settings) {
        val old = settings.value
        val new = app.prefs.update(transform)
        if (new.notifyHour != old.notifyHour) {
            WorkScheduler.scheduleDaily(app, new.notifyHour, replace = true)
        }
    }

    fun sendTestNotification() {
        Notifier.showTest(app)
        WorkScheduler.runNow(app, force = true)
    }

    // ------------------------------------------------------------------ foto

    private suspend fun savePhoto(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(app.filesDir, "photos").apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.jpg")
            val maxSide = 900
            val scale = minOf(1f, maxSide.toFloat() / maxOf(bitmap.width, bitmap.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
            } else bitmap
            FileOutputStream(file).use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            file.absolutePath
        }.getOrNull()
    }
}
