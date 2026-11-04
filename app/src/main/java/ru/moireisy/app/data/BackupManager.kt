package ru.moireisy.app.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Full offline backup. The archive contains Room data, app preferences and copies of attached documents. */
object BackupManager {
    private const val FORMAT = 1
    private const val JSON_FILE = "backup.json"

    suspend fun export(context: Context, uri: Uri) {
        context.contentResolver.openOutputStream(uri)?.use { raw ->
            writeArchive(context, raw)
        } ?: error("Не удалось создать файл резервной копии")
    }

    /** Creates an automatic local backup in app-private storage. */
    suspend fun exportToFile(context: Context, destination: File) {
        destination.parentFile?.mkdirs()
        val temp = File(destination.parentFile, destination.name + ".tmp")
        try {
            temp.outputStream().buffered().use { writeArchive(context, it) }
            if (destination.exists() && !destination.delete()) error("Не удалось заменить старую копию")
            if (!temp.renameTo(destination)) error("Не удалось сохранить резервную копию")
        } finally { if (temp.exists()) temp.delete() }
    }

    private suspend fun writeArchive(context: Context, output: java.io.OutputStream) {
        val db = AppDatabase.get(context)
        val dao = db.tripDao()
        val json = JSONObject().apply {
            put("format", FORMAT)
            put("createdAt", System.currentTimeMillis())
            put("trips", JSONArray().apply { dao.backupTrips().forEach { put(it.toJson()) } })
            put("dailyLogs", JSONArray().apply { dao.backupDailyLogs().forEach { put(it.toJson()) } })
            put("documents", JSONArray().apply { dao.backupDocuments().forEach { put(it.toJson()) } })
            put("trackPoints", JSONArray().apply { dao.backupTrackPoints().forEach { put(it.toJson()) } })
            put("pauses", JSONArray().apply { dao.backupPauses().forEach { put(it.toJson()) } })
            put("fuelEntries", JSONArray().apply { dao.backupFuelEntries().forEach { put(it.toJson()) } })
            put("expenses", JSONArray().apply { dao.backupExpenses().forEach { put(it.toJson()) } })
            val prefs = JSONObject()
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).all.forEach { (k, v) ->
                when (v) { is String -> prefs.put(k, v); is Boolean -> prefs.put(k, v); is Int -> prefs.put(k, v); is Long -> prefs.put(k, v); is Float -> prefs.put(k, v.toDouble()) }
            }
            put("settings", prefs)
            val tracking = JSONObject()
            context.getSharedPreferences("tracking_state", Context.MODE_PRIVATE).all.forEach { (k, v) ->
                when (v) { is String -> tracking.put(k, v); is Boolean -> tracking.put(k, v); is Int -> tracking.put(k, v); is Long -> tracking.put(k, v); is Float -> tracking.put(k, v.toDouble()) }
            }
            put("trackingState", tracking)
        }
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
                zip.putNextEntry(ZipEntry(JSON_FILE)); zip.write(json.toString().toByteArray(Charsets.UTF_8)); zip.closeEntry()
                dao.backupDocuments().forEach { doc ->
                    try {
                        val source = Uri.parse(doc.uri)
                        context.contentResolver.openInputStream(source)?.use { input ->
                            zip.putNextEntry(ZipEntry("documents/${doc.id}.bin"))
                            input.copyTo(zip)
                            zip.closeEntry()
                        }
                    } catch (_: Exception) { /* URI may have been revoked; metadata remains in the backup. */ }
                }
        }
    }

    suspend fun restore(context: Context, uri: Uri) {
        val entries = linkedMapOf<String, ByteArray>()
        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && (entry.name == JSON_FILE || entry.name.startsWith("documents/"))) {
                        entries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                }
            }
        } ?: error("Не удалось прочитать резервную копию")
        val root = JSONObject(String(entries[JSON_FILE] ?: error("В архиве нет backup.json"), Charsets.UTF_8))
        require(root.optInt("format") == FORMAT) { "Неподдерживаемый формат резервной копии" }
        val trips = root.array("trips", ::parseTrip)
        val logs = root.array("dailyLogs", ::parseDailyLog)
        val oldDocs = root.array("documents", ::parseDocument)
        val points = root.array("trackPoints", ::parseTrackPoint)
        val pauses = root.array("pauses", ::parsePause)
        val fuelEntries = root.array("fuelEntries", ::parseFuelEntry)
        val expenses = root.array("expenses", ::parseExpense)

        // Restore document bytes to private app storage and create shareable FileProvider URIs.
        val docsDir = File(context.filesDir, "restored_documents").apply { mkdirs() }
        val restoredDocs = oldDocs.map { doc ->
            val bytes = entries["documents/${doc.id}.bin"]
            if (bytes == null) doc else {
                val file = File(docsDir, "${doc.id}_${safeName(doc.title)}.bin")
                file.writeBytes(bytes)
                val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                doc.copy(uri = contentUri.toString())
            }
        }
        val db = AppDatabase.get(context)
        db.withTransaction {
            val dao = db.tripDao()
            dao.clearExpenses(); dao.clearFuelEntries(); dao.clearPauses(); dao.clearTrackPoints(); dao.clearDocuments(); dao.clearDailyLogs(); dao.clearTrips()
            dao.restoreTrips(trips); dao.restoreDailyLogs(logs); dao.restoreDocuments(restoredDocs)
            dao.restoreTrackPoints(points); dao.restorePauses(pauses); dao.restoreFuelEntries(fuelEntries); dao.restoreExpenses(expenses)
        }
        restorePrefs(context, "settings", root.optJSONObject("settings"))
        restorePrefs(context, "tracking_state", root.optJSONObject("trackingState"))
    }

    private fun restorePrefs(context: Context, name: String, json: JSONObject?) {
        if (json == null) return
        val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
        json.keys().forEach { key ->
            when (val value = json.get(key)) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Number -> editor.putFloat(key, value.toFloat())
                is String -> editor.putString(key, value)
            }
        }
        editor.commit()
    }

    private fun safeName(value: String) = value.replace(Regex("[^A-Za-z0-9А-Яа-я_-]"), "_").take(40)
    private fun <T> JSONObject.array(key: String, convert: (JSONObject) -> T): List<T> {
        val a = optJSONArray(key) ?: JSONArray(); return (0 until a.length()).map { convert(a.getJSONObject(it)) }
    }
    private fun Trip.toJson() = JSONObject().apply { put("id",id); put("fromPlace",fromPlace); put("toPlace",toPlace); put("cargo",cargo); put("waybill",waybill); put("sender",sender); put("receiver",receiver); put("vehicle",vehicle); put("startAt",startAt); put("endAt",endAt ?: JSONObject.NULL); put("totalKm",totalKm); put("status",status); put("notes",notes); put("ratePerKm",ratePerKm) }
    private fun DailyLog.toJson() = JSONObject().apply { put("id",id); put("tripId",tripId); put("dayStart",dayStart); put("km",km); put("hours",hours); put("note",note); put("mileageType",mileageType); put("ratePerKm",ratePerKm) }
    private fun TripDocument.toJson() = JSONObject().apply { put("id",id); put("tripId",tripId); put("uri",uri); put("title",title); put("addedAt",addedAt) }
    private fun TrackPoint.toJson() = JSONObject().apply { put("id",id); put("tripId",tripId); put("latitude",latitude); put("longitude",longitude); put("recordedAt",recordedAt); put("mileageType",mileageType); put("ratePerKm",ratePerKm) }
    private fun FuelEntry.toJson() = JSONObject().apply { put("id",id); put("tripId",tripId ?: JSONObject.NULL); put("filledAt",filledAt); put("liters",liters); put("pricePerLiter",pricePerLiter); put("odometerKm",odometerKm); put("station",station); put("fuelType",fuelType); put("receiptUri",receiptUri ?: JSONObject.NULL); put("note",note) }
    private fun ExpenseEntry.toJson() = JSONObject().apply { put("id",id); put("tripId",tripId ?: JSONObject.NULL); put("spentAt",spentAt); put("category",category); put("amount",amount); put("description",description); put("receiptUri",receiptUri ?: JSONObject.NULL) }
    private fun TripPause.toJson() = JSONObject().apply { put("id",id); put("tripId",tripId); put("startedAt",startedAt); put("endedAt",endedAt ?: JSONObject.NULL); put("reason",reason) }
    private fun JSONObject.nullableLong(key: String) = if (isNull(key)) null else getLong(key)
    private fun parseTrip(j: JSONObject) = Trip(j.getLong("id"),j.optString("fromPlace"),j.optString("toPlace"),j.optString("cargo"),j.optString("waybill"),j.optString("sender"),j.optString("receiver"),j.optString("vehicle"),j.getLong("startAt"),j.nullableLong("endAt"),j.optDouble("totalKm"),j.optString("status"),j.optString("notes"),j.optDouble("ratePerKm",7.0))
    private fun parseDailyLog(j: JSONObject) = DailyLog(j.getLong("id"),j.getLong("tripId"),j.getLong("dayStart"),j.optDouble("km"),j.optDouble("hours"),j.optString("note"),j.optString("mileageType","LOADED"),j.optDouble("ratePerKm",7.0))
    private fun parseDocument(j: JSONObject) = TripDocument(j.getLong("id"),j.getLong("tripId"),j.optString("uri"),j.optString("title"),j.getLong("addedAt"))
    private fun parseTrackPoint(j: JSONObject) = TrackPoint(j.getLong("id"),j.getLong("tripId"),j.getDouble("latitude"),j.getDouble("longitude"),j.getLong("recordedAt"),j.optString("mileageType","LOADED"),j.optDouble("ratePerKm",7.0))
    private fun parseFuelEntry(j: JSONObject) = FuelEntry(j.getLong("id"), if (j.isNull("tripId")) null else j.getLong("tripId"), j.getLong("filledAt"), j.getDouble("liters"), j.getDouble("pricePerLiter"), j.getDouble("odometerKm"), j.optString("station"), j.optString("fuelType","Дизель"), if (j.isNull("receiptUri")) null else j.optString("receiptUri"), j.optString("note"))
    private fun parseExpense(j: JSONObject) = ExpenseEntry(j.getLong("id"), if (j.isNull("tripId")) null else j.getLong("tripId"), j.getLong("spentAt"), j.optString("category","Прочее"), j.getDouble("amount"), j.optString("description"), if (j.isNull("receiptUri")) null else j.optString("receiptUri"))
    private fun parsePause(j: JSONObject) = TripPause(j.getLong("id"),j.getLong("tripId"),j.getLong("startedAt"),j.nullableLong("endedAt"),j.optString("reason"))
}
