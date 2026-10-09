package dev.thirty.app.ui.screens.journey

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.thirty.app.backup.JourneyImage
import dev.thirty.app.ui.components.EmptyState
import dev.thirty.app.ui.components.GhostCta
import dev.thirty.app.ui.components.PlanDropdown
import dev.thirty.app.ui.components.StepLabel
import dev.thirty.app.ui.components.ThirtyProgress
import dev.thirty.app.util.DateUtils
import kotlinx.coroutines.launch
import androidx.compose.runtime.LaunchedEffect

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun JourneyScreen(
    vm: JourneyViewModel,
    onStartNew: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val s by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // The tab is reused when you come back to it, so re-read the plans every
    // time it is shown: a plan finished on Today must vanish from here too.
    LaunchedEffect(Unit) { vm.refresh() }

    // Re-sync the grid every time the screen is shown, so a task just
    // completed on Today (or the widget) is reflected immediately.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val sel = vm.state.value.selected ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bmp = JourneyImage.render(
                    sel.challenge.title,
                    sel.days.sortedBy { it.dayNumber }.map { it.status },
                    sel.todayDayNumber,
                    sel.currentStreak,
                    sel.bestStreak,
                    sel.challenge.lengthDays
                )
                JourneyImage.writeToUri(context, uri, bmp)
            } catch (_: Exception) { }
        }
    }

    if (s.loading) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
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
            onCta = onStartNew,
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        )
        return
    }
    val c = s.selected ?: return

    if (s.showEndConfirm) {
        AlertDialog(
            onDismissRequest = { vm.dismissEndJourney() },
            title = { Text("End this journey?") },
            text = { Text("\u201C${c.challenge.title}\u201D moves to your past journeys. Its days and notes stay saved.") },
            confirmButton = {
                TextButton(onClick = { vm.endJourney() }) {
                    Text("End journey", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { vm.dismissEndJourney() }) { Text("Keep going") }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        StepLabel("Journey")
        Spacer(Modifier.height(8.dp))
        Text("YOUR ${c.challenge.lengthDays} DAYS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        if (s.plans.size > 1) {
            PlanDropdown(
                plans = s.plans,
                selectedId = c.challenge.id,
                onSelect = { vm.selectPlan(it) }
            )
            Spacer(Modifier.height(12.dp))
        }

        Text(c.challenge.title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        ThirtyProgress(completed = c.progressCount, total = c.challenge.lengthDays)
        Spacer(Modifier.height(10.dp))
        Text(
            "${c.completedCount} of ${c.challenge.lengthDays} done  ·  ${c.currentStreak} day streak",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Filled = done · ring = today · grey = missed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))
        dev.thirty.app.ui.components.ThirtyCalendar(
            days = c.days,
            todayDayNumber = c.todayDayNumber,
            onDayClick = { vm.selectDay(it) }
        )
        Spacer(Modifier.height(24.dp))
        GhostCta(text = "START ANOTHER PLAN", onClick = onStartNew)
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = {
            try {
                shareLauncher.launch("thirty-day${c.todayDayNumber}-${c.challenge.title.take(20)}.png")
            } catch (_: Exception) { }
        }, modifier = Modifier.fillMaxWidth()) {
            Text("Share journey image", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
            Text("View past journeys", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = { vm.askEndJourney() }, modifier = Modifier.fillMaxWidth()) {
            Text("End this journey", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(32.dp))
    }

    val sel = s.selectedDay
    if (sel != null) {
        val row = c.days.firstOrNull { it.dayNumber == sel }
        ModalBottomSheet(
            onDismissRequest = { vm.dismissDay() },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(Modifier.padding(24.dp)) {
                Text("DAY $sel", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                row?.let {
                    Text(
                        DateUtils.formatMedium(DateUtils.fromEpochDay(it.epochDay)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when (it.status) {
                            dev.thirty.app.data.local.DayStatus.COMPLETED -> "Completed ✓"
                            dev.thirty.app.data.local.DayStatus.MISSED -> "Missed — that\u2019s okay. One day doesn\u2019t define the journey."
                            else -> if (sel == c.todayDayNumber) "Today — show up." else "Upcoming."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(Modifier.height(16.dp))
                StepLabel("Journal — how did it feel? (optional)")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = s.noteDraft,
                    onValueChange = { vm.setNote(it) },
                    placeholder = { Text("A line or two…") },
                    minLines = 3,
                    maxLines = 6,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { vm.dismissDay() }) {
                        Text("Close", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { vm.saveNote() }) {
                        Text(if (s.noteSaved) "Saved ✓" else "Save note", color = MaterialTheme.colorScheme.primary)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

