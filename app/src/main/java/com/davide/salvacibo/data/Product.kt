package com.davide.salvacibo.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.davide.salvacibo.domain.Category
import com.davide.salvacibo.domain.ExpiryCalc
import com.davide.salvacibo.domain.ExpiryInfo
import com.davide.salvacibo.domain.ExpiryType
import com.davide.salvacibo.domain.ProductStatus
import com.davide.salvacibo.domain.Settings
import java.time.LocalDate

@Entity(tableName = "products", indices = [Index("barcode"), Index("status")])
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brand: String? = null,
    val category: String = Category.ALTRO.name,
    val barcode: String? = null,
    /** data stampata, salvata come LocalDate.toEpochDay() */
    val expiryEpochDay: Long,
    val expiryType: String = ExpiryType.USE_BY.name,
    /** giorno in cui è stato aperto (null = ancora chiuso) */
    val openedEpochDay: Long? = null,
    /** quanti giorni dura una volta aperto (null = non si applica) */
    val daysAfterOpening: Int? = null,
    /** foto da Open Food Facts */
    val imageUrl: String? = null,
    /** foto scattata durante la scansione (file interno all'app) */
    val photoPath: String? = null,
    val status: String = ProductStatus.ACTIVE.name,
    val createdAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
)

val Product.categoryEnum: Category get() = Category.fromName(category)
val Product.type: ExpiryType get() = ExpiryType.fromName(expiryType)
val Product.expiryDate: LocalDate get() = LocalDate.ofEpochDay(expiryEpochDay)
val Product.openedDate: LocalDate? get() = openedEpochDay?.let { LocalDate.ofEpochDay(it) }
val Product.isOpened: Boolean get() = openedEpochDay != null

fun Product.expiryInfo(settings: Settings, today: LocalDate = LocalDate.now()): ExpiryInfo =
    ExpiryCalc.info(expiryDate, type, openedDate, daysAfterOpening, today, settings)
