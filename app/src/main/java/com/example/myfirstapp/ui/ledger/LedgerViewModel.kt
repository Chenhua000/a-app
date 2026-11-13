package com.example.myfirstapp.ui.ledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.local.TransactionEntity
import com.example.myfirstapp.domain.MoneyParser
import com.example.myfirstapp.domain.MoneySummary
import com.example.myfirstapp.domain.Statistics
import com.example.myfirstapp.domain.TimeText
import com.example.myfirstapp.domain.TransactionType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

sealed class LedgerValidation {
    data object Success : LedgerValidation()
    data class Error(val message: String) : LedgerValidation()
}

class LedgerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MyFirstApp).container.ledgerRepository
    private val zone: ZoneId = ZoneId.systemDefault()

    private val transactions = repository.observeTransactions()

    val thisMonthSummary: StateFlow<MoneySummary> = transactions
        .map { all ->
            val month = YearMonth.now(zone)
            val from = TimeText.startOfDayMillis(month.atDay(1), zone)
            val to = TimeText.startOfDayMillis(month.plusMonths(1).atDay(1), zone)
            Statistics.summarize(all.filter { it.occurredAtEpochMillis in from until to })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneySummary(0L, 0L))

    /** 最近几笔，直接摆在记账首页，省得为了看一眼余额去翻统计页 */
    val recent: StateFlow<List<TransactionEntity>> = transactions
        .map { all -> all.take(6) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(type: TransactionType, amountText: String, note: String, onResult: (LedgerValidation) -> Unit, category: String = "其他", occurredAt: Long = System.currentTimeMillis()) {
        commit(null, type, amountText, note, onResult, category, occurredAt)
    }

    /** 编辑已有记录：保留 id 与发生时间，LedgerRepository.saveTransaction 会走 update 分支 */
    fun update(
        original: TransactionEntity,
        type: TransactionType,
        amountText: String,
        note: String,
        onResult: (LedgerValidation) -> Unit,
        category: String = original?.category ?: "其他",
        occurredAt: Long = original?.occurredAtEpochMillis ?: System.currentTimeMillis()
    ) {
        commit(original, type, amountText, note, onResult, category, occurredAt)
    }

    private fun commit(
        original: TransactionEntity?,
        type: TransactionType,
        amountText: String,
        note: String,
        onResult: (LedgerValidation) -> Unit,
        category: String = original?.category ?: "其他",
        occurredAt: Long = original?.occurredAtEpochMillis ?: System.currentTimeMillis()
    ) {
        val (entity, validationError) = parseTransactionInput(original, type, amountText, note, category, occurredAt)
        if (entity == null) return onResult(LedgerValidation.Error(validationError ?: "保存失败"))
        viewModelScope.launch {
            runCatching { repository.saveTransaction(entity) }
                .onFailure { onResult(LedgerValidation.Error("保存失败：${it.localizedMessage}")) }
                .onSuccess { onResult(LedgerValidation.Success) }
        }
    }

    fun delete(transaction: TransactionEntity) {
        viewModelScope.launch { repository.deleteTransaction(transaction) }
    }
}

/**
 * 新增 / 编辑共用的输入校验与实体构造：original 为 null 表示新增，
 * 非 null 表示编辑（沿用原 id、原发生时间，避免改动后跳到别的统计分组）。
 * 返回 `实体 to 错误信息`，两者只有一个非空。
 */
internal fun parseTransactionInput(
    original: TransactionEntity?,
    type: TransactionType,
    amountText: String,
    note: String,
    category: String = original?.category ?: "其他",
    occurredAt: Long = original?.occurredAtEpochMillis ?: System.currentTimeMillis()
): Pair<TransactionEntity?, String?> {
    val cents = MoneyParser.parseYuanToCents(amountText)
        ?: return null to "金额格式不对，最多两位小数，例如 18.50"
    if (cents <= 0) return null to "金额必须大于 0"
    val trimmedNote = note.trim().takeIf { it.isNotEmpty() }
    val entity = original?.copy(
        type = type,
        amountCents = cents,
        note = trimmedNote,
        category = category.trim().ifBlank { "其他" },
        occurredAtEpochMillis = occurredAt
    ) ?: TransactionEntity(
        type = type,
        amountCents = cents,
        note = trimmedNote,
        occurredAtEpochMillis = occurredAt,
        category = category.trim().ifBlank { "其他" }
    )
    return entity to null
}
