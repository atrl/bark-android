package day.bark.android.projects.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import day.bark.android.BuildConfig
import day.bark.android.projects.BarkProject
import day.bark.android.projects.BarkProjectUrl
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

object BasisRepository {
    private var cache: BasisFeedCache? = null
    private val states = ConcurrentHashMap<String, MutableStateFlow<BasisCacheState>>()

    fun supports(project: BarkProject): Boolean = BarkProjectUrl.sameOrigin("https://basis.atrl.me/", project.url)

    @Synchronized private fun cache(context: Context): BasisFeedCache = cache ?: BasisFeedCache(
        File(context.applicationContext.filesDir, "project-snapshots"), BasisFeedTransport(::fetch),
    ).also { cache = it }

    fun state(context: Context, family: String): MutableStateFlow<BasisCacheState> =
        states.getOrPut(family) { MutableStateFlow(cache(context).cached(family)) }

    fun cached(context: Context, family: String): BasisCacheState = state(context, family).value

    fun refresh(context: Context, family: String, force: Boolean = false): BasisCacheState {
        val state = state(context, family)
        state.value = state.value.copy(refreshing = true)
        val result = cache(context).refresh(family, force)
        state.value = result
        day.bark.android.projects.widget.BarkProjectWidgetProvider.updateAll(context.applicationContext)
        return result
    }

    private fun fetch(family: String, etag: String?): BasisFeedResponse {
        require(family in BasisSnapshot.FAMILIES)
        val connection = URL("https://basis.atrl.me/api/mobile-summary?family=$family").openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", "Bark-Android/${BuildConfig.VERSION_NAME}")
            connection.setRequestProperty("Accept", "application/json")
            etag?.let { connection.setRequestProperty("If-None-Match", it) }
            val status = connection.responseCode
            if (status != 200) return BasisFeedResponse(status)
            require(connection.contentLengthLong <= BasisSnapshot.MAX_BYTES)
            val bytes = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(bytes.size() + count <= BasisSnapshot.MAX_BYTES)
                    bytes.write(buffer, 0, count)
                }
            }
            return BasisFeedResponse(200, bytes.toString(Charsets.UTF_8.name()), connection.getHeaderField("ETag"))
        } finally { connection.disconnect() }
    }
}

@Composable
fun rememberBasisState(family: String): BasisCacheState {
    val context = LocalContext.current.applicationContext
    val owner = LocalLifecycleOwner.current
    val flow = remember(context, family) { BasisRepository.state(context, family) }
    val state by flow.collectAsState()
    LaunchedEffect(owner, family) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                withContext(Dispatchers.IO) { BasisRepository.refresh(context, family) }
                delay(60_000)
            }
        }
    }
    return state
}
