package tw.linedraw.quick;

import java.util.Arrays;
import java.util.List;

final class RegionCatalog {
    static final String ALL = "全台";
    static final String ALL_CITIES = "全部縣市";
    static final List<String> REGIONS = Arrays.asList(ALL, "北部", "中部", "南部", "東部", "離島");
    static final List<String> CITIES = Arrays.asList(
            "基隆市", "台北市", "新北市", "桃園市", "新竹市", "新竹縣", "宜蘭縣",
            "苗栗縣", "台中市", "彰化縣", "南投縣", "雲林縣",
            "嘉義市", "嘉義縣", "台南市", "高雄市", "屏東縣",
            "花蓮縣", "台東縣", "澎湖縣", "金門縣", "連江縣");

    private RegionCatalog() {}

    static String canonicalCity(String city) {
        return city == null ? "" : city.trim().replace('臺', '台');
    }

    static String regionOf(String city) {
        String name = canonicalCity(city);
        int index = CITIES.indexOf(name);
        if (index < 0) return "其他";
        if (index < 7) return "北部";
        if (index < 12) return "中部";
        if (index < 17) return "南部";
        if (index < 19) return "東部";
        return "離島";
    }
}
