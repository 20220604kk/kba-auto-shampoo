package tw.linedraw.quick;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Locale;

final class StoreGroups {
    static final class CityGroup {
        final String city;
        final ArrayList<StoreGroup> stores = new ArrayList<>();

        CityGroup(String city) { this.city = city; }
    }

    static final class StoreGroup {
        final String name;
        final ArrayList<DrawItem> items = new ArrayList<>();

        StoreGroup(String name) { this.name = name; }
    }

    private StoreGroups() {}

    static ArrayList<DrawItem> ordered(ArrayList<DrawItem> items) {
        ArrayList<DrawItem> result = new ArrayList<>(items);
        Collator names = Collator.getInstance(Locale.TAIWAN);
        result.sort((left, right) -> {
            String leftCity = RegionCatalog.canonicalCity(left.city);
            String rightCity = RegionCatalog.canonicalCity(right.city);
            int leftRank = RegionCatalog.CITIES.indexOf(leftCity);
            int rightRank = RegionCatalog.CITIES.indexOf(rightCity);
            int rank = Integer.compare(leftRank < 0 ? Integer.MAX_VALUE : leftRank,
                    rightRank < 0 ? Integer.MAX_VALUE : rightRank);
            if (rank != 0) return rank;
            int city = names.compare(leftCity, rightCity);
            if (city != 0) return city;
            int store = names.compare(left.store, right.store);
            return store != 0 ? store : left.store.compareTo(right.store);
        });
        return result;
    }

    static ArrayList<CityGroup> group(ArrayList<DrawItem> orderedItems) {
        ArrayList<CityGroup> cities = new ArrayList<>();
        CityGroup currentCity = null;
        StoreGroup currentStore = null;
        for (DrawItem item : orderedItems) {
            String city = RegionCatalog.canonicalCity(item.city);
            if (currentCity == null || !currentCity.city.equals(city)) {
                currentCity = new CityGroup(city);
                cities.add(currentCity);
                currentStore = null;
            }
            if (currentStore == null || !currentStore.name.equals(item.store)) {
                currentStore = new StoreGroup(item.store);
                currentCity.stores.add(currentStore);
            }
            currentStore.items.add(item);
        }
        return cities;
    }
}
