package day.bark.android;

import static org.junit.Assert.assertTrue;

import java.io.File;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public class AndroidManifestTest {
    @Test
    public void mainActivityHandlesBarkAddServerDeepLinks() throws Exception {
        Document manifest = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new File("src/main/AndroidManifest.xml"));

        NodeList activities = manifest.getElementsByTagName("activity");
        for (int activityIndex = 0; activityIndex < activities.getLength(); activityIndex++) {
            Element activity = (Element) activities.item(activityIndex);
            if (!".MainActivity".equals(activity.getAttribute("android:name"))) {
                continue;
            }
            assertTrue(hasBarkAddServerViewFilter(activity, "addserver"));
            assertTrue(hasBarkAddServerViewFilter(activity, "addServer"));
            return;
        }
        throw new AssertionError("MainActivity not found");
    }

    @Test
    public void mainActivityIsTextShareTargetForOutgoingBarkPushes() throws Exception {
        Document manifest = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new File("src/main/AndroidManifest.xml"));

        NodeList activities = manifest.getElementsByTagName("activity");
        for (int activityIndex = 0; activityIndex < activities.getLength(); activityIndex++) {
            Element activity = (Element) activities.item(activityIndex);
            if (!".MainActivity".equals(activity.getAttribute("android:name"))) {
                continue;
            }
            assertTrue(hasTextSendFilter(activity));
            return;
        }
        throw new AssertionError("MainActivity not found");
    }

    @Test
    public void mainActivityHandlesBarkHistoryDeepLinksFromWidget() throws Exception {
        Document manifest = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new File("src/main/AndroidManifest.xml"));

        NodeList activities = manifest.getElementsByTagName("activity");
        for (int activityIndex = 0; activityIndex < activities.getLength(); activityIndex++) {
            Element activity = (Element) activities.item(activityIndex);
            if (!".MainActivity".equals(activity.getAttribute("android:name"))) {
                continue;
            }
            assertTrue(hasBarkHistoryViewFilter(activity));
            return;
        }
        throw new AssertionError("MainActivity not found");
    }

    private static boolean hasBarkAddServerViewFilter(Element activity, String host) {
        NodeList filters = activity.getElementsByTagName("intent-filter");
        for (int filterIndex = 0; filterIndex < filters.getLength(); filterIndex++) {
            Element filter = (Element) filters.item(filterIndex);
            if (hasChildWithAttribute(filter, "action", "android:name", "android.intent.action.VIEW")
                && hasChildWithAttribute(filter, "category", "android:name", "android.intent.category.DEFAULT")
                && hasChildWithAttribute(filter, "category", "android:name", "android.intent.category.BROWSABLE")
                && hasChildWithAttribute(filter, "data", "android:scheme", "bark")
                && hasChildWithAttribute(filter, "data", "android:host", host)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTextSendFilter(Element activity) {
        NodeList filters = activity.getElementsByTagName("intent-filter");
        for (int filterIndex = 0; filterIndex < filters.getLength(); filterIndex++) {
            Element filter = (Element) filters.item(filterIndex);
            if (hasChildWithAttribute(filter, "action", "android:name", "android.intent.action.SEND")
                && hasChildWithAttribute(filter, "category", "android:name", "android.intent.category.DEFAULT")
                && hasChildWithAttribute(filter, "data", "android:mimeType", "text/plain")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasBarkHistoryViewFilter(Element activity) {
        NodeList filters = activity.getElementsByTagName("intent-filter");
        for (int filterIndex = 0; filterIndex < filters.getLength(); filterIndex++) {
            Element filter = (Element) filters.item(filterIndex);
            if (hasChildWithAttribute(filter, "action", "android:name", "android.intent.action.VIEW")
                && hasChildWithAttribute(filter, "category", "android:name", "android.intent.category.DEFAULT")
                && hasChildWithAttribute(filter, "category", "android:name", "android.intent.category.BROWSABLE")
                && hasChildWithAttribute(filter, "data", "android:scheme", "bark")
                && hasChildWithAttribute(filter, "data", "android:host", "history")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasChildWithAttribute(Element parent, String childName, String attribute, String value) {
        NodeList children = parent.getElementsByTagName(childName);
        for (int childIndex = 0; childIndex < children.getLength(); childIndex++) {
            Element child = (Element) children.item(childIndex);
            if (value.equals(child.getAttribute(attribute))) {
                return true;
            }
        }
        return false;
    }
}
