package tw.linedraw.quick;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/** Native, offline test double. It never connects to LINE and cannot create a real coupon. */
public final class MockCouponActivity extends Activity {
    private final int green = Color.rgb(17, 178, 92);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String scenario;
    private String productTitle;
    private int screenGeneration;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        scenario = getIntent().getStringExtra("scenario");
        if (!MockCatalog.validScenario(scenario)) scenario = "ready";
        productTitle = getIntent().getStringExtra("product");
        if (productTitle == null || productTitle.trim().isEmpty()) productTitle = MockCatalog.titleForScenario(scenario);
        showCoupon(false);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handler.removeCallbacksAndMessages(null);
        setIntent(intent);
        scenario = intent.getStringExtra("scenario");
        if (!MockCatalog.validScenario(scenario)) scenario = "ready";
        productTitle = intent.getStringExtra("product");
        if (productTitle == null || productTitle.trim().isEmpty()) productTitle = MockCatalog.titleForScenario(scenario);
        showCoupon(false);
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private void showCoupon(boolean acted) {
        screenGeneration++;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        root.setContentDescription("K爸測試優惠券頁");

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(18), dp(18), dp(22));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("官方帳號優惠券", 18, 0xFF111111);
        label.setTypeface(null, Typeface.BOLD);
        bar.addView(label, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button close = new Button(this);
        close.setText("×");
        close.setTextSize(28);
        close.setContentDescription("關閉");
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setOnClickListener(v -> {
            if (DrawAccessibilityService.isRunning()) showLineHost();
            else finish();
        });
        bar.addView(close, new LinearLayout.LayoutParams(dp(58), dp(52)));
        page.addView(bar);

        TextView warning = text("K爸測試，不會有真實訂單", 14, 0xFFB3261E);
        warning.setTypeface(null, Typeface.BOLD);
        warning.setGravity(Gravity.CENTER);
        warning.setPadding(0, dp(8), 0, dp(8));
        warning.setBackgroundColor(0xFFFFECEA);
        page.addView(warning, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text("優惠券抽選模擬", 25, 0xFF172B4D);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(44), 0, dp(12));
        page.addView(title);
        TextView product = text(productTitle, 16, 0xFF172B4D);
        product.setTypeface(null, Typeface.BOLD);
        product.setGravity(Gravity.CENTER);
        page.addView(product);
        TextView description = text("離線驗收頁面，只用來測試開啟、辨識、點擊與關閉流程。", 14, 0xFF6B7280);
        description.setGravity(Gravity.CENTER);
        page.addView(description);

        TextView state = text(stateText(acted), 19, 0xFF172B4D);
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(12), dp(70), dp(12), dp(18));
        page.addView(state, new LinearLayout.LayoutParams(-1, 0, 1));

        Button action = new Button(this);
        action.setAllCaps(false);
        action.setTextSize(18);
        action.setTextColor(Color.WHITE);
        action.setBackgroundColor(green);
        action.setVisibility(actionLabel(acted).isEmpty() ? View.INVISIBLE : View.VISIBLE);
        action.setText(actionLabel(acted));
        action.setContentDescription(actionLabel(acted));
        action.setOnClickListener(v -> showDrawing());
        page.addView(action, new LinearLayout.LayoutParams(-1, dp(64)));
        setContentView(root);
    }

    /** Keeps the acceptance fixture close to the real coupon flow: click, wait, result. */
    private void showDrawing() {
        final int generation = ++screenGeneration;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);
        root.setContentDescription("K爸測試優惠券頁");

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(18), dp(18), dp(22));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = text("官方帳號優惠券", 18, 0xFF111111);
        label.setTypeface(null, Typeface.BOLD);
        bar.addView(label, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView close = text("×", 30, 0xFF111111);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("關閉");
        close.setOnClickListener(v -> {
            screenGeneration++;
            if (DrawAccessibilityService.isRunning()) showLineHost();
            else finish();
        });
        bar.addView(close, new LinearLayout.LayoutParams(dp(58), dp(52)));
        page.addView(bar);

        TextView warning = text("K爸測試，不會有真實訂單", 14, 0xFFB3261E);
        warning.setTypeface(null, Typeface.BOLD);
        warning.setGravity(Gravity.CENTER);
        warning.setPadding(0, dp(8), 0, dp(8));
        warning.setBackgroundColor(0xFFFFECEA);
        page.addView(warning, new LinearLayout.LayoutParams(-1, -2));

        TextView title = text("優惠券抽選模擬", 25, 0xFF172B4D);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, dp(44), 0, dp(12));
        page.addView(title);
        TextView product = text(productTitle, 16, 0xFF172B4D);
        product.setTypeface(null, Typeface.BOLD);
        product.setGravity(Gravity.CENTER);
        page.addView(product);

        LinearLayout loading = new LinearLayout(this);
        loading.setOrientation(LinearLayout.VERTICAL);
        loading.setGravity(Gravity.CENTER);
        ProgressBar spinner = new ProgressBar(this);
        loading.addView(spinner, new LinearLayout.LayoutParams(dp(54), dp(54)));
        TextView drawing = text("抽獎中…", 23, 0xFF172B4D);
        drawing.setTypeface(null, Typeface.BOLD);
        drawing.setGravity(Gravity.CENTER);
        drawing.setPadding(0, dp(18), 0, dp(5));
        loading.addView(drawing);
        TextView waiting = text("正在開啟抽選結果，請稍候", 14, 0xFF6B7280);
        waiting.setGravity(Gravity.CENTER);
        loading.addView(waiting);
        page.addView(loading, new LinearLayout.LayoutParams(-1, 0, 1));

        Button disabled = new Button(this);
        disabled.setAllCaps(false);
        disabled.setText("抽獎中…");
        disabled.setTextSize(18);
        disabled.setTextColor(0xFF67737F);
        disabled.setEnabled(false);
        page.addView(disabled, new LinearLayout.LayoutParams(-1, dp(64)));
        setContentView(root);

        handler.postDelayed(() -> {
            if (!isFinishing() && generation == screenGeneration) showCoupon(true);
        }, 1400L);
    }

    private void showLineHost() {
        screenGeneration++;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFFF5F7FA);
        root.setContentDescription("K爸模擬 LINE 背景");
        TextView label = text("LINE 模擬背景\n正在開啟下一筆…", 16, 0xFF6B7280);
        label.setGravity(Gravity.CENTER);
        root.addView(label, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private String stateText(boolean acted) {
        if (scenario.equals("already")) return "可惜...沒有抽中！\n已抽選";
        if (scenario.equals("expired")) return "此為已結束的優惠券";
        if (scenario.equals("error")) return "目前無法載入優惠券，請稍後再試";
        if (scenario.equals("friend") && acted) return "已加入好友\n模擬抽選已完成";
        if (scenario.equals("friend")) return "此官方帳號尚未加入好友";
        if (scenario.equals("win") && acted) return "恭喜中獎！\n僅供展示，不能兌換";
        if (acted) return "可惜...沒有抽中！";
        return "準備完成，可以參加抽獎";
    }

    private String actionLabel(boolean acted) {
        if (acted || scenario.equals("already") || scenario.equals("expired") || scenario.equals("error")) return "";
        if (scenario.equals("friend")) return "加入好友並參加抽獎";
        return "參加抽獎";
    }
}
