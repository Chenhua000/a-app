package com.example.myfirstapp.data.repository

import com.example.myfirstapp.data.local.AppDatabase
import com.example.myfirstapp.data.local.TimeSlotEntity
import kotlinx.coroutines.flow.Flow

class SlotRepository(private val database: AppDatabase) {

    fun observeSlots(): Flow<List<TimeSlotEntity>> = database.timeSlotDao().observeSlots()

    suspend fun listSlots(): List<TimeSlotEntity> = database.timeSlotDao().listSlots()

    /** 首次使用时写入默认节次，已有数据则不动。 */
    suspend fun ensureDefaults() {
        if (database.timeSlotDao().count() == 0) {
            AppDatabase.defaultSlots().forEach { database.timeSlotDao().insert(it) }
        }
    }

    suspend fun save(slot: TimeSlotEntity) {
        if (slot.id == 0L) {
            val nextIndex = (database.timeSlotDao().listSlots().maxOfOrNull { it.sortIndex } ?: -1) + 1
            database.timeSlotDao().insert(slot.copy(sortIndex = nextIndex))
        } else {
            database.timeSlotDao().update(slot)
        }
    }

    suspend fun saveAll(slots: List<TimeSlotEntity>) {
        slots.forEach { database.timeSlotDao().update(it) }
    }

    suspend fun delete(slot: TimeSlotEntity) {
        database.timeSlotDao().delete(slot)
    }

    /** 恢复成默认 12 节 */
    suspend fun resetToDefaults() {
        database.timeSlotDao().listSlots().forEach { database.timeSlotDao().delete(it) }
        AppDatabase.defaultSlots().forEach { database.timeSlotDao().insert(it) }
    }

    suspend fun moveUp(slot: TimeSlotEntity) {
        val all = database.timeSlotDao().listSlots()
        val index = all.indexOfFirst { it.id == slot.id }
        if (index <= 0) return
        swap(all, index, index - 1)
    }

    suspend fun moveDown(slot: TimeSlotEntity) {
        val all = database.timeSlotDao().listSlots()
        val index = all.indexOfFirst { it.id == slot.id }
        if (index < 0 || index >= all.lastIndex) return
        swap(all, index, index + 1)
    }

    private suspend fun swap(all: List<TimeSlotEntity>, a: Int, b: Int) {
        val first = all[a].copy(sortIndex = all[b].sortIndex)
        val second = all[b].copy(sortIndex = all[a].sortIndex)
        database.timeSlotDao().update(first)
        database.timeSlotDao().update(second)
    }
}
