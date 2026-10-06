package tw.linedraw.quick;

import java.util.ArrayList;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ManualPostParser {
    private static final Pattern URL = Pattern.compile("\\[[^\\]]*\\]\\((https://(?:lin\\.ee|line\\.me)/[^)\\s]+)\\)|(https://(?:lin\\.ee|line\\.me)/[^\\s)\\]]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRODUCT = Pattern.compile("((?:BXG|BX|UX|CX)-\\d{2}[^\\n]*?)(?=\\s*🔗|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DATE = Pattern.compile("20\\d{2}[/.\\-]\\d{1,2}[/.\\-]\\d{1,2}\\s+\\d{1,2}:\\d{2}");

    private ManualPostParser() {}

    static ArrayList<DrawItem> parse(String city, String store, String raw) {
        String text = raw.replace("\r", "");
        Matcher dates = DATE.matcher(text);
        String period = dates.find() ? dates.group() : "";
        if (dates.find()) period += "～" + dates.group();
        ArrayList<DrawItem> parsed = new ArrayList<>();
        String pendingProduct = "";
        for (String line : text.split("\n")) {
            Matcher links = URL.matcher(line);
            int cursor = 0;
            while (links.find()) {
                Matcher productMatch = PRODUCT.matcher(line.substring(cursor, links.start()));
                if (productMatch.find()) pendingProduct = productMatch.group(1).trim().replaceFirst("^[.．]+", "");
                String url = links.group(1) == null ? links.group(2) : links.group(1);
                url = url.replaceFirst("[.,，。！!]+$", "");
                if (!pendingProduct.isEmpty()) {
                    parsed.add(new DrawItem(city, store, pendingProduct, period, url, "manual"));
                    pendingProduct = "";
                }
                cursor = links.end();
            }
            Matcher trailing = PRODUCT.matcher(line.substring(cursor));
            if (trailing.find()) pendingProduct = trailing.group(1).trim().replaceFirst("^[.．]+", "");
        }
        return parsed;
    }

    static String identity(String city, String store, String url) {
        String normalizedStore = store.toLowerCase(Locale.ROOT)
                .replaceAll("門市|店|[\\s·・._－-]", "");
        return RegionCatalog.canonicalCity(city) + "|" + normalizedStore + "|" + url.trim();
    }
}
