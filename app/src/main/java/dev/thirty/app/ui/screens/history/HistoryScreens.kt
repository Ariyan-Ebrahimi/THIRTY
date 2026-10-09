package dev.thirty.app.ui.screens.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.thirty.app.data.local.ChallengeEntity
import dev.thirty.app.data.local.DailyProgressEntity
import dev.thirty.app.data.repository.ThirtyRepository
import dev.thirty.app.domain.ChallengeLogic
import dev.thirty.app.notifications.ReminderScheduler
import dev.thirty.app.ui.components.EmptyState
import dev.thirty.app.ui.components.PrimaryCta
import dev.thirty.app.ui.components.StepLabel
import dev.thirty.app.util.DateUtils
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repository: ThirtyRepository,
    onBack: () -> Unit,
    onOpen: (Long) -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<Pair<ChallengeEntity, Int>>>(emptyList()) }
    var bestMap by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    suspend fun load() {
        try {
            val hist: List<ChallengeEntity> = repository.historyFlow.first()
            val withCounts: List<Pair<ChallengeEntity, Int>> = hist.map { c: ChallengeEntity ->
                val full = repository.challengeWithProgress(c.id)
                val done = full?.completedCount ?: 0
                c to done
            }
            items = withCounts
            bestMap = hist.associate { c: ChallengeEntity ->
                c.id to (repository.challengeWithProgress(c.id)?.bestStreak ?: 0)
            }
        } catch (_: Exception) { }
        loading = false
    }

    LaunchedEffect(Unit) { load() }

    // A plan just finished becomes history — reload whenever shown.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                loading = items.isEmpty()
                scope.launch { load() }
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Past journeys") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }
        if (items.isEmpty()) {
            EmptyState(
                title = "Your journeys will appear here.",
                body = "Finish or close a journey and it will show up.",
                modifier = Modifier.padding(padding).fillMaxSize()
            )
            return@Scaffold
        }
        LazyColumn(Modifier.padding(padding).padding(horizontal = 24.dp)) {
            items(items, key = { it.first.id }) { (c, done) ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onOpen(c.id) }
                        .padding(vertical = 16.dp)
                ) {
                    Text(c.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$done / ${c.lengthDays} completed  ·  best streak ${bestMap[c.id] ?: 0}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HistoryDetailScreen(
    repository: ThirtyRepository,
    challengeId: Long,
    onBack: () -> Unit,
    onRerun: () -> Unit = {}
) {
    var loading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var statuses by remember { mutableStateOf<List<dev.thirty.app.data.local.DayStatus>>(emptyList()) }
    var dayRows by remember { mutableStateOf<List<DailyProgressEntity>>(emptyList()) }
    var done by remember { mutableStateOf(0) }
    var best by remember { mutableStateOf(0) }
    var lengthDays by remember { mutableStateOf(30) }
    var stats by remember { mutableStateOf<ChallengeLogic.JourneyStats?>(null) }
    var rerunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(challengeId) {
        try {
            val full = repository.challengeWithProgress(challengeId)
            if (full != null) {
                title = full.challenge.title
                lengthDays = full.challenge.lengthDays
                dayRows = full.days.sortedBy { it.dayNumber }
                statuses = dayRows.map { it.status }
                done = ChallengeLogic.completedCount(statuses)
                best = ChallengeLogic.bestStreak(statuses)
                stats = ChallengeLogic.journeyStats(
                    statuses = statuses,
                    epochDays = dayRows.map { it.epochDay },
                    today = DateUtils.today()
                )
            }
        } catch (_: Exception) { }
        loading = false
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (title.isBlank()) "Journey" else title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Column(Modifier.padding(padding).padding(24.dp)) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }
        Column(Modifier.padding(padding).padding(24.dp)) {
            StepLabel("Your $lengthDays days")
            Spacer(Modifier.height(8.dp))
            Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                "$done / $lengthDays completed  ·  best streak $best",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            dev.thirty.app.ui.components.ThirtyCalendar(
                days = dayRows,
                todayDayNumber = null,
                onDayClick = null
            )

            stats?.let { st ->
                Spacer(Modifier.height(28.dp))
                StepLabel("Consistency")
                Spacer(Modifier.height(4.dp))
                StatRow("Days kept", "${st.keptPercent}%")
                StatRow("Best weekday", st.bestWeekday.ifEmpty { "—" })
                StatRow("Average streak", "${st.averageStreak} days")
            }

            Spacer(Modifier.height(28.dp))
            PrimaryCta(
                text = if (rerunning) "STARTING…" else "RUN IT AGAIN",
                enabled = !rerunning,
                onClick = {
                    if (rerunning) return@PrimaryCta
                    rerunning = true
                    scope.launch {
                        try {
                            val newId = repository.rerunChallenge(challengeId)
                            if (newId == null) {
                                rerunning = false
                                return@launch
                            }
                            val fresh = repository.challengeWithProgress(newId)?.challenge
                            if (fresh != null && fresh.reminderEnabled) {
                                try {
                                    ReminderScheduler.scheduleDaily(
                                        context, newId, fresh.reminderHour, fresh.reminderMinute
                                    )
                                    val st = repository.currentSettings()
                                    if (st.nudgeEnabled) {
                                        ReminderScheduler.scheduleNudge(
                                            context, newId, st.nudgeHour, st.nudgeMinute
                                        )
                                    }
                                } catch (_: Exception) { }
                            }
                            onRerun()
                        } catch (_: Exception) {
                            rerunning = false
                        }
                    }
                }
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Starts a fresh run of this plan, today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    androidx.compose.foundation.layout.Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().padding(vertical = 15.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                label.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
