package tw.linedraw.quick;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/** A small, deterministic summary of what changed during a weekly sync. */
final class SyncDiff {
    static final class Result {
        final int added;
        final int removed;
        final int timeChanged;
        final int unchanged;

        Result(int added, int removed, int timeChanged, int unchanged) {
            this.added = added;
            this.removed = removed;
            this.timeChanged = timeChanged;
            this.unchanged = unchanged;
        }

        String message() {
            return "新增 " + added + " 筆\n移除 " + removed + " 筆\n時間異動 " + timeChanged +
                    " 筆\n保留原狀 " + unchanged + " 筆";
        }
    }

    private SyncDiff() {}

    static Result compare(ArrayList<DrawItem> before, ArrayList<DrawItem> after) {
        Map<String, DrawItem> oldItems = sources(before);
        Map<String, DrawItem> newItems = sources(after);
        int added = 0;
        int removed = 0;
        int changed = 0;
        int unchanged = 0;
        for (Map.Entry<String, DrawItem> entry : newItems.entrySet()) {
            DrawItem old = oldItems.get(entry.getKey());
            if (old == null) added++;
            else if (!safe(old.period).equals(safe(entry.getValue().period))) changed++;
            else unchanged++;
        }
        for (String key : oldItems.keySet()) if (!newItems.containsKey(key)) removed++;
        return new Result(added, removed, changed, unchanged);
    }

    private static Map<String, DrawItem> sources(ArrayList<DrawItem> values) {
        Map<String, DrawItem> result = new HashMap<>();
        if (values == null) return result;
        for (DrawItem item : values)
            if (item != null && "source".equals(item.origin)) result.put(item.key(), item);
        return result;
    }

    private static String safe(String value) { return value == null ? "" : value.trim(); }
}
