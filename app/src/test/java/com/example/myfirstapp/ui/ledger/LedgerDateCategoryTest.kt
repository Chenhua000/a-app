package com.example.myfirstapp.ui.ledger

import com.example.myfirstapp.data.local.TransactionEntity
import com.example.myfirstapp.domain.TransactionType
import org.junit.Assert.*
import org.junit.Test

class LedgerDateCategoryTest {
    @Test fun backdatedEntryUsesChosenDateAndCategory() {
        val (entry, error) = parseTransactionInput(null, TransactionType.EXPENSE, "23.50", "午餐", "餐饮", 1_700_000_000_000L)
        assertNull(error)
        assertEquals(2350L, entry!!.amountCents)
        assertEquals("餐饮", entry.category)
        assertEquals(1_700_000_000_000L, entry.occurredAtEpochMillis)
    }

    @Test fun editingDateKeepsRecordIdentity() {
        val original = TransactionEntity(42, TransactionType.EXPENSE, 100, null, 1000, "交通")
        val (entry, error) = parseTransactionInput(original, TransactionType.EXPENSE, "2", "公交", "交通", 2000)
        assertNull(error)
        assertEquals(42L, entry!!.id)
        assertEquals(2000L, entry.occurredAtEpochMillis)
        assertEquals("交通", entry.category)
    }
}
