package tw.linedraw.quick;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

final class TestCatalog {
    private static final String[] DEFAULT_TOKENS = {
            "zY5OJZr", "t5QXmAN", "59RjUOS", "WCq14S5", "OvKQcaGy", "NGL540W", "xO9HJta", "o2KhHT8"
    };

    private TestCatalog() {}

    static void addDefaults(ArrayList<DrawItem> items, Set<String> removedUrls) {
        Set<String> existing = new HashSet<>();
        for (DrawItem item : items) existing.add(item.key());
        for (int i = 0; i < DEFAULT_TOKENS.length; i++) {
            DrawItem test = new DrawItem("測試", "方爸測試", "方爸測試 " + (i + 1), "",
                    "https://lin.ee/" + DEFAULT_TOKENS[i], "test");
            if (!removedUrls.contains(test.url) && existing.add(test.key())) items.add(test);
        }
    }

    static boolean removeByUrl(ArrayList<DrawItem> items, String url) {
        return items.removeIf(item -> item.origin.equals("test") && item.url.equals(url));
    }
}
