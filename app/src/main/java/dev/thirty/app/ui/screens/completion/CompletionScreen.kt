package dev.thirty.app.ui.screens.completion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import dev.thirty.app.backup.JourneyImage
import dev.thirty.app.data.repository.ThirtyRepository
import dev.thirty.app.ui.components.BrandHeader
import dev.thirty.app.ui.components.GhostCta
import dev.thirty.app.ui.components.PrimaryCta
import kotlinx.coroutines.launch

@Composable
fun CompletionScreen(
    repository: ThirtyRepository,
    challengeId: Long,
    onKeepHabit: () -> Unit,
    onStartAnother: () -> Unit
) {
    var loading by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(0) }
    var best by remember { mutableStateOf(0) }
    var finalStreak by remember { mutableStateOf(0) }
    var lengthDays by remember { mutableStateOf(30) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val shareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            try {
                val bmp = JourneyImage.renderMilestone(
                    title = title,
                    day = lengthDays,
                    lengthDays = lengthDays,
                    streak = finalStreak,
                    best = best
                )
                JourneyImage.writeToUri(context, uri, bmp)
            } catch (_: Exception) { }
        }
    }

    LaunchedEffect(challengeId) {
        scope.launch {
            try {
                val full = repository.challengeWithProgress(challengeId)
                    ?: repository.activeWithProgress()
                if (full != null) {
                    title = full.challenge.title
                    lengthDays = full.challenge.lengthDays
                    done = full.completedCount
                    best = full.bestStreak
                    finalStreak = full.currentStreak
                }
            } catch (_: Exception) { }
            loading = false
        }
    }

    if (loading) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(120.dp))
            CircularProgressIndicator(                    color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        BrandHeader()
        Spacer(Modifier.height(32.dp))
        Text("$lengthDays / $lengthDays", style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold,                     color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        Text("YOU DID IT.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        Text("Completed: $done / $lengthDays", style = MaterialTheme.typography.bodyLarge)
        Text("Best streak: $best", style = MaterialTheme.typography.bodyLarge)
        Text("Final streak: $finalStreak", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(32.dp))
        Text("WHAT NOW?", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        PrimaryCta(text = "START ANOTHER", onClick = onStartAnother)
        Spacer(Modifier.height(10.dp))
        GhostCta(text = "KEEP THIS HABIT", onClick = onKeepHabit)
        Spacer(Modifier.height(6.dp))
        TextButton(
            onClick = {
                try {
                    shareLauncher.launch("thirty-day${lengthDays}-milestone.png")
                } catch (_: Exception) { }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Share your finish", color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            "Your streak can end. Your journey doesn\u2019t.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
