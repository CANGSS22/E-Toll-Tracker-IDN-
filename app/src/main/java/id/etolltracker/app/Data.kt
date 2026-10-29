package id.etolltracker.app

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** type: TOLL | TOPUP | OTHER. source: MANUAL | ESTIMASI | IMPOR | DEMO | TERVERIFIKASI */
@Entity(tableName = "txn", indices = [Index(value = ["dedupeKey"], unique = true)])
data class Txn(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val amount: Long,
    val time: Long,
    val source: String,
    val cardType: String = "",
    val gateIn: String = "",
    val gateOut: String = "",
    val timeIn: Long? = null,
    val balanceBefore: Long? = null,
    val balanceAfter: Long? = null,
    val note: String = "",
    val dedupeKey: String
)

@Entity(tableName = "snapshot")
data class Snapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long, val cardUid: String, val cardType: String,
    val balance: Long?, val status: String
)

@Dao
interface AppDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(t: Txn): Long
    @Update suspend fun update(t: Txn)
    @Delete suspend fun delete(t: Txn)
    @Query("SELECT * FROM txn ORDER BY time DESC") fun txns(): Flow<List<Txn>>
    @Insert suspend fun insertSnap(s: Snapshot)
    @Query("SELECT * FROM snapshot ORDER BY time ASC") fun snaps(): Flow<List<Snapshot>>
    @Query("DELETE FROM txn") suspend fun clearTxn()
    @Query("DELETE FROM snapshot") suspend fun clearSnap()
    @Query("DELETE FROM txn WHERE source='DEMO'") suspend fun clearDemo()
}

@Database(entities = [Txn::class, Snapshot::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): AppDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "etoll.db").build().also { inst = it }
        }
    }
}
