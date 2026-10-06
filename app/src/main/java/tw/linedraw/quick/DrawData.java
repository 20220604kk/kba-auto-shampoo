package tw.linedraw.quick;

import android.content.Context;
import android.content.SharedPreferences;

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
    private static final String REVIEW_REPORT_HISTORY = "review-report-history";
    private static final String TEST_REPORT_HISTORY = "test-report-history";
    private static final String REVIEW_OUTCOMES = "review-outcomes";
    private static final String MANUAL_OPEN_KEY = "manual-open-key";
    private static final String ITEM_OVERRIDE_PREFIX = "item-override:";
    private static final String REMOVED_TEST_URLS = "removed-test-urls";
    private static final Pattern TEST_LINK_PATTERN = Pattern.compile("https://(?:lin\\.ee|line\\.me)/[^\\s)\\]>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern CUSTOM_TEST_NUMBER = Pattern.compile("自訂測試 (\\d+)");

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
            prefs(context).edit().putString(ITEM_OVERRIDE_PREFIX + changed.key(), state.toString()).apply();
        } catch (JSONException ignored) {}
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

    static String takeRunReport(Context context) {
        SharedPreferences store = prefs(context);
        String report = store.getString(RUN_REPORT, "");
        if (!report.isEmpty()) store.edit().remove(RUN_REPORT).apply();
        return report;
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
            save(context, fresh);
            String updated = document.selectFirst(".draw-last-updated") == null ? "" : document.selectFirst(".draw-last-updated").text().trim();
            prefs(context).edit().putLong("refreshedAt", System.currentTimeMillis()).putString("sourceUpdated", updated).putInt("sourceStores", storeCount).apply();
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
            items.add(new DrawItem("測試", "方爸測試", "自訂測試 " + nextNumber++, "", url, "test"));
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
            if (item.origin.equals("test")) keep.add(item);
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
                .apply();
        return removed;
    }

    private static void preserve(DrawItem fresh, DrawItem old) {
        if (old == null) return;
        fresh.status = old.status;
        fresh.screenshot = old.screenshot;
        fresh.updatedAt = old.updatedAt;
        fresh.selected = old.selected;
    }

    static boolean validLink(String raw) {
        return raw.startsWith("https://lin.ee/") || raw.startsWith("https://line.me/");
    }

    static long refreshedAt(Context context) { return prefs(context).getLong("refreshedAt", 0); }
    static String sourceUpdated(Context context) { return prefs(context).getString("sourceUpdated", ""); }
    static int sourceStores(Context context) { return prefs(context).getInt("sourceStores", 0); }
    static long lastSyncRequest(Context context) { return prefs(context).getLong(LAST_SYNC_REQUEST, 0); }
    static void markSyncRequest(Context context, long at) { prefs(context).edit().putLong(LAST_SYNC_REQUEST, at).apply(); }
    static float delay(Context context) { return prefs(context).getFloat("delay", 2f); }
    static void setDelay(Context context, float seconds) { prefs(context).edit().putFloat("delay", Math.max(.3f, Math.min(10f, seconds))).apply(); }
    static float closeDelay(Context context) { return prefs(context).getFloat("close-delay", .5f); }
    static void setCloseDelay(Context context, float seconds) {
        prefs(context).edit().putFloat("close-delay", Math.max(.3f, Math.min(30f, seconds))).apply();
    }
    static boolean onboardingSeen(Context context) { return prefs(context).getBoolean("onboarding-seen", false); }
    static void setOnboardingSeen(Context context) { prefs(context).edit().putBoolean("onboarding-seen", true).apply(); }
    static boolean lowMemoryMode(Context context) { return prefs(context).getBoolean("low-memory-mode", false); }
    static void setLowMemoryMode(Context context, boolean enabled) {
        prefs(context).edit().putBoolean("low-memory-mode", enabled).apply();
    }
}
