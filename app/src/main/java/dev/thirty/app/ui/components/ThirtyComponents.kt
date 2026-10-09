package dev.thirty.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.thirty.app.data.local.DailyProgressEntity
import dev.thirty.app.data.local.DayStatus
import dev.thirty.app.data.repository.ChallengeWithProgress
import dev.thirty.app.util.DateUtils

@Composable
fun BrandHeader(
    modifier: Modifier = Modifier,
    tagline: String = "30 DAYS. ONE CHANGE."
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "THIRTY",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = MaterialTheme.typography.headlineMedium.letterSpacing,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
            Spacer(Modifier.width(8.dp))
            Text(
                tagline,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun PrimaryCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .semantics { contentDescription = text },
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun GhostCta(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun StepLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing
    )
}

@Composable
fun ThirtyProgress(
    completed: Int,
    total: Int = 30,
    modifier: Modifier = Modifier,
    height: Dp = 8.dp
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                "$completed / $total",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${total - completed} days left",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { (completed / total.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(99.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outline,
            strokeCap = StrokeCap.Round
        )
    }
}

/**
 * One cell of a real calendar: always shows the actual day of the month.
 * Status is carried by colour instead of a different label:
 * solid fill = done, ring = today, border = rest, grey = missed,
 * faint = outside the plan.
 */
@Composable
fun CalendarCell(
    dayOfMonth: Int,
    status: DayStatus?,
    isToday: Boolean,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val inPlan = status != null
    val completed = status == DayStatus.COMPLETED
    val rest = status == DayStatus.REST
    val missed = status == DayStatus.MISSED

    val bg = if (completed) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderColor = when {
        completed || isToday || rest -> MaterialTheme.colorScheme.primary
        inPlan -> MaterialTheme.colorScheme.outline
        else -> Color.Transparent
    }
    val borderWidth = when {
        completed || isToday -> 2.dp
        inPlan -> 1.dp
        else -> 0.dp
    }
    val textColor = when {
        completed -> MaterialTheme.colorScheme.onPrimary
        isToday || rest -> MaterialTheme.colorScheme.primary
        missed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        inPlan -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    }
    val label = when (status) {
        DayStatus.COMPLETED -> "Day $dayOfMonth completed"
        DayStatus.MISSED -> "Day $dayOfMonth missed"
        DayStatus.REST -> "Day $dayOfMonth rest day"
        null -> "$dayOfMonth"
        else -> if (isToday) "Today, $dayOfMonth" else "$dayOfMonth"
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(width = borderWidth, color = borderColor, shape = RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center
    ) {
        Text(
            String.format("%d", dayOfMonth),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isToday || completed) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    ctaText: String? = null,
    onCta: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier
                .width(32.dp)
                .height(3.dp)
                .clip(RoundedCornerShape(99.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(12.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (ctaText != null && onCta != null) {
            Spacer(Modifier.height(28.dp))
            PrimaryCta(ctaText, onCta, modifier = Modifier.widthIn(max = 340.dp))
        }
    }
}

/**
 * Real calendar: full month grids with exact dates (1..31) aligned to the
 * correct weekday, Monday-first. Days that belong to the plan carry their
 * status colour; every other day of the month is shown faint, so the grid
 * reads like an actual calendar.
 */
@Composable
fun ThirtyCalendar(
    days: List<DailyProgressEntity>,
    todayDayNumber: Int?,
    onDayClick: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val gap = 6.dp
    val byEpoch = remember(days) { days.associateBy { it.epochDay } }
    val months = remember(days) {
        if (days.isEmpty()) emptyList()
        else {
            val first = java.time.YearMonth.from(DateUtils.fromEpochDay(days.first().epochDay))
            val last = java.time.YearMonth.from(DateUtils.fromEpochDay(days.last().epochDay))
            generateSequence(first) { it.plusMonths(1) }
                .takeWhile { !it.isAfter(last) }
                .toList()
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        months.forEach { month ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    DateUtils.formatMonth(month.atDay(1)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { d ->
                        Text(
                            d,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                val lead = month.atDay(1).dayOfWeek.value - 1
                val cellCount = lead + month.lengthOfMonth()
                val rows = (cellCount + 6) / 7
                repeat(rows) { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        repeat(7) { c ->
                            val index = r * 7 + c
                            val cellMod = Modifier.weight(1f).aspectRatio(1f)
                            if (index < lead || index >= cellCount) {
                                Spacer(cellMod)
                            } else {
                                val date = month.atDay(index - lead + 1)
                                val row = byEpoch[date.toEpochDay()]
                                CalendarCell(
                                    dayOfMonth = date.dayOfMonth,
                                    status = row?.status,
                                    isToday = todayDayNumber != null && row != null &&
                                        row.dayNumber == todayDayNumber,
                                    onClick = if (row != null && onDayClick != null) {
                                        { onDayClick(row.dayNumber) }
                                    } else {
                                        null
                                    },
                                    modifier = cellMod
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Plan picker drop box: shows the current plan, expands to the full list.
 * Plain DropdownMenu-based so it works on every version.
 */
@Composable
fun PlanDropdown(
    plans: List<ChallengeWithProgress>,
    selectedId: Long,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = plans.firstOrNull { it.challenge.id == selectedId } ?: plans.first()
    Box(modifier) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "PLAN",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    "${selected.challenge.title} — Day ${selected.todayDayNumber}/${selected.challenge.lengthDays}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = if (expanded) "Collapse plans" else "Expand plans"
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            plans.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Text("Day ${p.todayDayNumber}/${p.challenge.lengthDays} — ${p.challenge.title}")
                    },
                    onClick = {
                        expanded = false
                        onSelect(p.challenge.id)
                    }
                )
            }
        }
    }
}
