package dev.thirty.app.ui.screens.onboarding

import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.thirty.app.domain.ChallengeLogic
import dev.thirty.app.ui.components.PrimaryCta

private const val STEP_COUNT = 5

@Composable
fun OnboardingFlow(
    vm: OnboardingViewModel,
    onFinished: () -> Unit,
    canGoBack: Boolean = false,
    onBack: () -> Unit = {}
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    BackHandler(enabled = state.step > 0) { vm.back() }

    Column(Modifier.fillMaxSize().imePadding()) {
        OnboardingTopBar(
            step = state.step,
            canGoBack = canGoBack,
            onBack = {
                if (state.step > 0) vm.back() else onBack()
            }
        )

        // Scrollable body — content only, never the button.
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            AnimatedContent(
                targetState = state.step,
                transitionSpec = {
                    val offset = if (targetState > initialState) 1 else -1
                    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { full -> full / 8 * offset }) togetherWith
                        (fadeOut(tween(140)) + slideOutHorizontally(tween(200)) { full -> -full / 8 * offset })
                },
                label = "onboarding-step"
            ) { step ->
                // AnimatedContent stacks its children like a Box — each step
                // must be a single root node or everything overlaps.
                Column(Modifier.fillMaxWidth()) {
                    when (step) {
                        0 -> CategoryStep(state, vm)
                        1 -> GoalStep(state, vm)
                        2 -> TargetStep(state, vm)
                        3 -> ReminderStep(state, vm)
                        else -> CommitmentStep(state, vm)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }

        // Pinned footer — always reachable, never scrolls away.
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            state.createError?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
            PrimaryCta(
                text = when {
                    state.step == 4 -> if (state.creating) "STARTING…" else "START MY JOURNEY"
                    state.step == 3 -> "REVIEW MY PROMISE"
                    else -> "CONTINUE"
                },
                onClick = {
                    if (state.step == 4) vm.createChallenge(context.applicationContext, onDone = onFinished)
                    else vm.next()
                },
                enabled = !state.creating
            )
            Spacer(Modifier.height(10.dp))
        }
    }
}

/* ------------------------------------------------------------------ */
/* Chrome                                                              */
/* ------------------------------------------------------------------ */

@Composable
private fun OnboardingTopBar(step: Int, canGoBack: Boolean, onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                if (step > 0 || canGoBack) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
            Text(
                "THIRTY",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                letterSpacing = MaterialTheme.typography.labelLarge.letterSpacing,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            Text(
                "${pad(step + 1)} / $STEP_COUNT",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.width(72.dp).padding(end = 8.dp)
            )
        }
        // Segmented progress rail
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp, start = 8.dp, end = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(STEP_COUNT) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(99.dp))
                        .background(
                            if (i <= step) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                )
            }
        }
    }
}

@Composable
private fun StepHeader(kicker: String, headline: String, sub: String? = null) {
    Text(
        kicker.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))
    Text(
        headline,
        style = MaterialTheme.typography.headlineLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground
    )
    if (sub != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            sub,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ------------------------------------------------------------------ */
/* 01 — Category                                                       */
/* ------------------------------------------------------------------ */

@Composable
private fun CategoryStep(s: OnboardingUiState, vm: OnboardingViewModel) {
    StepHeader("01 — Category", "What do you want to change?", "Pick one lane. One goal. Thirty days.")
    Spacer(Modifier.height(24.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CATEGORIES.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { c ->
                    SelectTile(
                        label = c,
                        selected = s.category == c,
                        onClick = { vm.setCategory(c) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SelectTile(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) scheme.primary else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) scheme.primary else scheme.outline,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = if (selected) scheme.onPrimary else scheme.onBackground
        )
    }
}

/* ------------------------------------------------------------------ */
/* 02 — Goal                                                           */
/* ------------------------------------------------------------------ */

@Composable
private fun GoalStep(s: OnboardingUiState, vm: OnboardingViewModel) {
    val templates = TEMPLATES[s.category].orEmpty()

    StepHeader("02 — Goal", "What exactly will you do?", "One sentence you can keep on your worst day.")
    Spacer(Modifier.height(24.dp))

    LineField(
        value = s.title,
        onValueChange = { if (it.length <= 120) vm.setTitle(it) },
        placeholder = "Read 20 minutes every day",
        isError = s.titleError != null,
        supporting = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    s.titleError ?: "One line. One habit.",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (s.titleError != null) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${s.title.trim().length}/120",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )

    if (templates.isNotEmpty()) {
        Spacer(Modifier.height(32.dp))
        Text(
            "Popular starters",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        templates.forEachIndexed { i, t ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { vm.applyTemplate(t) }
                    .padding(vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    t.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    }
}

/* ------------------------------------------------------------------ */
/* 03 — Length & target                                                */
/* ------------------------------------------------------------------ */

@Composable
private fun TargetStep(s: OnboardingUiState, vm: OnboardingViewModel) {
    StepHeader("03 — Commitment", "How long will you commit?", "From one week to a full year.")
    Spacer(Modifier.height(24.dp))

    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            s.durationDays.toString(),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.width(10.dp))
        Text(
            "DAYS",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp)
        )
    }
    Spacer(Modifier.height(16.dp))
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ChallengeLogic.DURATIONS.forEach { d ->
            DurationPill(
                label = ChallengeLogic.durationLabel(d),
                selected = s.durationDays == d,
                onClick = { vm.setDuration(d) }
            )
        }
    }

    Spacer(Modifier.height(34.dp))
    Text(
        "Make it measurable",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(6.dp))
    Text(
        "Optional. Examples: 20 minutes · 2 liters · 30 pages.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        LineField(
            value = s.targetValueText,
            onValueChange = { vm.setTargetValue(it.filter { ch -> ch.isDigit() || ch == '.' }) },
            placeholder = "Amount",
            isError = s.targetError != null,
            large = false,
            numeric = true,
            supporting = {
                s.targetError?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
                }
            },
            modifier = Modifier.weight(1f)
        )
        LineField(
            value = s.targetUnit,
            onValueChange = { if (it.length <= 24) vm.setTargetUnit(it) },
            placeholder = "Unit",
            large = false,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DurationPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) scheme.primary else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) scheme.primary else scheme.outline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 11.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimary else scheme.onBackground
        )
    }
}

/* ------------------------------------------------------------------ */
/* 04 — Reminder                                                       */
/* ------------------------------------------------------------------ */

@Composable
private fun ReminderStep(s: OnboardingUiState, vm: OnboardingViewModel) {
    val context = LocalContext.current

    StepHeader("04 — Reminder", "When should we nudge you?", "One calm notification a day. Nothing more.")
    Spacer(Modifier.height(30.dp))

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = s.reminderEnabled) {
                TimePickerDialog(context, { _, h, m -> vm.setTime(h, m) }, s.hour, s.minute, true).show()
            }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            String.format("%02d:%02d", s.hour, s.minute),
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Bold,
            color = if (s.reminderEnabled) MaterialTheme.colorScheme.onBackground
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (s.reminderEnabled) "Tap to change" else "Reminder off",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Spacer(Modifier.height(24.dp))
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(7 to 0, 13 to 0, 20 to 0, 22 to 0).forEach { (h, m) ->
            if (h != 7) Spacer(Modifier.width(8.dp))
            PresetTime(
                label = String.format("%02d:00", h),
                selected = s.reminderEnabled && s.hour == h && s.minute == m,
                onClick = { vm.setTime(h, m) }
            )
        }
    }

    Spacer(Modifier.height(28.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Daily reminder",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
        Switch(
            checked = s.reminderEnabled,
            onCheckedChange = { vm.setReminderEnabled(it) },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary
            )
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun PresetTime(label: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) scheme.primary else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) scheme.primary else scheme.outline,
                shape = RoundedCornerShape(99.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) scheme.onPrimary else scheme.onBackground
        )
    }
}

/* ------------------------------------------------------------------ */
/* 05 — Review                                                         */
/* ------------------------------------------------------------------ */

@Composable
private fun CommitmentStep(s: OnboardingUiState, vm: OnboardingViewModel) {
    StepHeader("05 — Review", "Your promise.", "Read it once. Then begin.")
    Spacer(Modifier.height(24.dp))

    Text(
        s.title.trim().ifEmpty { "Your goal" },
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(24.dp))

    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
    SpecRow(
        "Category",
        s.category
    )
    SpecRow(
        "Length",
        ChallengeLogic.durationLabel(s.durationDays)
    )
    SpecRow(
        "Target",
        if (s.targetValueText.isNotBlank()) "${s.targetValueText} ${s.targetUnit}".trim() else "Not set"
    )
    SpecRow(
        "Reminder",
        if (s.reminderEnabled) String.format("Every day, %02d:%02d", s.hour, s.minute) else "Off"
    )

    Spacer(Modifier.height(20.dp))
    Text(
        "Missing one day doesn\u2019t erase your progress. Get back tomorrow.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (vm.state.value.step == 4) Spacer(Modifier.height(40.dp))
}

@Composable
private fun SpecRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
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
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.End
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
}

/* ------------------------------------------------------------------ */
/* Shared field                                                        */
/* ------------------------------------------------------------------ */

/**
 * Borderless input: large type, one hairline underneath that goes solid
 * (primary) on focus and red on error. Keeps the page flat and editorial.
 */
@Composable
private fun LineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    large: Boolean = true,
    numeric: Boolean = false,
    isError: Boolean = false,
    supporting: (@Composable () -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }
    val scheme = MaterialTheme.colorScheme
    val style = (if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge)
        .copy(color = scheme.onBackground, fontWeight = FontWeight.SemiBold)
    val placeholderStyle = (if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge)
        .copy(color = scheme.outline, fontWeight = FontWeight.SemiBold)

    Column(modifier) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style,
            cursorBrush = SolidColor(scheme.primary),
            singleLine = !large,
            maxLines = if (large) 3 else 1,
            keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.Decimal else KeyboardType.Text),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(Modifier.fillMaxWidth()) {
                    if (value.isEmpty()) Text(placeholder, style = placeholderStyle)
                    inner()
                }
            }
        )
        Spacer(Modifier.height(10.dp))
        HorizontalDivider(
            thickness = if (focused || isError) 2.dp else 1.dp,
            color = when {
                isError -> scheme.error
                focused -> scheme.primary
                else -> scheme.outline
            }
        )
        if (supporting != null) {
            Spacer(Modifier.height(8.dp))
            supporting()
        }
    }
}

private fun pad(n: Int): String = if (n < 10) "0$n" else n.toString()
