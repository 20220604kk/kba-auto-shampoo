package tw.linedraw.quick;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/** Standalone JVM check; does not launch Android or open any LINE links. */
public final class RunScopeSelfTest {
    private static DrawItem item(String city, String product, int number) {
        return new DrawItem(city, "門市 " + number, product, "", "https://lin.ee/example" + number, "source");
    }

    private static void expect(int actual, int wanted, String caseName) {
        if (actual != wanted) throw new AssertionError(caseName + ": expected " + wanted + ", got " + actual);
    }

    private static void expect(boolean actual, boolean wanted, String caseName) {
        if (actual != wanted) throw new AssertionError(caseName + ": expected " + wanted + ", got " + actual);
    }

    public static void main(String[] args) {
        ArrayList<DrawItem> items = new ArrayList<>();
        for (int i = 0; i < 23; i++) {
            DrawItem oldSelection = item("台北市", "UX-19 其他品項", i);
            oldSelection.selected = true;
            items.add(oldSelection);
        }
        items.add(item("桃園市", "UX-03 魔導神杖", 23));
        items.add(item("桃園市", "UX-03 魔導神杖", 24));
        items.add(item("桃園市", "UX-15 鮫鯊狂鱗", 25));
        items.add(item("台北市", "UX-15 鮫鯊狂鱗", 26));
        items.add(item("台北市", "CX-00 迪卡狂怒 FT3-60T", 29));
        items.add(item("台北市", "CX-00 新世紀福音戰士改造組", 30));
        DrawItem processed = item("桃園市", "UX-03 魔導神杖", 27);
        processed.status = "already";
        items.add(processed);
        DrawItem uncertain = item("桃園市", "UX-03 魔導神杖", 28);
        uncertain.status = "error";
        items.add(uncertain);
        DrawItem legacyManual = new DrawItem("桃園市", "舊自訂店", "UX-03 魔導神杖", "",
                "https://lin.ee/legacy", "manual");
        items.add(legacyManual);

        ArrayList<DrawItem> taoyuanUx03 = RunScope.visible(items, "ready", "北部", "桃園市", "UX-03");
        expect(taoyuanUx03.size(), 3, "visible Taoyuan UX-03 including uncertain");
        expect(RunScope.bulkSelectable(taoyuanUx03.get(0)), true, "pending bulk selectable");
        expect(RunScope.bulkSelectable(uncertain), true, "retryable uncertain item follows store selection");
        expect(RunScope.bulkSelectable(processed), false, "processed never bulk selectable");
        DrawItem busy = item("桃園市", "UX-03 魔導神杖", 31);
        busy.status = "busy";
        expect(RunScope.eligible(busy), true, "busy item remains retryable");
        expect(RunScope.bulkSelectable(busy), true, "busy item follows store selection");
        expect(RunScope.queue(taoyuanUx03, "ready").size(), 0, "unselected visible items are never queued");
        expect(RunScope.visible(items, "ready", RegionCatalog.ALL, RegionCatalog.ALL_CITIES, "UX-15").size(), 2, "all Taiwan UX-15");
        expect(RunScope.visible(items, "ready", "北部", "台北市", "UX-15").size(), 1, "Taipei UX-15");
        expect(RunScope.visible(items, "ready", "北部", "台北市", "CX-00 迪卡狂怒").size(), 1,
                "CX-00 Dika must not include EVA");
        expect(RunScope.visible(items, "ready", "北部", "台北市", "CX-00 新世紀福音戰士改造組").size(), 1,
                "CX-00 EVA must not include Dika");
        expect(RunScope.visible(items, "ready", new HashSet<>(Arrays.asList("北部")),
                new HashSet<>(Arrays.asList("台北市", "桃園市")),
                new HashSet<>(Arrays.asList("UX-03", "UX-15"))).size(), 5,
                "multi-city multi-product excludes processed and hidden products");

        taoyuanUx03.get(0).selected = true;
        expect(RunScope.queue(taoyuanUx03, "ready").size(), 1, "only selected visible item");
        taoyuanUx03.get(0).selected = false;
        expect(RunScope.queue(taoyuanUx03, "ready").size(), 0, "clearing selection never starts all items");
        System.out.println("RunScopeSelfTest passed");
    }
}
