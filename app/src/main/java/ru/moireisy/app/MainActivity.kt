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
            primary = androidx.compose.ui.graphics.Color(0xFF7F9EF3),
            onPrimary = androidx.compose.ui.graphics.Color(0xFF141414),
            secondary = androidx.compose.ui.graphics.Color(0xFFD2E0FA),
            onSecondary = androidx.compose.ui.graphics.Color(0xFF141414),
            background = androidx.compose.ui.graphics.Color(0xFF141414),
            onBackground = androidx.compose.ui.graphics.Color(0xFFF0F0F0),
            surface = androidx.compose.ui.graphics.Color(0xFF1F1F1F),
            onSurface = androidx.compose.ui.graphics.Color(0xFFF0F0F0),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFF262626),
            onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFA6A6A6),
            outline = androidx.compose.ui.graphics.Color(0xFF424242),
            tertiary = androidx.compose.ui.graphics.Color(0xFF9AB8C4)
        ) else lightColorScheme(
            primary = androidx.compose.ui.graphics.Color(0xFF2F54EB),
            onPrimary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
            secondary = androidx.compose.ui.graphics.Color(0xFF597EF7),
            onSecondary = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
            background = androidx.compose.ui.graphics.Color(0xFFF5F7FC),
            onBackground = androidx.compose.ui.graphics.Color(0xFF262626),
            surface = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
            onSurface = androidx.compose.ui.graphics.Color(0xFF262626),
            surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF0F5FF),
            onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF595959),
            outline = androidx.compose.ui.graphics.Color(0xFFD9D9D9),
            tertiary = androidx.compose.ui.graphics.Color(0xFF1D39C4)
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        }
                    }
                )
            },
            floatingActionButton = {
                if (selected == null && !showFinance && !showFuel) {
                    FloatingActionButton(onClick = { showForm = true }, shape = androidx.compose.foundation.shape.CircleShape) {
                        Icon(Icons.Default.Add, contentDescription = "Новый рейс")
                    }
                }
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = selected != null || (!showFinance && !showFuel),
                        onClick = { selectedTrip = null; showFinance = false; showFuel = false },
                        icon = { Icon(Icons.Default.Map, contentDescription = null) },
                        label = { Text("Рейсы") }
                    )
                    NavigationBarItem(
                        selected = showFinance,
                        onClick = { selectedTrip = null; showFinance = true; showFuel = false },
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                        label = { Text("Финансы") }
                    )
                    NavigationBarItem(
                        selected = showFuel,
                        onClick = { selectedTrip = null; showFinance = false; showFuel = true },
                        icon = { Text("⛽") },
                        label = { Text("Топливо") }
                    )
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
    Column(modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Сегодня в рейсах", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            StatCard("Всего рейсов", trips.size.toString(), Modifier.weight(1f))
            StatCard("Пробег, км", "%.0f".format(totalKm), Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Card(onClick = onFinance, modifier = Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("Финансы", style = MaterialTheme.typography.labelMedium)
                }
            }
            Card(onClick = onFuel, modifier = Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⛽", style = MaterialTheme.typography.titleMedium)
                    Text("Топливо", style = MaterialTheme.typography.labelMedium)
                }
            }
            Card(onClick = onSettings, modifier = Modifier.weight(1f)) {
                Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(4.dp))
                    Text("Настройки", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(22.dp))
        Text("История рейсов", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (trips.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Пока нет рейсов", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Нажмите «+», чтобы добавить первый рейс.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(trips, key = { it.id }) { trip ->
                    Card(onClick = { onSelect(trip.id) }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("${trip.fromPlace} → ${trip.toPlace}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(6.dp))
                            Text(trip.status, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(7.dp))
                            Text(trip.cargo.ifBlank { "Груз не указан" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(3.dp))
                            Text("Накладная: ${trip.waybill.ifBlank { "—" }} · %.1f км".format(trip.totalKm), style = MaterialTheme.typography.bodySmall)
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
        Column(Modifier.fillMaxWidth().padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(5.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
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
            .getString("mileage_type_" + trip.id, TrackingService.TYPE_LOADED) ?: TrackingService.TYPE_LOADED)
    }
    var showPauseDialog by remember(trip.id) { mutableStateOf(false) }
    var pauseReason by remember(trip.id) { mutableStateOf(TrackingService.PAUSE_LOADING) }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) onStart(selectedMileageType)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            catch (_: Exception) {}
            scope.launch { dao.insertDocument(TripDocument(tripId = trip.id, uri = it.toString(), title = "Документ " + (docs.size + 1))) }
        }
    }
    val isActive = activeTripId == trip.id
    val earned = logs.sumOf { it.km * it.ratePerKm }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(trip.fromPlace + " → " + trip.toPlace, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(date(trip.startAt), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(if (isActive && isPaused) "На паузе" else if (isActive) "В пути" else trip.status,
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Сводка по рейсу", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("Пробег", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("%.1f км".format(trip.totalKm), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Начислено", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(NumberFormat.getCurrencyInstance(Locale("ru", "RU")).format(earned), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Информация о рейсе", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                InfoLine("Груз", trip.cargo)
                Divider()
                InfoLine("Накладная", trip.waybill)
                Divider()
                InfoLine("Отправитель", trip.sender)
                Divider()
                InfoLine("Получатель", trip.receiver)
                Divider()
                InfoLine("Автомобиль", trip.vehicle)
                Divider()
                InfoLine("Ставка", "%.2f ₽/км".format(trip.ratePerKm))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Учет пробега", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Ставка %.2f ₽/км".format(trip.ratePerKm), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            FilterChip(selected = selectedMileageType == TrackingService.TYPE_LOADED, onClick = {
                selectedMileageType = TrackingService.TYPE_LOADED
                context.getSharedPreferences("settings", 0).edit().putString("mileage_type_" + trip.id, selectedMileageType).apply()
                if (isActive) onChangeType(selectedMileageType)
            }, label = { Text("С грузом") }, modifier = Modifier.weight(1f))
            FilterChip(selected = selectedMileageType == TrackingService.TYPE_EMPTY, onClick = {
                selectedMileageType = TrackingService.TYPE_EMPTY
                context.getSharedPreferences("settings", 0).edit().putString("mileage_type_" + trip.id, selectedMileageType).apply()
                if (isActive) onChangeType(selectedMileageType)
            }, label = { Text("Без груза") }, modifier = Modifier.weight(1f))
        }
        if (isActive) Text(if (isPaused) "Запись маршрута приостановлена" else "Запись маршрута активна",
            color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            if (!isActive) {
                Button(onClick = {
                    val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    if (Build.VERSION.SDK_INT >= 33) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                    locationPermission.launch(permissions.toTypedArray())
                }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Начать рейс")
                }
            } else if (isPaused) {
                Button(onClick = { onResume(selectedMileageType) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Продолжить")
                }
            } else {
                OutlinedButton(onClick = { showPauseDialog = true }, modifier = Modifier.weight(1f)) {
                    Text("Ⅱ"); Spacer(Modifier.width(6.dp)); Text("Пауза")
                }
            }
            OutlinedButton(onClick = onStop, enabled = isActive, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Stop, null); Spacer(Modifier.width(6.dp)); Text("Завершить")
            }
        }
        if (showPauseDialog) {
            val reasons = listOf(TrackingService.PAUSE_LOADING, TrackingService.PAUSE_UNLOADING,
                TrackingService.PAUSE_WAITING, TrackingService.PAUSE_REST, TrackingService.PAUSE_OTHER)
            AlertDialog(onDismissRequest = { showPauseDialog = false }, title = { Text("Причина паузы") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        reasons.chunked(2).forEach { rowItems ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                rowItems.forEach { reason ->
                                    FilterChip(selected = pauseReason == reason, onClick = { pauseReason = reason },
                                        label = { Text(reason) }, modifier = Modifier.weight(1f))
                                }
                                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { showPauseDialog = false }) { Text("Отмена") }
                        Button(onClick = { onPause(pauseReason); showPauseDialog = false }) { Text("Начать паузу") }
                    }
                })
        }
        Spacer(Modifier.height(18.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Пробег по дням", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    SmallFloatingActionButton(onClick = onAddLog) { Icon(Icons.Default.Add, contentDescription = "Добавить запись пробега") }
                }
                val groupedLogs = logs.groupBy { Triple(it.dayStart, it.mileageType, it.ratePerKm) }.values.map { entries ->
                    entries.first().copy(km = entries.sumOf { it.km }, hours = entries.sumOf { it.hours })
                }.sortedByDescending { it.dayStart }
                if (groupedLogs.isEmpty()) Text("Дневные записи пока отсутствуют.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp))
                else groupedLogs.forEachIndexed { index, log ->
                    if (index > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(date(log.dayStart), fontWeight = FontWeight.SemiBold)
                            Text((if (log.mileageType == "EMPTY") "Без груза" else "С грузом") + " · " + log.hours + " ч",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("%.1f км".format(log.km), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Остановки и паузы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                val pauseTotalMs = pauses.sumOf { (it.endedAt ?: System.currentTimeMillis()) - it.startedAt }.coerceAtLeast(0L)
                Spacer(Modifier.height(10.dp))
                InfoLine("Время пауз", durationText(pauseTotalMs))
                Divider()
                InfoLine("Время в движении", durationText(estimateMovingTimeMs(trackPoints, pauses)))
                Text("Оценка по GPS-интервалам; стоянки и пропуски GPS не включаются.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (pauses.isEmpty()) Text("Остановок пока нет.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp))
                else pauses.forEachIndexed { index, pause ->
                    if (index > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pause.reason, fontWeight = FontWeight.SemiBold)
                            Text(date(pause.startedAt) + " — " + (pause.endedAt?.let { date(it) } ?: "сейчас"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(durationText(((pause.endedAt ?: System.currentTimeMillis()) - pause.startedAt).coerceAtLeast(0L)))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Документы · " + docs.size, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { picker.launch(arrayOf("image/*", "application/pdf")) }) {
                        Icon(Icons.Default.AttachFile, "Добавить документ", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                if (docs.isEmpty()) Text("Прикрепите накладную или фото документов.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else docs.forEachIndexed { index, doc ->
                    if (index > 0) Divider()
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(doc.title, fontWeight = FontWeight.SemiBold)
                            Text("Документ рейса", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = {
                            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(doc.uri), "*/*")
                                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            try { context.startActivity(intent) } catch (_: Exception) {}
                        }) { Text("Открыть") }
                    }
                }
            }
        }
        OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
            Icon(Icons.Default.Delete, null); Spacer(Modifier.width(8.dp)); Text("Удалить рейс")
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.9f))
        Text(value.ifBlank { "—" }, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.1f))
    }
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
        onDismissRequest = onDismiss, title = { Text("Новый рейс") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card { Column(Modifier.padding(12.dp)) {
                    Text("Маршрут", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FormField("Откуда", from) { from = it }
                    FormField("Куда", to) { to = it }
                } }
                Card { Column(Modifier.padding(12.dp)) {
                    Text("Груз", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FormField("Наименование груза", cargo) { cargo = it }
                    FormField("Номер накладной", waybill) { waybill = it }
                } }
                Card { Column(Modifier.padding(12.dp)) {
                    Text("Участники", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FormField("Отправитель", sender) { sender = it }
                    FormField("Получатель", receiver) { receiver = it }
                } }
                Card { Column(Modifier.padding(12.dp)) {
                    Text("Автомобиль и оплата", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FormField("Автомобиль / госномер", vehicle) { vehicle = it }
                    FormField("Ставка, ₽ за километр", rate) { rate = it }
                } }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                Button(onClick = {
                    onSave(Trip(fromPlace = from.trim(), toPlace = to.trim(), cargo = cargo.trim(),
                        waybill = waybill.trim(), sender = sender.trim(), receiver = receiver.trim(),
                        vehicle = vehicle.trim(), ratePerKm = rate.replace(',', '.').toDoubleOrNull()?.coerceAtLeast(0.0) ?: defaultRate))
                }, enabled = from.isNotBlank() && to.isNotBlank()) { Text("Сохранить") }
            }
        }
    )
}

@Composable
private fun DailyLogForm(onDismiss: () -> Unit, onSave: (Double, Double, String, String) -> Unit) {
    var km by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var mileageType by remember { mutableStateOf("LOADED") }
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text("Добавить запись пробега") },
        text = {
            Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card { Column(Modifier.padding(12.dp)) {
                    FormField("Километры", km) { km = it }
                    FormField("Часы в дороге", hours) { hours = it }
                } }
                Card { Column(Modifier.padding(12.dp)) {
                    Text("Тип пробега", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(selected = mileageType == "LOADED", onClick = { mileageType = "LOADED" }, label = { Text("С грузом") })
                        FilterChip(selected = mileageType == "EMPTY", onClick = { mileageType = "EMPTY" }, label = { Text("Без груза") })
                    }
                    FormField("Комментарий", note) { note = it }
                } }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                Button(onClick = {
                    onSave(km.replace(',', '.').toDoubleOrNull() ?: 0.0,
                        hours.replace(',', '.').toDoubleOrNull() ?: 0.0, note, mileageType)
                }, enabled = (km.replace(',', '.').toDoubleOrNull() ?: 0.0) > 0) { Text("Сохранить") }
            }
        }
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
        onDismissRequest = onDismiss, title = { Text("Настройки") },
        text = {
            Column(Modifier.heightIn(max = 540.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Card { Column(Modifier.padding(12.dp)) {
                    Text("Тема приложения", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        listOf("SYSTEM" to "Системная", "LIGHT" to "Светлая", "DARK" to "Тёмная").forEach { (value, label) ->
                            FilterChip(selected = selectedTheme == value, onClick = { selectedTheme = value }, label = { Text(label) })
                        }
                    }
                } }
                Card { Column(Modifier.padding(12.dp)) {
                    Text("Ставка по умолчанию", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Применяется к новым рейсам; ставку можно изменить отдельно.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FormField("Ставка, ₽ за километр", rate) { rate = it }
                } }
                Card { Column(Modifier.padding(12.dp)) {
                    Text("Резервное копирование", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Рейсы, GPS-треки, начисления, остановки, настройки и документы сохраняются в ZIP-архив.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = onBackup, modifier = Modifier.weight(1f)) { Text("Создать копию") }
                        FilledTonalButton(onClick = onRestore, modifier = Modifier.weight(1f)) { Text("Из архива") }
                    }
                } }
                Card { Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("Автоматическая копия", fontWeight = FontWeight.SemiBold)
                            Text("Ежедневно · хранится до 7 копий", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = automaticBackup, onCheckedChange = { automaticBackup = it })
                    }
                    Text(if (lastAutomaticBackupAt > 0L)
                        "Последняя копия: " + SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(lastAutomaticBackupAt))
                    else "Автоматическая копия ещё не создавалась",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                Button(onClick = { onSave(rate, automaticBackup, selectedTheme) },
                    enabled = (rate.replace(',', '.').toFloatOrNull() ?: -1f) >= 0) { Text("Сохранить") }
            }
        }
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
    val history = filtered.groupBy { Triple(it.dayStart, it.mileageType, it.ratePerKm) }.values
        .map { rows -> rows.first().copy(km = rows.sumOf { it.km }) }.sortedByDescending { it.dayStart }

    LazyColumn(modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Финансовый отчёт", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                listOf("Сегодня", "Неделя", "Месяц", "Всё время").forEach { item ->
                    FilterChip(selected = period == item, onClick = { period = item }, label = { Text(item) })
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Итог за период", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    FinanceLine("Начислено за пробег", currency.format(income))
                    FinanceLine("Топливо · %.1f л".format(periodFuel.sumOf { it.liters }), "− " + currency.format(fuelCost))
                    FinanceLine("Другие расходы", "− " + currency.format(otherCost))
                    Divider(Modifier.padding(vertical = 6.dp))
                    FinanceLine("Прибыль", currency.format(profit))
                    FinanceLine("Общие расходы", currency.format(allCost))
                    FinanceLine("Пробег", "%.1f км".format(totalKm))
                    FinanceLine("С грузом / без груза", "%.1f / %.1f км".format(loadedKm, emptyKm))
                    FinanceLine("Прибыль на 1 км", if (totalKm > 0) currency.format(profit / totalKm) else "—")
                    FinanceLine("Расходы на 1 км", if (totalKm > 0) currency.format(allCost / totalKm) else "—")
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Другие расходы", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                SmallFloatingActionButton(onClick = onAddExpense) { Icon(Icons.Default.Add, contentDescription = "Добавить расход") }
            }
        }
        if (periodExpenses.isEmpty()) item {
            Text("Платные дороги, стоянки и другие расходы появятся здесь после добавления.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(periodExpenses, key = { "expense-" + it.id }) { item ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.category, fontWeight = FontWeight.SemiBold)
                        Text(date(item.spentAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall)
                        trips.firstOrNull { it.id == item.tripId }?.let {
                            Text(it.fromPlace + " → " + it.toPlace, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text(currency.format(item.amount), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { Text("Начисления по пробегу", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (history.isEmpty()) item { Text("За период нет записей пробега.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(history, key = { "mileage-" + it.id + "-" + it.dayStart }) { log ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(date(log.dayStart), fontWeight = FontWeight.SemiBold)
                        Text((if (log.mileageType == "EMPTY") "Без груза" else "С грузом") + " · %.1f км".format(log.km),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(currency.format(log.km * log.ratePerKm), fontWeight = FontWeight.SemiBold)
                }
            }
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
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Новый расход") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Категория", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                categories.chunked(2).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        rowItems.forEach { item -> FilterChip(selected = category == item, onClick = { category = item },
                            label = { Text(item) }, modifier = Modifier.weight(1f)) }
                    }
                }
                FormField("Сумма, ₽", amount) { amount = it }
                FormField("Описание / комментарий", description) { description = it }
                Text("Привязка к рейсу", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    FilterChip(selected = tripId == null, onClick = { tripId = null }, label = { Text("Без привязки") })
                    trips.take(5).forEach { trip ->
                        FilterChip(selected = tripId == trip.id, onClick = { tripId = trip.id },
                            label = { Text(trip.fromPlace + " → " + trip.toPlace) })
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                Button(onClick = {
                    if (parsed != null && parsed > 0) onSave(ExpenseEntry(tripId = tripId, category = category, amount = parsed, description = description))
                }, enabled = parsed != null && parsed > 0) { Text("Сохранить") }
            }
        }
    )
}

@Composable
private fun FuelEntryDialog(trips: List<Trip>, onDismiss: () -> Unit, onSave: (FuelEntry) -> Unit) {
    val stations = listOf("Роснефть", "Газпром", "ГазпромНефть", "Лукойл", "Татнефть")
    var liters by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var odometer by remember { mutableStateOf("") }
    var station by remember { mutableStateOf("") }
    var stationExpanded by remember { mutableStateOf(false) }
    var fuelType by remember { mutableStateOf("Дизель") }
    var tripId by remember { mutableStateOf<Long?>(null) }
    val total = (liters.replace(',', '.').toDoubleOrNull() ?: 0.0) * (price.replace(',', '.').toDoubleOrNull() ?: 0.0)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Добавить заправку") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Card { Column(Modifier.padding(12.dp)) {
                    FormField("Количество, литров", liters) { liters = it }
                    FormField("Цена за литр, ₽", price) { price = it }
                    FormField("Пробег одометра, км", odometer) { odometer = it }
                } }
                Card { Column(Modifier.padding(12.dp)) {
                    Text("Заправка", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { stationExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(if (station.isBlank()) "Выбрать АЗС" else station, modifier = Modifier.weight(1f))
                            Text("⌄")
                        }
                        DropdownMenu(expanded = stationExpanded, onDismissRequest = { stationExpanded = false }) {
                            stations.forEach { item ->
                                DropdownMenuItem(text = { Text(item) }, onClick = { station = item; stationExpanded = false })
                            }
                        }
                    }
                    Text("Тип топлива", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Дизель", "Другое").forEach { item ->
                            FilterChip(selected = fuelType == item, onClick = { fuelType = item }, label = { Text(item) })
                        }
                    }
                } }
                Card { Column(Modifier.padding(12.dp)) {
                    Text("Рейс", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        FilterChip(selected = tripId == null, onClick = { tripId = null }, label = { Text("Без привязки") })
                        trips.take(5).forEach { trip ->
                            FilterChip(selected = tripId == trip.id, onClick = { tripId = trip.id },
                                label = { Text(trip.fromPlace + " → " + trip.toPlace) })
                        }
                    }
                } }
                Text("Итого · %.2f ₽".format(total), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
        },
        confirmButton = {
            val l = liters.replace(',', '.').toDoubleOrNull() ?: 0.0
            val pr = price.replace(',', '.').toDoubleOrNull() ?: -1.0
            val odo = odometer.replace(',', '.').toDoubleOrNull() ?: -1.0
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Отмена") }
                Button(onClick = { onSave(FuelEntry(tripId = tripId, liters = l, pricePerLiter = pr,
                    odometerKm = odo, station = station, fuelType = fuelType)) },
                    enabled = l > 0 && pr >= 0 && odo >= 0) { Text("Сохранить") }
            }
        }
    )
}

@Composable
private fun FuelReport(entries: List<FuelEntry>, trips: List<Trip>, onAdd: () -> Unit, modifier: Modifier = Modifier) {
    val totalLiters = entries.sumOf { it.liters }
    val totalCost = entries.sumOf { it.totalCost }
    val ordered = entries.sortedBy { it.odometerKm }
    val distance = if (ordered.size >= 2) (ordered.last().odometerKm - ordered.first().odometerKm).coerceAtLeast(0.0) else 0.0
    val consumption = if (distance > 0) totalLiters * 100.0 / distance else 0.0
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("Учёт топлива", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                SmallFloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, contentDescription = "Добавить заправку") }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Расходы на топливо", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("%.2f ₽".format(totalCost), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text("%.1f л · %.2f ₽/л в среднем".format(totalLiters, if (totalLiters > 0) totalCost / totalLiters else 0.0))
                    Spacer(Modifier.height(5.dp))
                    Text(if (distance > 0) "Средний расход: %.1f л/100 км".format(consumption)
                        else "Для расчёта нужны две заправки с разным пробегом одометра",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item { Text("История заправок", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
        if (entries.isEmpty()) item { Text("Пока нет заправок. Добавьте первую запись.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(entries, key = { it.id }) { entry ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(date(entry.filledAt), fontWeight = FontWeight.SemiBold)
                        Text(entry.station.ifBlank { "АЗС не указана" } + " · " + entry.fuelType)
                        Text("%.1f л × %.2f ₽ · одометр %.0f км".format(entry.liters, entry.pricePerLiter, entry.odometerKm),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        trips.firstOrNull { it.id == entry.tripId }?.let {
                            Text(it.fromPlace + " → " + it.toPlace, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Text("%.2f ₽".format(entry.totalCost), fontWeight = FontWeight.SemiBold)
                }
            }
        }
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
