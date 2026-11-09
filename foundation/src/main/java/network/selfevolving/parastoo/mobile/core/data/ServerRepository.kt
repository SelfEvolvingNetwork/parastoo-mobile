package network.selfevolving.parastoo.mobile.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "servers")
data class ServerProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val url: String,
    val certSha256: String,
    val createdAtMs: Long,
)

@Dao
interface ServerDao {
    @Query("SELECT * FROM servers ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun byId(id: Long): ServerProfileEntity?

    @Insert
    suspend fun insert(e: ServerProfileEntity): Long

    @Delete
    suspend fun delete(e: ServerProfileEntity)
}

@Database(entities = [ServerProfileEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
}

/** دیتابیس محلی فقط متادیتای سرورها را نگه می‌دارد — رازها در SecretVault */
object Db {
    @Volatile
    private var instance: AppDatabase? = null

    fun init(context: Context): AppDatabase =
        instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "parastoo.db",
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }

    fun get(): AppDatabase = instance ?: throw IllegalStateException("Db.init نشده است")
}
