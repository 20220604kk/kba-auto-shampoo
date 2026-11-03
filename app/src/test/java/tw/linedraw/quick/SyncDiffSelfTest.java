package tw.linedraw.quick;

import java.util.ArrayList;

public final class SyncDiffSelfTest {
    private static DrawItem item(String store, String url, String period) {
        return new DrawItem("台北市", store, "UX-03", period, url, "source");
    }

    public static void main(String[] args) {
        ArrayList<DrawItem> before = new ArrayList<>();
        before.add(item("A", "https://lin.ee/a", "10:00"));
        before.add(item("B", "https://lin.ee/b", "10:30"));
        before.add(item("C", "https://lin.ee/c", "11:00"));
        ArrayList<DrawItem> after = new ArrayList<>();
        after.add(item("A", "https://lin.ee/a", "10:00"));
        after.add(item("B", "https://lin.ee/b", "11:00"));
        after.add(item("D", "https://lin.ee/d", "12:00"));
        SyncDiff.Result diff = SyncDiff.compare(before, after);
        if (diff.added != 1 || diff.removed != 1 || diff.timeChanged != 1 || diff.unchanged != 1)
            throw new AssertionError(diff.message());
        System.out.println("SyncDiffSelfTest passed");
    }
}
