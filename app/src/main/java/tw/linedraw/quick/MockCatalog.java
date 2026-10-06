package tw.linedraw.quick;

import java.util.ArrayList;

/** Isolated acceptance fixtures. None of these URLs or items can reach LINE. */
final class MockCatalog {
    private static final String[] SCENARIOS = {"ready", "already", "win", "expired", "error", "friend"};
    private static final String[] TITLES = {
            "K爸模擬 1｜可抽選", "K爸模擬 2｜已抽過", "K爸模擬 3｜中獎結果",
            "K爸模擬 4｜已失效", "K爸模擬 5｜載入錯誤", "K爸模擬 6｜加入好友"
    };

    private MockCatalog() {}

    static ArrayList<DrawItem> items() {
        ArrayList<DrawItem> items = new ArrayList<>();
        for (int i = 0; i < SCENARIOS.length; i++) {
            DrawItem item = new DrawItem("模擬", "K爸測試", TITLES[i], "", "mock://" + SCENARIOS[i], "mock");
            item.selected = true;
            items.add(item);
        }
        return items;
    }

    static boolean validScenario(String scenario) {
        for (String known : SCENARIOS) if (known.equals(scenario)) return true;
        return false;
    }

    static String titleForScenario(String scenario) {
        for (int i = 0; i < SCENARIOS.length; i++) if (SCENARIOS[i].equals(scenario)) return TITLES[i];
        return TITLES[0];
    }
}
