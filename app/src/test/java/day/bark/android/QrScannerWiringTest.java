package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class QrScannerWiringTest {
    @Test
    public void appDeclaresQrScannerDependency() throws Exception {
        String buildFile = readFile("build.gradle.kts");

        assertTrue(buildFile.contains("com.journeyapps:zxing-android-embedded:4.3.0"));
        assertTrue(buildFile.contains("androidx.core:core:"));
    }

    @Test
    public void manifestDeclaresCameraPermissionForQrScanning() throws Exception {
        String manifest = readFile("src/main/AndroidManifest.xml");

        assertTrue(manifest.contains("android.permission.CAMERA"));
    }

    @Test
    public void mainActivityLaunchesQrScannerAndImportsScannedContents() throws Exception {
        String activity = readFile("src/main/java/day/bark/android/MainActivity.kt");

        assertTrue(activity.contains("button(\"Scan QR\")"));
        assertTrue(activity.contains("CAMERA_PERMISSION_REQUEST"));
        assertTrue(activity.contains("requestPermissions(arrayOf(Manifest.permission.CAMERA)"));
        assertTrue(activity.contains("onRequestPermissionsResult"));
        assertTrue(activity.contains("IntentIntegrator(this)"));
        assertTrue(activity.contains("IntentIntegrator.parseActivityResult"));
        assertTrue(activity.contains("importServerLink(listOf(contents))"));
    }

    private static String readFile(String path) throws Exception {
        return new String(Files.readAllBytes(Path.of(path)), StandardCharsets.UTF_8);
    }
}
