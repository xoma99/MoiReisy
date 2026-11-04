package ru.moireisy.app.tracking

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

/** Restores only a user-started, still-active trip after reboot or app update. */
class TrackingBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val prefs = context.getSharedPreferences(TrackingService.PREFS, Context.MODE_PRIVATE)
        val activeTripId = prefs.getLong(TrackingService.KEY_ACTIVE_TRIP, -1L)
        if (activeTripId <= 0L) return

        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            Log.w(TAG, "Active trip $activeTripId was not restored: location permission is missing")
            return
        }

        // Starting a location FGS from the background after boot requires background location access.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val background = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!background) {
                Log.w(TAG, "Active trip $activeTripId needs the 'Allow all the time' location permission for boot recovery")
                return
            }
        }

        try {
            val serviceIntent = Intent(context, TrackingService::class.java)
                .putExtra(TrackingService.EXTRA_TRIP_ID, activeTripId)
            ContextCompat.startForegroundService(context, serviceIntent)
        } catch (e: Exception) {
            // Android/OEM battery policies can still block automatic background service starts.
            Log.e(TAG, "Could not restore GPS tracking after $action", e)
        }
    }

    companion object { private const val TAG = "MoiReisyBoot" }
}
