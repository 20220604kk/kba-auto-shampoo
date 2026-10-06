package tw.linedraw.quick;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Collator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class StoreCatalog {
    static final class Choice {
        final String city;
        final String label;
        final String store;

        Choice(String city, String label, String store) {
            this.city = city;
            this.label = label;
            this.store = store;
        }

        @Override public String toString() { return label; }
    }

    private StoreCatalog() {}

    static ArrayList<Choice> load(Context context, ArrayList<DrawItem> knownItems) {
        ArrayList<Choice> choices = new ArrayList<>();
        try (InputStream input = context.getAssets().open("stores.json")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count);
            JSONArray array = new JSONArray(bytes.toString(StandardCharsets.UTF_8.name()));
            for (int i = 0; i < array.length(); i++) {
                JSONObject entry = array.getJSONObject(i);
                String city = RegionCatalog.canonicalCity(entry.optString("city"));
                String brand = entry.optString("brand").trim();
                String name = entry.optString("name").trim();
                if (city.isEmpty() || name.isEmpty()) continue;
                String label = brand.isEmpty() ? name : brand + " · " + name;
                String store = matchingStore(city, brand, name, knownItems);
                choices.add(new Choice(city, label, store == null ? (brand + " " + name).trim() : store));
            }
        } catch (Exception ignored) {
            Set<String> seen = new HashSet<>();
            for (DrawItem item : knownItems) {
                if (item.origin.equals("test")) continue;
                String city = RegionCatalog.canonicalCity(item.city);
                if (seen.add(city + "|" + item.store)) choices.add(new Choice(city, item.store, item.store));
            }
        }
        Collator names = Collator.getInstance(Locale.TAIWAN);
        choices.sort((a, b) -> {
            int first = cityRank(a.city) - cityRank(b.city);
            if (first != 0) return first;
            int city = names.compare(a.city, b.city);
            return city != 0 ? city : names.compare(a.label, b.label);
        });
        return choices;
    }

    private static int cityRank(String city) {
        int rank = RegionCatalog.CITIES.indexOf(city);
        return rank < 0 ? RegionCatalog.CITIES.size() : rank;
    }

    private static String matchingStore(String city, String brand, String name, ArrayList<DrawItem> items) {
        String target = compact(name);
        String targetBrand = compact(brand);
        for (DrawItem item : items) {
            if (item.origin.equals("test") || !RegionCatalog.canonicalCity(item.city).equals(city)) continue;
            String existing = compact(item.store);
            if (existing.contains(target) && (targetBrand.isEmpty() || existing.contains(targetBrand))) return item.store;
        }
        return null;
    }

    private static String compact(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[\\s·・._－-]", "");
    }
}
