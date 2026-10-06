package tw.linedraw.quick;

public final class CouponPageSignalsSelfTest {
    public static void main(String[] args) {
        if (!CouponPageSignals.isNotStarted("官方帳號優惠券 抽選期間尚未開始"))
            throw new AssertionError("未開抽須維持待抽");
        if (!CouponPageSignals.isNotStarted("抽選尚未開始"))
            throw new AssertionError("未開抽文案變體須維持待抽");
        if (CouponPageSignals.isNotStarted("已抽選 可惜...沒有抽中！"))
            throw new AssertionError("已抽過不得誤判成未開抽");
        if (!CouponPageSignals.isEnded("此為已結束的優惠券"))
            throw new AssertionError("LINE 已結束頁必須被辨識");
        if (!CouponPageSignals.isEnded("官方帳號優惠券 此為已 結束的 優惠券 已結束"))
            throw new AssertionError("LINE 分段顯示的已結束頁必須被辨識");
        if (CouponPageSignals.isEnded("參加抽獎"))
            throw new AssertionError("可抽頁不可誤判結束");
    }
}
