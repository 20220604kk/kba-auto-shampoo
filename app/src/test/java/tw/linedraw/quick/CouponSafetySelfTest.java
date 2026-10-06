package tw.linedraw.quick;

/** Standalone JVM check; no device, LINE, or coupon is touched. */
public final class CouponSafetySelfTest {
    private static void expect(boolean actual, boolean wanted, String label) {
        if (actual != wanted) throw new AssertionError(label + ": expected " + wanted + ", got " + actual);
    }

    public static void main(String[] args) {
        expect(CouponSafety.isRedemptionLabel("使用優惠券"), true, "redeem traditional");
        expect(CouponSafety.isRedemptionLabel("使用優惠卷"), true, "redeem variant");
        expect(CouponSafety.isRedemptionLabel("立即兌換"), true, "exchange");
        expect(CouponSafety.isRedemptionLabel("核銷"), true, "write off");
        expect(CouponSafety.isRedemptionLabel("已使用的優惠券無法再次使用"), false, "instructions are not a control");
        expect(CouponSafety.isRedemptionLabel("可惜...沒有抽中！"), false, "grey result");
        expect(CouponSafety.isAllowedClickLabel("參加抽獎"), true, "draw");
        expect(CouponSafety.isAllowedClickLabel("關閉"), true, "close");
        expect(CouponSafety.isAllowedClickLabel("加入好友"), true, "explicit friend join");
        expect(CouponSafety.isAllowedClickLabel("加入好友並參加抽獎"), true, "friend draw combined control");
        expect(CouponSafety.isAllowedClickLabel("加入好友並使用優惠券"), false, "friend redemption is never allowed");
        expect(CouponSafety.isAllowedClickLabel("加好友"), true, "short friend join");
        expect(CouponSafety.isAllowedClickLabel("加入"), false, "generic join is not allowed");
        expect(CouponSafety.isAllowedClickLabel("使用優惠券"), false, "no redemption");
        expect(CouponSafety.isAllowedClickLabel("確定"), false, "no generic confirmation");
        expect(CouponSafety.isAllowedClickLabel("已領取"), false, "no other coupon card");
        System.out.println("CouponSafetySelfTest passed");
    }
}
