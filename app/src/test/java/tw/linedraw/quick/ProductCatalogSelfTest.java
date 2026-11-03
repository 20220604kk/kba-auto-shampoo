package tw.linedraw.quick;

import java.util.ArrayList;
import java.util.Map;

/** Standalone JVM check; does not open LINE or submit any draw. */
public final class ProductCatalogSelfTest {
    public static void main(String[] args) {
        ArrayList<DrawItem> items = new ArrayList<>();
        items.add(item("UX-03 魔導神杖 售價295元", 1));
        items.add(item("UX-03 魔導神杖", 2));
        items.add(item("UX03 魔導神杖", 3));
        items.add(item("UX-15 鮫鯊狂鱗 售價795元", 4));
        items.add(item("CX-00 迪卡狂怒 FT3-60T", 5));
        items.add(item("CX-00 迪卡狂怒", 6));
        items.add(item("CX-00 新世紀福音戰士改造組", 7));
        Map<String, String> labels = ProductCatalog.labels(items);
        if (!"UX-03 魔導神杖".equals(labels.get("UX-03")))
            throw new AssertionError("UX-03 name: " + labels.get("UX-03"));
        if (!"UX-15 鮫鯊狂鱗".equals(labels.get("UX-15")))
            throw new AssertionError("UX-15 name: " + labels.get("UX-15"));
        if (!"CX-00 迪卡狂怒".equals(labels.get("CX-00 迪卡狂怒")))
            throw new AssertionError("CX-00 Dika name: " + labels.get("CX-00 迪卡狂怒"));
        if (!"CX-00 新世紀福音戰士改造組".equals(labels.get("CX-00 新世紀福音戰士改造組")))
            throw new AssertionError("CX-00 EVA name: " + labels.get("CX-00 新世紀福音戰士改造組"));
        if (labels.size() != 4) throw new AssertionError("Product categories: " + labels);
        System.out.println("ProductCatalogSelfTest passed");
    }

    private static DrawItem item(String product, int number) {
        return new DrawItem("台北市", "門市 " + number, product, "", "https://lin.ee/example" + number, "source");
    }
}
