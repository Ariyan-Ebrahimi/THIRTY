package dev.thirty.app.ui.screens.settings

import android.Manifest
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.thirty.app.ThirtyApp
import dev.thirty.app.backup.BackupDto
import dev.thirty.app.backup.BackupManager
import dev.thirty.app.backup.BackupResult
import dev.thirty.app.data.datastore.AppSettings
import dev.thirty.app.data.datastore.ThemeMode
import dev.thirty.app.data.local.ChallengeEntity
import dev.thirty.app.data.repository.ThirtyRepository
import dev.thirty.app.notifications.ReminderScheduler
import dev.thirty.app.ui.components.GhostCta
import dev.thirty.app.ui.components.StepLabel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

@Composable
fun SettingsScreen(
    repository: ThirtyRepository,
    onOpenHistory: () -> Unit
) {
    val factory = remember { SettingsViewModelFactory(repository) }
    val vm: SettingsViewModel = viewModel(factory = factory)
    val plans by vm.plansFlow.collectAsState(initial = emptyList())
    val nudgeSettings by vm.settingsFlow.collectAsState(initial = AppSettings())
    val context = LocalContext.current
    val appContext = context.applicationContext
    val scope = rememberCoroutineScope()

    var backupMsg by remember { mutableStateOf<String?>(null) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var exactAllowed by remember { mutableStateOf(true) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) {
                exactAllowed = ReminderScheduler.hasExactAlarmPermission(appContext)
            }
        }
        lifecycle.addObserver(obs)
        onDispose { lifecycle.removeObserver(obs) }
    }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) backupMsg = "System notifications are off — enable them in system settings."
    }

    fun ensurePostNotifPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                try { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) } catch (_: Exception) { }
                return false
            }
        }
        return true
    }

    suspend fun buildExportContent(): String? {
        return try {
            val active = repository.activeListWithProgress()
            val histList: List<ChallengeEntity> = try {
                repository.historyFlow.first()
            } catch (_: Exception) { emptyList() }
            val allChallenges: List<ChallengeEntity> =
                active.map { it.challenge }.plus(histList)
            val daysMap = allChallenges.associate { c: ChallengeEntity ->
                c.id to (repository.challengeWithProgress(c.id)?.days.orEmpty())
            }
            BackupManager.buildExport(allChallenges, daysMap, repository.currentSettings())
        } catch (_: Exception) { null }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val content = buildExportContent()
            if (content == null) {
                backupMsg = "Export failed. Try again."
                return@launch
            }
            val ok = BackupManager.exportToUri(context, uri, content)
            backupMsg = if (ok) "Backup exported." else "Export failed. Try again."
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val raw = BackupManager.readFromUri(context, uri)
            if (raw == null) {
                backupMsg = "Couldn\u2019t read that file."
                return@launch
            }
            when (val v = BackupManager.parseAndValidate(raw)) {
                is BackupResult.Err -> backupMsg = v.message
                is BackupResult.Ok -> {
                    try {
                        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<BackupDto>(raw)
                        val (challenges, dayPairs, appSettings) = BackupManager.toEntities(dto)
                        repository.clearAll()
                        val idMap = mutableMapOf<Int, Long>()
                        challenges.forEachIndexed { index, c ->
                            val newId = repository.createChallenge(
                                title = c.title,
                                category = c.category,
                                targetValue = c.targetValue,
                                targetUnit = c.targetUnit,
                                reminderHour = c.reminderHour,
                                reminderMinute = c.reminderMinute,
                                reminderEnabled = c.reminderEnabled,
                                lengthDays = c.lengthDays.coerceIn(1, 366),
                                startDate = dev.thirty.app.util.DateUtils.fromEpochDay(c.startEpochDay)
                            )
                            idMap[index] = newId
                        }
                        val db = (appContext as ThirtyApp).database
                        dayPairs.forEach { (idx, day) ->
                            val newId = idMap[idx] ?: return@forEach
                            try {
                                val existing = db.progressDao().dayOnce(newId, day.dayNumber)
                                if (existing != null) {
                                    db.progressDao().update(
                                        existing.copy(
                                            status = day.status,
                                            completedAtMillis = day.completedAtMillis,
                                            note = day.note
                                        )
                                    )
                                }
                            } catch (_: Exception) { }
                        }
                        // Restore each plan's original status (active plans stay active).
                        challenges.forEachIndexed { idx, c ->
                            val newId = idMap[idx] ?: return@forEachIndexed
                            try {
                                val ch = db.challengeDao().byId(newId)
                                if (ch != null && ch.status != c.status) {
                                    db.challengeDao().update(ch.copy(status = c.status))
                                }
                            } catch (_: Exception) { }
                        }
                        try {
                            repository.setNotificationsEnabled(appSettings.notificationsEnabled)
                            repository.setNudgeEnabled(appSettings.nudgeEnabled)
                            repository.setNudgeTime(appSettings.nudgeHour, appSettings.nudgeMinute)
                            val s = repository.currentSettings()
                            repository.activeListWithProgress().forEach { p ->
                                if (p.challenge.reminderEnabled) {
                                    ReminderScheduler.scheduleDaily(
                                        appContext, p.challenge.id,
                                        p.challenge.reminderHour, p.challenge.reminderMinute
                                    )
                                    if (s.nudgeEnabled) {
                                        ReminderScheduler.scheduleNudge(
                                            appContext, p.challenge.id,
                                            s.nudgeHour, s.nudgeMinute
                                        )
                                    }
                                }
                            }
                        } catch (_: Exception) { }
                        repository.refreshDayStates()
                        backupMsg = "Backup restored: ${v.challenges} plans, ${v.days} days."
                    } catch (e: Exception) {
                        backupMsg = "Restore failed: ${e.message ?: "unknown error"}"
                    }
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear all data?") },
            text = { Text("This deletes all your plans, journeys and notes on this device. This can\u2019t be undone. Consider exporting a backup first.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirm = false
                    vm.clearAll(appContext) { backupMsg = "All data cleared." }
                }) { Text("Delete everything", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Keep my data") }
            }
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        StepLabel("Settings")
        Spacer(Modifier.height(8.dp))
        Text("SETTINGS", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(20.dp))

        if (showPasswordDialog) {
            PasswordDialog(
                onDismiss = { showPasswordDialog = false },
                onSave = { pw ->
                    vm.setLockPassword(pw) { showPasswordDialog = false }
                }
            )
        }

        SectionCard(title = "Appearance") {
            Text("Theme", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeChip("System", nudgeSettings.themeMode == ThemeMode.SYSTEM, Modifier.weight(1f)) {
                    vm.setTheme(ThemeMode.SYSTEM)
                }
                ThemeChip("Dark", nudgeSettings.themeMode == ThemeMode.DARK, Modifier.weight(1f)) {
                    vm.setTheme(ThemeMode.DARK)
                }
                ThemeChip("Light", nudgeSettings.themeMode == ThemeMode.LIGHT, Modifier.weight(1f)) {
                    vm.setTheme(ThemeMode.LIGHT)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Light mode uses black text on a light background.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionCard(title = "Notifications") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Evening nudge", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "One gentle follow-up if the day isn't done yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        enabled = nudgeSettings.nudgeEnabled,
                        onClick = {
                            TimePickerDialog(
                                context,
                                { _, h, m -> vm.setNudgeTime(appContext, h, m) },
                                nudgeSettings.nudgeHour, nudgeSettings.nudgeMinute, true
                            ).show()
                        }
                    ) {
                        Text(
                            String.format(
                                "%02d:%02d", nudgeSettings.nudgeHour, nudgeSettings.nudgeMinute
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Switch(
                    checked = nudgeSettings.nudgeEnabled,
                    onCheckedChange = { vm.setNudgeEnabled(appContext, it) },
                    colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            if (!exactAllowed) {
                Text(
                    "Reminders need exact-alarm permission, otherwise they may arrive late or not at all.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                GhostCta(
                    text = "ALLOW EXACT ALARMS",
                    onClick = {
                        ReminderScheduler.openExactAlarmSettings(appContext)
                        exactAllowed = ReminderScheduler.hasExactAlarmPermission(appContext)
                    }
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
            }
            if (plans.isEmpty()) {
                Text(
                    "No active plans. Your reminders will appear here once you start a plan.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                plans.forEachIndexed { i, plan ->
                    PlanReminderRow(
                        plan = plan,
                        onToggle = { enabled ->
                            if (enabled && !ensurePostNotifPermission()) return@PlanReminderRow
                            vm.setPlanEnabled(appContext, plan.id, enabled)
                        },
                        onPickTime = { h, m -> vm.setPlanTime(appContext, plan.id, h, m) }
                    )
                    if (i < plans.lastIndex) {
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            GhostCta(
                text = "SEND TEST NOTIFICATION",
                onClick = { vm.sendTestNotification(appContext) { backupMsg = it } }
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionCard(title = "Data") {
            GhostCta(text = "EXPORT BACKUP", onClick = {
                try {
                    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
                        .format(java.util.Date())
                    exportLauncher.launch("thirty-backup-$stamp.json")
                } catch (e: Exception) {
                    backupMsg = "Export failed: ${e.message}"
                }
            })
            Spacer(Modifier.height(10.dp))
            GhostCta(text = "IMPORT BACKUP", onClick = {
                try { importLauncher.launch(arrayOf("application/json", "*/*")) }
                catch (e: Exception) { backupMsg = "Import failed: ${e.message}" }
            })
            Spacer(Modifier.height(10.dp))
            GhostCta(text = "CLEAR ALL DATA", onClick = { showClearConfirm = true })
            backupMsg?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionCard(title = "Journeys") {
            GhostCta(text = "VIEW PAST JOURNEYS", onClick = onOpenHistory)
        }

        Spacer(Modifier.height(16.dp))
        SectionCard(title = "App lock") {
            val hasPassword = nudgeSettings.lockHash.isNotEmpty()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Lock THIRTY", style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (hasPassword) "A password is required when the app opens."
                        else "Optional — use THIRTY without a lock, or add a password.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = nudgeSettings.lockEnabled && hasPassword,
                    onCheckedChange = { want ->
                        if (want && !hasPassword) {
                            showPasswordDialog = true
                        } else {
                            vm.setLockEnabled(want)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            if (hasPassword) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { showPasswordDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("CHANGE PASSWORD") }
                    OutlinedButton(
                        onClick = { vm.removeLock() },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("REMOVE") }
                }
            } else {
                GhostCta(text = "SET A PASSWORD", onClick = { showPasswordDialog = true })
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionCard(title = "About") {
            Text("THIRTY", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("30 DAYS. ONE CHANGE.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(
                "Show up every day.\nWorks fully offline. Your data stays on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text("Version 1.9.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun PlanReminderRow(
    plan: ChallengeEntity,
    onToggle: (Boolean) -> Unit,
    onPickTime: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(plan.title, style = MaterialTheme.typography.titleLarge, maxLines = 2)
            Text(
                nextReminderLabel(plan.reminderHour, plan.reminderMinute),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = {
                    TimePickerDialog(
                        context,
                        { _, h, m -> onPickTime(h, m) },
                        plan.reminderHour, plan.reminderMinute, true
                    ).show()
                }
            ) {
                Text(
                    String.format("%02d:%02d daily", plan.reminderHour, plan.reminderMinute),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Switch(
            checked = plan.reminderEnabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
            checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun nextReminderLabel(hour: Int, minute: Int): String {
    val now = java.util.Calendar.getInstance()
    val t = (now.clone() as java.util.Calendar).apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val day = if (t.timeInMillis > now.timeInMillis) "Today" else "Tomorrow"
    return "Next: $day %02d:%02d".format(hour, minute)
}

@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) accent else MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (selected) accent else MaterialTheme.colorScheme.onSurface
        )
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun PasswordDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val accent = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set a password") },
        text = {
            Column {
                Text(
                    "You'll need it every time THIRTY opens. It can't be recovered — it stays only on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = pw,
                    onValueChange = { pw = it; err = null },
                    label = { Text("Password (min 4)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it; err = null },
                    label = { Text("Repeat password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                err?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    pw.length < 4 -> err = "Use at least 4 characters."
                    pw != confirm -> err = "The passwords don't match."
                    else -> onSave(pw)
                }
            }) { Text("Save", color = accent) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        HorizontalDivider()
        Spacer(Modifier.height(14.dp))
        content()
    }
}
