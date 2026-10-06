package tw.linedraw.quick;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;

public final class QueuePrioritySelfTest {
    private static DrawItem item(String city, String product, int number) {
        DrawItem result = new DrawItem(city, "門市" + number, product, "", "https://lin.ee/priority" + number, "source");
        result.selected = true;
        return result;
    }

    private static void expect(String actual, String wanted) {
        if (!actual.equals(wanted)) throw new AssertionError(actual + " != " + wanted);
    }

    public static void main(String[] args) {
        ArrayList<DrawItem> source = new ArrayList<>(Arrays.asList(
                item("台北市", "BX-09 通行證", 1), item("桃園市", "CX-13 測試", 2),
                item("桃園市", "UX-15 鮫鯊狂鱗", 3), item("台北市", "UX-15 鮫鯊狂鱗", 4),
                item("新竹市", "UX-15 鮫鯊狂鱗", 5)));
        ArrayList<String> cities = new ArrayList<>(Arrays.asList("桃園市", "台北市", "新竹市"));
        ArrayList<String> products = new ArrayList<>(Arrays.asList("UX-15", "CX-13", "BX-09"));
        ArrayList<DrawItem> cityFirst = QueuePriority.order(source, cities, products, true);
        expect(cityFirst.get(0).key(), source.get(2).key());
        expect(cityFirst.get(1).key(), source.get(1).key());
        expect(cityFirst.get(2).key(), source.get(3).key());
        ArrayList<DrawItem> productFirst = QueuePriority.order(source, cities, products, false);
        expect(productFirst.get(0).key(), source.get(2).key());
        expect(productFirst.get(1).key(), source.get(3).key());
        expect(productFirst.get(2).key(), source.get(4).key());
        expect(source.get(0).key(), "門市1|https://lin.ee/priority1"); // input must not change
        LinkedHashSet<String> selected = new LinkedHashSet<>();
        CitySelection.toggleRegion(selected, cities, "北部");
        if (selected.size() != 3) throw new AssertionError("select north");
        selected.remove("台北市");
        if (CitySelection.selectedInRegion(selected, cities, "北部") != 2)
            throw new AssertionError("partial north");
        CitySelection.toggleRegion(selected, cities, "北部");
        if (selected.size() != 3) throw new AssertionError("partial click selects all");
        CitySelection.toggleRegion(selected, cities, "北部");
        if (!selected.isEmpty()) throw new AssertionError("full click clears region");
        System.out.println("QueuePrioritySelfTest passed");
    }
}
