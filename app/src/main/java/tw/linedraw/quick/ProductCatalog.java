package tw.linedraw.quick;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Groups a product by its model code while showing the most common complete name. */
final class ProductCatalog {
    private static final Pattern CODE = Pattern.compile("(?i)^\\s*(BXG|BX|UX|CX)\\s*[-－]?\\s*(\\d{2})\\s*");
    private static final Pattern PRICE = Pattern.compile("(?i)\\s*(?:售價|價格|定價|NT\\$|NTD|\\$)\\s*[:：]?[\\d,]+.*$");

    private ProductCatalog() {}

    static String cleanName(DrawItem item) {
        String category = RunScope.productCategory(item);
        Matcher code = CODE.matcher(item.product);
        if (!code.find()) return item.product.trim();
        String baseCode = code.group(1).toUpperCase(Locale.ROOT) + "-" + code.group(2);
        if (!category.equals(baseCode)) return category;
        String suffix = item.product.substring(code.end()).trim().replaceFirst("^[.．:：·・_－-]+", "").trim();
        suffix = PRICE.matcher(suffix).replaceFirst("").trim();
        suffix = suffix.replaceAll("\\s+", " ");
        return suffix.isEmpty() ? category : category + " " + suffix;
    }

    static Map<String, String> labels(ArrayList<DrawItem> items) {
        Map<String, Map<String, Integer>> counts = new HashMap<>();
        for (DrawItem item : items) {
            if (item.origin.equals("test")) continue;
            String category = RunScope.productCategory(item);
            String label = cleanName(item);
            counts.computeIfAbsent(category, ignored -> new HashMap<>())
                    .merge(label, 1, Integer::sum);
        }
        Map<String, String> labels = new TreeMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : counts.entrySet()) {
            String best = entry.getKey();
            int bestCount = -1;
            for (Map.Entry<String, Integer> candidate : entry.getValue().entrySet()) {
                int count = candidate.getValue();
                if (count > bestCount || (count == bestCount &&
                        (candidate.getKey().length() > best.length() ||
                                (candidate.getKey().length() == best.length() && candidate.getKey().compareTo(best) < 0)))) {
                    best = candidate.getKey();
                    bestCount = count;
                }
            }
            labels.put(entry.getKey(), best);
        }
        return labels;
    }
}
