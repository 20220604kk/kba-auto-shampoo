package tw.linedraw.quick;

final class CouponSafety {
    private CouponSafety() {}

    static String normalized(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", "");
    }

    static boolean isRedemptionLabel(String raw) {
        String label = normalized(raw);
        return label.equals("使用") || label.contains("使用優惠券") || label.contains("使用優惠卷") ||
                label.contains("使用优惠券") || label.contains("立即使用") ||
                label.contains("兌換") || label.contains("兑换") || label.contains("核銷") || label.contains("核销");
    }

    static boolean isAllowedClickLabel(String raw) {
        String label = normalized(raw);
        return label.equals("打開LINE") || label.equals("開啟LINE") || label.equalsIgnoreCase("OpenLINE") ||
                isFriendJoinLabel(label) || isFriendDrawLabel(label) ||
                label.equals("參加抽獎") || label.equals("参加抽奖") ||
                label.equals("參加抽選") || label.equals("立即抽選") ||
                label.equals("關閉") || label.equalsIgnoreCase("Close") || label.equals("閉じる");
    }

    static boolean isFriendJoinLabel(String raw) {
        String label = normalized(raw);
        return label.equals("加入好友") || label.equals("加好友") ||
                label.equals("新增好友") || label.equals("加入官方帳號") ||
                label.equalsIgnoreCase("AddFriend") || label.equalsIgnoreCase("AddasFriend");
    }

    static boolean isFriendDrawLabel(String raw) {
        return normalized(raw).equals("加入好友並參加抽獎");
    }
}
