package ru.moireisy.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Trip::class, DailyLog::class, TripDocument::class, TrackPoint::class, TripPause::class, FuelEntry::class, ExpenseEntry::class],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE trips ADD COLUMN ratePerKm REAL NOT NULL DEFAULT 7.0")
                db.execSQL("ALTER TABLE daily_logs ADD COLUMN mileageType TEXT NOT NULL DEFAULT 'LOADED'")
                db.execSQL("ALTER TABLE daily_logs ADD COLUMN ratePerKm REAL NOT NULL DEFAULT 7.0")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE track_points ADD COLUMN mileageType TEXT NOT NULL DEFAULT 'LOADED'")
                db.execSQL("ALTER TABLE track_points ADD COLUMN ratePerKm REAL NOT NULL DEFAULT 7.0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS trip_pauses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripId INTEGER NOT NULL, startedAt INTEGER NOT NULL, endedAt INTEGER, reason TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_trip_pauses_tripId ON trip_pauses(tripId)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS fuel_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripId INTEGER, filledAt INTEGER NOT NULL, liters REAL NOT NULL, pricePerLiter REAL NOT NULL, odometerKm REAL NOT NULL, station TEXT NOT NULL, fuelType TEXT NOT NULL, receiptUri TEXT, note TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_fuel_entries_tripId ON fuel_entries(tripId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_fuel_entries_filledAt ON fuel_entries(filledAt)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS expense_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, tripId INTEGER, spentAt INTEGER NOT NULL, category TEXT NOT NULL, amount REAL NOT NULL, description TEXT NOT NULL, receiptUri TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_entries_spentAt ON expense_entries(spentAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_entries_tripId ON expense_entries(tripId)")
            }
        }

        @Volatile private var INSTANCE: AppDatabase? = null
        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "moi_reisy.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6).build().also { INSTANCE = it }
            }
    }
}
