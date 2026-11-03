package tw.linedraw.quick;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

final class TestCatalog {
    private static final String[] TITLES = {
            "模擬測試001-1% 不限次", "模擬測試002-1% 限次",
            "模擬測試003-20% 不限次", "模擬測試004-20% 限次",
            "模擬測試005-40% 不限次", "模擬測試006-40% 限次",
            "模擬測試007-60% 不限次", "模擬測試007-60% 限次"
    };
    private static final String[] URLS = {
            "https://lin.ee/ZmtaKXp", "https://lin.ee/tPweJdV",
            "https://lin.ee/Y6DCGP1", "https://lin.ee/sjtLxFgY",
            "https://lin.ee/oE6HWaL", "https://lin.ee/wbPdSVo",
            "https://lin.ee/8JOmO1s", "https://lin.ee/UdzlJxu"
    };

    private TestCatalog() {}

    static void addDefaults(ArrayList<DrawItem> items, Set<String> removedUrls) {
        Set<String> existing = new HashSet<>();
        for (DrawItem item : items) existing.add(item.key());
        for (int i = 0; i < URLS.length; i++) {
            DrawItem test = new DrawItem("測試", "K爸的官方帳號", TITLES[i], "", URLS[i], "test");
            if (!removedUrls.contains(test.url) && existing.add(test.key())) items.add(test);
        }
    }

    static boolean isDefault(DrawItem item) {
        if (item == null || !item.origin.equals("test")) return false;
        for (String url : URLS) if (item.url.equals(url)) return true;
        return false;
    }

    static String expectedPageProduct(DrawItem item) {
        if (!isDefault(item)) return "";
        for (int i = 0; i < URLS.length; i++) if (item.url.equals(URLS[i])) return TITLES[i];
        return "";
    }

    static boolean removeByUrl(ArrayList<DrawItem> items, String url) {
        return items.removeIf(item -> item.origin.equals("test") && item.url.equals(url));
    }
}
