package dev.thirty.app.ui.screens.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.thirty.app.data.local.DayStatus
import dev.thirty.app.data.repository.ChallengeWithProgress
import dev.thirty.app.data.repository.ThirtyRepository
import dev.thirty.app.domain.ChallengeLogic
import dev.thirty.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TodayUiState(
    val loading: Boolean = true,
    val plans: List<ChallengeWithProgress> = emptyList(),
    val selectedId: Long? = null,
    val note: String = "",
    val noteSaved: Boolean = false,
    val error: String? = null
) {
    val selected: ChallengeWithProgress? get() = plans.firstOrNull { it.challenge.id == selectedId }
        ?: plans.firstOrNull()
    val todayRow get() = selected?.let { c ->
        c.days.firstOrNull { it.dayNumber == c.todayDayNumber }
    }
    val isDone get() = todayRow?.status == DayStatus.COMPLETED
}

class TodayViewModel(private val repo: ThirtyRepository) : ViewModel() {
    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            // Soft refresh: only show the spinner on first load, never flash it
            // when returning from another tab (which also preserves typing).
            _state.update { it.copy(loading = it.plans.isEmpty(), error = null) }
            try {
                repo.refreshDayStates()
                // Finished / ended plans must never show on the home page.
                val plans = repo.activeListWithProgress()
                    .filter { it.challenge.status == dev.thirty.app.data.local.ChallengeStatus.ACTIVE }
                val prev = _state.value.selectedId
                // The remembered selection is global: it wins so Today and
                // Journey always show the same plan.
                val saved = repo.selectedPlanId()
                val sel = plans.firstOrNull { it.challenge.id == saved }?.challenge?.id
                    ?: plans.firstOrNull { it.challenge.id == prev }?.challenge?.id
                    ?: plans.firstOrNull()?.challenge?.id
                if (sel != null && sel != saved) repo.setSelectedPlanId(sel)
                val selected = plans.firstOrNull { it.challenge.id == sel }
                val freshNote = selected?.days
                    ?.firstOrNull { d -> d.dayNumber == (selected.todayDayNumber) }
                    ?.note.orEmpty()
                _state.update {
                    it.copy(
                        loading = false,
                        plans = plans,
                        selectedId = sel,
                        // Don't wipe an unsaved note the user is still typing.
                        note = if (sel == prev) it.note else freshNote,
                        noteSaved = if (sel == prev) it.noteSaved else false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Couldn\u2019t load today. Try again.") }
            }
        }
    }

    fun select(id: Long) {
        val plan = _state.value.plans.firstOrNull { it.challenge.id == id } ?: return
        _state.update {
            it.copy(
                selectedId = id,
                note = plan.days.firstOrNull { d -> d.dayNumber == plan.todayDayNumber }?.note.orEmpty(),
                noteSaved = false,
                error = null
            )
        }
        viewModelScope.launch { repo.setSelectedPlanId(id) }
    }

    fun completeDay(onCompleted30: (Long) -> Unit, onSuccess: () -> Unit = {}) {
        val c = _state.value.selected ?: return
        viewModelScope.launch {
            try {
                val ok = repo.completeToday(c.challenge.id, _state.value.note)
                if (!ok) {
                    _state.update { it.copy(error = "Couldn\u2019t save. Try again.") }
                    return@launch
                }
                onSuccess()
                val updated = repo.activeListWithProgress()
                val stillActive = updated.firstOrNull { it.challenge.id == c.challenge.id }
                if (stillActive == null) {
                    // Plan finished (day 30 done) and moved to history.
                    _state.update { it.copy(plans = updated) }
                    onCompleted30(c.challenge.id)
                    return@launch
                }
                _state.update { st ->
                    st.copy(
                        plans = updated,
                        noteSaved = false
                    )
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = "Couldn\u2019t save. Try again.") }
            }
        }
    }

    fun setNote(t: String) {
        if (t.length > 1000) return
        _state.update { it.copy(note = t, noteSaved = false) }
    }

    fun saveNote() {
        val c = _state.value.selected ?: return
        viewModelScope.launch {
            try {
                repo.saveNote(c.challenge.id, c.todayDayNumber, _state.value.note)
                _state.update { it.copy(noteSaved = true) }
            } catch (_: Exception) { }
        }
    }

    fun restDay() {
        val c = _state.value.selected ?: return
        viewModelScope.launch {
            try {
                repo.restToday(c.challenge.id)
                refresh()
            } catch (_: Exception) {
                _state.update { it.copy(error = "Couldn\u2019t save. Try again.") }
            }
        }
    }

    fun setReminderTime(appContext: Context, id: Long, h: Int, m: Int) {
        viewModelScope.launch {
            try {
                repo.setChallengeReminderTime(id, h, m)
                repo.setChallengeReminderEnabled(id, true)
                ReminderScheduler.scheduleDaily(appContext, id, h, m)
                refresh()
            } catch (_: Exception) {
                _state.update { it.copy(error = "Couldn\u2019t save. Try again.") }
            }
        }
    }

    fun milestone(): String? {
        val c = _state.value.selected ?: return null
        return ChallengeLogic.milestoneFor(c.todayDayNumber, c.challenge.lengthDays)
    }
}

class TodayViewModelFactory(private val repo: ThirtyRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = TodayViewModel(repo) as T
}
