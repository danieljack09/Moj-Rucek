package com.mojrucek.app.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.mojrucek.app.repository.ImportResult
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                Column(Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Неделен потсетник (недела)", style = MaterialTheme.typography.titleMedium)
                        Switch(
                            checked = state.settings.sundayReminderEnabled,
                            onCheckedChange = viewModel::setReminderEnabled
                        )
                    }
                    Text(
                        "Потсетник во %02d:%02d часот секоја недела да се испланира следната недела.".format(
                            state.settings.sundayReminderHour, state.settings.sundayReminderMinute
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Резервна копија", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Извези ги сите намирници, рецепти, планови и листи во JSON датотека, или вчитај претходно извезена датотека.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedButton(onClick = viewModel::exportData, modifier = Modifier.fillMaxWidth()) {
                        Text("Извези податоци")
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

    state.importMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearImportMessage,
            title = { Text("Готово") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearImportMessage) { Text("Во ред") } }
        )
    }
}
