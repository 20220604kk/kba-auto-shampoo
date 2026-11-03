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

    static boolean isBusy(String pageText) {
        String text = CouponSafety.normalized(pageText);
        return text.contains("系統忙碌中") ||
                text.contains("系統繁忙") ||
                text.contains("請稍後再開啟此頁面") ||
                text.contains("請稍後再試") ||
                text.contains("目前無法載入優惠券") ||
                text.contains("systemisbusy") ||
                text.contains("tryagainlater");
    }

    static boolean isNotFound(String pageText) {
        String text = CouponSafety.normalized(pageText).toLowerCase(java.util.Locale.ROOT);
        return text.contains("404notfound") ||
                text.contains("404pagenotfound") ||
                text.contains("404找不到") ||
                text.contains("找不到網頁404");
    }
}
