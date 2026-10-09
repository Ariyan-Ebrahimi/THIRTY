package dev.thirty.app.ui.screens.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import dev.thirty.app.data.repository.ThirtyRepository
import dev.thirty.app.domain.ChallengeLogic
import dev.thirty.app.notifications.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

val CATEGORIES = listOf("Fitness", "Health", "Learning", "Productivity", "Mind", "Personal", "Other")

data class GoalTemplate(val title: String, val value: String = "", val unit: String = "")

val TEMPLATES: Map<String, List<GoalTemplate>> = mapOf(
    "Fitness" to listOf(
        GoalTemplate("Walk 30 minutes every day", "30", "minutes"),
        GoalTemplate("Do 10 push-ups every day", "10", "push-ups"),
        GoalTemplate("Stretch for 10 minutes", "10", "minutes")
    ),
    "Health" to listOf(
        GoalTemplate("Drink 2 liters of water", "2", "liters"),
        GoalTemplate("Sleep before 11 PM"),
        GoalTemplate("No sugary drinks")
    ),
    "Learning" to listOf(
        GoalTemplate("Read 20 minutes every day", "20", "minutes"),
        GoalTemplate("Learn 10 new words", "10", "words"),
        GoalTemplate("Study for 30 minutes", "30", "minutes")
    ),
    "Productivity" to listOf(
        GoalTemplate("Deep work for 1 hour", "1", "hour"),
        GoalTemplate("No social media today"),
        GoalTemplate("Write tomorrow's top 3 tasks", "3", "tasks")
    ),
    "Mind" to listOf(
        GoalTemplate("Meditate for 10 minutes", "10", "minutes"),
        GoalTemplate("Write one journal page", "1", "page"),
        GoalTemplate("List 3 gratitudes", "3", "gratitudes")
    ),
    "Personal" to listOf(
        GoalTemplate("Make the bed"),
        GoalTemplate("Tidy for 10 minutes", "10", "minutes"),
        GoalTemplate("Call a loved one")
    ),
    "Other" to emptyList()
)

data class OnboardingUiState(
    val step: Int = 0, // 0 category, 1 goal, 2 length+target, 3 reminder, 4 commitment
    val category: String = "Personal",
    val title: String = "",
    val titleError: String? = null,
    val durationDays: Int = 30,
    val targetValueText: String = "",
    val targetUnit: String = "",
    val targetError: String? = null,
    val reminderEnabled: Boolean = true,
    val hour: Int = 20,
    val minute: Int = 0,
    val creating: Boolean = false,
    val createError: String? = null
)

class OnboardingViewModel(private val repo: ThirtyRepository) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    fun setCategory(c: String) = _state.update { it.copy(category = c) }
    fun setDuration(d: Int) {
        if (ChallengeLogic.isValidLength(d)) _state.update { it.copy(durationDays = d) }
    }
    fun applyTemplate(t: GoalTemplate) = _state.update {
        it.copy(
            title = t.title, titleError = null,
            targetValueText = t.value, targetUnit = t.unit,
            targetError = null, createError = null
        )
    }
    fun setTitle(t: String) = _state.update { it.copy(title = t, titleError = null, createError = null) }
    fun setTargetValue(t: String) = _state.update { it.copy(targetValueText = t, targetError = null) }
    fun setTargetUnit(u: String) = _state.update { it.copy(targetUnit = u) }
    fun setReminderEnabled(v: Boolean) = _state.update { it.copy(reminderEnabled = v) }
    fun setTime(h: Int, m: Int) = _state.update { it.copy(hour = h, minute = m) }

    fun next() {
        val s = _state.value
        when (s.step) {
            1 -> {
                val err = ChallengeLogic.validateTitle(s.title)
                if (err != null) {
                    _state.update { it.copy(titleError = err) }
                    return
                }
            }
            2 -> {
                val err = ChallengeLogic.validateTargetValue(s.targetValueText)
                if (err != null) {
                    _state.update { it.copy(targetError = err) }
                    return
                }
            }
        }
        _state.update { it.copy(step = (it.step + 1).coerceAtMost(4)) }
    }

    fun back() {
        _state.update { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
    }

    fun goTo(step: Int) = _state.update { it.copy(step = step.coerceIn(0, 4)) }

    /** Creates the plan and arms its alarms. Caller must navigate on success. */
    fun createChallenge(appContext: Context, onDone: () -> Unit) {
        val s = _state.value
        val titleErr = ChallengeLogic.validateTitle(s.title)
        if (titleErr != null) {
            _state.update { it.copy(titleError = titleErr, step = 1) }
            return
        }
        val targetErr = ChallengeLogic.validateTargetValue(s.targetValueText)
        if (targetErr != null) {
            _state.update { it.copy(targetError = targetErr, step = 2) }
            return
        }
        if (_state.value.creating) return
        _state.update { it.copy(creating = true, createError = null) }
        viewModelScope.launch {
            try {
                val targetValue = s.targetValueText.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
                val id = repo.createChallenge(
                    title = s.title,
                    category = s.category,
                    targetValue = targetValue,
                    targetUnit = s.targetUnit.trim().takeIf { it.isNotEmpty() },
                    reminderHour = s.hour,
                    reminderMinute = s.minute,
                    reminderEnabled = s.reminderEnabled,
                    lengthDays = s.durationDays
                )
                if (s.reminderEnabled) {
                    try {
                        ReminderScheduler.scheduleDaily(appContext, id, s.hour, s.minute)
                    } catch (_: Exception) { }
                    try {
                        val nudge = repo.currentSettings()
                        if (nudge.nudgeEnabled) {
                            ReminderScheduler.scheduleNudge(
                                appContext, id, nudge.nudgeHour, nudge.nudgeMinute
                            )
                        }
                    } catch (_: Exception) { }
                } else {
                    ReminderScheduler.cancel(appContext, id)
                    ReminderScheduler.cancelNudge(appContext, id)
                }
                onDone()
            } catch (e: Exception) {
                _state.update { it.copy(creating = false, createError = "Couldn\u2019t start your journey. Try again.") }
            }
        }
    }
}

class OnboardingViewModelFactory(private val repo: ThirtyRepository) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        OnboardingViewModel(repo) as T
}
