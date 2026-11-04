package ru.moireisy.app.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.moireisy.app.data.AppDatabase
import ru.moireisy.app.data.DailyLog
import ru.moireisy.app.data.TrackPoint
import ru.moireisy.app.data.TripPause
import java.util.Calendar
import java.util.Locale

/** Sticky foreground GPS recorder. A service restart deliberately starts a fresh GPS segment
 * so the downtime between the last fix and recovery is never counted as distance or earnings. */
class TrackingService : Service(), LocationListener {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pauseDbMutex = Mutex()
    private val pointMutex = Mutex()
    private lateinit var locationManager: LocationManager
    private var tripId: Long = -1
    private var previous: Location? = null
    private var distanceKm = 0.0
    private var mileageType: String = TYPE_LOADED
    private var ratePerKm: Double = 7.0
    private var updatesRequested = false
    private var paused = false
    private var pauseReason = ""

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val incomingTripId = intent?.getLongExtra(EXTRA_TRIP_ID, -1) ?: -1
        if (incomingTripId > 0) tripId = incomingTripId
        if (tripId <= 0) tripId = prefs.getLong(KEY_ACTIVE_TRIP, -1)
        if (tripId <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Always restore the durable state first. START_STICKY may recreate the service with a null intent.
        paused = prefs.getBoolean(KEY_PAUSED, false)
        mileageType = prefs.getString(KEY_MILEAGE_TYPE, TYPE_LOADED) ?: TYPE_LOADED
        ratePerKm = prefs.getFloat(KEY_RATE, 7f).toDouble().coerceAtLeast(0.0)
        pauseReason = prefs.getString(KEY_PAUSE_REASON, "") ?: ""
        previous = null // Never bridge GPS gaps across process death/reboot.

        when (intent?.action) {
            ACTION_STOP -> {
                startAsForeground()
                stopTracking()
                return START_NOT_STICKY
            }
            ACTION_PAUSE -> {
                pauseReason = intent.getStringExtra(EXTRA_PAUSE_REASON) ?: PAUSE_OTHER
                persistState()
                pauseTracking()
            }
            ACTION_RESUME -> {
                mileageType = intent.getStringExtra(EXTRA_MILEAGE_TYPE) ?: mileageType
                ratePerKm = intent.getDoubleExtra(EXTRA_RATE_PER_KM, ratePerKm).coerceAtLeast(0.0)
                resumeTracking()
            }
            else -> {
                // A fresh user start may supply a type/rate. A null sticky restart uses saved values.
                if (intent != null) {
                    mileageType = intent.getStringExtra(EXTRA_MILEAGE_TYPE) ?: mileageType
                    ratePerKm = intent.getDoubleExtra(EXTRA_RATE_PER_KM, ratePerKm).coerceAtLeast(0.0)
                }
                persistState()
            }
        }

        startAsForeground()
        if (!paused) requestUpdates()
        else updateNotification()
        return START_STICKY
    }

    private fun startAsForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification(statusText()), type)
    }

    private fun persistState() {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .putLong(KEY_ACTIVE_TRIP, tripId)
            .putBoolean(KEY_PAUSED, paused)
            .putString(KEY_PAUSE_REASON, pauseReason)
            .putString(KEY_MILEAGE_TYPE, mileageType)
            .putFloat(KEY_RATE, ratePerKm.toFloat())
            .commit()
    }

    private fun requestUpdates() {
        if (updatesRequested) return
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            updateNotification("Нет разрешения на геолокацию")
            stopSelf()
            return
        }
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            updateNotification("GPS выключен · включите геолокацию")
            return
        }
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10_000L, 20f, this)
            try { locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30_000L, 50f, this) } catch (_: Exception) { }
            updatesRequested = true
            updateNotification("GPS активен · ожидаю точку")
        } catch (_: SecurityException) {
            updateNotification("Нет разрешения на геолокацию")
            stopSelf()
        } catch (_: Exception) {
            updateNotification("Не удалось запустить GPS · проверьте настройки")
        }
    }

    private fun pauseTracking() {
        if (paused) return
        paused = true
        previous = null
        if (updatesRequested) {
            try { locationManager.removeUpdates(this) } catch (_: Exception) { }
            updatesRequested = false
        }
        val now = System.currentTimeMillis()
        val currentTripId = tripId
        scope.launch {
            pauseDbMutex.withLock {
                AppDatabase.get(applicationContext).tripDao().insertPause(
                    TripPause(tripId = currentTripId, startedAt = now, reason = pauseReason)
                )
            }
        }
        persistState()
        updateNotification()
    }

    private fun resumeTracking() {
        val now = System.currentTimeMillis()
        val currentTripId = tripId
        scope.launch {
            pauseDbMutex.withLock {
                AppDatabase.get(applicationContext).tripDao().closeOpenPauses(currentTripId, now)
            }
        }
        paused = false
        pauseReason = ""
        previous = null
        persistState()
        startAsForeground()
        requestUpdates()
    }

    private fun stopTracking() {
        val currentTripId = tripId
        scope.launch {
            pauseDbMutex.withLock {
                AppDatabase.get(applicationContext).tripDao().closeOpenPauses(currentTripId, System.currentTimeMillis())
            }
        }
        if (updatesRequested) {
            try { locationManager.removeUpdates(this) } catch (_: Exception) { }
            updatesRequested = false
        }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
            .remove(KEY_ACTIVE_TRIP).putBoolean(KEY_PAUSED, false).remove(KEY_PAUSE_REASON).commit()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else { @Suppress("DEPRECATION") stopForeground(true) }
        stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        if (paused) return
        if (location.hasAccuracy() && location.accuracy > 50f) return
        val old = previous
        if (old != null && location.time - old.time < 5_000L) return
        val segmentKm = if (old != null) old.distanceTo(location) / 1000.0 else 0.0
        val acceptedKm = segmentKm.takeIf { it in 0.01..5.0 } ?: 0.0
        previous = location
        distanceKm += acceptedKm

        val point = TrackPoint(
            tripId = tripId, latitude = location.latitude, longitude = location.longitude,
            recordedAt = location.time, mileageType = mileageType, ratePerKm = ratePerKm
        )
        val currentTripId = tripId
        val currentType = mileageType
        val currentRate = ratePerKm
        scope.launch {
            pointMutex.withLock {
                val dao = AppDatabase.get(applicationContext).tripDao()
                dao.insertTrackPoint(point)
                if (acceptedKm > 0.0) {
                    val dayStart = Calendar.getInstance().apply {
                        timeInMillis = location.time
                        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    dao.insertDailyLog(DailyLog(
                        tripId = currentTripId, dayStart = dayStart, km = acceptedKm, hours = 0.0,
                        note = "GPS · автоматический учёт · ${if (currentType == TYPE_EMPTY) "без груза" else "с грузом"}",
                        mileageType = currentType, ratePerKm = currentRate
                    ))
                    val trip = dao.getTrip(currentTripId)
                    if (trip != null) dao.updateTrip(trip.copy(totalKm = dao.dailyKmTotal(currentTripId)))
                }
            }
        }
        updateNotification()
    }

    private fun statusText(): String {
        if (paused) return "Пауза · ${pauseReason.ifBlank { PAUSE_OTHER }}"
        val type = if (mileageType == TYPE_EMPTY) "без груза" else "с грузом"
        return "GPS · $type · ${"%.1f".format(Locale.getDefault(), distanceKm)} км"
    }

    private fun updateNotification(overrideText: String? = null) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, notification(overrideText ?: statusText()))
    }

    override fun onDestroy() {
        if (updatesRequested) try { locationManager.removeUpdates(this) } catch (_: Exception) { }
        updatesRequested = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onProviderEnabled(provider: String) { if (!paused) requestUpdates() }
    override fun onProviderDisabled(provider: String) { updateNotification("GPS выключен · запись временно приостановлена") }
    @Deprecated("Deprecated in Android")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Запись маршрута", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun notification(text: String): Notification = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Мои Рейсы")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.ic_menu_mylocation)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"
        const val EXTRA_MILEAGE_TYPE = "mileage_type"
        const val EXTRA_RATE_PER_KM = "rate_per_km"
        const val EXTRA_PAUSE_REASON = "pause_reason"
        const val ACTION_PAUSE = "ru.moireisy.app.action.PAUSE"
        const val ACTION_RESUME = "ru.moireisy.app.action.RESUME"
        const val ACTION_STOP = "ru.moireisy.app.action.STOP"
        const val TYPE_LOADED = "LOADED"
        const val TYPE_EMPTY = "EMPTY"
        const val PAUSE_LOADING = "Погрузка"
        const val PAUSE_UNLOADING = "Разгрузка"
        const val PAUSE_REST = "Отдых"
        const val PAUSE_WAITING = "Ожидание"
        const val PAUSE_OTHER = "Другая остановка"
        const val PREFS = "tracking_state"
        const val KEY_ACTIVE_TRIP = "active_trip_id"
        const val KEY_PAUSED = "paused"
        const val KEY_MILEAGE_TYPE = "mileage_type"
        const val KEY_RATE = "rate"
        const val KEY_PAUSE_REASON = "pause_reason"
        private const val CHANNEL_ID = "moi_reisy_tracking"
        private const val NOTIFICATION_ID = 501
    }
}
