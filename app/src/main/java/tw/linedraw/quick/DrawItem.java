package tw.linedraw.quick;

import org.json.JSONException;
import org.json.JSONObject;

final class DrawItem {
    String city;
    String store;
    String product;
    String period;
    String url;
    String origin;
    String status;
    String screenshot;
    long updatedAt;
    boolean selected;

    DrawItem(String city, String store, String product, String period, String url, String origin) {
        this.city = city;
        this.store = store;
        this.product = product;
        this.period = period;
        this.url = url;
        this.origin = origin;
        this.status = "pending";
        this.screenshot = "";
    }

    String key() { return store + "|" + url; }

    JSONObject toJson() throws JSONException {
        JSONObject value = new JSONObject();
        value.put("city", city);
        value.put("store", store);
        value.put("product", product);
        value.put("period", period);
        value.put("url", url);
        value.put("origin", origin);
        value.put("status", status);
        value.put("screenshot", screenshot);
        value.put("updatedAt", updatedAt);
        value.put("selected", selected);
        return value;
    }

    static DrawItem fromJson(JSONObject value) {
        DrawItem item = new DrawItem(value.optString("city"), value.optString("store"),
                value.optString("product"), value.optString("period"), value.optString("url"),
                value.optString("origin"));
        item.status = value.optString("status", "pending");
        item.screenshot = value.optString("screenshot");
        item.updatedAt = value.optLong("updatedAt");
        item.selected = value.optBoolean("selected");
        return item;
    }
}
