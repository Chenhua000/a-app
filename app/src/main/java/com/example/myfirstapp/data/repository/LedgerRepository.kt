package com.example.myfirstapp.data.repository

import com.example.myfirstapp.data.local.AppDatabase
import com.example.myfirstapp.data.local.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LedgerRepository(
    private val database: AppDatabase,
    private val ioDispatcher: kotlin.coroutines.CoroutineContext = Dispatchers.IO
) {
    fun observeTransactions(): Flow<List<TransactionEntity>> =
        database.transactionDao().observeTransactions()

    suspend fun saveTransaction(entity: TransactionEntity) = withContext(ioDispatcher) {
        if (entity.id == 0L) {
            database.transactionDao().insert(entity)
        } else {
            database.transactionDao().update(entity)
        }
    }

    suspend fun deleteTransaction(entity: TransactionEntity) = withContext(ioDispatcher) {
        database.transactionDao().delete(entity)
    }
}
