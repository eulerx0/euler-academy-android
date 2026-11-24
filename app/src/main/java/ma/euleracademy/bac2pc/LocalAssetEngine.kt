package ma.euleracademy.bac2pc

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceResponse
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

class LocalAssetEngine(private val context: Context) {

    companion object {
        const val VIRTUAL_HOST = "app.euleracademy.local"
        const val BASE_URL = "https://$VIRTUAL_HOST/"
    }

    val otaDir: File by lazy {
        File(context.filesDir, "ota_www").apply { if (!exists()) mkdirs() }
    }

    fun shouldIntercept(uri: Uri?): Boolean {
        return uri != null && uri.host.equals(VIRTUAL_HOST, ignoreCase = true)
    }

    fun intercept(uri: Uri): WebResourceResponse {
        val rawPath = (uri.path ?: "").trimStart('/')
        val cleanPath = if (rawPath.isEmpty()) "index.html" else rawPath

        // Guard against directory traversal
        if (cleanPath.contains("..")) {
            return WebResourceResponse("text/plain", "utf-8", 403, "Forbidden", emptyMap(), null)
        }

        val stream: InputStream? = openOtaOrAsset(cleanPath)
            ?: if (!cleanPath.contains('.')) openOtaOrAsset("index.html") else null

        if (stream == null) {
            return WebResourceResponse("text/plain", "utf-8", 404, "Not Found", emptyMap(), null)
        }

        val mime = guessMimeType(cleanPath)
        val encoding = if (isTextMime(mime)) "utf-8" else null
        val headers = mapOf(
            "Access-Control-Allow-Origin" to "*",
            "Cache-Control" to "no-store, must-revalidate"
        )
        return WebResourceResponse(mime, encoding, 200, "OK", headers, stream)
    }

    private fun openOtaOrAsset(relPath: String): InputStream? {
        val otaCanonicalRoot = otaDir.canonicalPath
        val otaFile = File(otaDir, relPath)
        if (otaFile.exists() && otaFile.isFile && otaFile.length() > 0 &&
            otaFile.canonicalPath.startsWith(otaCanonicalRoot)
        ) {
            return FileInputStream(otaFile)
        }
        return try {
            context.assets.open("www/$relPath")
        } catch (_: Exception) {
            null
        }
    }

    fun readActiveManifestText(): String? {
        val otaManifest = File(otaDir, "manifest.json")
        if (otaManifest.exists() && otaManifest.isFile && otaManifest.length() > 0) {
            return runCatching { otaManifest.readText(Charsets.UTF_8) }.getOrNull()
        }
        return runCatching {
            context.assets.open("www/manifest.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        }.getOrNull()
    }

    private fun guessMimeType(path: String): String {
        val lower = path.lowercase()
        return when {
            lower.endsWith(".html") || lower.endsWith(".htm") -> "text/html"
            lower.endsWith(".js") || lower.endsWith(".mjs") -> "application/javascript"
            lower.endsWith(".css") -> "text/css"
            lower.endsWith(".woff2") -> "font/woff2"
            lower.endsWith(".woff") -> "font/woff"
            lower.endsWith(".ttf") -> "font/ttf"
            lower.endsWith(".svg") -> "image/svg+xml"
            lower.endsWith(".json") -> "application/json"
            lower.endsWith(".webmanifest") -> "application/manifest+json"
            lower.endsWith(".png") -> "image/png"
            lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
            lower.endsWith(".webp") -> "image/webp"
            else -> "application/octet-stream"
        }
    }

    private fun isTextMime(mime: String): Boolean {
        return mime.startsWith("text/") ||
            mime == "application/javascript" ||
            mime == "application/json" ||
            mime == "application/manifest+json" ||
            mime == "image/svg+xml"
    }
}
