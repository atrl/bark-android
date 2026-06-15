package day.bark.android

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

object BarkCallAlertPlayer {
    private const val CALL_DURATION_MILLIS = 30_000L

    @Volatile
    private var currentPlayer: MediaPlayer? = null

    fun play(context: Context, message: BarkMessage) {
        val appContext = context.applicationContext
        val uri = resolvedSoundUri(appContext, message) ?: return
        val volume = BarkSoundPolicy.playerVolume(message)

        val player = try {
            MediaPlayer.create(appContext, uri)
        } catch (_: Exception) {
            null
        } ?: return

        synchronized(this) {
            stopLocked()
            currentPlayer = player
            player.isLooping = true
            player.setVolume(volume, volume)
            player.start()
        }

        Thread {
            try {
                Thread.sleep(CALL_DURATION_MILLIS)
            } catch (_: InterruptedException) {
                return@Thread
            }
            synchronized(this) {
                if (currentPlayer === player) {
                    stopLocked()
                }
            }
        }.apply {
            name = "BarkCallAlertStopper"
            isDaemon = true
            start()
        }
    }

    private fun resolvedSoundUri(context: Context, message: BarkMessage): Uri? {
        BarkSoundPolicy.resourceNameFor(message)?.let { sound ->
            return Uri.parse("android.resource://${context.packageName}/raw/$sound")
        }
        val custom = BarkCustomSoundStore(context).find(message.sound) ?: return null
        return Uri.fromFile(custom.file)
    }

    private fun stopLocked() {
        currentPlayer?.let { player ->
            try {
                if (player.isPlaying) player.stop()
            } catch (_: Exception) {
            } finally {
                player.release()
            }
        }
        currentPlayer = null
    }
}
