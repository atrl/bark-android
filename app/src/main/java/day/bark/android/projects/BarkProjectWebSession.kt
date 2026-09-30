package day.bark.android.projects

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.MutableContextWrapper
import android.content.res.Configuration
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebView
import android.webkit.WebViewClient

/** Keeps only the most recently opened page, detached from its Activity, for a quick return. */
object BarkProjectWebSession {
    data class Lease(val view: WebView, val reused: Boolean, val lastUrl: String)
    private data class Saved(val projectId: String, val projectUrl: String, val view: WebView, val savedAt: Long)
    private var saved: Saved? = null
    private var registered = false

    fun acquire(context: Context, project: BarkProject): Lease {
        register(context.applicationContext)
        val previous = saved
        saved = null
        if (previous != null && previous.projectId == project.id && previous.projectUrl == project.url &&
            SystemClock.elapsedRealtime() - previous.savedAt <= 5 * 60_000 &&
            BarkProjectUrl.sameOrigin(project.url, previous.view.url.orEmpty())) {
            (previous.view.context as MutableContextWrapper).baseContext = context
            previous.view.visibility = View.VISIBLE
            return Lease(previous.view, true, previous.view.url ?: project.url)
        }
        previous?.view?.destroy()
        return Lease(WebView(MutableContextWrapper(context.applicationContext).apply { baseContext = context }), false, project.url)
    }

    fun release(context: Context, project: BarkProject, view: WebView, retain: Boolean) {
        view.visibility = View.GONE
        view.onPause()
        view.stopLoading()
        (view.parent as? ViewGroup)?.removeView(view)
        view.webChromeClient = null
        view.setDownloadListener(null)
        view.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                if (saved?.view === view) saved = null
                view.destroy()
                return true
            }
        }
        (view.context as MutableContextWrapper).baseContext = context.applicationContext
        if (!retain || !BarkProjectUrl.sameOrigin(project.url, view.url.orEmpty())) {
            view.destroy()
            return
        }
        saved?.view?.destroy()
        saved = Saved(project.id, project.url, view, SystemClock.elapsedRealtime())
    }

    private fun register(context: Context) {
        if (registered) return
        registered = true
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(configuration: Configuration) = Unit
            override fun onLowMemory() = clear()
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) clear()
            }
        })
    }

    private fun clear() { saved?.view?.destroy(); saved = null }
}
