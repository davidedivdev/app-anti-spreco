package com.davide.salvacibo.data

import com.davide.salvacibo.domain.ProductStatus
import kotlinx.coroutines.flow.Flow
import java.io.File

class ProductRepository(private val dao: ProductDao) {

    val active: Flow<List<Product>> = dao.observeActive()

    fun observe(id: Long): Flow<Product?> = dao.observe(id)
    suspend fun get(id: Long): Product? = dao.get(id)
    suspend fun getActive(): List<Product> = dao.getActive()
    suspend fun lastWithBarcode(barcode: String): Product? = dao.lastWithBarcode(barcode)

    suspend fun save(product: Product): Long =
        if (product.id == 0L) dao.insert(product) else { dao.update(product); product.id }

    suspend fun setStatus(id: Long, status: ProductStatus) =
        dao.setStatus(id, status.name, System.currentTimeMillis())

    suspend fun delete(product: Product) {
        product.photoPath?.let { runCatching { File(it).delete() } }
        dao.delete(product)
    }

    fun countSince(status: ProductStatus, fromMillis: Long): Flow<Int> =
        dao.observeCountSince(status.name, fromMillis)
}
