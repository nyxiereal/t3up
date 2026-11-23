package meow.nyx.t3up

import android.content.Context
import android.content.pm.PackageManager
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object Updater {
    const val TARGET = "com.t3tools.t3code"
    private const val API = "https://api.github.com/repos/pingdotgg/t3code/releases?per_page=15"

    // The APK name carries the real Android versionName and versionCode; the git tag does not match them.
    private val APK = Regex("""^t3code-android-[a-z]+-(.+)-(\d+)\.apk$""")

    data class Release(
        val tag: String,
        val versionName: String,
        val versionCode: Long,
        val url: String,
        val size: Long,
        val sha256: String?,
    )

    data class Installed(val versionName: String, val versionCode: Long)

    fun installed(ctx: Context): Installed? = try {
        val p = ctx.packageManager.getPackageInfo(TARGET, 0)
        Installed(p.versionName ?: "?", p.longVersionCode)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    /** Newest APK across recent releases (nightlies included), picked by versionCode. */
    fun latest(ctx: Context): Release? {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        // Optional PAT lifts the 60 requests/hour anonymous limit; only sent to api.github.com.
        UpdateJob.prefs(ctx).getString("token", null)?.takeIf { it.isNotBlank() }?.let {
            conn.setRequestProperty("Authorization", "Bearer ${it.trim()}")
        }
        conn.setRequestProperty("User-Agent", "t3up")
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        try {
            if (conn.responseCode != 200) error("GitHub API returned ${conn.responseCode}")
            return parse(conn.inputStream.bufferedReader().readText())
        } finally {
            conn.disconnect()
        }
    }

    fun parse(json: String): Release? {
        val releases = JSONArray(json)
        var best: Release? = null
        for (i in 0 until releases.length()) {
            val r = releases.getJSONObject(i)
            if (r.optBoolean("draft")) continue
            val assets = r.getJSONArray("assets")
            for (j in 0 until assets.length()) {
                val a = assets.getJSONObject(j)
                val m = APK.matchEntire(a.getString("name")) ?: continue
                val code = m.groupValues[2].toLong()
                // Releases come newest first, so ">" keeps the newest tag when nightlies share one APK.
                if (best == null || code > best.versionCode) {
                    best = Release(
                        tag = r.getString("tag_name"),
                        versionName = m.groupValues[1],
                        versionCode = code,
                        url = a.getString("browser_download_url"),
                        size = a.getLong("size"),
                        sha256 = a.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 },
                    )
                }
            }
        }
        return best
    }

    fun download(ctx: Context, r: Release, progress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "apk").apply { mkdirs() }
        val out = File(dir, "t3code-${r.versionCode}.apk")
        if (out.length() == r.size) return out // verified on an earlier run
        dir.listFiles()?.forEach { it.delete() }

        val part = File(dir, out.name + ".part")
        val md = MessageDigest.getInstance("SHA-256")
        val conn = URL(r.url).openConnection() as HttpURLConnection
        conn.setRequestProperty("User-Agent", "t3up")
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        try {
            if (conn.responseCode != 200) error("Download returned ${conn.responseCode}")
            conn.inputStream.use { input ->
                part.outputStream().use { output ->
                    val buf = ByteArray(1 shl 16)
                    var done = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        md.update(buf, 0, n)
                        done += n
                        val pct = (done * 100 / r.size).toInt()
                        if (pct != lastPct) { lastPct = pct; progress(pct) }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        val hash = md.digest().joinToString("") { "%02x".format(it) }
        if (part.length() != r.size || (r.sha256 != null && hash != r.sha256)) {
            part.delete()
            error("Checksum mismatch, download discarded")
        }
        part.renameTo(out)
        return out
    }

    /** Streams the APK into `pm install` as root, so no file permissions or SELinux labels matter. */
    fun install(apk: File) {
        val p = ProcessBuilder("su", "-c", "pm install -r -S ${apk.length()}")
            .redirectErrorStream(true)
            .start()
        apk.inputStream().use { input -> p.outputStream.use { input.copyTo(it) } }
        val output = p.inputStream.bufferedReader().readText().trim()
        if (p.waitFor() != 0 || !output.contains("Success")) {
            error("pm install failed: ${output.ifEmpty { "no root access?" }}")
        }
        apk.delete()
    }

    private val lock = Any()

    /** Check, download and install if newer. Returns a one-line status. */
    fun update(ctx: Context, progress: (Int) -> Unit = {}): String = synchronized(lock) {
        val latest = latest(ctx) ?: return "No Android APK found in recent releases"
        val have = installed(ctx)
        if (have != null && have.versionCode >= latest.versionCode) {
            return "Up to date (${have.versionName} / ${have.versionCode})"
        }
        install(download(ctx, latest, progress))
        "Installed ${latest.versionName} / ${latest.versionCode} from ${latest.tag}"
    }
}
