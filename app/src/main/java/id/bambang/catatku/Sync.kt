package id.bambang.catatku

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

object Sync {
    private val lock = Mutex()

    /** @return jumlah catatan masuk dari server */
    suspend fun run(ctx: Context): Result<Int> = withContext(Dispatchers.IO) {
        lock.withLock {
            runCatching {
                val p = Prefs(ctx)
                if (!p.configured) error("Server belum diatur")
                val dao = AppDb.get(ctx).dao()
                val dirty = dao.dirty()

                val body = JSONObject()
                    .put("since", p.lastSync)
                    .put("changes", JSONArray().apply { dirty.forEach { put(it.toJson()) } })

                val resp = post("${p.url}/sync", p.token, body.toString())

                dirty.forEach { dao.markClean(it.id, it.updatedAt) }

                val arr = resp.getJSONArray("notes")
                var incoming = 0
                for (i in 0 until arr.length()) {
                    val r = arr.getJSONObject(i).toNote()
                    val local = dao.get(r.id)
                    if (local == null || !local.dirty || r.updatedAt >= local.updatedAt) {
                        if (local == null || local.updatedAt != r.updatedAt) incoming++
                        dao.upsert(r)
                    }
                }
                p.lastSync = resp.getLong("now")
                incoming
            }
        }
    }

    private fun post(url: String, token: String, body: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = "POST"
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setRequestProperty("Authorization", "Bearer $token")
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            if (code == 401) error("Token salah")
            if (code !in 200..299) error("Server error $code")
            return JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    private fun Note.toJson() = JSONObject()
        .put("id", id).put("text", text).put("tags", tags)
        .put("pinned", if (pinned) 1 else 0)
        .put("created_at", createdAt).put("updated_at", updatedAt)
        .put("deleted", if (deleted) 1 else 0)

    private fun JSONObject.toNote() = Note(
        id = getString("id"),
        text = getString("text"),
        tags = optString("tags", ""),
        pinned = optInt("pinned", 0) == 1,
        createdAt = getLong("created_at"),
        updatedAt = getLong("updated_at"),
        deleted = optInt("deleted", 0) == 1,
        dirty = false,
    )
}

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!Prefs(applicationContext).configured) return Result.success()
        return if (Sync.run(applicationContext).isSuccess) Result.success() else Result.retry()
    }

    companion object {
        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniquePeriodicWork("catatku-sync", ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
