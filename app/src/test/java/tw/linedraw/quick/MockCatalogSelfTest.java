package tw.linedraw.quick;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Checks that the acceptance queue is isolated from every real coupon link. */
public final class MockCatalogSelfTest {
    public static void main(String[] args) {
        ArrayList<DrawItem> items = MockCatalog.items();
        if (items.size() != 6) throw new AssertionError("expected six mock cases");
        Set<String> urls = new HashSet<>();
        for (DrawItem item : items) {
            if (!"mock".equals(item.origin) || !item.url.startsWith("mock://") ||
                    !item.selected || !urls.add(item.url)) {
                throw new AssertionError("mock item escaped isolation: " + item.url);
            }
            if (!MockCatalog.validScenario(item.url.substring("mock://".length()))) {
                throw new AssertionError("unknown scenario: " + item.url);
            }
        }
        if (MockCatalog.validScenario("https://lin.ee/test"))
            throw new AssertionError("real link accepted as mock");
        System.out.println("MockCatalogSelfTest passed");
    }
}
