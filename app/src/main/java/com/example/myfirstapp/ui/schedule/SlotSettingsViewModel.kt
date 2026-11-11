package com.example.myfirstapp.ui.schedule

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.myfirstapp.MyFirstApp
import com.example.myfirstapp.data.local.TimeSlotEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SlotSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as MyFirstApp).container.slotRepository

    val slots: StateFlow<List<TimeSlotEntity>> = repository.observeSlots()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(slot: TimeSlotEntity) {
        viewModelScope.launch { repository.save(slot) }
    }

    fun delete(slot: TimeSlotEntity) {
        viewModelScope.launch { repository.delete(slot) }
    }

    fun moveUp(slot: TimeSlotEntity) {
        viewModelScope.launch { repository.moveUp(slot) }
    }

    fun moveDown(slot: TimeSlotEntity) {
        viewModelScope.launch { repository.moveDown(slot) }
    }

    fun resetDefaults() {
        viewModelScope.launch {
            repository.resetToDefaults()
        }
    }
}
