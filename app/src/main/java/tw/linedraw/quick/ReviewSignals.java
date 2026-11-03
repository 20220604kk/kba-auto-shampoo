package tw.linedraw.quick;

/** Text signals from the primary coupon, before the unrelated "其他優惠券" section. */
final class ReviewSignals {
    private ReviewSignals() {}

    static String primary(String page) {
        return (page == null ? "" : page).split("其他優惠券", 2)[0];
    }

    static boolean isSubmissionAcknowledged(String page) {
        String text = primary(page);
        return text.contains("抽選中") || text.contains("抽獎中") ||
                text.contains("正在抽選") || text.contains("正在抽獎") ||
                text.contains("恭喜中獎") || text.contains("可惜...沒有抽中") ||
                text.contains("已抽選") || text.contains("已參加抽獎");
    }

    /** Empty means keep waiting. "unknown" means visible but the result is not provable. */
    static String classify(String page, boolean redemptionControl) {
        String text = primary(page);
        String compact = CouponSafety.normalized(text);
        if (text.contains("恭喜中獎") || text.contains("您已中獎") || text.contains("你已中獎")) return "win";
        if (text.contains("可惜...沒有抽中") || text.contains("沒有抽中！") ||
                text.contains("未中獎") || compact.contains("可惜，沒有中獎") ||
                compact.contains("可惜...沒有中獎") || compact.contains("沒有中獎")) return "lose";
        if (redemptionControl) return "win";
        if (CouponPageSignals.isEnded(text)) return "expired";
        if (CouponPageSignals.isNotStarted(text)) return "scheduled";
        if (CouponPageSignals.isBusy(text)) return "busy";
        if (text.contains("已抽選") || text.contains("已參加抽獎")) return "unknown";
        if (text.contains("加入好友") || text.contains("加好友") ||
                text.contains("新增好友") || text.contains("加入官方帳號") ||
                text.toLowerCase(java.util.Locale.ROOT).contains("add friend")) return "not_friend";
        if (text.contains("參加抽獎") ||
                text.contains("参加抽奖") || text.contains("參加抽選")) return "not_drawn";
        return "";
    }
}
