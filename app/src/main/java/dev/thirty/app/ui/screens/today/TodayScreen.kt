package dev.thirty.app.ui.screens.today

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.thirty.app.backup.JourneyImage
import dev.thirty.app.data.local.DayStatus
import dev.thirty.app.data.repository.ChallengeWithProgress
import dev.thirty.app.ui.components.EmptyState
import dev.thirty.app.ui.components.PlanDropdown
import dev.thirty.app.ui.components.PrimaryCta
import dev.thirty.app.ui.components.StepLabel
import dev.thirty.app.ui.components.ThirtyProgress
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    vm: TodayViewModel,
    onGoJourney: () -> Unit,
    onGoOnboarding: () -> Unit,
    onCompleted30: (Long) -> Unit
) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        vm.refresh()
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                try { permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } catch (_: Exception) { }
            }
        }
    }

    // Re-sync every time the screen is shown (plan switched elsewhere,
    // task done from the widget, midnight passed, etc.).
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    if (s.loading) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(120.dp))
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (s.plans.isEmpty()) {
        EmptyState(
            title = "No journey yet.",
            body = "Choose one thing.\nGive it time.",
            ctaText = "START MY JOURNEY",
            onCta = onGoOnboarding,
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    val c = s.selected ?: return

    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val milestoneLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val sel = vm.state.value.selected ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bmp = JourneyImage.renderMilestone(
                    title = sel.challenge.title,
                    day = sel.todayDayNumber,
                    lengthDays = sel.challenge.lengthDays,
                    streak = sel.currentStreak,
                    best = sel.bestStreak
                )
                JourneyImage.writeToUri(context, uri, bmp)
            } catch (_: Exception) { }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        StepLabel("Today")
        Spacer(Modifier.height(16.dp))

        if (s.plans.size > 1) {
            PlanDropdown(
                plans = s.plans,
                selectedId = c.challenge.id,
                onSelect = { vm.select(it) }
            )
            Spacer(Modifier.height(12.dp))
        }

        PlanCard(
            c = c,
            note = s.note,
            noteSaved = s.noteSaved,
            milestone = vm.milestone(),
            error = s.error,
            onComplete = {
                vm.completeDay(
                    onCompleted30 = onCompleted30,
                    onSuccess = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            },
            onNoteChange = { vm.setNote(it) },
            onSaveNote = { vm.saveNote() },
            onRest = { vm.restDay() },
            onShareMilestone = {
                try {
                    milestoneLauncher.launch("thirty-day${c.todayDayNumber}-milestone.png")
                } catch (_: Exception) { }
            },
            onPickTime = { h, m ->
                vm.setReminderTime(context.applicationContext, c.challenge.id, h, m)
            },
            onGoJourney = onGoJourney
        )

        if (s.plans.size >= 1) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onGoOnboarding, modifier = Modifier.fillMaxWidth()) {
                Text("+ Start another plan", color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PlanCard(
    c: ChallengeWithProgress,
    note: String,
    noteSaved: Boolean,
    milestone: String?,
    error: String?,
    onComplete: () -> Unit,
    onNoteChange: (String) -> Unit,
    onSaveNote: () -> Unit,
    onRest: () -> Unit,
    onPickTime: (Int, Int) -> Unit,
    onShareMilestone: () -> Unit,
    onGoJourney: () -> Unit
) {
    var showRestConfirm by remember { mutableStateOf(false) }
    val dialogContext = LocalContext.current

    if (showRestConfirm) {
        AlertDialog(
            onDismissRequest = { showRestConfirm = false },
            title = { Text("Take a rest day?") },
            text = { Text("You get one rest day per journey. It keeps your streak alive and counts toward your journey. Sick, traveling, exhausted — no guilt.") },
            confirmButton = {
                TextButton(onClick = { showRestConfirm = false; onRest() }) {
                    Text("Rest today", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestConfirm = false }) { Text("Keep going") }
            }
        )
    }
    Text(
        "DAY ${c.todayDayNumber}",
        style = MaterialTheme.typography.displayLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(4.dp))
    Text(
        c.challenge.title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground
    )
    c.challenge.targetValue?.let { v ->
        val unit = c.challenge.targetUnit.orEmpty()
        Text(
            "Target: ${if (v % 1.0 == 0.0) v.toInt().toString() else v.toString()} $unit".trim(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Reminder daily ${"%02d:%02d".format(c.challenge.reminderHour, c.challenge.reminderMinute)}" +
                if (c.challenge.reminderEnabled) "" else " (off)",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = {
            TimePickerDialog(
                dialogContext,
                { _, h, m -> onPickTime(h, m) },
                c.challenge.reminderHour, c.challenge.reminderMinute, true
            ).show()
        }) {
            Text("Change", color = MaterialTheme.colorScheme.primary)
        }
    }
    Spacer(Modifier.height(16.dp))
    ThirtyProgress(completed = c.progressCount, total = c.challenge.lengthDays)
    Spacer(Modifier.height(10.dp))
    Text(
        "${c.currentStreak} day streak  ·  best ${c.bestStreak}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    milestone?.let { m ->
        Spacer(Modifier.height(14.dp))
        Text(
            m,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }

    val todayStatus = c.days.firstOrNull { it.dayNumber == c.todayDayNumber }?.status
    val isDone = todayStatus == DayStatus.COMPLETED
    val isRest = todayStatus == DayStatus.REST

    Spacer(Modifier.height(28.dp))
    if (isDone) {
        Text(
            "DONE.",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Day ${c.todayDayNumber} complete. See you tomorrow.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onGoJourney) {
            Text("View journey", color = MaterialTheme.colorScheme.primary)
        }
        if (c.todayDayNumber == 7 || c.todayDayNumber == 15 || c.todayDayNumber == c.challenge.lengthDays) {
            TextButton(onClick = onShareMilestone, modifier = Modifier.fillMaxWidth()) {
                Text("Share milestone", color = MaterialTheme.colorScheme.primary)
            }
        }
    } else if (isRest) {
        Text(
            "REST DAY.",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Recovery is part of the plan. See you tomorrow.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onGoJourney) {
            Text("View journey", color = MaterialTheme.colorScheme.primary)
        }
    } else {
        PrimaryCta(text = "I DID IT", onClick = onComplete)
        if (!c.restUsed) {
            TextButton(onClick = { showRestConfirm = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Take a rest day", color = MaterialTheme.colorScheme.primary)
            }
        }
    }

    error?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error)
    }

    Spacer(Modifier.height(28.dp))
    StepLabel("How did it feel today?")
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = note,
        onValueChange = onNoteChange,
        placeholder = { Text("A line or two…") },
        minLines = 3,
        maxLines = 6,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(4.dp))
    TextButton(onClick = onSaveNote) {
        Text(if (noteSaved) "Saved ✓" else "Save note", color = MaterialTheme.colorScheme.primary)
    }
}

