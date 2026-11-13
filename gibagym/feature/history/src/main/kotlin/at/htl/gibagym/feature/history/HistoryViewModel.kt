package at.htl.gibagym.feature.history

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import at.htl.gibagym.core.model.TrainingSession
import at.htl.gibagym.core.model.TrainingSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.launch

/**
 * Zustand des Verlaufs (/LF60/).
 *
 * [laedt] ist kein Zierrat: Sobald der Speicher eine echte Datenbank ist, dauert
 * das Laden messbar, und die Anzeige braucht dafür einen Zustand. Ihn erst
 * später einzuführen hieße, die Bildschirmlogik noch einmal anzufassen.
 */
@Immutable
data class HistoryUiState(
    val laedt: Boolean = true,
    val sessions: List<TrainingSession> = emptyList(),
) {
    val einheiten: Int get() = sessions.size

    val saubereQuote: Int
        get() {
            val gesamt = sessions.sumOf { it.repetitions }
            if (gesamt == 0) return 0
            return sessions.sumOf { it.cleanRepetitions } * 100 / gesamt
        }
}

class HistoryViewModel(
    private val repository: TrainingSessionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HistoryUiState())
    val state: StateFlow<HistoryUiState> = _state.asStateFlow()

    init {
        repository.sessions
            .onEach { liste -> _state.value = HistoryUiState(laedt = false, sessions = liste) }
            .launchIn(viewModelScope)
    }

    fun loeschen(id: String) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun allesLoeschen() {
        viewModelScope.launch { repository.clear() }
    }

    companion object {
        fun factory(repository: TrainingSessionRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { HistoryViewModel(repository) }
        }
    }
}
