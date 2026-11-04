package ru.moireisy.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import androidx.room.Index

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromPlace: String,
    val toPlace: String,
    val cargo: String,
    val waybill: String,
    val sender: String,
    val receiver: String,
    val vehicle: String = "",
    val startAt: Long = System.currentTimeMillis(),
    val endAt: Long? = null,
    val totalKm: Double = 0.0,
    val status: String = "В пути",
    val notes: String = "",
    val ratePerKm: Double = 7.0
)

@Entity(tableName = "daily_logs")
data class DailyLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val dayStart: Long,
    val km: Double,
    val hours: Double,
    val note: String = "",
    val mileageType: String = "LOADED",
    val ratePerKm: Double = 7.0
)

@Entity(tableName = "trip_documents")
data class TripDocument(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val uri: String,
    val title: String,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "track_points")
data class TrackPoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val latitude: Double,
    val longitude: Double,
    val recordedAt: Long,
    @ColumnInfo(defaultValue = "'LOADED'") val mileageType: String = "LOADED",
    @ColumnInfo(defaultValue = "7.0") val ratePerKm: Double = 7.0
)


@Entity(tableName = "trip_pauses", indices = [Index(value = ["tripId"])])
data class TripPause(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long,
    val startedAt: Long,
    val endedAt: Long? = null,
    val reason: String
)


@Entity(tableName = "fuel_entries", indices = [Index(value = ["tripId"]), Index(value = ["filledAt"])])
data class FuelEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long? = null,
    val filledAt: Long = System.currentTimeMillis(),
    val liters: Double,
    val pricePerLiter: Double,
    val odometerKm: Double,
    val station: String = "",
    val fuelType: String = "Дизель",
    val receiptUri: String? = null,
    val note: String = ""
) { val totalCost: Double get() = liters * pricePerLiter }


@Entity(tableName = "expense_entries", indices = [Index(value = ["spentAt"]), Index(value = ["tripId"])])
data class ExpenseEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val tripId: Long? = null,
    val spentAt: Long = System.currentTimeMillis(),
    val category: String,
    val amount: Double,
    val description: String = "",
    val receiptUri: String? = null
)
