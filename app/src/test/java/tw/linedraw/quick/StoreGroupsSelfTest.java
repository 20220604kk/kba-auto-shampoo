package tw.linedraw.quick;

import java.util.ArrayList;

/** Standalone JVM check for grouped filtered results. */
public final class StoreGroupsSelfTest {
    private static DrawItem item(String city, String store, String product, int number) {
        return new DrawItem(city, store, product, "", "https://lin.ee/sample" + number, "source");
    }

    private static void expect(int actual, int wanted, String label) {
        if (actual != wanted) throw new AssertionError(label + ": expected " + wanted + ", got " + actual);
    }

    public static void main(String[] args) {
        ArrayList<DrawItem> items = new ArrayList<>();
        items.add(item("桃園市", "店 B", "UX-03", 1));
        items.add(item("桃園市", "店 A", "UX-03", 2));
        items.add(item("台北市", "店 C", "UX-15", 3));
        items.add(item("桃園市", "店 A", "UX-15", 4));
        ArrayList<DrawItem> ordered = StoreGroups.ordered(items);
        ArrayList<StoreGroups.CityGroup> cities = StoreGroups.group(ordered);
        expect(cities.size(), 2, "cities");
        if (!cities.get(0).city.equals("台北市")) throw new AssertionError("city order");
        expect(cities.get(1).stores.size(), 2, "Taoyuan store boxes");
        if (!cities.get(1).stores.get(0).name.equals("店 A")) throw new AssertionError("store order");
        expect(cities.get(1).stores.get(0).items.size(), 2, "same-store products share one box");
        System.out.println("StoreGroupsSelfTest passed");
    }
}
