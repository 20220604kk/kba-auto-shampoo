package tw.linedraw.quick;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.provider.Settings;

final class PermissionHealth {
    private PermissionHealth() {}

    static boolean accessibilityEnabled(Context context) {
        String enabled = Settings.Secure.getString(context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null || enabled.isEmpty()) return false;
        ComponentName wanted = new ComponentName(context, DrawAccessibilityService.class);
        for (String raw : enabled.split(":")) {
            ComponentName component = ComponentName.unflattenFromString(raw);
            if (wanted.equals(component)) return true;
        }
        return false;
    }

    static boolean lineInstalled(Context context) {
        try {
            context.getPackageManager().getPackageInfo("jp.naver.line.android", 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) { return false; }
    }

    static String shortStatus(Context context) {
        if (DrawAccessibilityService.available()) return "輔助服務已連線";
        if (accessibilityEnabled(context)) return "輔助服務已開啟，等待系統重新連線";
        return "輔助服務尚未開啟";
    }

    static String fullStatus(Context context) {
        String service = DrawAccessibilityService.available() ? "已連線，可執行" :
                accessibilityEnabled(context) ? "已啟用但尚未連線（通常是系統中止服務）" : "未啟用";
        return "輔助使用服務：" + service + "\n" +
                "LINE：" + (lineInstalled(context) ? "已安裝" : "未偵測到原生 LINE") + "\n" +
                "浮動進度列：" + (Settings.canDrawOverlays(context) ? "已授權" : "未授權；仍會嘗試輔助服務浮窗") + "\n" +
                "LINE 直式鎖定：" + (OrientationGuard.canLock(context) ? "已授權" : "未授權") + "\n\n" +
                "這不是缺少套件。Android／模擬器可能在更新 APP、省電、清理背景或分身切換後停止輔助服務；" +
                "系統不允許 APP 自行重新開啟這項高權限。建議把本 APP 的電池使用設為「不限制」，並允許自動啟動；" +
                "若顯示已啟用但未連線，請到輔助使用設定將本服務關閉再開啟一次。";
    }
}
