package day.bark.android;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class RemoteImageCacheWiringTest {
    @Test
    public void remoteImageCacheStoresDownloadsUnderCacheDirByUrlHash() throws Exception {
        Path cachePath = Path.of("src/main/java/day/bark/android/BarkRemoteImageCache.kt");

        assertTrue("BarkRemoteImageCache should own remote image caching", Files.exists(cachePath));

        String cache = readFile(cachePath.toString());
        assertTrue(cache.contains("File(context.cacheDir, \"remote_images\")"));
        assertTrue(cache.contains("MessageDigest.getInstance(\"SHA-256\")"));
        assertTrue(cache.contains("cacheFile.exists()"));
        assertTrue(cache.contains("val connection = URL(url).openConnection()"));
        assertTrue(cache.contains("connection.connectTimeout = 10_000"));
        assertTrue(cache.contains("connection.readTimeout = 10_000"));
        assertTrue(cache.contains("connection.getInputStream()"));
        assertTrue(cache.contains("BitmapFactory.decodeFile(cacheFile.absolutePath)"));
        assertTrue(cache.contains("fun file(url: String?): File?"));
        assertTrue(cache.contains("return file(normalizedUrl)?.let { cacheFile ->"));
    }

    @Test
    public void notifierLoadsRemoteBitmapsThroughCacheOnly() throws Exception {
        String notifier = readFile("src/main/java/day/bark/android/BarkNotifier.kt");

        assertTrue(notifier.contains("private val remoteImageCache = BarkRemoteImageCache(context)"));
        assertTrue(notifier.contains("private fun loadBitmap(url: String?) = remoteImageCache.bitmap(url)"));
        assertFalse(notifier.contains("URL(url).openStream()"));
        assertFalse(notifier.contains("BitmapFactory::decodeStream"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}
