package day.bark.android

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

data class BarkCustomSound(
    val name: String,
    val file: File,
)

class BarkCustomSoundStore(context: Context) {
    private val appContext = context.applicationContext
    private val soundsDir: File = File(appContext.filesDir, "sounds")

    fun import(uri: Uri): BarkCustomSound {
        val displayName = displayName(uri) ?: uri.lastPathSegment.orEmpty()
        val fileName = BarkCustomSoundName.storageFileName(displayName)
            ?: throw IllegalArgumentException("Unsupported sound file")
        val target = File(soundsDir, fileName)
        soundsDir.mkdirs()
        appContext.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Unable to open sound file" }
            target.outputStream().use(input::copyTo)
        }
        return BarkCustomSound(
            name = BarkCustomSoundName.copyName(fileName),
            file = target,
        )
    }

    fun list(): List<BarkCustomSound> {
        val files = soundsDir.listFiles()?.toList().orEmpty()
        return files
            .filter { it.isFile && BarkCustomSoundName.storageFileName(it.name) != null }
            .map { file ->
                BarkCustomSound(
                    name = BarkCustomSoundName.copyName(file.name),
                    file = file,
                )
            }
            .sortedBy { it.name }
    }

    fun find(soundName: String?): BarkCustomSound? {
        if (soundName.isNullOrBlank()) return null
        val copyName = BarkCustomSoundName.copyName(soundName)
        return list().firstOrNull { it.name == copyName }
    }

    fun delete(sound: BarkCustomSound) {
        sound.file.delete()
    }

    private fun displayName(uri: Uri): String? {
        val cursor = appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }
}
