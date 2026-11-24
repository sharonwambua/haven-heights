package com.havenheights.app.data.local

import androidx.room.*
import com.google.gson.reflect.TypeToken
import com.havenheights.app.data.HavenJson
import kotlinx.coroutines.flow.Flow

/** Last good server response per screen, so the app opens with data even without signal. */
@Entity(tableName = "cache")
data class CacheEntry(@PrimaryKey val key: String, val json: String, val updatedAt: Long)

/** Something the user did offline that still has to reach the server. */
@Entity(tableName = "pending")
data class PendingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: String,          // KIND_READING | KIND_REQUEST
    val payload: String,       // JSON of PendingReading / NewRequest
    val photoPath: String?,
    val owner: String,
    val createdAt: Long,
    val error: String? = null, // set when the server refused it; waits for the user
    val errorCode: Int = 0,
)

@Dao
interface LocalDao {
    @Query("SELECT * FROM cache WHERE `key` = :key") suspend fun cached(key: String): CacheEntry?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putCache(entry: CacheEntry)
    @Query("DELETE FROM cache") suspend fun clearCache()

    @Insert suspend fun addPending(item: PendingItem): Long
    @Update suspend fun updatePending(item: PendingItem)
    @Delete suspend fun deletePending(item: PendingItem)
    @Query("SELECT * FROM pending ORDER BY id") fun pendingFlow(): Flow<List<PendingItem>>
    @Query("SELECT * FROM pending ORDER BY id") suspend fun pendingList(): List<PendingItem>
    @Query("UPDATE pending SET error = :error, errorCode = :code WHERE id = :id")
    suspend fun markError(id: Long, error: String, code: Int)
}

@Database(entities = [CacheEntry::class, PendingItem::class], version = 1, exportSchema = false)
abstract class LocalDb : RoomDatabase() {
    abstract fun dao(): LocalDao
}

class LocalCache(val dao: LocalDao) {
    suspend inline fun <reified T> get(key: String): T? {
        val json = dao.cached(key)?.json ?: return null
        return try {
            HavenJson.gson.fromJson<T>(json, object : TypeToken<T>() {}.type)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun put(key: String, value: Any) =
        dao.putCache(CacheEntry(key, HavenJson.gson.toJson(value), System.currentTimeMillis()))
}
