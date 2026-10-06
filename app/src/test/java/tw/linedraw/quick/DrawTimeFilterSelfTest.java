package tw.linedraw.quick;

public final class DrawTimeFilterSelfTest {
    private static void expect(String actual, String wanted) {
        if (!actual.equals(wanted)) throw new AssertionError(actual + " != " + wanted);
    }

    public static void main(String[] args) {
        expect(DrawTimeFilter.openingTime("抽選/購買時間：2026/09/24 11:00~2026/09/25 21:00"), "11:00");
        expect(DrawTimeFilter.openingTime("抽選開放：2026/09/24 10:30｜購買資格券有效至 21:00"), "10:30");
        expect(DrawTimeFilter.openingTime("抽選/購買日期：2026/09/24~2026/09/25（原文未註明時間）"), "未標時間");
        expect(DrawTimeFilter.openingTime(""), "未標時間");
        System.out.println("DrawTimeFilterSelfTest passed");
    }
}
