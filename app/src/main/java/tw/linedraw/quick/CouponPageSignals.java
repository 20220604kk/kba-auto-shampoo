package tw.linedraw.quick;

final class CouponPageSignals {
    private CouponPageSignals() {}

    static boolean isNotStarted(String pageText) {
        return pageText != null && (
                pageText.contains("抽選期間尚未開始") ||
                pageText.contains("抽選尚未開始") ||
                pageText.contains("尚未開放抽選"));
    }

    static boolean isEnded(String pageText) {
        String text = CouponSafety.normalized(pageText);
        return text.contains("此為已結束的優惠券") ||
                text.contains("此為已結束的優惠卷") ||
                text.contains("此为已结束的优惠券") ||
                text.contains("優惠券已結束") ||
                text.contains("優惠券已失效") ||
                text.contains("抽選期間已結束") ||
                text.contains("已過期");
    }
}
