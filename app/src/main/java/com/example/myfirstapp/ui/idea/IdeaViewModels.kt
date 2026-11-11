package com.example.myfirstapp.ui.idea

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.local.IdeaEntity
import com.example.myfirstapp.data.repository.IdeaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** 灵感列表：直接观察数据库，置顶恒在最前，其余按所选方式排序 */
class IdeaListViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MyFirstApp).container.ideaRepository

    val ideas: StateFlow<List<IdeaEntity>> = repository.observeIdeas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 列表内直接置顶 / 取消置顶，落库后排序自动刷新 */
    fun togglePin(idea: IdeaEntity) {
        viewModelScope.launch { repository.save(idea.copy(pinned = !idea.pinned)) }
    }

    /** 列表内直接删除 */
    fun delete(idea: IdeaEntity) {
        viewModelScope.launch { repository.delete(idea) }
    }
}

/**
 * 灵感编辑器：借鉴 Simplenote/Bear 的「边写边存」——每次改动直接落库，没有「保存」按钮。
 * ideaId == 0 表示新建；第一次出现非空内容时才写入数据库，避免留下空白便签。
 */
class IdeaEditorViewModel(
    application: Application,
    private val ideaId: Long
) : AndroidViewModel(application) {
    private val repository = (application as MyFirstApp).container.ideaRepository

    /** ideaId <= 0 表示新建；用传入的唯一值仅作为 ViewModel 的隔离键，落库时统一从 0 插入 */
    private val isNew = ideaId <= 0L
    private var persistedId: Long = if (isNew) 0L else ideaId
    private val saveMutex = Mutex()

    private val _title = MutableStateFlow("")
    val title: StateFlow<String> = _title

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content

    private val _colorIndex = MutableStateFlow(0)
    val colorIndex: StateFlow<Int> = _colorIndex

    private val _pinned = MutableStateFlow(false)
    val pinned: StateFlow<Boolean> = _pinned

    private val _createdAt = MutableStateFlow(0L)
    val createdAt: StateFlow<Long> = _createdAt

    private val _updatedAt = MutableStateFlow(0L)
    val updatedAt: StateFlow<Long> = _updatedAt

    init {
        if (!isNew) {
            viewModelScope.launch {
                repository.getIdea(ideaId)?.let { idea ->
                    _title.value = idea.title
                    _content.value = idea.content
                    _colorIndex.value = idea.colorIndex
                    _pinned.value = idea.pinned
                    _createdAt.value = idea.createdAtMillis
                    _updatedAt.value = idea.updatedAtMillis
                }
            }
        }
    }

    fun updateTitle(value: String) {
        _title.value = value
        persist()
    }

    fun updateContent(value: String) {
        _content.value = value
        persist()
    }

    fun setColor(index: Int) {
        _colorIndex.value = index
        persist()
    }

    fun togglePin() {
        _pinned.value = !_pinned.value
        persist()
    }

    /** 仅当存在非空内容时才写入，保证「打开即写」但不留空便签 */
    private fun persist() {
        val t = _title.value
        val c = _content.value
        if (t.isBlank() && c.isBlank()) return
        val now = System.currentTimeMillis()
        _updatedAt.value = now
        viewModelScope.launch {
            saveMutex.withLock {
                val isFirstSave = persistedId == 0L
                val entity = IdeaEntity(
                    id = persistedId,
                    title = t.trim(),
                    content = c,
                    colorIndex = _colorIndex.value,
                    pinned = _pinned.value
                )
                val savedId = repository.save(entity)
                if (isFirstSave) {
                    persistedId = savedId
                    _createdAt.value = now
                }
            }
        }
    }

    fun deleteAndBack(onBack: () -> Unit) {
        viewModelScope.launch {
            if (persistedId != 0L) repository.deleteById(persistedId)
            onBack()
        }
    }

    companion object {
        fun factory(application: Application, ideaId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    IdeaEditorViewModel(application, ideaId) as T
            }
    }
}
