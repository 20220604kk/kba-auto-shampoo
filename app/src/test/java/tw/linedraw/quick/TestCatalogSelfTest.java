package tw.linedraw.quick;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Standalone JVM check; does not open the test links. */
public final class TestCatalogSelfTest {
    private static void expect(int actual, int wanted, String label) {
        if (actual != wanted) throw new AssertionError(label + ": expected " + wanted + ", got " + actual);
    }

    public static void main(String[] args) {
        ArrayList<DrawItem> items = new ArrayList<>();
        Set<String> removed = new HashSet<>();
        TestCatalog.addDefaults(items, removed);
        expect(items.size(), 8, "initial links");
        String first = items.get(0).url;
        if (!TestCatalog.removeByUrl(items, first)) throw new AssertionError("first link not removed");
        removed.add(first);
        TestCatalog.addDefaults(items, removed);
        expect(items.size(), 7, "removed default does not return");
        removed.remove(first);
        TestCatalog.addDefaults(items, removed);
        expect(items.size(), 8, "link can be restored");
        TestCatalog.addDefaults(items, removed);
        expect(items.size(), 8, "no duplicate defaults");
        System.out.println("TestCatalogSelfTest passed");
    }
}
