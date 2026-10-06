package tw.linedraw.quick;

import java.util.Collection;
import java.util.Set;

/** A region is only a batch action over cities, never a second active filter. */
final class CitySelection {
    private CitySelection() {}

    static int selectedInRegion(Set<String> selected, Collection<String> available, String region) {
        int count = 0;
        for (String city : available)
            if (RegionCatalog.regionOf(city).equals(region) && selected.contains(city)) count++;
        return count;
    }

    static int availableInRegion(Collection<String> available, String region) {
        int count = 0;
        for (String city : available) if (RegionCatalog.regionOf(city).equals(region)) count++;
        return count;
    }

    static void toggleRegion(Set<String> selected, Collection<String> available, String region) {
        int total = availableInRegion(available, region);
        boolean allSelected = total > 0 && selectedInRegion(selected, available, region) == total;
        for (String city : available) if (RegionCatalog.regionOf(city).equals(region)) {
            if (allSelected) selected.remove(city);
            else selected.add(city);
        }
    }
}
