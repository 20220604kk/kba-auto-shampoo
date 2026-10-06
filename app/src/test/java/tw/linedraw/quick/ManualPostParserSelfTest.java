package tw.linedraw.quick;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public final class ManualPostParserSelfTest {
    private static void expect(int actual, int wanted, String label) {
        if (actual != wanted) throw new AssertionError(label + ": expected " + wanted + ", got " + actual);
    }

    public static void main(String[] args) {
        String post = "2026/09/24 11:00-\n2026/09/25 21:00\n" +
                ".UX-03 魔導神杖 售價295元\n🔗[https://lin.ee/splMMW0](https://lin.ee/splMMW0)\n" +
                ".UX-15 鮫鯊狂鱗 售價795元\n🔗[https://lin.ee/Xs958LQ](https://lin.ee/Xs958LQ)\n" +
                ".UX-00 新世紀福音戰士改造組 售價1395元\n🔗[https://lin.ee/ptK8LLe](https://lin.ee/ptK8LLe)\n" +
                ".BX-37雙重極限衝擊戰鬥盤豪華組 售價2750元\n🔗[https://lin.ee/W00QvT8](https://lin.ee/W00QvT8)\n" +
                ".CX-11 帝王威能 售價995元\n🔗[https://lin.ee/ZBXvSX3](https://lin.ee/ZBXvSX3)\n" +
                ".BX-09 戰鬥陀螺X通行證 售價995元\n🔗[https://lin.ee/sIAraz3](https://lin.ee/sIAraz3) " +
                ".CX-19 鱷魚裂甲 售價350元 🔗[https://lin.ee/YQO68w0a](https://lin.ee/YQO68w0a)";
        ArrayList<DrawItem> parsed = ManualPostParser.parse("桃園市", "Funbox 桃園店", post);
        expect(parsed.size(), 7, "seven pasted products");
        if (!parsed.get(6).product.startsWith("CX-19")) throw new AssertionError("same-line final product");
        if (!parsed.get(0).period.contains("2026/09/25 21:00")) throw new AssertionError("period");
        String first = ManualPostParser.identity("桃園市", "Funbox 桃園店", parsed.get(0).url);
        String duplicate = ManualPostParser.identity("桃園市", "Funbox 桃園", parsed.get(0).url);
        if (!first.equals(duplicate)) throw new AssertionError("same-store duplicate not normalized");
        Set<String> known = new HashSet<>();
        known.add(first);
        int novel = 0;
        for (DrawItem item : parsed)
            if (known.add(ManualPostParser.identity(item.city, item.store, item.url))) novel++;
        expect(novel, 6, "later post adds only new links");
        System.out.println("ManualPostParserSelfTest passed");
    }
}
