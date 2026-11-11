package com.example.myfirstapp.ui.ledger

import com.example.myfirstapp.data.local.TransactionEntity
import com.example.myfirstapp.domain.MoneyFormatter
import com.example.myfirstapp.domain.MoneyParser
import com.example.myfirstapp.domain.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记账「长按编辑」链路的纯逻辑回归：
 * 编辑必须沿用原 id 与发生时间（否则会变成新增一笔、或跳到别的统计分组）。
 */
class LedgerInputTest {

    @Test
    fun newTransactionUsesCurrentTimeAndTrimmedNote() {
        val before = System.currentTimeMillis()
        val (entity, message) = parseTransactionInput(null, TransactionType.EXPENSE, "18.50", "  午饭  ")

        assertNull(message)
        assertNotNull(entity)
        assertEquals(0L, entity!!.id)
        assertEquals(TransactionType.EXPENSE, entity.type)
        assertEquals(1850L, entity.amountCents)
        assertEquals("午饭", entity.note)
        assertTrue(entity.occurredAtEpochMillis >= before)
    }

    @Test
    fun editingKeepsIdAndOccurredAtButUpdatesFields() {
        val original = TransactionEntity(
            id = 42L,
            type = TransactionType.EXPENSE,
            amountCents = 1200L,
            note = "旧备注",
            occurredAtEpochMillis = 1_700_000_000_000L
        )
        val (entity, message) = parseTransactionInput(original, TransactionType.INCOME, "36", " 奖金 ")

        assertNull(message)
        assertNotNull(entity)
        assertEquals(42L, entity!!.id)
        assertEquals(1_700_000_000_000L, entity.occurredAtEpochMillis)
        assertEquals(TransactionType.INCOME, entity.type)
        assertEquals(3600L, entity.amountCents)
        assertEquals("奖金", entity.note)
        // 原对象不能被就地改动
        assertEquals(1200L, original.amountCents)
    }

    @Test
    fun editingWithBlankNoteClearsNote() {
        val original = TransactionEntity(
            id = 7L,
            type = TransactionType.INCOME,
            amountCents = 500L,
            note = "旧备注",
            occurredAtEpochMillis = 1_700_000_000_000L
        )
        val (entity, message) = parseTransactionInput(original, TransactionType.INCOME, "5", "   ")

        assertNull(message)
        assertNull(entity!!.note)
    }

    @Test
    fun invalidAmountReturnsValidationMessage() {
        val (entity, message) = parseTransactionInput(null, TransactionType.INCOME, "1.234", "备注")

        assertNull(entity)
        assertEquals("金额格式不对，最多两位小数，例如 18.50", message)
    }

    @Test
    fun zeroAmountIsRejected() {
        val (entity, message) = parseTransactionInput(null, TransactionType.EXPENSE, "0", "")

        assertNull(entity)
        assertEquals("金额必须大于 0", message)
    }

    @Test
    fun preformattedAmountRoundTripsWithoutChangingValue() {
        // 编辑面板用 MoneyFormatter 预填金额，原样保存后金额必须不变
        listOf(1L, 50L, 1850L, 3600L, 100000L).forEach { cents ->
            val prefilled = MoneyFormatter.formatCents(cents)
            assertEquals("预填 $prefilled 解析失败", cents, MoneyParser.parseYuanToCents(prefilled))
        }
    }
}
