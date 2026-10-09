package dev.thirty.app.ui.screens.journey

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.thirty.app.data.repository.ChallengeWithProgress
import dev.thirty.app.data.repository.ThirtyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class JourneyUiState(
    val loading: Boolean = true,
    val plans: List<ChallengeWithProgress> = emptyList(),
    val selectedId: Long? = null,
    val selectedDay: Int? = null,
    val noteDraft: String = "",
    val noteSaved: Boolean = false,
    val showEndConfirm: Boolean = false,
    val error: String? = null
) {
    val selected: ChallengeWithProgress? get() = plans.firstOrNull { it.challenge.id == selectedId }
        ?: plans.firstOrNull()
}

class JourneyViewModel(private val repo: ThirtyRepository) : ViewModel() {
    private val _state = MutableStateFlow(JourneyUiState())
    val state: StateFlow<JourneyUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            // Soft refresh: spinner only on first load, so switching plans
            // or returning from another tab never blanks the grid.
            _state.update { it.copy(loading = it.plans.isEmpty(), error = null) }
            try {
                repo.refreshDayStates()
                // Finished / ended plans must never show here: only active ones.
                val plans = repo.activeListWithProgress()
                    .filter { it.challenge.status == dev.thirty.app.data.local.ChallengeStatus.ACTIVE }
                val prev = _state.value.selectedId
                // The remembered selection is global: it wins so Journey and
                // Today always show the same plan (never "first journey").
                val saved = repo.selectedPlanId()
                val sel = plans.firstOrNull { it.challenge.id == saved }?.challenge?.id
                    ?: plans.firstOrNull { it.challenge.id == prev }?.challenge?.id
                    ?: plans.firstOrNull()?.challenge?.id
                if (sel != null && sel != saved) repo.setSelectedPlanId(sel)
                _state.update { it.copy(loading = false, plans = plans, selectedId = sel) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Couldn\u2019t load journey.") }
            }
        }
    }

    fun selectPlan(id: Long) {
        _state.update { it.copy(selectedId = id, selectedDay = null, noteDraft = "", noteSaved = false) }
        viewModelScope.launch { repo.setSelectedPlanId(id) }
    }

    fun selectDay(day: Int) {
        val c = _state.value.selected ?: return
        val row = c.days.firstOrNull { it.dayNumber == day } ?: return
        _state.update { it.copy(selectedDay = day, noteDraft = row.note, noteSaved = false) }
    }

    fun dismissDay() = _state.update { it.copy(selectedDay = null, noteDraft = "", noteSaved = false) }

    fun setNote(t: String) {
        if (t.length <= 1000) _state.update { it.copy(noteDraft = t, noteSaved = false) }
    }

    fun saveNote() {
        val c = _state.value.selected ?: return
        val day = _state.value.selectedDay ?: return
        viewModelScope.launch {
            try {
                repo.saveNote(c.challenge.id, day, _state.value.noteDraft)
                _state.update { it.copy(noteSaved = true) }
                val plans = repo.activeListWithProgress()
                val row = plans.firstOrNull { it.challenge.id == c.challenge.id }
                    ?.days?.firstOrNull { it.dayNumber == day }
                _state.update { it.copy(plans = plans, noteDraft = row?.note.orEmpty()) }
            } catch (_: Exception) { }
        }
    }

    fun askEndJourney() = _state.update { it.copy(showEndConfirm = true) }
    fun dismissEndJourney() = _state.update { it.copy(showEndConfirm = false) }

    fun endJourney() {
        val c = _state.value.selected ?: return
        viewModelScope.launch {
            try {
                repo.cancelChallenge(c.challenge.id)
            } catch (_: Exception) { }
            _state.update { it.copy(showEndConfirm = false) }
            refresh()
        }
    }
}

class JourneyViewModelFactory(private val repo: ThirtyRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = JourneyViewModel(repo) as T
}
