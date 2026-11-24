package ma.euleracademy.bac2pc

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var loadingProgress: ProgressBar
    private lateinit var updateBanner: LinearLayout
    private lateinit var updateBannerText: TextView
    private lateinit var updateBannerAction: MaterialButton

    private lateinit var assetEngine: LocalAssetEngine
    private lateinit var syncManager: OtaSyncManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var suppressNavSync = false
    private var lastSubjectNavId = R.id.nav_math
    private var lastBackPressMs = 0L

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = Color.parseColor("#060911")
        window.navigationBarColor = Color.parseColor("#0B1120")
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.eulerWebView)
        bottomNav = findViewById(R.id.bottomNav)
        loadingProgress = findViewById(R.id.loadingProgress)
        updateBanner = findViewById(R.id.updateBanner)
        updateBannerText = findViewById(R.id.updateBannerText)
        updateBannerAction = findViewById(R.id.updateBannerAction)

        assetEngine = LocalAssetEngine(applicationContext)
        syncManager = OtaSyncManager(applicationContext, assetEngine)

        setupWebView()
        setupBottomNav()
        setupBackNavigation()

        webView.loadUrl("${LocalAssetEngine.BASE_URL}index.html")
        runBackgroundUpdateCheck(isManual = false)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.setBackgroundColor(Color.parseColor("#060911"))
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
        }

        webView.addJavascriptInterface(EulerNativeBridge(), "EulerNative")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                loadingProgress.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val uri = request?.url ?: return null
                return if (assetEngine.shouldIntercept(uri)) {
                    assetEngine.intercept(uri)
                } else {
                    null
                }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                if (assetEngine.shouldIntercept(uri)) {
                    return false
                }
                if (uri.scheme == "http" || uri.scheme == "https") {
                    runCatching {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    }
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                loadingProgress.visibility = View.GONE
            }
        }
    }

    private fun setupBottomNav() {
        bottomNav.setOnItemSelectedListener { item ->
            if (suppressNavSync) return@setOnItemSelectedListener true
            when (item.itemId) {
                R.id.nav_math -> {
                    lastSubjectNavId = R.id.nav_math
                    navigateSubject("math")
                    true
                }
                R.id.nav_physics -> {
                    lastSubjectNavId = R.id.nav_physics
                    navigateSubject("physics")
                    true
                }
                R.id.nav_chemistry -> {
                    lastSubjectNavId = R.id.nav_chemistry
                    navigateSubject("chemistry")
                    true
                }
                R.id.nav_sync -> {
                    runBackgroundUpdateCheck(isManual = true)
                    bottomNav.post {
                        suppressNavSync = true
                        bottomNav.selectedItemId = lastSubjectNavId
                        suppressNavSync = false
                    }
                    false
                }
                else -> false
            }
        }
    }

    private fun navigateSubject(subjectKey: String) {
        webView.evaluateJavascript(
            "window.EulerRouter && window.EulerRouter.goToSubject('$subjectKey');",
            null
        )
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                webView.evaluateJavascript(
                    "(window.EulerMobileBack && window.EulerMobileBack()) ? 'true' : 'false';"
                ) { result ->
                    if (result == "\"true\"" || result == "true") {
                        return@evaluateJavascript
                    }
                    if (webView.canGoBack()) {
                        webView.goBack()
                        return@evaluateJavascript
                    }
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressMs < 2000L) {
                        finish()
                    } else {
                        lastBackPressMs = now
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.press_back_again),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    private fun runBackgroundUpdateCheck(isManual: Boolean) {
        if (isManual) {
            Toast.makeText(this, "جاري التحقق من التحديثات...", Toast.LENGTH_SHORT).show()
        }
        scope.launch {
            val lessonResult = withContext(Dispatchers.IO) {
                syncManager.checkAndSyncLessons()
            }
            val apkUpdate = withContext(Dispatchers.IO) {
                syncManager.checkApkUpdate(BuildConfig.VERSION_CODE)
            }

            when (lessonResult) {
                is LessonSyncResult.Updated -> {
                    showUpdateBanner(
                        "✨ تم تنزيل ${lessonResult.updatedFilesCount} ملفات محدثة (${lessonResult.completedAxes}/${lessonResult.totalAxes} محوراً)",
                        "تطبيق الآن"
                    ) {
                        updateBanner.visibility = View.GONE
                        webView.reload()
                    }
                }
                is LessonSyncResult.UpToDate -> {
                    if (apkUpdate != null) {
                        showApkUpdateBanner(apkUpdate)
                    } else if (isManual) {
                        Toast.makeText(
                            this@MainActivity,
                            "✅ المحتوى محدّث بالكامل (${lessonResult.completedAxes}/${lessonResult.totalAxes} محوراً) وجاهز أوفلاين",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
                is LessonSyncResult.OfflineOrFailed -> {
                    if (apkUpdate != null) {
                        showApkUpdateBanner(apkUpdate)
                    } else if (isManual) {
                        Toast.makeText(
                            this@MainActivity,
                            "📶 الوضع الأوفلاين نشط — جميع الدروس المحفوظة متاحة بدون إنترنت",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    private fun showApkUpdateBanner(info: ApkUpdateInfo) {
        showUpdateBanner(
            "🚀 يتوفر إصدار جديد من التطبيق (v${info.versionName})",
            "تنزيل"
        ) {
            updateBannerAction.isEnabled = false
            updateBannerAction.text = "0%"
            scope.launch {
                val downloaded = withContext(Dispatchers.IO) {
                    runCatching {
                        syncManager.downloadApk(info.apkUrl) { pct ->
                            runOnUiThread { updateBannerAction.text = "$pct%" }
                        }
                    }.getOrNull()
                }
                updateBannerAction.isEnabled = true
                updateBannerAction.text = "تثبيت"
                if (downloaded != null) {
                    val launched = syncManager.launchApkInstaller(downloaded)
                    if (!launched) {
                        Toast.makeText(
                            this@MainActivity,
                            "يرجى السماح بالتثبيت من هذا المصدر ثم الضغط على تثبيت",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@MainActivity,
                        "تعذر تحميل التحديث، تحقق من الاتصال",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun showUpdateBanner(text: String, actionLabel: String, onClick: () -> Unit) {
        updateBannerText.text = text
        updateBannerAction.text = actionLabel
        updateBannerAction.isEnabled = true
        updateBannerAction.setOnClickListener { onClick() }
        updateBanner.visibility = View.VISIBLE
    }

    inner class EulerNativeBridge {
        @JavascriptInterface
        fun onRouteChanged(hash: String, subject: String, routeType: String) {
            runOnUiThread {
                val targetId = when (subject) {
                    "physics" -> R.id.nav_physics
                    "chemistry" -> R.id.nav_chemistry
                    else -> R.id.nav_math
                }
                lastSubjectNavId = targetId
                if (bottomNav.selectedItemId != targetId) {
                    suppressNavSync = true
                    bottomNav.selectedItemId = targetId
                    suppressNavSync = false
                }
            }
        }

        @JavascriptInterface
        fun printPage(title: String) {
            runOnUiThread {
                val printManager = getSystemService(PRINT_SERVICE) as? PrintManager ?: return@runOnUiThread
                val docName = if (title.isNotBlank()) "Euler Academy - $title" else "Euler Academy Lesson"
                val adapter = webView.createPrintDocumentAdapter(docName)
                printManager.print(docName, adapter, PrintAttributes.Builder().build())
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        webView.destroy()
        super.onDestroy()
    }
}
