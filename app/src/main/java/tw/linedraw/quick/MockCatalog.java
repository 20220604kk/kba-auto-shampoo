package tw.linedraw.quick;

import java.util.ArrayList;

/** Isolated acceptance fixtures. None of these URLs or items can reach LINE. */
final class MockCatalog {
    static final int MAX_COUNT = 30;
    private static final String[] SCENARIOS = {"ready", "already", "win", "expired", "error", "friend"};
    private static final String[] TITLES = {
            "K爸模擬 1｜可抽選", "K爸模擬 2｜已抽過", "K爸模擬 3｜中獎結果",
            "K爸模擬 4｜已失效", "K爸模擬 5｜載入錯誤", "K爸模擬 6｜加入好友"
    };

    private MockCatalog() {}

    static ArrayList<DrawItem> items() { return items(MAX_COUNT); }

    static ArrayList<DrawItem> items(int requestedCount) {
        ArrayList<DrawItem> items = new ArrayList<>();
        int count = Math.max(1, Math.min(MAX_COUNT, requestedCount));
        for (int i = 0; i < count; i++) {
            int scenarioIndex = i % SCENARIOS.length;
            String number = String.format(java.util.Locale.ROOT, "%03d", i + 1);
            DrawItem item = new DrawItem("模擬", "K爸模擬驗收", "模擬驗收 " + number + "｜" + TITLES[scenarioIndex], "",
                    "mock://" + SCENARIOS[scenarioIndex] + "/" + number, "mock");
            item.selected = true;
            items.add(item);
        }
        return items;
    }

    static boolean validScenario(String scenario) {
        for (String known : SCENARIOS) if (known.equals(scenario)) return true;
        return false;
    }

    static String scenarioFromUrl(String url) {
        if (url == null || !url.startsWith("mock://")) return "";
        String value = url.substring("mock://".length());
        int slash = value.indexOf('/');
        return slash < 0 ? value : value.substring(0, slash);
    }

    static String titleForScenario(String scenario) {
        for (int i = 0; i < SCENARIOS.length; i++) if (SCENARIOS[i].equals(scenario)) return TITLES[i];
        return TITLES[0];
    }
}
