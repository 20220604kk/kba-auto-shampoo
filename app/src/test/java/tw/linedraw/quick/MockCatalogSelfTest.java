package tw.linedraw.quick;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Checks that the acceptance queue is isolated from every real coupon link. */
public final class MockCatalogSelfTest {
    public static void main(String[] args) {
        ArrayList<DrawItem> items = MockCatalog.items();
        if (items.size() != 30) throw new AssertionError("expected thirty mock cases");
        Set<String> urls = new HashSet<>();
        for (DrawItem item : items) {
            if (!"mock".equals(item.origin) || !item.url.startsWith("mock://") ||
                    !item.selected || !urls.add(item.url)) {
                throw new AssertionError("mock item escaped isolation: " + item.url);
            }
            if (!MockCatalog.validScenario(MockCatalog.scenarioFromUrl(item.url))) {
                throw new AssertionError("unknown scenario: " + item.url);
            }
        }
        if (MockCatalog.items(3).size() != 3 || MockCatalog.items(99).size() != 30)
            throw new AssertionError("mock count clamp failed");
        if (MockCatalog.validScenario("https://lin.ee/test"))
            throw new AssertionError("real link accepted as mock");
        System.out.println("MockCatalogSelfTest passed");
    }
}
