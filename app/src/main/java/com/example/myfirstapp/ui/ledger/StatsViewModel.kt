package com.example.myfirstapp.ui.ledger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.local.TransactionEntity
import com.example.myfirstapp.domain.MoneySummary
import com.example.myfirstapp.domain.Statistics
import com.example.myfirstapp.domain.TimeText
import com.example.myfirstapp.domain.TransactionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import android.net.Uri
import com.example.myfirstapp.domain.ledgerCsv
import java.time.YearMonth
import java.time.ZoneId

enum class StatsRange(val label: String) { MONTH("本月"), YEAR("今年") }

data class StatsUiState(
    val range: StatsRange = StatsRange.MONTH,
    val rangeLabel: String = "",
    val entries: List<TransactionEntity> = emptyList(),
    val summary: MoneySummary = MoneySummary(0L, 0L)
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {
    private val ledgerRepository = (application as MyFirstApp).container.ledgerRepository
    private val zone: ZoneId = ZoneId.systemDefault()

    private val _range = MutableStateFlow(StatsRange.MONTH)
    val range: StateFlow<StatsRange> = _range

    private val _month = MutableStateFlow(YearMonth.now(zone))
    val month: StateFlow<YearMonth> = _month

    private fun bounds(range: StatsRange, month: YearMonth): Pair<Long, Long> =
        if (range == StatsRange.MONTH) {
            val from = TimeText.startOfDayMillis(month.atDay(1), zone)
            val to = TimeText.startOfDayMillis(month.plusMonths(1).atDay(1), zone)
            from to to
        } else {
            val from = TimeText.startOfDayMillis(month.year.let { YearMonth.of(it, 1) }.atDay(1), zone)
            val to = TimeText.startOfDayMillis(YearMonth.of(month.year + 1, 1).atDay(1), zone)
            from to to
        }

    val uiState: StateFlow<StatsUiState> = combine(
        ledgerRepository.observeTransactions(),
        _range,
        _month
    ) { all, range, month ->
        val (from, to) = bounds(range, month)
        val entries = all.filter { it.occurredAtEpochMillis in from until to }
        val label = if (range == StatsRange.MONTH) {
            "${month.year} 年 ${month.monthValue} 月"
        } else {
            "${month.year} 年"
        }
        StatsUiState(
            range = range,
            rangeLabel = label,
            entries = entries,
            summary = Statistics.summarize(entries)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting
    private val _exportMessage = MutableStateFlow<String?>(null)
    val exportMessage: StateFlow<String?> = _exportMessage
    private var exportSnapshot: List<TransactionEntity>? = null

    fun prepareExport(entries: List<TransactionEntity>) { exportSnapshot = entries.toList() }
    fun clearExportMessage() { _exportMessage.value = null }
    fun export(uri: Uri?) {
        val entries = exportSnapshot
        exportSnapshot = null
        if (uri == null) return
        if (entries == null) { _exportMessage.value = "导出状态已过期，请重新点击导出"; return }
        if (_exporting.value) return
        _exporting.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(ledgerCsv(entries, zone).toByteArray(Charsets.UTF_8))
                    } ?: error("无法写入所选位置")
                }
                _exportMessage.value = "已导出 ${entries.size} 笔记录"
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { _exportMessage.value = "导出失败：${error.localizedMessage}" }
            finally { _exporting.value = false }
        }
    }

    fun goCurrentPeriod() { _month.value = YearMonth.now(zone) }

    fun setRange(range: StatsRange) {
        _range.value = range
    }

    fun moveMonth(delta: Long) {
        _month.value = _month.value.plusMonths(delta)
    }

    fun moveYear(delta: Long) {
        _month.value = _month.value.plusYears(delta)
    }

    /** 编辑流水：与记账首页共用同一套校验 / 构造逻辑 */
    fun update(
        original: TransactionEntity,
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
            runCatching { ledgerRepository.saveTransaction(entity) }
                .onFailure { onResult(LedgerValidation.Error("保存失败：${it.localizedMessage}")) }
                .onSuccess { onResult(LedgerValidation.Success) }
        }
    }

    fun delete(transaction: TransactionEntity) {
        viewModelScope.launch { ledgerRepository.deleteTransaction(transaction) }
    }
}
