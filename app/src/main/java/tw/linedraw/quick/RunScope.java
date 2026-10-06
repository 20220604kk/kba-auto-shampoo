package tw.linedraw.quick;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The list, displayed count and launch queue must all use this same scope. */
final class RunScope {
    static final String ALL_PRODUCTS = "全部品項";
    private static final Pattern PRODUCT_CODE = Pattern.compile("(?i)^\\s*(BXG|BX|UX|CX)\\s*[-－]?\\s*(\\d{2})");

    private RunScope() {}

    static String productCategory(DrawItem item) {
        Matcher match = PRODUCT_CODE.matcher(item.product);
        return match.find() ? match.group(1).toUpperCase(Locale.ROOT) + "-" + match.group(2) : item.product;
    }

    static boolean eligible(DrawItem item) {
        return item.status.equals("pending") || item.status.equals("scheduled") ||
                item.status.equals("not_friend") || item.status.equals("error");
    }

    // Uncertain/error items can be opted into individually, never by bulk selection.
    static boolean bulkSelectable(DrawItem item) {
        return item.status.equals("pending") || item.status.equals("scheduled") ||
                item.status.equals("not_friend");
    }

    static boolean modeMatches(DrawItem item, String mode) {
        if (mode.equals("test") != item.origin.equals("test")) return false;
        if (!mode.equals("test") && !item.origin.equals("source")) return false;
        if (mode.equals("ready") && !eligible(item)) return false;
        return !mode.equals("done") || !item.status.equals("pending");
    }

    static ArrayList<DrawItem> visible(ArrayList<DrawItem> items, String mode, String region, String city, String product) {
        ArrayList<DrawItem> result = new ArrayList<>();
        for (DrawItem item : items) {
            if (!modeMatches(item, mode)) continue;
            if (!region.equals(RegionCatalog.ALL) && !region.equals(RegionCatalog.regionOf(item.city))) continue;
            if (!city.equals(RegionCatalog.ALL_CITIES) && !city.equals(RegionCatalog.canonicalCity(item.city))) continue;
            if (!product.equals(ALL_PRODUCTS) && !product.equals(productCategory(item))) continue;
            result.add(item);
        }
        return result;
    }

    static ArrayList<DrawItem> visible(ArrayList<DrawItem> items, String mode,
                                       Set<String> regions, Set<String> cities, Set<String> products) {
        ArrayList<DrawItem> result = new ArrayList<>();
        for (DrawItem item : items) {
            if (!modeMatches(item, mode)) continue;
            if (!regions.isEmpty() && !regions.contains(RegionCatalog.regionOf(item.city))) continue;
            if (!cities.isEmpty() && !cities.contains(RegionCatalog.canonicalCity(item.city))) continue;
            if (!products.isEmpty() && !products.contains(productCategory(item))) continue;
            result.add(item);
        }
        return result;
    }

    static ArrayList<DrawItem> queue(ArrayList<DrawItem> shown, String mode) {
        ArrayList<DrawItem> queue = new ArrayList<>();
        for (DrawItem item : shown) if (eligible(item) && item.selected) queue.add(item);
        return queue;
    }
}
