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
        if (!match.find()) return item.product;
        String code = match.group(1).toUpperCase(Locale.ROOT) + "-" + match.group(2);
        // CX-00 currently contains two unrelated products. Treating the model code as
        // the category would make selecting one silently include the other.
        if (code.equals("CX-00")) {
            String compact = item.product.replaceAll("\\s+", "");
            if (compact.contains("迪卡狂怒")) return "CX-00 迪卡狂怒";
            if (compact.contains("新世紀福音戰士")) return "CX-00 新世紀福音戰士改造組";
        }
        return code;
    }

    static boolean eligible(DrawItem item) {
        return item.status.equals("pending") || item.status.equals("scheduled") ||
                item.status.equals("not_friend") || item.status.equals("busy") ||
                item.status.equals("error");
    }

    // Every item shown in the draw tab is actionable. A store or current-scope
    // selection must never silently omit the retryable items the user can see.
    static boolean bulkSelectable(DrawItem item) {
        return eligible(item);
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
