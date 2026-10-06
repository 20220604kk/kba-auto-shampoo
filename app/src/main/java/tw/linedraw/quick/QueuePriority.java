package tw.linedraw.quick;

import java.util.ArrayList;
import java.util.List;

/** Stable queue order: selection and priority are separate decisions. */
final class QueuePriority {
    private QueuePriority() {}

    static ArrayList<DrawItem> order(List<DrawItem> queue, List<String> cities,
                                     List<String> products, boolean cityFirst) {
        ArrayList<DrawItem> result = new ArrayList<>(queue);
        result.sort((left, right) -> {
            int city = Integer.compare(rank(cities, RegionCatalog.canonicalCity(left.city)),
                    rank(cities, RegionCatalog.canonicalCity(right.city)));
            int product = Integer.compare(rank(products, RunScope.productCategory(left)),
                    rank(products, RunScope.productCategory(right)));
            return cityFirst ? (city != 0 ? city : product) : (product != 0 ? product : city);
        });
        return result;
    }

    private static int rank(List<String> priorities, String value) {
        int index = priorities.indexOf(value);
        return index < 0 ? priorities.size() : index;
    }
}
