@file:Suppress("DEPRECATION")

package day.bark.android.projects

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** A saved project is the only source of the initial URL and allowed origin. */
class BarkProjectWebActivity : ComponentActivity() {
    private lateinit var project: BarkProject
    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private lateinit var errorPanel: LinearLayout
    private lateinit var errorText: TextView
    private var lastProjectUrl = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(BarkProjectNavigator.EXTRA_PROJECT_ID).orEmpty()
        val savedProject = BarkProjectStore(this).find(id)
        if (savedProject == null) {
            Toast.makeText(this, "项目已移除，请重新选择项目", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        project = savedProject
        lastProjectUrl = project.url
        createContent()
        configureWebView()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })
        val state = savedInstanceState?.getBundle(WEBVIEW_STATE)
        val stateUrl = savedInstanceState?.getString(LAST_URL)
        val canRestore = savedInstanceState?.getString(PROJECT_URL) == project.url &&
            stateUrl != null && BarkProjectUrl.sameOrigin(project.url, stateUrl)
        if (canRestore && state != null && webView.restoreState(state) != null) {
            lastProjectUrl = stateUrl!!
        } else {
            webView.loadUrl(project.url)
        }
    }

    private fun createContent() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(4))
            addView(TextView(this@BarkProjectWebActivity).apply {
                text = project.name
                textSize = 20f
                maxLines = 1
            })
            addView(TextView(this@BarkProjectWebActivity).apply {
                text = BarkProjectUrl.displayOrigin(project.url)
                textSize = 12f
                maxLines = 1
            })
            addView(LinearLayout(this@BarkProjectWebActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                addView(action("返回") { goBack() })
                addView(action("刷新") { reload() })
                addView(action("浏览器") { openBrowser(lastProjectUrl) })
            })
        }
        root.addView(header)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100 }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))
        errorText = TextView(this).apply { textSize = 15f }
        errorPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            visibility = View.GONE
            addView(errorText)
            addView(Button(this@BarkProjectWebActivity).apply {
                text = "重试"
                setOnClickListener { reload() }
            })
        }
        root.addView(errorPanel)
        webView = WebView(this)
        root.addView(webView, LinearLayout.LayoutParams(-1, 0, 1f))
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, value: Int) {
                progress.progress = value
                progress.visibility = if (value < 100 && errorPanel.visibility != View.VISIBLE) View.VISIBLE else View.GONE
            }
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (BarkProjectUrl.sameOrigin(project.url, url)) return false
                if (request.isForMainFrame) {
                    if (request.hasGesture() && runCatching { BarkProjectUrl.normalize(url) }.isSuccess) {
                        openBrowser(url)
                    } else {
                        showError("链接已离开项目网址，可点击“浏览器”在浏览器中打开项目。")
                    }
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url == null || !BarkProjectUrl.sameOrigin(project.url, url)) {
                    view.stopLoading()
                    showError("无法在项目内打开此网址。")
                    return
                }
                lastProjectUrl = url
                errorPanel.visibility = View.GONE
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView, url: String?) {
                progress.visibility = View.GONE
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) showError("网页暂时无法加载，请检查网络后重试。")
            }

            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame) showError("网页返回错误 ${response.statusCode}，可以重试或使用浏览器打开。")
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                handler.cancel()
                val failedUrl = runCatching { BarkProjectUrl.normalize(error.url) }.getOrNull()
                val currentUrl = view.url?.let { runCatching { BarkProjectUrl.normalize(it) }.getOrNull() }
                if (failedUrl != null && (failedUrl == currentUrl || failedUrl == BarkProjectUrl.normalize(lastProjectUrl))) {
                    showError("网站证书验证失败，可重试或使用浏览器打开。")
                }
            }
        }
        webView.setDownloadListener { url, _, _, _, _ ->
            if (runCatching { BarkProjectUrl.normalize(url) }.isSuccess) openBrowser(url)
        }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorPanel.visibility = View.VISIBLE
        progress.visibility = View.GONE
    }

    private fun reload() {
        errorPanel.visibility = View.GONE
        webView.loadUrl(lastProjectUrl)
    }

    private fun goBack() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else finish()
    }

    private fun openBrowser(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BarkProjectUrl.normalize(url)))) }
            .onFailure { Toast.makeText(this, "未找到可打开此网页的浏览器", Toast.LENGTH_LONG).show() }
    }

    private fun action(label: String, callback: () -> Unit): Button = Button(this).apply {
        text = label
        layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        setOnClickListener { callback() }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onSaveInstanceState(outState: Bundle) {
        if (::webView.isInitialized) {
            val state = Bundle()
            webView.saveState(state)
            outState.putBundle(WEBVIEW_STATE, state)
            outState.putString(PROJECT_URL, project.url)
            outState.putString(LAST_URL, lastProjectUrl)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onPause() {
        if (::webView.isInitialized) webView.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::webView.isInitialized) webView.onResume()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) {
            webView.stopLoading()
            (webView.parent as? LinearLayout)?.removeView(webView)
            webView.destroy()
        }
        super.onDestroy()
    }

    companion object {
        private const val WEBVIEW_STATE = "project_webview"
        private const val PROJECT_URL = "project_url"
        private const val LAST_URL = "project_last_url"
    }
}
