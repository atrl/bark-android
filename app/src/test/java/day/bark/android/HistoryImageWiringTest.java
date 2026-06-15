package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class HistoryImageWiringTest {
    @Test
    public void historyItemsRenderRemoteImagesAndExposeOpenImageAction() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("import android.widget.ImageView"));
        assertTrue(activity.contains("message.image?.takeIf { it.isNotBlank() }?.let { imageUrl ->"));
        assertTrue(activity.contains("ImageView(this@MainActivity)"));
        assertTrue(activity.contains("loadHistoryImage(imageUrl, imageView)"));
        assertTrue(activity.contains("BarkRemoteImageCache(this).bitmap(imageUrl)"));
        assertTrue(activity.contains("imageView.setImageBitmap(bitmap)"));
        assertTrue(activity.contains("button(\"Open Image\") { openHistoryImage(message) }"));
        assertTrue(activity.contains("button(\"Share Image\") { shareHistoryImage(message) }"));
        assertTrue(activity.contains("button(\"Save Image\") { saveHistoryImage(message) }"));
        assertTrue(activity.contains("private fun openHistoryImage(message: BarkMessage)"));
        assertTrue(activity.contains("Intent(Intent.ACTION_VIEW, Uri.parse(imageUrl))"));
    }

    @Test
    public void historyImagesCanBeSharedFromThePrivateImageCache() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");
        String paths = readFile("src/main/res/xml/bark_sound_paths.xml");

        assertTrue(activity.contains("private fun shareHistoryImage(message: BarkMessage)"));
        assertTrue(activity.contains("BarkRemoteImageCache(this).file(imageUrl)"));
        assertTrue(activity.contains("FileProvider.getUriForFile("));
        assertTrue(activity.contains("\"${packageName}.soundprovider\""));
        assertTrue(activity.contains("Intent(Intent.ACTION_SEND).apply"));
        assertTrue(activity.contains("setType(historyImageMimeType(imageUrl))"));
        assertTrue(activity.contains("putExtra(Intent.EXTRA_STREAM, uri)"));
        assertTrue(activity.contains("addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)"));
        assertTrue(activity.contains("Intent.createChooser(intent, \"Share Bark image\")"));
        assertTrue(paths.contains("path=\"remote_images/\""));
    }

    @Test
    public void historyImagesCanBeSavedToTheSystemPhotoLibrary() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("private fun saveHistoryImage(message: BarkMessage)"));
        assertTrue(activity.contains("MediaStore.Images.Media.EXTERNAL_CONTENT_URI"));
        assertTrue(activity.contains("contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)"));
        assertTrue(activity.contains("contentResolver.openOutputStream(uri)"));
        assertTrue(activity.contains("Environment.DIRECTORY_PICTURES"));
        assertTrue(activity.contains("MediaStore.Images.Media.RELATIVE_PATH"));
        assertTrue(activity.contains("MediaStore.Images.Media.IS_PENDING"));
        assertTrue(activity.contains("status(\"Saved image\")"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}
