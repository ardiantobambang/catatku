package id.bambang.catatku

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

const val F_PINNED = "📌"
const val F_LINK = "🔗"

class NotesVm(app: Application) : AndroidViewModel(app) {
    private val dao = AppDb.get(app).dao()
    val prefs = Prefs(app)

    val query = MutableStateFlow("")
    /** tag aktif, atau F_PINNED / F_LINK */
    val filter = MutableStateFlow<String?>(null)
    val syncing = MutableStateFlow(false)
    val toast = MutableSharedFlow<String>(extraBufferCapacity = 4)

    private val all: StateFlow<List<Note>> =
        dao.all().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val notes: StateFlow<List<Note>> = combine(all, query, filter) { list, q, f ->
        list.filter { n ->
            val okF = when (f) {
                null -> true
                F_PINNED -> n.pinned
                F_LINK -> hasLink(n.text)
                else -> f in n.tagList()
            }
            okF && (q.isBlank() || n.text.contains(q, ignoreCase = true))
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** tag urut paling sering dipakai */
    val tags: StateFlow<List<String>> = all.map { l ->
        l.flatMap { it.tagList() }.groupingBy { it }.eachCount()
            .entries.sortedByDescending { it.value }.map { it.key }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun save(raw: String, editing: Note?) {
        var text = raw.trim()
        if (text.isEmpty()) return
        // filter tag aktif → otomatis ikut tag itu
        val f = filter.value
        if (editing == null && f != null && f != F_PINNED && f != F_LINK && f !in parseTags(text).split(' ')) {
            text = "$text #$f"
        }
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            dao.upsert(
                editing?.copy(text = text, tags = parseTags(text), updatedAt = now, dirty = true)
                    ?: Note(text = text, tags = parseTags(text), createdAt = now, updatedAt = now)
            )
            sync(silent = true)
        }
    }

    fun togglePin(n: Note) = update(n.copy(pinned = !n.pinned))
    fun delete(n: Note) = update(n.copy(deleted = true))
    fun restore(n: Note) = update(n.copy(deleted = false))

    private fun update(n: Note) = viewModelScope.launch {
        dao.upsert(n.copy(updatedAt = System.currentTimeMillis(), dirty = true))
        sync(silent = true)
    }

    fun sync(silent: Boolean = false) {
        if (!prefs.configured) {
            if (!silent) toast.tryEmit("Atur server dulu (⚙)")
            return
        }
        viewModelScope.launch {
            syncing.value = true
            val r = Sync.run(getApplication())
            syncing.value = false
            r.onSuccess { if (!silent) toast.tryEmit(if (it > 0) "Sync ✓ $it masuk" else "Sync ✓") }
            r.onFailure { if (!silent) toast.tryEmit("Sync gagal: ${it.message}") }
        }
    }

    suspend fun exportJson(): String {
        val arr = JSONArray()
        dao.allOnce().forEach {
            arr.put(
                JSONObject().put("text", it.text).put("tags", it.tags)
                    .put("pinned", it.pinned).put("created_at", it.createdAt)
            )
        }
        return arr.toString(2)
    }
}
