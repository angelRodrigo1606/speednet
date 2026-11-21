package com.angel.speednet.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.angel.speednet.SpeednetApplication
import com.angel.speednet.domain.model.TestStage
import com.angel.speednet.domain.repository.MeasurementRepository
import com.angel.speednet.domain.repository.SpeedTestRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface HomeUiState {
    data class Idle(val lastScore: Int? = null) : HomeUiState
    data class Measuring(val stage: TestStage, val progress: Float) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

class HomeViewModel(
    private val speedTestRepository: SpeedTestRepository,
    private val measurementRepository: MeasurementRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _navigateToResult = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val navigateToResult: SharedFlow<Long> = _navigateToResult.asSharedFlow()

    private var testJob: Job? = null
    private var lastScore: Int? = null

    init {
        viewModelScope.launch {
            measurementRepository.observeAll().collect { measurements ->
                lastScore = measurements.firstOrNull()?.score
                if (_uiState.value is HomeUiState.Idle) {
                    _uiState.value = HomeUiState.Idle(lastScore)
                }
            }
        }
    }

    fun startTest() {
        if (testJob?.isActive == true) return
        testJob = viewModelScope.launch {
            _uiState.value = HomeUiState.Measuring(TestStage.CHECKING_NETWORK, 0f)
            try {
                val measurement = speedTestRepository.runTest { stage, progress ->
                    _uiState.value = HomeUiState.Measuring(stage, progress.coerceIn(0f, 1f))
                }
                lastScore = measurement.score
                _uiState.value = HomeUiState.Idle(measurement.score)
                measurement.id?.let { _navigateToResult.emit(it) }
            } catch (cancellation: CancellationException) {
                _uiState.value = HomeUiState.Idle(lastScore)
                throw cancellation
            } catch (error: Exception) {
                _uiState.value = HomeUiState.Error(
                    error.message ?: "No se pudo completar la medición"
                )
            }
        }
    }

    fun cancelTest() {
        testJob?.cancel()
        testJob = null
        _uiState.value = HomeUiState.Idle(lastScore)
    }

    fun dismissError() {
        _uiState.value = HomeUiState.Idle(lastScore)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SpeednetApplication
                HomeViewModel(
                    speedTestRepository = app.container.speedTestRepository,
                    measurementRepository = app.container.measurementRepository
                )
            }
        }
    }
}
