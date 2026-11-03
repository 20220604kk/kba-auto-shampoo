package tw.linedraw.quick;

import android.content.Context;
import android.content.SharedPreferences;
import android.app.ActivityManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DrawData {
    static final String SOURCE_URL = "https://uxux11.github.io/funbox-line/";
    private static final String PREFS = "linedraw-data-v1";
    private static final String LAST_SYNC_REQUEST = "last-sync-request";
    private static final String REVIEW_REPORT = "review-report";
    private static final String RUN_REPORT = "run-report";
    private static final String RUN_REPORT_TEST = "run-report-test";
    private static final String REVIEW_REPORT_HISTORY = "review-report-history";
    private static final String TEST_REPORT_HISTORY = "test-report-history";
    private static final String REVIEW_OUTCOMES = "review-outcomes";
    private static final String MANUAL_OPEN_KEY = "manual-open-key";
    private static final String FILTER_FAVORITE = "filter-favorite";
    private static final String FILTER_FAVORITES = "filter-favorites-v2";
    private static final String SOURCE_WEEK_KEY = "source-week-key";
    private static final String ITEM_OVERRIDE_PREFIX = "item-override:";
    private static final String REMOVED_TEST_URLS = "removed-test-urls";
    private static final String DATA_MIGRATION = "data-migration";
    private static final String STATUS_REVISION = "status-revision";
    private static final Pattern TEST_LINK_PATTERN = Pattern.compile("https://(?:lin\\.ee|line\\.me)/[^\\s)\\]>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CUSTOM_TEST_NUMBER = Pattern.compile("自訂測試 (\\d+)");
    private static final Pattern PERIOD_DATE = Pattern.compile("(20\\d{2})[/-](\\d{1,2})[/-](\\d{1,2})");

    private DrawData() {}

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static synchronized ArrayList<DrawItem> load(Context context) {
        ArrayList<DrawItem> items = new ArrayList<>();
        SharedPreferences store = prefs(context);
        Set<String> removedTests = new HashSet<>(store.getStringSet(REMOVED_TEST_URLS, new HashSet<>()));
        try {
            JSONArray array = new JSONArray(store.getString("items", "[]"));
            for (int i = 0; i < array.length(); i++) {
                DrawItem item = DrawItem.fromJson(array.getJSONObject(i));
                applyOverride(store, item);
                migrateTestBrand(item);
                if (!RunScope.eligible(item)) item.selected = false;
                if (!item.origin.equals("test") || !removedTests.contains(item.url)) items.add(item);
            }
        } catch (JSONException ignored) {}
        TestCatalog.addDefaults(items, removedTests);
        return items;
    }

    static synchronized void save(Context context, ArrayList<DrawItem> items) {
        SharedPreferences store = prefs(context);
        JSONArray array = new JSONArray();
        for (DrawItem item : items) {
            applyOverride(store, item);
            try { array.put(item.toJson()); } catch (JSONException ignored) {}
        }
        SharedPreferences.Editor edit = store.edit().putString("items", array.toString());
        // The full snapshot now contains every override; avoid accumulating old per-item keys.
        for (String key : store.getAll().keySet())
            if (key.startsWith(ITEM_OVERRIDE_PREFIX)) edit.remove(key);
        edit.apply();
    }

    private static void applyOverride(SharedPreferences store, DrawItem item) {
        String override = store.getString(ITEM_OVERRIDE_PREFIX + item.key(), "");
        if (override.isEmpty()) return;
        try {
            JSONObject state = new JSONObject(override);
            item.status = state.optString("status", item.status);
            item.screenshot = state.optString("screenshot", item.screenshot);
            item.updatedAt = state.optLong("updatedAt", item.updatedAt);
            item.selected = state.optBoolean("selected", item.selected);
            if (!RunScope.eligible(item)) item.selected = false;
        } catch (JSONException ignored) {}
    }

    static synchronized void updateItem(Context context, DrawItem changed) {
        try {
            JSONObject state = new JSONObject();
            state.put("status", changed.status);
            state.put("screenshot", changed.screenshot);
            state.put("updatedAt", System.currentTimeMillis());
            state.put("selected", RunScope.eligible(changed) && changed.selected);
            SharedPreferences store = prefs(context);
            long revision = Math.max(System.currentTimeMillis(), store.getLong(STATUS_REVISION, 0L) + 1L);
            store.edit().putString(ITEM_OVERRIDE_PREFIX + changed.key(), state.toString())
                    .putLong(STATUS_REVISION, revision).apply();
        } catch (JSONException ignored) {}
    }

    /** Changes written by the accessibility service must invalidate the Activity's in-memory list. */
    static long statusRevision(Context context) {
        return prefs(context).getLong(STATUS_REVISION, 0L);
    }

    static synchronized void clearSelections(Context context) {
        ArrayList<DrawItem> items = load(context);
        boolean changed = false;
        for (DrawItem item : items) {
            if (!item.selected) continue;
            item.selected = false;
            changed = true;
        }
        if (changed) save(context, items);
    }

    static final class FilterFavorite {
        final String name;
        final ArrayList<String> cities;
        final ArrayList<String> products;
        final boolean cityFirst;
        final String time;

        FilterFavorite(String name, ArrayList<String> cities, ArrayList<String> products,
                       boolean cityFirst, String time) {
            this.name = name == null || name.trim().isEmpty() ? "我的喜好" : name.trim();
            this.cities = cities;
            this.products = products;
            this.cityFirst = cityFirst;
            this.time = time == null ? "" : time;
        }
    }

    static synchronized void saveFilterFavorite(Context context, Set<String> cities,
                                                Set<String> products, boolean cityFirst,
                                                String time) {
        saveFilterFavorite(context, "我的喜好", cities, products, cityFirst, time);
    }

    static synchronized boolean saveFilterFavorite(Context context, String name, Set<String> cities,
                                                    Set<String> products, boolean cityFirst,
                                                    String time) {
        try {
            JSONObject value = new JSONObject();
            value.put("name", name == null || name.trim().isEmpty() ? "我的喜好" : name.trim());
            value.put("cities", new JSONArray(cities));
            value.put("products", new JSONArray(products));
            value.put("cityFirst", cityFirst);
            value.put("time", time == null ? "" : time);
            JSONArray saved = favoritesArray(context);
            String wanted = value.getString("name");
            int replace = -1;
            for (int i = 0; i < saved.length(); i++) {
                JSONObject previous = saved.optJSONObject(i);
                if (previous != null && wanted.equalsIgnoreCase(previous.optString("name"))) {
                    replace = i;
                    break;
                }
            }
            if (replace >= 0) saved.put(replace, value);
            else {
                if (saved.length() >= 3) return false;
                saved.put(value);
            }
            prefs(context).edit().putString(FILTER_FAVORITES, saved.toString())
                    .putString(FILTER_FAVORITE, value.toString()).apply();
            return true;
        } catch (JSONException ignored) { return false; }
    }

    static synchronized FilterFavorite loadFilterFavorite(Context context) {
        ArrayList<FilterFavorite> values = loadFilterFavorites(context);
        return values.isEmpty() ? null : values.get(0);
    }

    static synchronized ArrayList<FilterFavorite> loadFilterFavorites(Context context) {
        ArrayList<FilterFavorite> result = new ArrayList<>();
        try {
            JSONArray values = favoritesArray(context);
            for (int i = 0; i < Math.min(3, values.length()); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                result.add(new FilterFavorite(value.optString("name", "喜好 " + (i + 1)),
                        strings(value.optJSONArray("cities")), strings(value.optJSONArray("products")),
                        value.optBoolean("cityFirst", true), value.optString("time", "")));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private static JSONArray favoritesArray(Context context) {
        SharedPreferences store = prefs(context);
        String raw = store.getString(FILTER_FAVORITES, "");
        try { if (!raw.isEmpty()) return new JSONArray(raw); } catch (JSONException ignored) {}
        String legacy = store.getString(FILTER_FAVORITE, "");
        JSONArray result = new JSONArray();
        if (!legacy.isEmpty()) try {
            JSONObject value = new JSONObject(legacy);
            if (!value.has("name")) value.put("name", "我的喜好");
            result.put(value);
        } catch (JSONException ignored) {}
        return result;
    }

    static synchronized void removeFilterFavorite(Context context, int index) {
        JSONArray values = favoritesArray(context);
        JSONArray remaining = new JSONArray();
        for (int i = 0; i < values.length(); i++) if (i != index) remaining.put(values.opt(i));
        prefs(context).edit().putString(FILTER_FAVORITES, remaining.toString())
                .remove(FILTER_FAVORITE).apply();
    }

    static synchronized void clearFilterFavorite(Context context) {
        prefs(context).edit().remove(FILTER_FAVORITE).remove(FILTER_FAVORITES).apply();
    }

    private static ArrayList<String> strings(JSONArray source) {
        ArrayList<String> values = new ArrayList<>();
        if (source == null) return values;
        for (int i = 0; i < source.length(); i++) {
            String value = source.optString(i, "");
            if (!value.isEmpty() && !values.contains(value)) values.add(value);
        }
        return values;
    }

    static void saveReviewReport(Context context, String report) {
        if (report == null || report.trim().isEmpty()) return;
        JSONArray history = new JSONArray();
        try {
            JSONArray previous = new JSONArray(prefs(context).getString(REVIEW_REPORT_HISTORY, "[]"));
            JSONObject newest = new JSONObject();
            newest.put("at", System.currentTimeMillis());
            newest.put("text", report);
            history.put(newest);
            for (int i = 0; i < Math.min(2, previous.length()); i++) history.put(previous.getJSONObject(i));
        } catch (JSONException ignored) {
            try {
                JSONObject newest = new JSONObject();
                newest.put("at", System.currentTimeMillis());
                newest.put("text", report);
                history.put(newest);
            } catch (JSONException impossible) {}
        }
        prefs(context).edit().putString(REVIEW_REPORT, report)
                .putString(REVIEW_REPORT_HISTORY, history.toString()).apply();
    }

    static String takeReviewReport(Context context) {
        SharedPreferences store = prefs(context);
        String report = store.getString(REVIEW_REPORT, "");
        if (!report.isEmpty()) store.edit().remove(REVIEW_REPORT).apply();
        return report;
    }

    static void saveRunReport(Context context, String report) {
        prefs(context).edit().putString(RUN_REPORT, report == null ? "" : report).apply();
    }

    static void saveRunReportType(Context context, boolean testRun) {
        prefs(context).edit().putBoolean(RUN_REPORT_TEST, testRun).apply();
    }

    static String takeRunReport(Context context) {
        SharedPreferences store = prefs(context);
        String report = store.getString(RUN_REPORT, "");
        if (!report.isEmpty()) store.edit().remove(RUN_REPORT).apply();
        return report;
    }

    static boolean takeRunReportWasTest(Context context) {
        SharedPreferences store = prefs(context);
        boolean value = store.getBoolean(RUN_REPORT_TEST, false);
        store.edit().remove(RUN_REPORT_TEST).apply();
        return value;
    }

    static final class ReviewReportEntry {
        final long at;
        final String text;
        ReviewReportEntry(long at, String text) { this.at = at; this.text = text; }
    }

    static ArrayList<ReviewReportEntry> reviewReports(Context context) {
        ArrayList<ReviewReportEntry> reports = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(REVIEW_REPORT_HISTORY, "[]"));
            for (int i = 0; i < Math.min(3, array.length()); i++) {
                JSONObject value = array.getJSONObject(i);
                String text = value.optString("text");
                if (!text.isEmpty()) reports.add(new ReviewReportEntry(value.optLong("at"), text));
            }
        } catch (JSONException ignored) {}
        return reports;
    }

    static void saveTestReport(Context context, String report) {
        saveReportHistory(context, TEST_REPORT_HISTORY, report);
    }

    static ArrayList<ReviewReportEntry> testReports(Context context) {
        return reportHistory(context, TEST_REPORT_HISTORY);
    }

    private static void saveReportHistory(Context context, String key, String report) {
        if (report == null || report.trim().isEmpty()) return;
        JSONArray history = new JSONArray();
        try {
            JSONArray previous = new JSONArray(prefs(context).getString(key, "[]"));
            JSONObject newest = new JSONObject();
            newest.put("at", System.currentTimeMillis());
            newest.put("text", report);
            history.put(newest);
            for (int i = 0; i < Math.min(2, previous.length()); i++) history.put(previous.getJSONObject(i));
        } catch (JSONException ignored) {}
        prefs(context).edit().putString(key, history.toString()).apply();
    }

    private static ArrayList<ReviewReportEntry> reportHistory(Context context, String key) {
        ArrayList<ReviewReportEntry> reports = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(prefs(context).getString(key, "[]"));
            for (int i = 0; i < Math.min(3, array.length()); i++) {
                JSONObject value = array.getJSONObject(i);
                String text = value.optString("text");
                if (!text.isEmpty()) reports.add(new ReviewReportEntry(value.optLong("at"), text));
            }
        } catch (JSONException ignored) {}
        return reports;
    }

    static synchronized void saveReviewOutcomes(Context context, Map<String, String> outcomes) {
        JSONObject object = new JSONObject();
        for (Map.Entry<String, String> entry : outcomes.entrySet()) {
            try { object.put(entry.getKey(), entry.getValue()); } catch (JSONException ignored) {}
        }
        prefs(context).edit().putString(REVIEW_OUTCOMES, object.toString()).apply();
    }

    static synchronized Map<String, String> loadReviewOutcomes(Context context) {
        Map<String, String> result = new HashMap<>();
        try {
            JSONObject object = new JSONObject(prefs(context).getString(REVIEW_OUTCOMES, "{}"));
            java.util.Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                result.put(key, object.optString(key));
            }
        } catch (JSONException ignored) {}
        return result;
    }

    static void markManualOpen(Context context, DrawItem item) {
        prefs(context).edit().putString(MANUAL_OPEN_KEY, item.key()).apply();
    }

    static String takeManualOpen(Context context) {
        SharedPreferences store = prefs(context);
        String key = store.getString(MANUAL_OPEN_KEY, "");
        if (!key.isEmpty()) store.edit().remove(MANUAL_OPEN_KEY).apply();
        return key;
    }

    static ArrayList<DrawItem> refresh(Context context) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(SOURCE_URL + "?refresh=" + System.currentTimeMillis()).openConnection();
        connection.setConnectTimeout(12000);
        connection.setReadTimeout(12000);
        connection.setRequestProperty("User-Agent", "LineDraw/0.1 Android");
        try {
            if (connection.getResponseCode() != 200) throw new IOException("來源網站 HTTP " + connection.getResponseCode());
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = connection.getInputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count);
            }
            String html = bytes.toString(StandardCharsets.UTF_8.name());
            Document document = Jsoup.parse(html);
            ArrayList<DrawItem> previous = load(context);
            Map<String, DrawItem> oldByKey = new HashMap<>();
            for (DrawItem item : previous) oldByKey.put(item.key(), item);
            ArrayList<DrawItem> fresh = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            int storeCount = 0;
            for (Element store : document.select("#page-draws .draw-store")) {
                String city = store.attr("data-draw-city").trim();
                String name = store.selectFirst(".draw-store-name") == null ? "" : store.selectFirst(".draw-store-name").text().trim();
                String period = store.selectFirst(".draw-start") == null ? "" : store.selectFirst(".draw-start").text().trim();
                if (name.isEmpty()) continue;
                storeCount++;
                for (Element row : store.select("[data-draw-href]")) {
                    String url = row.attr("data-draw-href").trim();
                    Element productNode = row.selectFirst(".draw-product");
                    String product = productNode == null ? "" : productNode.text().trim();
                    if (!validLink(url) || product.isEmpty()) continue;
                    DrawItem item = new DrawItem(city, name, product, period, url, "source");
                    if (!seen.add(item.key())) continue;
                    preserve(item, oldByKey.get(item.key()));
                    fresh.add(item);
                }
            }
            if (storeCount == 0) throw new IOException("來源網站沒有可辨識的抽選清單");
            for (DrawItem old : previous) {
                if ((old.origin.equals("manual") || old.origin.equals("test")) && seen.add(old.key())) fresh.add(old);
            }
            TestCatalog.addDefaults(fresh, new HashSet<>(prefs(context).getStringSet(REMOVED_TEST_URLS, new HashSet<>())));
            String previousWeek = prefs(context).getString(SOURCE_WEEK_KEY, "");
            String currentWeek = sourceWeekKey(fresh);
            if (!previousWeek.isEmpty() && !currentWeek.isEmpty() && !previousWeek.equals(currentWeek))
                clearFilterFavorite(context);
            save(context, fresh);
            String updated = document.selectFirst(".draw-last-updated") == null ? "" : document.selectFirst(".draw-last-updated").text().trim();
            prefs(context).edit().putLong("refreshedAt", System.currentTimeMillis())
                    .putString("sourceUpdated", updated).putInt("sourceStores", storeCount)
                    .putString(SOURCE_WEEK_KEY, currentWeek).apply();
            return fresh;
        } finally { connection.disconnect(); }
    }

    static synchronized int addManual(Context context, String city, String store, String raw) {
        ArrayList<DrawItem> items = load(context);
        Set<String> seen = new HashSet<>();
        for (DrawItem item : items) seen.add(ManualPostParser.identity(item.city, item.store, item.url));
        int added = 0;
        for (DrawItem item : ManualPostParser.parse(city, store, raw)) {
            if (validLink(item.url) && seen.add(ManualPostParser.identity(item.city, item.store, item.url))) {
                items.add(item);
                added++;
            }
        }
        if (added > 0) save(context, items);
        return added;
    }

    static synchronized int addTestLinks(Context context, String raw) {
        ArrayList<DrawItem> items = load(context);
        Set<String> removedTests = new HashSet<>(prefs(context).getStringSet(REMOVED_TEST_URLS, new HashSet<>()));
        Set<String> existingUrls = new HashSet<>();
        int nextNumber = 1;
        for (DrawItem item : items) {
            existingUrls.add(item.url);
            if (!item.origin.equals("test")) continue;
            Matcher number = CUSTOM_TEST_NUMBER.matcher(item.product);
            if (number.matches()) nextNumber = Math.max(nextNumber, Integer.parseInt(number.group(1)) + 1);
        }
        Matcher links = TEST_LINK_PATTERN.matcher(raw);
        int added = 0;
        while (links.find()) {
            String url = links.group().replaceFirst("[.,，。！!]+$", "");
            if (!validLink(url) || !existingUrls.add(url)) continue;
            items.add(new DrawItem("測試", "K爸的官方帳號", "自訂測試 " + nextNumber++, "", url, "test"));
            removedTests.remove(url);
            added++;
        }
        if (added > 0) {
            prefs(context).edit().putStringSet(REMOVED_TEST_URLS, removedTests).apply();
            save(context, items);
        }
        return added;
    }

    static synchronized boolean removeTest(Context context, String url) {
        ArrayList<DrawItem> items = load(context);
        boolean found = TestCatalog.removeByUrl(items, url);
        if (!found) return false;
        Set<String> removedTests = new HashSet<>(prefs(context).getStringSet(REMOVED_TEST_URLS, new HashSet<>()));
        removedTests.add(url);
        prefs(context).edit().putStringSet(REMOVED_TEST_URLS, removedTests).apply();
        save(context, items);
        return true;
    }

    static synchronized int clearWeeklyData(Context context) {
        ArrayList<DrawItem> all = load(context);
        ArrayList<DrawItem> keep = new ArrayList<>();
        int removed = 0;
        for (DrawItem item : all) {
            if (item.origin.equals("test") && !isLegacyOfficialTest(item)) {
                migrateTestBrand(item);
                item.status = "pending";
                item.selected = false;
                item.screenshot = "";
                item.updatedAt = 0;
                keep.add(item);
            }
            else removed++;
        }
        save(context, keep);
        prefs(context).edit()
                .remove("refreshedAt")
                .remove("sourceUpdated")
                .remove("sourceStores")
                .remove(LAST_SYNC_REQUEST)
                .remove(REVIEW_REPORT)
                .remove(REVIEW_REPORT_HISTORY)
                .remove(REVIEW_OUTCOMES)
                .remove(MANUAL_OPEN_KEY)
                .remove(TEST_REPORT_HISTORY)
                .remove(RUN_REPORT)
                .remove(RUN_REPORT_TEST)
                .remove(SOURCE_WEEK_KEY)
                .apply();
        return removed;
    }

    static synchronized boolean applyV041Reset(Context context) {
        SharedPreferences store = prefs(context);
        if (store.getInt(DATA_MIGRATION, 0) >= 41) return false;
        boolean hadPreviousData = store.contains("items") || store.contains("refreshedAt") || store.contains(SOURCE_WEEK_KEY);
        // v0.4.1 intentionally starts from a clean weekly state. Restore the new
        // built-in eight test links, while ordinary timing and device settings remain.
        store.edit().remove(REMOVED_TEST_URLS).apply();
        clearWeeklyData(context);
        ArrayList<DrawItem> clean = load(context);
        save(context, clean);
        store.edit().putInt(DATA_MIGRATION, 41).apply();
        return hadPreviousData;
    }

    private static boolean isLegacyOfficialTest(DrawItem item) {
        if (item == null || !"test".equals(item.origin)) return false;
        if (item.product != null && item.product.matches("(?:方爸|K爸)測試 [1-8]")) return true;
        String[] oldTokens = {"zY5OJZr", "t5QXmAN", "59RjUOS", "WCq14S5", "OvKQcaGy", "NGL540W", "xO9HJta", "o2KhHT8"};
        for (String token : oldTokens) if (item.url != null && item.url.startsWith("https://lin.ee/" + token)) return true;
        return false;
    }

    private static void migrateTestBrand(DrawItem item) {
        if (!item.origin.equals("test")) return;
        if (item.store.equals("方爸測試")) item.store = "K爸的官方帳號";
        if (item.product.startsWith("方爸測試 "))
            item.product = "K爸測試 " + item.product.substring("方爸測試 ".length());
    }

    private static void preserve(DrawItem fresh, DrawItem old) {
        if (old == null) return;
        fresh.status = old.status;
        fresh.screenshot = old.screenshot;
        fresh.updatedAt = old.updatedAt;
        fresh.selected = old.selected;
    }

    private static String sourceWeekKey(ArrayList<DrawItem> items) {
        Map<String, Integer> counts = new HashMap<>();
        for (DrawItem item : items) {
            if (!item.origin.equals("source")) continue;
            Matcher match = PERIOD_DATE.matcher(item.period == null ? "" : item.period);
            if (!match.find()) continue;
            String date = String.format(java.util.Locale.ROOT, "%s-%02d-%02d", match.group(1),
                    Integer.parseInt(match.group(2)), Integer.parseInt(match.group(3)));
            counts.put(date, counts.getOrDefault(date, 0) + 1);
        }
        String selected = "";
        int best = -1;
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > best || (entry.getValue() == best && entry.getKey().compareTo(selected) < 0)) {
                selected = entry.getKey();
                best = entry.getValue();
            }
        }
        return selected;
    }

    static boolean validLink(String raw) {
        return raw.startsWith("https://lin.ee/") || raw.startsWith("https://line.me/");
    }

    static long refreshedAt(Context context) { return prefs(context).getLong("refreshedAt", 0); }
    static String sourceUpdated(Context context) { return prefs(context).getString("sourceUpdated", ""); }
    static int sourceStores(Context context) { return prefs(context).getInt("sourceStores", 0); }
    static long lastSyncRequest(Context context) { return prefs(context).getLong(LAST_SYNC_REQUEST, 0); }
    static void markSyncRequest(Context context, long at) { prefs(context).edit().putLong(LAST_SYNC_REQUEST, at).apply(); }
    static float delay(Context context) { return prefs(context).getFloat("delay", .8f); }
    static void setDelay(Context context, float seconds) { prefs(context).edit().putFloat("delay", Math.max(.1f, Math.min(10f, seconds))).apply(); }
    static float closeDelay(Context context) { return prefs(context).getFloat("close-delay", .5f); }
    static void setCloseDelay(Context context, float seconds) {
        prefs(context).edit().putFloat("close-delay", Math.max(.1f, Math.min(30f, seconds))).apply();
    }
    static float errorDelay(Context context) { return prefs(context).getFloat("error-delay", 8f); }
    static void setErrorDelay(Context context, float seconds) {
        prefs(context).edit().putFloat("error-delay", Math.max(3f, Math.min(30f, seconds))).apply();
    }
    static boolean onboardingSeen(Context context) { return prefs(context).getBoolean("onboarding-seen", false); }
    static void setOnboardingSeen(Context context) { prefs(context).edit().putBoolean("onboarding-seen", true).apply(); }
    static boolean lowMemoryMode(Context context) {
        SharedPreferences store = prefs(context);
        if (store.contains("low-memory-mode")) return store.getBoolean("low-memory-mode", false);
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        return manager != null && (manager.isLowRamDevice() || manager.getMemoryClass() <= 192);
    }
    static void setLowMemoryMode(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("low-memory-mode", enabled).apply();
    }
    static boolean fastHandoff(Context context) { return prefs(context).getBoolean("fast-handoff", true); }
    static void setFastHandoff(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("fast-handoff", enabled).apply();
    }

    static String speedMode(Context context) { return prefs(context).getString("speed-mode", "balanced"); }

    static void setSpeedModeName(Context context, String mode) {
        prefs(context).edit().putString("speed-mode", mode == null ? "balanced" : mode).apply();
    }

    static void applySpeedMode(Context context, String mode) {
        SharedPreferences.Editor edit = prefs(context).edit();
        if ("fast".equals(mode)) edit.putFloat("close-delay", .3f).putFloat("delay", .3f)
                .putFloat("error-delay", 6f).putBoolean("fast-handoff", true);
        else if ("compatible".equals(mode)) edit.putFloat("close-delay", 1.4f).putFloat("delay", 2f)
                .putFloat("error-delay", 15f).putBoolean("fast-handoff", false);
        else {
            mode = "balanced";
            edit.putFloat("close-delay", .5f).putFloat("delay", .8f)
                    .putFloat("error-delay", 8f).putBoolean("fast-handoff", true);
        }
        edit.putString("speed-mode", mode).apply();
    }
}
