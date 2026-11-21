package com.angel.speednet.ui.results

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.angel.speednet.SpeednetApplication
import com.angel.speednet.domain.model.Measurement
import com.angel.speednet.domain.repository.MeasurementRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ResultsUiState {
    data object Loading : ResultsUiState
    data class Success(val measurement: Measurement) : ResultsUiState
    data class Error(val message: String) : ResultsUiState
}

class ResultsViewModel(
    private val repository: MeasurementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ResultsUiState>(ResultsUiState.Loading)
    val uiState: StateFlow<ResultsUiState> = _uiState.asStateFlow()

    fun load(id: Long) {
        if (id < 0) {
            _uiState.value = ResultsUiState.Error("Medición no encontrada")
            return
        }
        _uiState.value = ResultsUiState.Loading
        viewModelScope.launch {
            val measurement = repository.getById(id)
            _uiState.value = if (measurement != null) {
                ResultsUiState.Success(measurement)
            } else {
                ResultsUiState.Error("Medición no encontrada")
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpeednetApplication
                ResultsViewModel(app.container.measurementRepository)
            }
        }
    }
}
