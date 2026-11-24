package ma.euleracademy.bac2pc

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

sealed class LessonSyncResult {
    data class UpToDate(val completedAxes: Int, val totalAxes: Int, val totalLessons: Int) : LessonSyncResult()
    data class Updated(val updatedFilesCount: Int, val completedAxes: Int, val totalAxes: Int) : LessonSyncResult()
    data class OfflineOrFailed(val reason: String) : LessonSyncResult()
}

data class ApkUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String
)

class OtaSyncManager(
    private val context: Context,
    private val assetEngine: LocalAssetEngine
) {
    companion object {
        const val REMOTE_BASE = "https://eulerx0.github.io/euler-academy/"
        const val REMOTE_MANIFEST_URL = "${REMOTE_BASE}manifest.json"
        const val REMOTE_APK_VERSION_URL =
            "https://github.com/eulerx0/euler-academy-android/releases/latest/download/version.json"
    }

    fun checkAndSyncLessons(): LessonSyncResult {
        return try {
            val remoteRaw = fetchText(REMOTE_MANIFEST_URL)
            val remoteJson = JSONObject(remoteRaw)
            val remoteHash = remoteJson.optString("bundleHash", "")
            val remoteFiles = remoteJson.optJSONObject("files")
                ?: return LessonSyncResult.OfflineOrFailed("Invalid manifest")
            val stats = remoteJson.optJSONObject("stats")
            val completedAxes = stats?.optInt("completedAxes", 0) ?: 0
            val totalAxes = stats?.optInt("totalAxes", 0) ?: 0
            val totalLessons = stats?.optInt("totalLessons", 0) ?: 0

            val localRaw = assetEngine.readActiveManifestText()
            val localJson = localRaw?.let { runCatching { JSONObject(it) }.getOrNull() }
            val localHash = localJson?.optString("bundleHash", "") ?: ""
            val localFiles = localJson?.optJSONObject("files")

            if (remoteHash.isNotEmpty() && remoteHash == localHash) {
                return LessonSyncResult.UpToDate(completedAxes, totalAxes, totalLessons)
            }

            val changed = mutableListOf<Pair<String, String>>()
            val keys = remoteFiles.keys()
            while (keys.hasNext()) {
                val rel = keys.next()
                if (rel.contains("..")) continue
                val expectedSha = remoteFiles.optString(rel, "")
                val existingSha = localFiles?.optString(rel, "") ?: ""
                if (expectedSha.isNotEmpty() && expectedSha != existingSha) {
                    changed.add(rel to expectedSha)
                }
            }

            if (changed.isEmpty()) {
                return LessonSyncResult.UpToDate(completedAxes, totalAxes, totalLessons)
            }

            val stagingDir = File(context.filesDir, "ota_staging")
            if (stagingDir.exists()) stagingDir.deleteRecursively()
            stagingDir.mkdirs()

            for ((rel, expectedSha) in changed) {
                val bytes = fetchBytes(REMOTE_BASE + rel)
                val actualSha = sha256Hex(bytes)
                if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                    stagingDir.deleteRecursively()
                    return LessonSyncResult.OfflineOrFailed("Checksum mismatch on $rel")
                }
                val target = File(stagingDir, rel)
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
            }

            // Promote verified files atomically to ota_www
            val otaRoot = assetEngine.otaDir
            val otaCanonical = otaRoot.canonicalPath
            for ((rel, _) in changed) {
                val src = File(stagingDir, rel)
                val dest = File(otaRoot, rel)
                if (!dest.canonicalPath.startsWith(otaCanonical)) continue
                dest.parentFile?.mkdirs()
                src.copyTo(dest, overwrite = true)
            }
            File(otaRoot, "manifest.json").writeText(remoteRaw, Charsets.UTF_8)
            stagingDir.deleteRecursively()

            LessonSyncResult.Updated(changed.size, completedAxes, totalAxes)
        } catch (e: Exception) {
            LessonSyncResult.OfflineOrFailed(e.message ?: "Network error")
        }
    }

    fun checkApkUpdate(currentVersionCode: Int): ApkUpdateInfo? {
        return try {
            val raw = fetchText(REMOTE_APK_VERSION_URL)
            val json = JSONObject(raw)
            val remoteCode = json.optInt("versionCode", 0)
            val remoteName = json.optString("versionName", "")
            val apkUrl = json.optString("apkUrl", "")
            if (remoteCode > currentVersionCode && apkUrl.startsWith("https://")) {
                ApkUpdateInfo(remoteCode, remoteName, apkUrl)
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    fun downloadApk(apkUrl: String, onProgress: (Int) -> Unit): File {
        val updatesDir = File(context.cacheDir, "updates").apply { if (!exists()) mkdirs() }
        val outFile = File(updatesDir, "Euler-Academy.apk")
        val tmpFile = File(updatesDir, "Euler-Academy.apk.tmp")

        val conn = openConnection(apkUrl, readTimeoutMs = 30_000)
        try {
            val total = conn.contentLengthLong.coerceAtLeast(1L)
            conn.inputStream.use { input ->
                FileOutputStream(tmpFile).use { output ->
                    val buf = ByteArray(16 * 1024)
                    var readTotal = 0L
                    var lastPct = -1
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        readTotal += n
                        val pct = ((readTotal * 100) / total).toInt().coerceIn(0, 100)
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct)
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }

        if (outFile.exists()) outFile.delete()
        tmpFile.renameTo(outFile)
        return outFile
    }

    fun launchApkInstaller(apkFile: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val permIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permIntent)
            return false
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(installIntent)
        return true
    }

    private fun openConnection(urlStr: String, readTimeoutMs: Int = 12_000): HttpURLConnection {
        var current = urlStr
        repeat(5) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "EulerAcademy-Android/1.0")
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (loc.isNullOrBlank()) throw IllegalStateException("Empty redirect location")
                current = URL(URL(current), loc).toString()
            } else if (code in 200..299) {
                return conn
            } else {
                conn.disconnect()
                throw IllegalStateException("HTTP $code for $current")
            }
        }
        throw IllegalStateException("Too many redirects for $urlStr")
    }

    private fun fetchText(urlStr: String): String {
        val conn = openConnection(urlStr)
        return try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun fetchBytes(urlStr: String): ByteArray {
        val conn = openConnection(urlStr, readTimeoutMs = 20_000)
        return try {
            conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
