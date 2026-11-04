package ru.moireisy.app

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.moireisy.app.data.*
import ru.moireisy.app.tracking.TrackingService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.text.NumberFormat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MoiReisyApp() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoiReisyApp() {
    val context = LocalContext.current
    LaunchedEffect(Unit) { AutomaticBackupScheduler.sync(context) }
    val dao = remember { AppDatabase.get(context).tripDao() }
    val trips by dao.observeTrips().collectAsStateWithLifecycle(initialValue = emptyList())
    val allLogs by dao.observeAllDailyLogs().collectAsStateWithLifecycle(initialValue = emptyList())
    val fuelEntries by dao.observeFuelEntries().collectAsStateWithLifecycle(initialValue = emptyList())
    val expenses by dao.observeExpenses().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    val backupPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            try { BackupManager.export(context, uri); Toast.makeText(context, "Резервная копия сохранена", Toast.LENGTH_LONG).show() }
            catch (e: Exception) { Toast.makeText(context, "Ошибка резервного копирования: ${e.message}", Toast.LENGTH_LONG).show() }
        }
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingRestoreUri = uri
    }
    var selectedTrip by remember { mutableStateOf<Long?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }
    var showFinance by remember { mutableStateOf(false) }
    var showFuel by remember { mutableStateOf(false) }
    var showFuelForm by remember { mutableStateOf(false) }
    var showExpenseForm by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    val settingsPrefs = remember { context.getSharedPreferences("settings", 0) }
    var defaultRate by remember { mutableStateOf(settingsPrefs.getFloat("default_rate", 7f).toString()) }
    var themeMode by remember { mutableStateOf(settingsPrefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM") }
    val systemDarkTheme = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK" -> true
        else -> systemDarkTheme
    }
    val trackingPrefs = remember { context.getSharedPreferences("tracking_state", 0) }
    var activeTripId by remember { mutableLongStateOf(trackingPrefs.getLong("active_trip_id", -1L)) }
    var trackingPaused by remember { mutableStateOf(trackingPrefs.getBoolean("paused", false)) }
    val selected = trips.firstOrNull { it.id == selectedTrip }

    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF55B9D5),
            secondary = androidx.compose.ui.graphics.Color(0xFF9AB8C4),
            background = androidx.compose.ui.graphics.Color(0xFF101820),
            surface = androidx.compose.ui.graphics.Color(0xFF1A2732)
        ) else lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF087E9B),
            secondary = androidx.compose.ui.graphics.Color(0xFF526B76),
            background = androidx.compose.ui.graphics.Color(0xFFF6F8FA),
            surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF)
        )
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Мои Рейсы", fontWeight = FontWeight.Bold)
                            Text(
                                when { selected != null -> "Карточка рейса"; showFinance -> "Финансовый отчёт"; showFuel -> "Учёт топлива"; else -> "Журнал перевозок" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    },
                    navigationIcon = {
                        if (selected != null || showFinance || showFuel) {
                            IconButton(onClick = { selectedTrip = null; showFinance = false; showFuel = false }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                            }
                        }
                    },
                    actions = {
                        if (selected == null && !showFinance && !showFuel) {
                            IconButton(onClick = { showSettings = true }) {
                                Icon(Icons.Default.Settings, contentDescription = "Настройки")
                            }
                            IconButton(onClick = { showForm = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Добавить рейс")
                            }
                        }
                    }
                )
            },
            floatingActionButton = {
                if (selected == null && !showFinance && !showFuel) {
                    FloatingActionButton(onClick = { showForm = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Новый рейс")
                    }
                }
            }
        ) { padding ->
            when {
                selected != null -> TripDetails(
                    trip = selected,
                    dao = dao,
                    activeTripId = activeTripId,
                    onStart = { mileageType ->
                        context.getSharedPreferences("settings", 0).edit()
                            .putString("mileage_type_${selected.id}", mileageType).apply()
                        activeTripId = selected.id
                        trackingPaused = false
                        trackingPrefs.edit().putLong("active_trip_id", selected.id).putBoolean("paused", false).apply()
                        val intent = Intent(context, TrackingService::class.java)
                            .putExtra(TrackingService.EXTRA_TRIP_ID, selected.id)
                            .putExtra(TrackingService.EXTRA_MILEAGE_TYPE, mileageType)
                            .putExtra(TrackingService.EXTRA_RATE_PER_KM, selected.ratePerKm)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            context.startForegroundService(intent)
                        else context.startService(intent)
                    },
                    isPaused = trackingPaused,
                    onPause = { reason ->
                        val intent = Intent(context, TrackingService::class.java)
                            .setAction(TrackingService.ACTION_PAUSE)
                            .putExtra(TrackingService.EXTRA_TRIP_ID, selected.id)
                            .putExtra(TrackingService.EXTRA_PAUSE_REASON, reason)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                        else context.startService(intent)
                        trackingPaused = true
                        trackingPrefs.edit().putBoolean("paused", true).apply()
                    },
                    onResume = { mileageType ->
                        val intent = Intent(context, TrackingService::class.java)
                            .setAction(TrackingService.ACTION_RESUME)
                            .putExtra(TrackingService.EXTRA_TRIP_ID, selected.id)
                            .putExtra(TrackingService.EXTRA_MILEAGE_TYPE, mileageType)
                            .putExtra(TrackingService.EXTRA_RATE_PER_KM, selected.ratePerKm)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
                        else context.startService(intent)
                        trackingPaused = false
                        trackingPrefs.edit().putBoolean("paused", false).putString("mileage_type_${selected.id}", mileageType).apply()
                    },
                    onChangeType = { mileageType ->
                        context.getSharedPreferences("settings", 0).edit()
                            .putString("mileage_type_${selected.id}", mileageType).apply()
                        val intent = Intent(context, TrackingService::class.java)
                            .putExtra(TrackingService.EXTRA_TRIP_ID, selected.id)
                            .putExtra(TrackingService.EXTRA_MILEAGE_TYPE, mileageType)
                            .putExtra(TrackingService.EXTRA_RATE_PER_KM, selected.ratePerKm)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            context.startForegroundService(intent)
                        else context.startService(intent)
                    },
                    onStop = {
                        val intent = Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_STOP)
                        context.startService(intent)
                        activeTripId = -1L
                        trackingPaused = false
                        trackingPrefs.edit().remove("active_trip_id").putBoolean("paused", false).apply()
                    },
                    onAddLog = { showLog = true },
                    onDelete = {
                        scope.launch {
                            dao.deleteTrip(selected)
                            selectedTrip = null
                        }
                    },
                    modifier = Modifier.padding(padding)
                )
                showFinance -> FinanceReport(logs = allLogs, fuelEntries = fuelEntries, expenses = expenses, trips = trips, onAddExpense = { showExpenseForm = true }, modifier = Modifier.padding(padding))
                showFuel -> FuelReport(entries = fuelEntries, trips = trips, onAdd = { showFuelForm = true }, modifier = Modifier.padding(padding))
                else -> Dashboard(
                    trips = trips,
                    onSelect = { selectedTrip = it },
                    onFinance = { showFinance = true },
                    onFuel = { showFuel = true },
                    onSettings = { showSettings = true },
                    modifier = Modifier.padding(padding)
                )
            }
        }

        if (showForm) {
            TripForm(
                defaultRate = defaultRate.toDoubleOrNull() ?: 7.0,
                onDismiss = { showForm = false },
                onSave = { trip ->
                    scope.launch {
                        dao.insertTrip(trip)
                        showForm = false
                    }
                }
            )
        }
        if (showExpenseForm) {
            ExpenseEntryDialog(trips = trips, onDismiss = { showExpenseForm = false }, onSave = { entry ->
                scope.launch { dao.insertExpense(entry); showExpenseForm = false }
            })
        }
        if (showFuelForm) {
            FuelEntryDialog(trips = trips, onDismiss = { showFuelForm = false }, onSave = { entry ->
                scope.launch { dao.insertFuelEntry(entry); showFuelForm = false }
            })
        }
        if (showLog && selected != null) {
            DailyLogForm(
                onDismiss = { showLog = false },
                onSave = { km, hours, note, mileageType ->
                    scope.launch {
                        dao.insertDailyLog(
                            DailyLog(tripId = selected.id, dayStart = System.currentTimeMillis(),
                                km = km, hours = hours, note = note, mileageType = mileageType,
                                ratePerKm = selected.ratePerKm)
                        )
                        val total = dao.dailyKmTotal(selected.id)
                        dao.updateTrip(selected.copy(totalKm = total))
                        showLog = false
                    }
                }
            )
        }
        if (showSettings) {
            SettingsDialog(
                currentRate = defaultRate,
                currentTheme = themeMode,
                automaticBackupEnabled = context.getSharedPreferences("settings", 0)
                    .getBoolean(AutomaticBackupWorker.KEY_ENABLED, false),
                lastAutomaticBackupAt = context.getSharedPreferences("settings", 0)
                    .getLong(AutomaticBackupWorker.KEY_LAST_BACKUP, 0L),
                onDismiss = { showSettings = false },
                onBackup = { backupPicker.launch("MoiReisy_backup_${System.currentTimeMillis()}.zip") },
                onRestore = { restorePicker.launch(arrayOf("application/zip", "application/octet-stream")) },
                onSave = { value, automaticBackup, selectedTheme ->
                    AutomaticBackupScheduler.setEnabled(context, automaticBackup)
                    settingsPrefs.edit().putString("theme_mode", selectedTheme).apply()
                    themeMode = selectedTheme
                    val parsed = value.replace(',', '.').toFloatOrNull()
                    if (parsed != null && parsed >= 0) {
                        context.getSharedPreferences("settings", 0).edit().putFloat("default_rate", parsed).apply()
                        defaultRate = parsed.toString()
                    }
                    showSettings = false
                }
            )
        }
        pendingRestoreUri?.let { restoreUri ->
            AlertDialog(
                onDismissRequest = { pendingRestoreUri = null },
                title = { Text("Восстановить резервную копию?") },
                text = { Text("Текущие данные приложения будут заменены данными из архива. Перед продолжением убедитесь, что у вас есть свежая резервная копия текущих данных.") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingRestoreUri = null
                        scope.launch {
                            try { BackupManager.restore(context, restoreUri); Toast.makeText(context, "Данные восстановлены. Перезапустите приложение.", Toast.LENGTH_LONG).show() }
                            catch (e: Exception) { Toast.makeText(context, "Ошибка восстановления: ${e.message}", Toast.LENGTH_LONG).show() }
                        }
                    }) { Text("Восстановить") }
                },
                dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text("Отмена") } }
            )
        }
    }
}


@Composable
private fun Dashboard(
    trips: List<Trip>, onSelect: (Long) -> Unit,
    onFinance: () -> Unit, onFuel: () -> Unit, onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalKm = trips.sumOf { it.totalKm }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatCard("Всего рейсов", trips.size.toString(), Modifier.weight(1f))
            StatCard("Пробег, км", "%.0f".format(totalKm), Modifier.weight(1f))
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onFinance,
                modifier = Modifier.weight(1f).height(82.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AccountBalanceWallet, null)
                    Text("Финансы", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }

            OutlinedButton(
                onClick = onFuel,
                modifier = Modifier.weight(1f).height(82.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⛽", style = MaterialTheme.typography.titleMedium)
                    Text("Топливо", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }

            OutlinedButton(
                onClick = onSettings,
                modifier = Modifier.weight(1f).height(82.dp),
                contentPadding = PaddingValues(4.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Settings, null)
                    Text("Настройки", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "История поездок",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))

        if (trips.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "Пока нет рейсов",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Нажмите «+», чтобы добавить первую поездку.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(trips, key = { it.id }) { trip ->
                    Card(
                        onClick = { onSelect(trip.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "${trip.fromPlace} → ${trip.toPlace}",
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "${trip.cargo.ifBlank { "Груз не указан" }} · ${trip.status}",
                                color = MaterialTheme.colorScheme.secondary
                            )
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "Накладная: ${trip.waybill.ifBlank { "—" }} · %.1f км"
                                    .format(trip.totalKm)
                            )
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun TripDetails(
    trip: Trip, dao: TripDao, activeTripId: Long, isPaused: Boolean,
    onStart: (String) -> Unit, onChangeType: (String) -> Unit,
    onPause: (String) -> Unit, onResume: (String) -> Unit,
    onStop: () -> Unit, onAddLog: () -> Unit,
    onDelete: () -> Unit, modifier: Modifier = Modifier
) {
    val logs by dao.observeDailyLogs(trip.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val docs by dao.observeDocuments(trip.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val pauses by dao.observePauses(trip.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val trackPoints by dao.observeTrackPoints(trip.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedMileageType by remember(trip.id) {
        mutableStateOf(context.getSharedPreferences("settings", 0)
            .getString("mileage_type_${trip.id}", TrackingService.TYPE_LOADED) ?: TrackingService.TYPE_LOADED)
    }
    var showPauseDialog by remember(trip.id) { mutableStateOf(false) }
    var pauseReason by remember(trip.id) { mutableStateOf(TrackingService.PAUSE_LOADING) }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            onStart(selectedMileageType)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: Exception) {}
            scope.launch {
                dao.insertDocument(TripDocument(tripId = trip.id, uri = it.toString(),
                    title = "Документ ${docs.size + 1}"))
            }
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("${trip.fromPlace} → ${trip.toPlace}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Статус: ${trip.status} · ${date(trip.startAt)}", color = MaterialTheme.colorScheme.secondary)
        Spacer(Modifier.height(16.dp))
        InfoLine("Груз", trip.cargo)
        InfoLine("Номер накладной", trip.waybill)
        InfoLine("Отправитель", trip.sender)
        InfoLine("Получатель", trip.receiver)
        InfoLine("Автомобиль", trip.vehicle)
        InfoLine("Пробег по рейсу", "%.1f км".format(trip.totalKm))
        InfoLine("Ставка оплаты", "%.2f ₽/км".format(trip.ratePerKm))
        InfoLine("Начислено по рейсу", NumberFormat.getCurrencyInstance(Locale("ru", "RU")).format(logs.sumOf { it.km * it.ratePerKm }))
        Spacer(Modifier.height(16.dp))
        Text("Тип текущего пробега", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            FilterChip(
                selected = selectedMileageType == TrackingService.TYPE_LOADED,
                onClick = {
                    selectedMileageType = TrackingService.TYPE_LOADED
                    context.getSharedPreferences("settings", 0).edit().putString("mileage_type_${trip.id}", selectedMileageType).apply()
                    if (activeTripId == trip.id) onChangeType(selectedMileageType)
                },
                label = { Text("С грузом") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = selectedMileageType == TrackingService.TYPE_EMPTY,
                onClick = {
                    selectedMileageType = TrackingService.TYPE_EMPTY
                    context.getSharedPreferences("settings", 0).edit().putString("mileage_type_${trip.id}", selectedMileageType).apply()
                    if (activeTripId == trip.id) onChangeType(selectedMileageType)
                },
                label = { Text("Без груза") },
                modifier = Modifier.weight(1f)
            )
        }
        Text("Ставка: %.2f ₽/км · применяется к выбранному типу".format(trip.ratePerKm),
            color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
        if (activeTripId == trip.id) {
            Text(
                if (isPaused) "Статус GPS: пауза" else "Статус GPS: запись маршрута активна",
                color = if (isPaused) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (activeTripId != trip.id) {
                Button(
                    onClick = {
                        val permissions = mutableListOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                        if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        locationPermission.launch(permissions.toTypedArray())
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, null); Text(" GPS")
                }
            } else if (isPaused) {
                Button(onClick = { onResume(selectedMileageType) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, null); Text(" Продолжить")
                }
            } else {
                OutlinedButton(onClick = { showPauseDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("Пауза")
                }
            }
            OutlinedButton(onClick = onStop, enabled = activeTripId == trip.id, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Stop, null); Text(" Стоп")
            }
        }
        if (showPauseDialog) {
            AlertDialog(
                onDismissRequest = { showPauseDialog = false },
                title = { Text("Причина остановки") },
                text = {
                    Column {
                        listOf(TrackingService.PAUSE_LOADING, TrackingService.PAUSE_UNLOADING,
                            TrackingService.PAUSE_WAITING, TrackingService.PAUSE_REST,
                            TrackingService.PAUSE_OTHER).forEach { reason ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = pauseReason == reason, onClick = { pauseReason = reason })
                                Text(reason)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { onPause(pauseReason); showPauseDialog = false }) { Text("Начать паузу") }
                },
                dismissButton = { TextButton(onClick = { showPauseDialog = false }) { Text("Отмена") } }
            )
        }
        OutlinedButton(onClick = onAddLog, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null); Text(" Добавить дневной пробег")
        }
        Text("Пробег по дням", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val groupedLogs = logs.groupBy { Triple(it.dayStart, it.mileageType, it.ratePerKm) }.values.map { entries ->
            entries.first().copy(km = entries.sumOf { it.km }, hours = entries.sumOf { it.hours })
        }.sortedByDescending { it.dayStart }
        if (groupedLogs.isEmpty()) Text("Дневные записи пока отсутствуют.")
        groupedLogs.forEach { log ->
            ListItem(
                headlineContent = { Text(date(log.dayStart)) },
                supportingContent = {
                    Text("${if (log.mileageType == "EMPTY") "Без груза" else "С грузом"} · ${log.hours} ч · ${log.note}")
                },
                trailingContent = { Text("%.1f км".format(log.km)) }
            )
        }
        Spacer(Modifier.height(14.dp))
        Text("Остановки и паузы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val pauseTotalMs = pauses.sumOf { pause ->
            (pause.endedAt ?: System.currentTimeMillis()) - pause.startedAt
        }.coerceAtLeast(0L)
        InfoLine("Общее время пауз", durationText(pauseTotalMs))
        InfoLine("Оценка времени движения", durationText(estimateMovingTimeMs(trackPoints, pauses)))
        Text("Время движения оценивается по GPS-интервалам; стоянки и интервалы с пропусками GPS не включаются.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
        if (pauses.isEmpty()) {
            Text("Остановок пока нет.", color = MaterialTheme.colorScheme.secondary)
        } else {
            pauses.forEach { pause ->
                ListItem(
                    headlineContent = { Text(pause.reason) },
                    supportingContent = { Text("${date(pause.startedAt)} — ${pause.endedAt?.let { date(it) } ?: "сейчас"}") },
                    trailingContent = {
                        Text(durationText(((pause.endedAt ?: System.currentTimeMillis()) - pause.startedAt).coerceAtLeast(0L)))
                    }
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Документы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { picker.launch(arrayOf("image/*", "application/pdf")) }) {
                Icon(Icons.Default.AttachFile, "Добавить документ")
            }
        }
        Text("Файлы: ${docs.size}")
        docs.forEach { doc ->
            ListItem(
                headlineContent = { Text(doc.title) },
                supportingContent = { Text(doc.uri, maxLines = 1) },
                trailingContent = {
                    IconButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(doc.uri), "*/*").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }) { Icon(Icons.Default.Map, "Открыть") }
                }
            )
        }
        Spacer(Modifier.height(20.dp))
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.Default.Delete, null); Text(" Удалить рейс")
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    ListItem(headlineContent = { Text(label, color = MaterialTheme.colorScheme.secondary) },
        supportingContent = { Text(value.ifBlank { "—" }) })
}

@Composable
private fun TripForm(defaultRate: Double, onDismiss: () -> Unit, onSave: (Trip) -> Unit) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var cargo by remember { mutableStateOf("") }
    var waybill by remember { mutableStateOf("") }
    var sender by remember { mutableStateOf("") }
    var receiver by remember { mutableStateOf("") }
    var vehicle by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf(defaultRate.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый рейс") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                FormField("Откуда", from) { from = it }
                FormField("Куда", to) { to = it }
                FormField("Груз", cargo) { cargo = it }
                FormField("Номер накладной", waybill) { waybill = it }
                FormField("Отправитель", sender) { sender = it }
                FormField("Получатель", receiver) { receiver = it }
                FormField("Автомобиль / госномер", vehicle) { vehicle = it }
                FormField("Ставка, ₽ за км (оба типа пробега)", rate) { rate = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(Trip(fromPlace = from.trim(), toPlace = to.trim(), cargo = cargo.trim(),
                    waybill = waybill.trim(), sender = sender.trim(), receiver = receiver.trim(),
                    vehicle = vehicle.trim(), ratePerKm = rate.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: defaultRate))
            }, enabled = from.isNotBlank() && to.isNotBlank()) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun DailyLogForm(onDismiss: () -> Unit, onSave: (Double, Double, String, String) -> Unit) {
    var km by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var mileageType by remember { mutableStateOf("LOADED") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Пробег за день") },
        text = {
            Column {
                FormField("Километры", km) { km = it }
                FormField("Часы в дороге", hours) { hours = it }
                Text("Тип пробега", fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = mileageType == "LOADED", onClick = { mileageType = "LOADED" })
                    Text("С грузом")
                    Spacer(Modifier.width(12.dp))
                    RadioButton(selected = mileageType == "EMPTY", onClick = { mileageType = "EMPTY" })
                    Text("Без груза")
                }
                FormField("Комментарий", note) { note = it }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(km.replace(',', '.').toDoubleOrNull() ?: 0.0,
                    hours.replace(',', '.').toDoubleOrNull() ?: 0.0, note, mileageType)
            }, enabled = (km.replace(',', '.').toDoubleOrNull() ?: 0.0) >= 0) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun SettingsDialog(
    currentRate: String,
    currentTheme: String,
    automaticBackupEnabled: Boolean,
    lastAutomaticBackupAt: Long,
    onDismiss: () -> Unit,
    onBackup: () -> Unit,
    onRestore: () -> Unit,
    onSave: (String, Boolean, String) -> Unit
) {
    var rate by remember(currentRate) { mutableStateOf(currentRate) }
    var selectedTheme by remember(currentTheme) { mutableStateOf(currentTheme) }
    var automaticBackup by remember(automaticBackupEnabled) { mutableStateOf(automaticBackupEnabled) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройки приложения") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Тема приложения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                listOf("SYSTEM" to "Системная", "LIGHT" to "Светлая", "DARK" to "Тёмная").forEach { (value, label) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        RadioButton(selected = selectedTheme == value, onClick = { selectedTheme = value })
                        Text(label, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("Ставка по умолчанию применяется к новым рейсам. Для отдельного рейса её можно изменить перед стартом.")
                Spacer(Modifier.height(10.dp))
                FormField("Ставка, ₽ за километр", rate) { rate = it }
                Spacer(Modifier.height(16.dp))
                Text("Резервное копирование", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Сохраните рейсы, GPS-треки, начисления, остановки, настройки и вложенные документы в один ZIP-архив.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onBackup, modifier = Modifier.weight(1f)) { Text("Создать копию") }
                    OutlinedButton(onClick = onRestore, modifier = Modifier.weight(1f)) { Text("Восстановить") }
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("Автоматическая копия каждый день", fontWeight = FontWeight.SemiBold)
                        Text("Хранится до 7 последних копий в памяти приложения", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = automaticBackup, onCheckedChange = { automaticBackup = it })
                }
                if (lastAutomaticBackupAt > 0L) {
                    Text("Последняя копия: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(lastAutomaticBackupAt)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                } else {
                    Text("Автоматическая копия ещё не создавалась", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                }
                Text("Копия создаётся без интернета. Для защиты при потере телефона периодически сохраняйте ZIP вручную в облако или на компьютер.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(rate, automaticBackup, selectedTheme) }, enabled = (rate.replace(',', '.').toFloatOrNull() ?: -1f) >= 0) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanceReport(
    logs: List<DailyLog>, fuelEntries: List<FuelEntry>, expenses: List<ExpenseEntry>, trips: List<Trip>,
    onAddExpense: () -> Unit, modifier: Modifier = Modifier
) {
    var period by remember { mutableStateOf("Всё время") }
    val now = System.currentTimeMillis()
    val start = when (period) {
        "Сегодня" -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        "Неделя" -> Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); val d = (get(Calendar.DAY_OF_WEEK) + 5) % 7; add(Calendar.DAY_OF_MONTH, -d) }.timeInMillis
        "Месяц" -> Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        else -> 0L
    }
    // GPS rows are authoritative for a trip/day/load-type group. A manual row in the same group is
    // ignored to prevent double counting when a driver also enters the odometer total manually.
    val gpsGroups = logs.filter { it.note.startsWith("GPS ·") }.map { Triple(it.tripId, dayBucket(it.dayStart), it.mileageType) }.toSet()
    val filtered = logs.filter { it.dayStart in start..now && (it.note.startsWith("GPS ·") || Triple(it.tripId, dayBucket(it.dayStart), it.mileageType) !in gpsGroups) }
    val periodFuel = fuelEntries.filter { it.filledAt in start..now }
    val periodExpenses = expenses.filter { it.spentAt in start..now }
    val loadedKm = filtered.filter { it.mileageType == "LOADED" }.sumOf { it.km }
    val emptyKm = filtered.filter { it.mileageType == "EMPTY" }.sumOf { it.km }
    val totalKm = loadedKm + emptyKm
    val income = filtered.sumOf { it.km * it.ratePerKm }
    val fuelCost = periodFuel.sumOf { it.totalCost }
    val otherCost = periodExpenses.sumOf { it.amount }
    val allCost = fuelCost + otherCost
    val profit = income - allCost
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("ru", "RU")) }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Финансовый отчёт", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
            listOf("Сегодня", "Неделя", "Месяц", "Всё время").forEach { item -> FilterChip(selected = period == item, onClick = { period = item }, label = { Text(item) }) }
        }
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("Чистый результат", color = MaterialTheme.colorScheme.secondary)
            Text(currency.format(profit), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = if (profit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(12.dp))
            FinanceLine("Начислено за пробег", currency.format(income))
            FinanceLine("Топливо (${"%.1f".format(periodFuel.sumOf { it.liters })} л)", "− ${currency.format(fuelCost)}")
            FinanceLine("Другие расходы", "− ${currency.format(otherCost)}")
            Divider(Modifier.padding(vertical = 8.dp))
            FinanceLine("Всего расходов", currency.format(allCost))
            FinanceLine("Пробег", "%.1f км".format(totalKm))
            FinanceLine("С грузом / без груза", "%.1f / %.1f км".format(loadedKm, emptyKm))
            FinanceLine("Прибыль на 1 км", if (totalKm > 0) currency.format(profit / totalKm) else "—")
            FinanceLine("Расходы на 1 км", if (totalKm > 0) currency.format(allCost / totalKm) else "—")
        } }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Другие расходы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Button(onClick = onAddExpense) { Text("Добавить +") }
        }
        if (periodExpenses.isEmpty()) Text("Платные дороги, стоянки, ремонт и прочие расходы появятся здесь после добавления.", color = MaterialTheme.colorScheme.secondary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = false)) {
            items(periodExpenses, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(item.category, fontWeight = FontWeight.SemiBold); Text(date(item.spentAt), style = MaterialTheme.typography.bodySmall); if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary); val trip = trips.firstOrNull { it.id == item.tripId }; if (trip != null) Text("${trip.fromPlace} → ${trip.toPlace}", style = MaterialTheme.typography.labelSmall) }
                    Text(currency.format(item.amount), fontWeight = FontWeight.Bold)
                } }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Начисления по пробегу", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        val history = filtered.groupBy { Triple(it.dayStart, it.mileageType, it.ratePerKm) }.values.map { rows -> rows.first().copy(km = rows.sumOf { it.km }) }.sortedByDescending { it.dayStart }
        if (history.isEmpty()) Text("За период нет записей пробега.", color = MaterialTheme.colorScheme.secondary)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f, fill = false)) {
            items(history, key = { it.id }) { log -> Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(date(log.dayStart)); Text(if (log.mileageType == "EMPTY") "Без груза · %.1f км".format(log.km) else "С грузом · %.1f км".format(log.km), style = MaterialTheme.typography.bodySmall) }; Text(currency.format(log.km * log.ratePerKm), fontWeight = FontWeight.Bold) } } }
        }
    }
}

private fun dayBucket(timestamp: Long): Long = Calendar.getInstance().apply { timeInMillis = timestamp; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

@Composable
private fun FinanceLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = MaterialTheme.colorScheme.secondary); Text(value, fontWeight = FontWeight.SemiBold) }
}

@Composable
private fun ExpenseEntryDialog(trips: List<Trip>, onDismiss: () -> Unit, onSave: (ExpenseEntry) -> Unit) {
    val categories = listOf("Платная дорога", "Стоянка", "Ремонт", "Обслуживание", "Мойка", "Прочее")
    var category by remember { mutableStateOf(categories.first()) }
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var tripId by remember { mutableStateOf<Long?>(null) }
    val parsed = amount.replace(',', '.').toDoubleOrNull()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Новый расход") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Категория", style = MaterialTheme.typography.labelLarge)
            categories.forEach { item -> Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = category == item, onClick = { category = item }); Text(item) } }
            FormField("Сумма, ₽", amount) { amount = it }
            FormField("Описание / комментарий", description) { description = it }
            Text("Рейс (необязательно)", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { tripId = null }) { Text(if (tripId == null) "✓ Без привязки" else "Без привязки") }
            trips.take(5).forEach { trip -> TextButton(onClick = { tripId = trip.id }) { Text("${if (tripId == trip.id) "✓ " else ""}${trip.fromPlace} → ${trip.toPlace}") } }
        }
    }, confirmButton = { TextButton(onClick = { if (parsed != null && parsed > 0) onSave(ExpenseEntry(tripId = tripId, category = category, amount = parsed, description = description)) }, enabled = parsed != null && parsed > 0) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
private fun FuelEntryDialog(trips: List<Trip>, onDismiss: () -> Unit, onSave: (FuelEntry) -> Unit) {
    var liters by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }
    var station by remember { mutableStateOf("") }
    var fuelType by remember { mutableStateOf("Дизель") }
    var tripId by remember { mutableStateOf<Long?>(null) }
    val total = (liters.replace(',', '.').toDoubleOrNull() ?: 0.0) * (price.replace(',', '.').toDoubleOrNull() ?: 0.0)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Добавить заправку") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            FormField("Количество, литров", liters) { liters = it }
            FormField("Цена за литр, ₽", price) { price = it }
            FormField("Пробег одометра, км", odometer) { odometer = it }
            FormField("АЗС / место", station) { station = it }
            Text("Тип топлива: $fuelType")
            Row { TextButton(onClick = { fuelType = "Дизель" }) { Text("Дизель") }; TextButton(onClick = { fuelType = "Другое" }) { Text("Другое") } }
            Text("Привязка к рейсу", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = { tripId = null }) { Text("Без привязки") }
            trips.take(5).forEach { trip -> TextButton(onClick = { tripId = trip.id }) { Text("${if (tripId == trip.id) "✓ " else ""}${trip.fromPlace} → ${trip.toPlace}") } }
            Text("Итого: ${"%.2f".format(total)} ₽", fontWeight = FontWeight.Bold)
        }
    }, confirmButton = { TextButton(onClick = {
        val l = liters.replace(',', '.').toDoubleOrNull() ?: 0.0
        val pr = price.replace(',', '.').toDoubleOrNull() ?: 0.0
        val odo = odometer.replace(',', '.').toDoubleOrNull() ?: 0.0
        if (l > 0 && pr >= 0 && odo >= 0) onSave(FuelEntry(tripId = tripId, liters = l, pricePerLiter = pr, odometerKm = odo, station = station, fuelType = fuelType))
    }, enabled = (liters.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0 && (price.replace(',', '.').toDoubleOrNull() ?: -1.0) >= 0 && (odometer.replace(',', '.').toDoubleOrNull() ?: -1.0) >= 0) { Text("Сохранить") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } })
}

@Composable
private fun FuelReport(entries: List<FuelEntry>, trips: List<Trip>, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val totalLiters = entries.sumOf { it.liters }; val totalCost = entries.sumOf { it.totalCost }
    val ordered = entries.sortedBy { it.odometerKm }
    val distance = if (ordered.size >= 2) (ordered.last().odometerKm - ordered.first().odometerKm).coerceAtLeast(0.0) else 0.0
    val consumption = if (distance > 0) totalLiters * 100.0 / distance else 0.0
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Учёт топлива", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Button(onClick = onAdd) { Text("Заправка +") } }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text("Расходы на топливо", color = MaterialTheme.colorScheme.secondary)
            Text("%.2f ₽".format(totalCost), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text("%.1f л · %.2f ₽/л в среднем".format(totalLiters, if (totalLiters > 0) totalCost / totalLiters else 0.0))
            Text(if (distance > 0) "Средний расход: %.1f л/100 км".format(consumption) else "Для расчёта расхода нужны минимум две заправки с разным пробегом одометра", style = MaterialTheme.typography.bodySmall)
        } }
        Spacer(Modifier.height(12.dp)); Text("История заправок", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        if (entries.isEmpty()) Text("Пока нет заправок. Добавьте первую запись.", color = MaterialTheme.colorScheme.secondary)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(entries, key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth()) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(date(entry.filledAt), fontWeight = FontWeight.SemiBold); Text("${entry.station.ifBlank { "АЗС не указана" }} · ${entry.fuelType}"); Text("%.1f л × %.2f ₽ · одометр %.0f км".format(entry.liters, entry.pricePerLiter, entry.odometerKm), style = MaterialTheme.typography.bodySmall) }; Text("%.2f ₽".format(entry.totalCost), fontWeight = FontWeight.Bold) } }
        } }
    }
}

@Composable
private fun FormField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(label) },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), singleLine = true)
}

private fun date(time: Long): String =
    SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(time))

private fun durationText(milliseconds: Long): String {
    val totalMinutes = milliseconds / 60_000L
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours} ч ${minutes} мин" else "${minutes} мин"
}

private fun estimateMovingTimeMs(points: List<TrackPoint>, pauses: List<TripPause>): Long {
    if (points.size < 2) return 0L
    var total = 0L
    val ordered = points.sortedBy { it.recordedAt }
    for (index in 1 until ordered.size) {
        val previous = ordered[index - 1]
        val current = ordered[index]
        val elapsed = current.recordedAt - previous.recordedAt
        if (elapsed !in 5_000L..300_000L) continue
        val overlapsPause = pauses.any { pause ->
            val pauseEnd = pause.endedAt ?: System.currentTimeMillis()
            pause.startedAt < current.recordedAt && pauseEnd > previous.recordedAt
        }
        if (overlapsPause) continue
        val results = FloatArray(1)
        android.location.Location.distanceBetween(
            previous.latitude, previous.longitude,
            current.latitude, current.longitude,
            results
        )
        val speedKmh = (results[0] / 1000.0) / (elapsed / 3_600_000.0)
        if (speedKmh >= 2.0 && speedKmh <= 140.0) total += elapsed
    }
    return total
}
