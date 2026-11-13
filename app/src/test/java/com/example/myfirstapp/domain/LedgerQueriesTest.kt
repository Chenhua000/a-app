package com.example.myfirstapp.domain

import com.example.myfirstapp.data.local.TransactionEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId

class LedgerQueriesTest {
    private val entries = listOf(
        TransactionEntity(1,TransactionType.EXPENSE,2350,"餐饮店停车费",0,"交通"),
        TransactionEntity(2,TransactionType.EXPENSE,3000,"午餐",0,"餐饮"),
        TransactionEntity(3,TransactionType.INCOME,5000,"退款",0,"餐饮")
    )
    @Test fun categoryFilterDoesNotMatchUnrelatedNotesOrIncome() {
        assertEquals(listOf(2L),filterLedgerEntries(entries,category="餐饮",type=TransactionType.EXPENSE).map { it.id })
    }
    @Test fun queryCombinesWithTypeAndCategory() {
        assertEquals(listOf(1L),filterLedgerEntries(entries,query="23.50",category="交通",type=TransactionType.EXPENSE).map { it.id })
        assertTrue(filterLedgerEntries(entries,query="午餐",type=TransactionType.INCOME).isEmpty())
    }
    @Test fun filteredTotalsReflectOnlySelectedEntries() {
        val result=Statistics.summarize(filterLedgerEntries(entries,type=TransactionType.INCOME))
        assertEquals(5000L,result.incomeCents)
        assertEquals(0L,result.expenseCents)
    }
    @Test fun csvPreservesChineseQuotesCommasAndLineBreaks() {
        val csv=ledgerCsv(listOf(entries[0].copy(note="中文,\"备注\"\n第二行")),ZoneId.of("UTC"))
        assertTrue(csv.startsWith("\uFEFF日期,"))
        assertTrue(csv.contains("\"中文,\"\"备注\"\"\n第二行\"\r\n"))
        assertTrue(csv.contains(",23.50,"))
    }
    @Test fun exportedTextIsNotInterpretedAsFormula() {
        val csv=ledgerCsv(listOf(entries[0].copy(note="=1+1")),ZoneId.of("UTC"))
        assertTrue(csv.contains("\"'=1+1\""))
    }
}
