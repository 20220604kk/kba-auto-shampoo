package tw.linedraw.quick;

/** Standalone check: no device, LINE or coupon interaction. */
public final class ReviewSignalsSelfTest {
    private static void expect(String actual, String wanted) {
        if (!wanted.equals(actual)) throw new AssertionError("expected " + wanted + ", got " + actual);
    }

    public static void main(String[] args) {
        expect(ReviewSignals.classify("官方帳號優惠券 恭喜中獎", false), "win");
        expect(ReviewSignals.classify("官方帳號優惠券 使用說明", true), "win");
        expect(ReviewSignals.classify("可惜...沒有抽中！", false), "lose");
        expect(ReviewSignals.classify("參加抽獎", false), "not_drawn");
        expect(ReviewSignals.classify("加入好友並參加抽獎", false), "not_friend");
        expect(ReviewSignals.classify("已抽選", false), "unknown");
        expect(ReviewSignals.classify("此為已結束的優惠券", false), "expired");
        expect(ReviewSignals.classify("抽選期間尚未開始", false), "scheduled");
        expect(ReviewSignals.classify("尚未載入", false), "");
        expect(ReviewSignals.classify("參加抽獎 其他優惠券 恭喜中獎", false), "not_drawn");
        if (!ReviewSignals.isSubmissionAcknowledged("抽選中...")) throw new AssertionError("draw transition");
        if (ReviewSignals.isSubmissionAcknowledged("參加抽獎")) throw new AssertionError("unchanged draw button");
        System.out.println("ReviewSignalsSelfTest passed");
    }
}
