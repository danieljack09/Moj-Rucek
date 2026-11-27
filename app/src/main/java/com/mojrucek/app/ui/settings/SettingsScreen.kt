package com.mojrucek.app.ui.settings

import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.BoxWithConstraints
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mojrucek.app.data.entity.AppSettings
import com.mojrucek.app.repository.ImportResult
import com.mojrucek.app.ui.components.AppDialog
import com.mojrucek.app.ui.components.PillListRow
import com.mojrucek.app.ui.components.PillStepper
import com.mojrucek.app.ui.components.RowChevron
import com.mojrucek.app.ui.components.RowIconCircle
import com.mojrucek.app.ui.components.ScreenHeader
import com.mojrucek.app.ui.components.SectionCard
import com.mojrucek.app.ui.components.SectionCardFill
import com.mojrucek.app.ui.components.SectionHeader
import com.mojrucek.app.ui.components.SettingsDivider
import com.mojrucek.app.ui.components.SettingsRow
import com.mojrucek.app.ui.components.appSwitchColors
import com.mojrucek.app.ui.components.suggestedBackupFileName
import com.mojrucek.app.ui.theme.MojRucekTheme
import com.mojrucek.app.ui.theme.PillShape
import com.mojrucek.app.ui.theme.Spacing
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.mojrucek.app.domain.text.MkPlural

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    accountCard: @Composable () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingImportText by remember { mutableStateOf<String?>(null) }
    var pendingImportSummary by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        if (text != null) {
            when (val result = viewModel.validateImport(text)) {
                is ImportResult.Success -> {
                    pendingImportText = text
                    pendingImportSummary = "Ќе се вчитаат ${MkPlural.recipes(result.summary.recipeCount)}, " +
                        "${MkPlural.ingredients(result.summary.ingredientCount)}, ${MkPlural.items(result.summary.shoppingItemCount)} за пазарење. " +
                        "Ова ги заменува сите постоечки податоци. Продолжи?"
                }
                is ImportResult.InvalidFile -> pendingImportSummary = result.reason
            }
        }
    }

    // System file creator (Storage Access Framework): the user picks the place, no storage permission.
    // Cancelling returns null, which the ViewModel ignores without showing an error.
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        viewModel.saveBackupTo(uri)
    }

    LaunchedEffect(state.exportedJson) {
        val jsonText = state.exportedJson ?: return@LaunchedEffect
        val fileName = "mojrucek-backup-${LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))}.json"
        val dir = File(context.cacheDir, "backups").apply { mkdirs() }
        val file = File(dir, fileName)
        file.writeText(jsonText)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Сподели резервна копија"))
        viewModel.clearExport()
    }

    // No top bar: the Scaffold's top padding is the status-bar inset; the header stays fixed above the list.
    Scaffold { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ScreenHeader(
                title = "Поставки",
                onBack = onBack,
                modifier = Modifier.padding(horizontal = Spacing.screenHorizontal)
            )
            Column(
                // Scrolls now that the account card makes the screen taller than small phones.
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Spacing.screenHorizontal, top = 8.dp, end = Spacing.screenHorizontal, bottom = Spacing.betweenSections),
                verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)
            ) {
                accountCard()

                // Servings and the reminder: one greenContainer group of rows with thin dividers.
                SectionCard(
                    fill = SectionCardFill.GreenContainer,
                    contentPadding = PaddingValues(horizontal = Spacing.cardPadding, vertical = 4.dp)
                ) {
                    SettingsRow(title = "Стандарден број на порции") {
                        PillStepper(
                            valueText = state.settings.defaultHouseholdServings.toString(),
                            onDecrease = { viewModel.setDefaultServings(state.settings.defaultHouseholdServings - 1) },
                            onIncrease = { viewModel.setDefaultServings(state.settings.defaultHouseholdServings + 1) },
                            decreaseDescription = "Намали",
                            increaseDescription = "Зголеми"
                        )
                    }
                    SettingsDivider()
                    SettingsRow(title = "Потсетник за планирање") {
                        Switch(
                            checked = state.settings.reminderEnabled,
                            onCheckedChange = viewModel::setReminderEnabled,
                            colors = appSwitchColors()
                        )
                    }

                    if (state.settings.reminderEnabled) {
                        Column(
                            Modifier.padding(top = 4.dp, bottom = Spacing.betweenCards),
                            verticalArrangement = Arrangement.spacedBy(Spacing.betweenCards)
                        ) {
                            ReminderModeSelector(
                                mode = state.settings.reminderMode,
                                onModeChange = viewModel::setReminderMode
                            )
                            if (state.settings.reminderMode == AppSettings.ReminderMode.WEEKLY) {
                                DayCircleRow(
                                    isSelected = { it == state.settings.reminderWeeklyDay },
                                    onToggle = viewModel::setReminderWeeklyDay
                                )
                            } else {
                                DayCircleRow(
                                    isSelected = { it in state.settings.reminderDays },
                                    onToggle = { day ->
                                        val days = state.settings.reminderDays
                                        viewModel.setReminderDays(if (day in days) days - day else days + day)
                                    }
                                )
                            }
                        }
                        SettingsDivider()
                        // Title on its own line, the two steppers centred below: side by side they leave the title no room.
                        SettingsRow(title = "Време на потсетник")
                        Box(Modifier.fillMaxWidth().padding(bottom = Spacing.betweenCards), contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TimeStepper(
                                    value = state.settings.reminderHour,
                                    range = 0..23,
                                    onValueChange = { viewModel.setReminderTime(it, state.settings.reminderMinute) }
                                )
                                Text(":", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 2.dp))
                                TimeStepper(
                                    value = state.settings.reminderMinute,
                                    range = 0..59,
                                    step = 5,
                                    onValueChange = { viewModel.setReminderTime(state.settings.reminderHour, it) }
                                )
                            }
                        }
                    }
                }

                // Catalog: heading, explanation, and the sync action as a list row.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Каталог", modifier = Modifier.padding(top = Spacing.betweenSections - Spacing.betweenCards))
                    Text(
                        "Листата на намирници и рецептите се преземаат од серверот. Синхронизирај за да ги добиеш најновите промени.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PillListRow(
                        title = if (state.isSyncing) "Се синхронизира…" else "Синхронизирај",
                        // Disabled while syncing, as before.
                        onClick = if (state.isSyncing) null else viewModel::syncCatalog,
                        leading = { RowIconCircle(Icons.Outlined.Sync, modifier = Modifier.padding(start = 4.dp)) },
                        trailing = { if (!state.isSyncing) RowChevron() }
                    )
                }

                // Backup: heading, explanation, then save / share / restore rows with line icons.
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Резервна копија", modifier = Modifier.padding(top = Spacing.betweenSections - Spacing.betweenCards))
                    Text(
                        "Зачувај ги сите намирници, рецепти, планови и листи во датотека на телефонот, " +
                            "или сподели ја копијата. Подоцна можеш да ја вчиташ назад.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    PillListRow(
                        title = if (state.isSaving) "Се зачувува…" else "Зачувај на уредот",
                        subtitle = "Ќе избереш каде да се зачува, на пример во Преземања или Документи.",
                        // Disabled while saving, as before.
                        onClick = if (state.isSaving) null else ({ saveLauncher.launch(suggestedBackupFileName()) }),
                        leading = { RowIconCircle(Icons.Outlined.SaveAlt, modifier = Modifier.padding(start = 4.dp)) },
                        trailing = { if (!state.isSaving) RowChevron() }
                    )
                    PillListRow(
                        title = "Сподели копија",
                        onClick = viewModel::exportData,
                        leading = { RowIconCircle(Icons.Outlined.Share, modifier = Modifier.padding(start = 4.dp)) },
                        trailing = { RowChevron() }
                    )
                    PillListRow(
                        title = "Вчитај од датотека",
                        onClick = { importLauncher.launch("application/json") },
                        leading = { RowIconCircle(Icons.Outlined.FileOpen, modifier = Modifier.padding(start = 4.dp)) },
                        trailing = { RowChevron() }
                    )
                }
            }
        }
    }

    pendingImportSummary?.let { summary ->
        val dismissImport = { pendingImportSummary = null; pendingImportText = null }
        if (pendingImportText != null) {
            AppDialog(
                onDismissRequest = dismissImport,
                title = "Увоз на податоци",
                text = summary,
                confirmLabel = "Продолжи",
                onConfirm = {
                    viewModel.importData(pendingImportText!!)
                    pendingImportSummary = null
                    pendingImportText = null
                },
                dismissLabel = "Откажи",
                onDismiss = dismissImport
            )
        } else {
            // Invalid file: only "Откажи", as before.
            AppDialog(
                onDismissRequest = dismissImport,
                title = "Увоз на податоци",
                text = summary,
                confirmLabel = null,
                onConfirm = null,
                dismissLabel = "Откажи",
                onDismiss = dismissImport
            )
        }
    }

    state.saveMessage?.let { message ->
        AppDialog(
            onDismissRequest = viewModel::clearSaveMessage,
            title = "Резервна копија",
            text = message,
            confirmLabel = "Во ред",
            onConfirm = viewModel::clearSaveMessage
        )
    }

    state.syncMessage?.let { message ->
        AppDialog(
            onDismissRequest = viewModel::clearSyncMessage,
            title = "Синхронизација",
            text = message,
            confirmLabel = "Во ред",
            onConfirm = viewModel::clearSyncMessage
        )
    }

    state.importMessage?.let { message ->
        AppDialog(
            onDismissRequest = viewModel::clearImportMessage,
            title = "Готово",
            text = message,
            confirmLabel = "Во ред",
            onConfirm = viewModel::clearImportMessage
        )
    }
}

private val dayLabels = mapOf(
    1 to "По", 2 to "Вт", 3 to "Ср", 4 to "Че", 5 to "Пе", 6 to "Са", 7 to "Не"
)

/**
 * Pill segmented control: a background-colour track (it sits on a greenContainer card) with the
 * selected segment filled green. Colours animate as before.
 */
@Composable
private fun ReminderModeSelector(mode: String, onModeChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.background)
            .padding(4.dp)
    ) {
        ModeSegment(
            label = "Неделно",
            selected = mode == AppSettings.ReminderMode.WEEKLY,
            onClick = { onModeChange(AppSettings.ReminderMode.WEEKLY) },
            modifier = Modifier.weight(1f)
        )
        ModeSegment(
            label = "Дневно",
            selected = mode == AppSettings.ReminderMode.DAILY,
            onClick = { onModeChange(AppSettings.ReminderMode.DAILY) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeSegment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MojRucekTheme.colors.green else Color.Transparent,
        label = "segmentBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "segmentContent"
    )
    Box(
        modifier = modifier
            .heightIn(min = 40.dp)
            .clip(PillShape)
            .background(backgroundColor)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DayCircleRow(isSelected: (Int) -> Boolean, onToggle: (Int) -> Unit) {
    // Up to 40dp per circle, smaller on narrow screens so the seven circles (and the selected
    // one's slight grow) never touch.
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val circle = minOf(40.dp, (maxWidth - 8.dp * 6) / 7)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            dayLabels.forEach { (day, label) ->
                DayCircle(
                    label = label,
                    selected = isSelected(day),
                    onClick = { onToggle(day) },
                    size = circle
                )
            }
        }
    }
}

/**
 * 40dp circle: accent fill with onAccent text when selected; background colour when not (the
 * card behind is greenContainer). Scale and colour animate as before.
 */
@Composable
private fun DayCircle(label: String, selected: Boolean, onClick: () -> Unit, size: Dp = 40.dp) {
    val scale by animateFloatAsState(targetValue = if (selected) 1.08f else 1f, label = "dayScale")
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background,
        label = "dayBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "dayContent"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.size(size).scale(scale),
        shape = CircleShape,
        color = backgroundColor,
        contentColor = contentColor
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
        }
    }
}

/** Pill stepper for hours or minutes; wraps around at the ends of [range], as before. */
@Composable
private fun TimeStepper(value: Int, range: IntRange, onValueChange: (Int) -> Unit, step: Int = 1) {
    PillStepper(
        valueText = "%02d".format(value),
        onDecrease = {
            val next = value - step
            onValueChange(if (next < range.first) range.last else next)
        },
        onIncrease = {
            val next = value + step
            onValueChange(if (next > range.last) range.first else next)
        },
        decreaseDescription = "Намали",
        increaseDescription = "Зголеми"
    )
}
