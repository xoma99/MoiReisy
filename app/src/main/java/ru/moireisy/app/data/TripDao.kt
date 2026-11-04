package ru.moireisy.app.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert suspend fun insertExpense(entry: ExpenseEntry): Long
    @Update suspend fun updateExpense(entry: ExpenseEntry)
    @Delete suspend fun deleteExpense(entry: ExpenseEntry)
    @Query("SELECT * FROM expense_entries ORDER BY spentAt DESC") fun observeExpenses(): Flow<List<ExpenseEntry>>
    @Query("SELECT * FROM expense_entries") suspend fun backupExpenses(): List<ExpenseEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreExpenses(items: List<ExpenseEntry>)
    @Query("DELETE FROM expense_entries") suspend fun clearExpenses()

    @Insert suspend fun insertFuelEntry(entry: FuelEntry): Long
    @Update suspend fun updateFuelEntry(entry: FuelEntry)
    @Delete suspend fun deleteFuelEntry(entry: FuelEntry)
    @Query("SELECT * FROM fuel_entries ORDER BY filledAt DESC") fun observeFuelEntries(): Flow<List<FuelEntry>>
    @Query("SELECT * FROM fuel_entries") suspend fun backupFuelEntries(): List<FuelEntry>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreFuelEntries(items: List<FuelEntry>)
    @Query("DELETE FROM fuel_entries") suspend fun clearFuelEntries()

    @Query("SELECT * FROM trips ORDER BY startAt DESC") fun observeTrips(): Flow<List<Trip>>
    @Query("SELECT * FROM trips WHERE id = :id LIMIT 1") suspend fun getTrip(id: Long): Trip?
    @Insert suspend fun insertTrip(trip: Trip): Long
    @Update suspend fun updateTrip(trip: Trip)
    @Delete suspend fun deleteTrip(trip: Trip)
    @Insert suspend fun insertDailyLog(log: DailyLog)
    @Query("SELECT * FROM daily_logs WHERE tripId = :tripId ORDER BY dayStart") fun observeDailyLogs(tripId: Long): Flow<List<DailyLog>>
    @Query("SELECT * FROM daily_logs ORDER BY dayStart DESC") fun observeAllDailyLogs(): Flow<List<DailyLog>>
    @Query("SELECT COALESCE(SUM(km), 0) FROM daily_logs WHERE tripId = :tripId") suspend fun dailyKmTotal(tripId: Long): Double
    @Insert suspend fun insertDocument(document: TripDocument)
    @Query("SELECT * FROM trip_documents WHERE tripId = :tripId ORDER BY addedAt DESC") fun observeDocuments(tripId: Long): Flow<List<TripDocument>>
    @Query("DELETE FROM trip_documents WHERE id = :id") suspend fun deleteDocument(id: Long)
    @Insert suspend fun insertTrackPoint(point: TrackPoint)
    @Insert suspend fun insertPause(pause: TripPause): Long
    @Query("UPDATE trip_pauses SET endedAt = :endedAt WHERE tripId = :tripId AND endedAt IS NULL") suspend fun closeOpenPauses(tripId: Long, endedAt: Long)
    @Query("SELECT * FROM trip_pauses WHERE tripId = :tripId ORDER BY startedAt DESC") fun observePauses(tripId: Long): Flow<List<TripPause>>
    @Query("SELECT * FROM trip_pauses WHERE tripId = :tripId AND endedAt IS NULL ORDER BY startedAt DESC LIMIT 1") suspend fun getOpenPause(tripId: Long): TripPause?
    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY recordedAt DESC LIMIT 1") suspend fun getLastTrackPoint(tripId: Long): TrackPoint?
    @Query("SELECT * FROM track_points WHERE tripId = :tripId ORDER BY recordedAt") fun observeTrackPoints(tripId: Long): Flow<List<TrackPoint>>

    // Backup/restore API. IDs are preserved so trips, GPS points, logs and pauses remain linked.
    @Query("SELECT * FROM trips") suspend fun backupTrips(): List<Trip>
    @Query("SELECT * FROM daily_logs") suspend fun backupDailyLogs(): List<DailyLog>
    @Query("SELECT * FROM trip_documents") suspend fun backupDocuments(): List<TripDocument>
    @Query("SELECT * FROM track_points") suspend fun backupTrackPoints(): List<TrackPoint>
    @Query("SELECT * FROM trip_pauses") suspend fun backupPauses(): List<TripPause>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreTrips(items: List<Trip>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreDailyLogs(items: List<DailyLog>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreDocuments(items: List<TripDocument>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restoreTrackPoints(items: List<TrackPoint>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun restorePauses(items: List<TripPause>)
    @Query("DELETE FROM trip_pauses") suspend fun clearPauses()
    @Query("DELETE FROM track_points") suspend fun clearTrackPoints()
    @Query("DELETE FROM trip_documents") suspend fun clearDocuments()
    @Query("DELETE FROM daily_logs") suspend fun clearDailyLogs()
    @Query("DELETE FROM trips") suspend fun clearTrips()
}
