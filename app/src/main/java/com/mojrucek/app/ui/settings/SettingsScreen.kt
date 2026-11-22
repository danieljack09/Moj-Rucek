package com.mojrucek.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import com.mojrucek.app.data.entity.AppSettings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mojrucek.app.repository.ImportResult
import com.mojrucek.app.ui.components.suggestedBackupFileName
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
                    pendingImportSummary = "Ќе се вчитаат ${result.summary.recipeCount} рецепти, " +
                        "${result.summary.ingredientCount} намирници, ${result.summary.shoppingItemCount} ставки за пазарење. " +
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Поставки") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Назад") } }
            )
        }
    ) { padding ->
        Column(
            // Scrolls now that the account card makes the screen taller than small phones.
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            accountCard()

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Стандарден број на порции", style = MaterialTheme.typography.titleMedium)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { viewModel.setDefaultServings(state.settings.defaultHouseholdServings - 1) }) {
                            Icon(Icons.Filled.Remove, contentDescription = "Намали")
                        }
                        Text(state.settings.defaultHouseholdServings.toString(), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { viewModel.setDefaultServings(state.settings.defaultHouseholdServings + 1) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Зголеми")
                        }
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Потсетник за планирање", style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = state.settings.reminderEnabled,
                            onCheckedChange = viewModel::setReminderEnabled
                        )
                    }

                    if (state.settings.reminderEnabled) {
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

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Време на потсетник", style = MaterialTheme.typography.titleSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TimeStepper(
                                    value = state.settings.reminderHour,
                                    range = 0..23,
                                    onValueChange = { viewModel.setReminderTime(it, state.settings.reminderMinute) }
                                )
                                Text(":", style = MaterialTheme.typography.titleMedium)
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
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Каталог", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Листата на намирници и рецептите се преземаат од серверот. Синхронизирај за да ги добиеш најновите промени.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(
                        onClick = viewModel::syncCatalog,
                        enabled = !state.isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.isSyncing) "Се синхронизира…" else "Синхронизирај")
                    }
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Резервна копија", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Зачувај ги сите намирници, рецепти, планови и листи во датотека на телефонот, " +
                            "или сподели ја копијата. Подоцна можеш да ја вчиташ назад.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Button(
                        onClick = { saveLauncher.launch(suggestedBackupFileName()) },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (state.isSaving) "Се зачувува…" else "Зачувај на уредот")
                    }
                    Text(
                        "Ќе избереш каде да се зачува, на пример во Преземања или Документи.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(onClick = viewModel::exportData, modifier = Modifier.fillMaxWidth()) {
                        Text("Сподели копија")
                    }
                    OutlinedButton(onClick = { importLauncher.launch("application/json") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Вчитај од датотека")
                    }
                }
            }
        }
    }

    pendingImportSummary?.let { summary ->
        AlertDialog(
            onDismissRequest = { pendingImportSummary = null; pendingImportText = null },
            title = { Text("Увоз на податоци") },
            text = { Text(summary) },
            confirmButton = {
                if (pendingImportText != null) {
                    TextButton(onClick = {
                        viewModel.importData(pendingImportText!!)
                        pendingImportSummary = null
                        pendingImportText = null
                    }) { Text("Продолжи") }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingImportSummary = null; pendingImportText = null }) { Text("Откажи") }
            }
        )
    }

    state.saveMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearSaveMessage,
            title = { Text("Резервна копија") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearSaveMessage) { Text("Во ред") } }
        )
    }

    state.syncMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearSyncMessage,
            title = { Text("Синхронизација") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearSyncMessage) { Text("Во ред") } }
        )
    }

    state.importMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearImportMessage,
            title = { Text("Готово") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearImportMessage) { Text("Во ред") } }
        )
    }
}

private val dayLabels = mapOf(
    1 to "По", 2 to "Вт", 3 to "Ср", 4 to "Че", 5 to "Пе", 6 to "Са", 7 to "Не"
)

@Composable
private fun ReminderModeSelector(mode: String, onModeChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
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
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        label = "segmentBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "segmentContent"
    )
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = contentColor, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun DayCircleRow(isSelected: (Int) -> Boolean, onToggle: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        dayLabels.forEach { (day, label) ->
            DayCircle(
                label = label,
                selected = isSelected(day),
                onClick = { onToggle(day) }
            )
        }
    }
}

@Composable
private fun DayCircle(label: String, selected: Boolean, onClick: () -> Unit) {
    val scale by animateFloatAsState(targetValue = if (selected) 1.08f else 1f, label = "dayScale")
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        label = "dayBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "dayContent"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp).scale(scale),
        shape = CircleShape,
        color = backgroundColor,
        contentColor = contentColor,
        shadowElevation = if (selected) 4.dp else 0.dp,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun TimeStepper(value: Int, range: IntRange, onValueChange: (Int) -> Unit, step: Int = 1) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = {
            val next = value - step
            onValueChange(if (next < range.first) range.last else next)
        }) {
            Icon(Icons.Filled.Remove, contentDescription = "Намали")
        }
        Text("%02d".format(value), style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = {
            val next = value + step
            onValueChange(if (next > range.last) range.first else next)
        }) {
            Icon(Icons.Filled.Add, contentDescription = "Зголеми")
        }
    }
}
