package id.bambang.catatku

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val tags: String,            // "ide kerja link"
    val pinned: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val dirty: Boolean = true,   // belum ter-sync
) {
    fun tagList(): List<String> = if (tags.isBlank()) emptyList() else tags.split(' ')
}

private val TAG_RE = Regex("#([\\p{L}\\p{N}_-]+)")
val URL_RE = Regex("(https?://[^\\s]+)|(www\\.[^\\s]+)", RegexOption.IGNORE_CASE)

fun parseTags(text: String): String =
    TAG_RE.findAll(text).map { it.groupValues[1].lowercase() }.distinct().joinToString(" ")

fun hasLink(text: String) = URL_RE.containsMatchIn(text)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY createdAt ASC")
    fun all(): Flow<List<Note>>

    @Query("SELECT * FROM notes WHERE deleted = 0 ORDER BY createdAt ASC")
    suspend fun allOnce(): List<Note>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: String): Note?

    @Query("SELECT * FROM notes WHERE dirty = 1")
    suspend fun dirty(): List<Note>

    @Query("UPDATE notes SET dirty = 0 WHERE id = :id AND updatedAt = :updatedAt")
    suspend fun markClean(id: String, updatedAt: Long)

    @Upsert
    suspend fun upsert(note: Note)
}

@Database(entities = [Note::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): NoteDao

    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(ctx: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "catatku.db")
                .build().also { inst = it }
        }
    }
}

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("catatku", Context.MODE_PRIVATE)
    var url: String
        get() = sp.getString("url", "") ?: ""
        set(v) = sp.edit().putString("url", v.trim().trimEnd('/')).apply()
    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v.trim()).apply()
    var lastSync: Long
        get() = sp.getLong("lastSync", 0)
        set(v) = sp.edit().putLong("lastSync", v).apply()
    val configured get() = url.isNotBlank() && token.isNotBlank()
}
