package com.example.myfirstapp.domain

import com.example.myfirstapp.data.local.TransactionEntity

data class MoneySummary(
    val incomeCents: Long,
    val expenseCents: Long
) {
    val balanceCents: Long get() = incomeCents - expenseCents
}

object Statistics {
    fun summarize(transactions: List<TransactionEntity>): MoneySummary {
        var income = 0L
        var expense = 0L
        transactions.forEach {
            when (it.type) {
                TransactionType.INCOME -> income += it.amountCents
                TransactionType.EXPENSE -> expense += it.amountCents
            }
        }
        return MoneySummary(income, expense)
    }
}
