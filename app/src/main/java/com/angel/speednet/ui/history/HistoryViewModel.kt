package com.angel.speednet.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.angel.speednet.SpeednetApplication
import com.angel.speednet.domain.model.Measurement
import com.angel.speednet.domain.repository.MeasurementRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: MeasurementRepository
) : ViewModel() {

    val measurements: StateFlow<List<Measurement>> = repository.observeAll()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun clearHistory() {
        viewModelScope.launch {
            repository.deleteAll()
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpeednetApplication
                HistoryViewModel(app.container.measurementRepository)
            }
        }
    }
}
