package com.davide.salvacibo.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query("SELECT * FROM products WHERE status = 'ACTIVE' ORDER BY expiryEpochDay")
    fun observeActive(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE status = 'ACTIVE'")
    suspend fun getActive(): List<Product>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observe(id: Long): Flow<Product?>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun get(id: Long): Product?

    /** Per "ricordare" un prodotto già scansionato in passato (nome, categoria, giorni dopo apertura). */
    @Query("SELECT * FROM products WHERE barcode = :barcode ORDER BY createdAt DESC LIMIT 1")
    suspend fun lastWithBarcode(barcode: String): Product?

    @Insert
    suspend fun insert(product: Product): Long

    @Update
    suspend fun update(product: Product)

    @Delete
    suspend fun delete(product: Product)

    @Query("UPDATE products SET status = :status, finishedAt = :at WHERE id = :id")
    suspend fun setStatus(id: Long, status: String, at: Long)

    @Query("SELECT COUNT(*) FROM products WHERE status = :status AND finishedAt >= :from")
    fun observeCountSince(status: String, from: Long): Flow<Int>
}
