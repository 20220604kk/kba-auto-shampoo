package tw.linedraw.quick;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

public final class MainActivity extends Activity {
    private final int navy = Color.rgb(23, 48, 91);
    private final int light = Color.rgb(243, 247, 252);
    private final int accent = Color.rgb(52, 126, 226);
    private LinearLayout content;
    private LinearLayout controlsArea;
    private LinearLayout overview;
    private LinearLayout reviewPanel;
    private LinearLayout filterRows;
    private LinearLayout timeSection;
    private LinearLayout timeChoices;
    private LinearLayout resultsTools;
    private LinearLayout selectionArea;
    private LinearLayout list;
    private TextView summary;
    private TextView sourceTime;
    private TextView selectedCount;
    private Button cityFilter;
    private Button productFilter;
    private Button priorityButton;
    private Button addTest;
    private Button mockTest;
    private Button syncButton;
    private Button allTab;
    private Button testTab;
    private Button readyTab;
    private Button selectAll;
    private Button selectedOnlyButton;
    private Button records;
    private Button start;
    private ScrollView scroll;
    private FrameLayout shell;
    private ArrayList<DrawItem> items = new ArrayList<>();
    private Map<String, String> reviewOutcomes = new HashMap<>();
    private String reviewFilter = "all";
    private ArrayList<DrawItem> shownItems = new ArrayList<>();
    private int displayLimit = 100;
    private static final int DISPLAY_PAGE_SIZE = 100;
    private boolean lastSyncFailed;
    private boolean selectionDirty;
    private String mode = "all";
    private String selectedTime = "";
    private boolean selectedOnly;
    private final LinkedHashSet<String> selectedCities = new LinkedHashSet<>();
    // Remembers a fully selected header only to include newly published cities after sync.
    private final LinkedHashSet<String> selectedAllCityGroups = new LinkedHashSet<>();
    private final LinkedHashSet<String> selectedProducts = new LinkedHashSet<>();
    private boolean cityFirst = true;
    private boolean refreshing;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable syncTicker = this::updateSyncButton;
    private final Runnable selectionWriter = this::flushSelectionSave;
    private static final long SYNC_COOLDOWN_MS = 30_000L;

    @Override protected void onCreate(Bundle state) {
        setTheme(R.style.AppTheme);
        super.onCreate(state);
        if (!DrawAccessibilityService.isRunning()) OrientationGuard.restore(this);
        DrawData.clearSelections(this);
        getWindow().setStatusBarColor(navy);
        getWindow().setNavigationBarColor(navy);
        buildScreen();
        if (!DrawData.onboardingSeen(this)) uiHandler.post(() -> showOnboarding(false));
        if (DrawData.refreshedAt(this) == 0) refreshSource();
    }

    @Override protected void onResume() {
        super.onResume();
        render();
        String report = DrawData.takeReviewReport(this);
        if (!report.isEmpty()) uiHandler.post(() -> new AlertDialog.Builder(this)
                .setTitle(report.startsWith("手動連結") ? "手動抽選核對" : "全台重掃結果")
                .setMessage(report).setPositiveButton("知道了", null).show());
        else {
            String runReport = DrawData.takeRunReport(this);
            if (!runReport.isEmpty()) {
                uiHandler.post(() -> new AlertDialog.Builder(this)
                        .setTitle("本次抽選完成")
                        .setMessage(runReport)
                        .setPositiveButton("查看結果", (dialog, which) -> setMode("done"))
                        .setNegativeButton("關閉", null).show());
                return;
            }
            String manualKey = DrawData.takeManualOpen(this);
            if (!manualKey.isEmpty() && !DrawAccessibilityService.isRunning())
                uiHandler.post(() -> promptManualResult(manualKey));
        }
    }

    @Override protected void onDestroy() {
        uiHandler.removeCallbacks(syncTicker);
        uiHandler.removeCallbacks(selectionWriter);
        super.onDestroy();
    }

    @Override protected void onPause() {
        flushSelectionSave();
        super.onPause();
    }

    private void scheduleSelectionSave() {
        selectionDirty = true;
        uiHandler.removeCallbacks(selectionWriter);
        uiHandler.postDelayed(selectionWriter, 400);
    }

    private void flushSelectionSave() {
        uiHandler.removeCallbacks(selectionWriter);
        if (!selectionDirty) return;
        selectionDirty = false;
        DrawData.save(this, items);
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private int displayPageSize() { return DrawData.lowMemoryMode(this) ? 50 : DISPLAY_PAGE_SIZE; }

    private LinearLayout vertical() {
        LinearLayout value = new LinearLayout(this);
        value.setOrientation(LinearLayout.VERTICAL);
        return value;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String value) {
        Button view = new Button(this);
        view.setText(value);
        view.setAllCaps(false);
        view.setTextSize(13);
        return view;
    }

    private GradientDrawable panelBackground(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(10));
        background.setStroke(dp(1), 0xFFDCE5EF);
        return background;
    }

    private void buildScreen() {
        shell = new FrameLayout(this);
        shell.setBackgroundColor(navy);
        shell.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsets.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return windowInsets;
        });
        setContentView(shell);
        FrameLayout safeContent = new FrameLayout(this);
        safeContent.setBackgroundColor(light);
        shell.addView(safeContent, new FrameLayout.LayoutParams(-1, -1));
        ImageView watermark = new ImageView(this);
        watermark.setImageResource(R.drawable.fangba_watermark);
        watermark.setScaleType(ImageView.ScaleType.FIT_CENTER);
        watermark.setAlpha(0.30f);
        safeContent.addView(watermark, new FrameLayout.LayoutParams(-1, dp(360), Gravity.CENTER));
        LinearLayout root = vertical();
        safeContent.addView(root, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout header = vertical();
        header.setPadding(dp(18), dp(13), dp(18), dp(14));
        header.setBackgroundColor(navy);
        LinearLayout headerTop = new LinearLayout(this);
        headerTop.setGravity(Gravity.CENTER_VERTICAL);
        headerTop.setMinimumHeight(dp(48));
        TextView title = text("方爸全自動洗頭機", 21, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine(true);
        headerTop.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        syncButton = button("⟳ 同步");
        syncButton.setTextSize(12);
        syncButton.setTextColor(0xFF12683A);
        syncButton.setBackgroundColor(0xFFE3F8EA);
        syncButton.setPadding(dp(5), 0, dp(5), 0);
        syncButton.setTranslationY(dp(3));
        syncButton.setOnClickListener(v -> refreshSource());
        headerTop.addView(syncButton, new LinearLayout.LayoutParams(dp(76), dp(38)));
        Button gear = button("⚙");
        gear.setContentDescription("設定");
        gear.setTextSize(22);
        gear.setTextColor(Color.WHITE);
        gear.setBackgroundColor(navy);
        gear.setTranslationY(dp(3));
        gear.setOnClickListener(v -> settingsDialog());
        LinearLayout.LayoutParams gearParams = new LinearLayout.LayoutParams(dp(42), dp(38));
        gearParams.leftMargin = dp(5);
        headerTop.addView(gear, gearParams);
        header.addView(headerTop);
        TextView subtitle = text("快速洗全台頭 · v0.2.9", 12, 0xFFBCD4F4);
        header.addView(subtitle);
        root.addView(header);

        LinearLayout modes = new LinearLayout(this);
        modes.setPadding(dp(16), dp(8), dp(16), dp(6));
        modes.setBackgroundColor(light);
        allTab = addModeButton(modes, "本週抽選", "all");
        testTab = addModeButton(modes, "結果", "done");
        readyTab = addModeButton(modes, "測試", "test");
        root.addView(modes, new LinearLayout.LayoutParams(-1, dp(62)));
        styleTabs();

        scroll = new ScrollView(this);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        content = vertical();
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        scroll.addView(content);

        overview = vertical();
        overview.setPadding(dp(16), dp(15), dp(16), dp(15));
        overview.setBackground(panelBackground(Color.WHITE));
        overview.setMinimumHeight(dp(84));
        summary = text("載入中", 20, navy);
        summary.setTypeface(null, Typeface.BOLD);
        summary.setSingleLine(true);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        summary.setMinHeight(dp(32));
        overview.addView(summary);
        sourceTime = text("", 12, 0xFF718096);
        sourceTime.setLines(2);
        sourceTime.setGravity(Gravity.TOP);
        overview.addView(sourceTime);
        TextView credit = text("開發 by ken (20220647)", 10, 0xFF9AA8BA);
        credit.setGravity(Gravity.END);
        overview.addView(credit);
        content.addView(overview, new LinearLayout.LayoutParams(-1, dp(124)));

        reviewPanel = vertical();
        reviewPanel.setPadding(dp(12), dp(8), dp(12), dp(8));
        reviewPanel.setBackground(panelBackground(Color.WHITE));
        reviewPanel.setMinimumHeight(dp(118));
        LinearLayout.LayoutParams reviewParams = new LinearLayout.LayoutParams(-1, dp(118));
        reviewParams.topMargin = dp(8);
        content.addView(reviewPanel, reviewParams);
        reviewPanel.setVisibility(View.INVISIBLE);

        controlsArea = vertical();
        // Only the page-specific controls change. The title, tabs, overview and
        // status cards above stay fixed; the content below uses its natural
        // height instead of manufacturing a large empty spacer.
        content.addView(controlsArea, new LinearLayout.LayoutParams(-1, -2));

        addTest = button("＋ 新增測試");
        addTest.setOnClickListener(v -> testLinksDialog());
        controlsArea.addView(addTest, new LinearLayout.LayoutParams(-1, dp(48)));
        mockTest = button("K爸模擬驗收 · 不會有真實訂單");
        mockTest.setOnClickListener(v -> mockDialog());
        controlsArea.addView(mockTest, new LinearLayout.LayoutParams(-1, dp(48)));

        resultsTools = vertical();
        resultsTools.setPadding(0, dp(12), 0, dp(8));
        TextView resultHint = text("狀態已分流；點上方分類即可快速找出需要處理的連結。", 13, 0xFF53657A);
        resultsTools.addView(resultHint);
        Button reports = button("查看最近三筆掃描報告");
        reports.setOnClickListener(v -> reportsDialog());
        resultsTools.addView(reports, new LinearLayout.LayoutParams(-1, dp(48)));
        Button review = button("掃描待確認項目（自動跳過已知結果）");
        review.setOnClickListener(v -> confirmReview());
        resultsTools.addView(review, new LinearLayout.LayoutParams(-1, dp(48)));
        controlsArea.addView(resultsTools);

        filterRows = vertical();
        controlsArea.addView(filterRows);
        cityFilter = filterButton("全部縣市");
        productFilter = filterButton("全部品項");
        priorityButton = filterButton("縣市優先");
        filterRows.addView(labelWithControl("縣市（可複選）", cityFilter));
        filterRows.addView(labelWithControl("品項（可複選）", productFilter));
        filterRows.addView(labelWithControl("抽選順序", priorityButton));
        cityFilter.setOnClickListener(v -> chooseCities());
        productFilter.setOnClickListener(v -> chooseProducts());
        priorityButton.setOnClickListener(v -> choosePriority());

        timeSection = vertical();
        timeSection.setPadding(0, dp(6), 0, dp(5));
        timeSection.addView(text("開抽時間", 13, navy));
        HorizontalScrollView timeScroll = new HorizontalScrollView(this);
        timeScroll.setHorizontalScrollBarEnabled(false);
        timeChoices = new LinearLayout(this);
        timeChoices.setGravity(Gravity.CENTER_VERTICAL);
        timeScroll.addView(timeChoices);
        timeSection.addView(timeScroll, new LinearLayout.LayoutParams(-1, dp(42)));
        controlsArea.addView(timeSection);

        selectionArea = vertical();
        selectedCount = text("已選 0 筆", 15, navy);
        selectedCount.setTypeface(null, Typeface.BOLD);
        selectionArea.addView(selectedCount);
        LinearLayout selectionActions = new LinearLayout(this);
        selectionActions.setPadding(0, dp(4), 0, dp(4));
        selectedOnlyButton = button("只看已選");
        selectedOnlyButton.setOnClickListener(v -> {
            selectedOnly = !selectedOnly;
            displayLimit = displayPageSize();
            renderList();
            scroll.smoothScrollTo(0, selectionArea.getTop());
        });
        selectionActions.addView(selectedOnlyButton, new LinearLayout.LayoutParams(0, dp(40), 1));
        selectAll = button("全選目前結果");
        selectAll.setOnClickListener(v -> toggleAll());
        selectionActions.addView(selectAll, new LinearLayout.LayoutParams(0, dp(40), 1));
        selectionArea.addView(selectionActions);
        content.addView(selectionArea, new LinearLayout.LayoutParams(-1, dp(88)));
        list = vertical();
        content.addView(list);

        LinearLayout footer = new LinearLayout(this);
        footer.setPadding(dp(16), dp(9), dp(16), dp(12));
        footer.setBackgroundColor(navy);
        records = button("紀錄");
        records.setOnClickListener(v -> {
            if (mode.equals("done")) reportsDialog();
            else if (mode.equals("test")) testRecordsDialog();
            else recordsDialog();
        });
        footer.addView(records, new LinearLayout.LayoutParams(0, dp(52), 1));
        start = button("開始連抽");
        start.setOnClickListener(v -> {
            if (mode.equals("done")) confirmReview();
            else startRun();
        });
        footer.addView(start, new LinearLayout.LayoutParams(0, dp(52), 2));
        root.addView(footer);
    }

    private Button addModeButton(LinearLayout parent, String title, String value) {
        Button view = button(title);
        view.setOnClickListener(v -> setMode(value));
        parent.addView(view, new LinearLayout.LayoutParams(0, dp(46), 1));
        return view;
    }

    private void styleTabs() {
        if (allTab == null || testTab == null || readyTab == null) return;
        allTab.setBackgroundColor(mode.equals("all") ? 0xFF315F9D : 0xFFE8EEF6);
        allTab.setTextColor(mode.equals("all") ? Color.WHITE : navy);
        testTab.setBackgroundColor(mode.equals("done") ? 0xFF315F9D : 0xFFE8EEF6);
        testTab.setTextColor(mode.equals("done") ? Color.WHITE : navy);
        readyTab.setBackgroundColor(mode.equals("test") ? 0xFF315F9D : 0xFFE8EEF6);
        readyTab.setTextColor(mode.equals("test") ? Color.WHITE : navy);
    }

    private void setMode(String value) {
        mode = value;
        reviewFilter = "all";
        selectedOnly = false;
        displayLimit = displayPageSize();
        styleTabs();
        updateOverview();
        updateFilters();
        updateReviewPanel();
        if (value.equals("all")) pruneHiddenSelections();
        renderList();
    }

    private Button filterButton(String label) {
        Button view = button(label);
        view.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        view.setTextColor(navy);
        view.setTextSize(14);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        view.setPadding(dp(12), 0, dp(12), 0);
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.WHITE);
        background.setCornerRadius(dp(7));
        background.setStroke(dp(1), 0xFFD1DCEB);
        view.setBackground(background);
        return view;
    }

    private LinearLayout labelWithControl(String label, View control) {
        LinearLayout row = vertical();
        row.setPadding(0, dp(7), 0, dp(5));
        row.addView(text(label, 13, navy));
        row.addView(control, new LinearLayout.LayoutParams(-1, dp(44)));
        return row;
    }

    private void render() {
        if (content == null) return;
        items = DrawData.load(this);
        reviewOutcomes = DrawData.loadReviewOutcomes(this);
        updateOverview();
        updateSyncButton();
        updateFilters();
        updateReviewPanel();
        renderList();
    }

    private void updateReviewPanel() {
        if (reviewPanel == null) return;
        reviewPanel.removeAllViews();
        int wins = 0;
        int losses = 0;
        int ended = 0;
        int scheduled = 0;
        int notFriend = 0;
        int notDrawn = 0;
        int problem = 0;
        int handled = 0;
        for (DrawItem item : items) {
            if (!item.origin.equals("source")) continue;
            if (!RunScope.eligible(item)) handled++;
            String outcome = effectiveOutcome(item);
            if (isReviewWin(outcome)) wins++;
            else if (outcome.equals("lose")) losses++;
            else if (outcome.equals("expired")) ended++;
            else if (outcome.equals("scheduled")) scheduled++;
            else if (outcome.equals("not_friend")) notFriend++;
            else if (isReviewNotDrawn(outcome)) notDrawn++;
            else if (isReviewProblem(outcome)) problem++;
        }
        reviewPanel.setVisibility(View.VISIBLE);
        if (mode.equals("all")) {
            TextView title = text("本週狀態", 14, navy);
            title.setTypeface(null, Typeface.BOLD);
            reviewPanel.addView(title);
            reviewPanel.addView(text("未開賣 " + scheduled + "・未加好友 " + notFriend +
                    "・未抽籤 " + notDrawn, 12, 0xFF53657A));
            reviewPanel.addView(text("已處理 " + handled + "・需確認 " + problem +
                    "　選好範圍後再勾選本次任務。", 12, 0xFF718096));
            return;
        }
        if (mode.equals("test")) {
            TextView title = text("測試工具與獨立紀錄", 14, navy);
            title.setTypeface(null, Typeface.BOLD);
            reviewPanel.addView(title);
            reviewPanel.addView(text("可驗證私人 LINE 測試券與 K爸離線模擬；兩者共用測試紀錄。", 12, 0xFF718096));
            reviewPanel.addView(text("等待時間、權限與低階模式請由右上角齒輪設定。", 12, 0xFF718096));
            return;
        }
        TextView title = text("結果分類　點一下只看該狀態", 13, navy);
        title.setTypeface(null, Typeface.BOLD);
        reviewPanel.addView(title);
        LinearLayout first = new LinearLayout(this);
        LinearLayout second = new LinearLayout(this);
        addResultFilter(first, "全部", "all");
        addResultFilter(first, "未開賣 " + scheduled, "scheduled");
        addResultFilter(first, "未加好友 " + notFriend, "not_friend");
        addResultFilter(first, "未抽籤 " + notDrawn, "not_drawn");
        addResultFilter(second, "中獎 " + wins, "win");
        addResultFilter(second, "未中 " + losses, "lose");
        addResultFilter(second, "已結束 " + ended, "expired");
        addResultFilter(second, "需確認 " + problem, "problem");
        reviewPanel.addView(first, new LinearLayout.LayoutParams(-1, dp(36)));
        reviewPanel.addView(second, new LinearLayout.LayoutParams(-1, dp(36)));
    }

    private void addResultFilter(LinearLayout row, String label, String filter) {
        Button chip = button(label);
        chip.setTextSize(10);
        boolean active = reviewFilter.equals(filter);
        chip.setBackgroundColor(active ? 0xFF315F9D : 0xFFE8EEF6);
        chip.setTextColor(active ? Color.WHITE : navy);
        chip.setOnClickListener(v -> setReviewFilter(filter));
        row.addView(chip, new LinearLayout.LayoutParams(0, dp(34), 1));
    }

    private String effectiveOutcome(DrawItem item) {
        String outcome = reviewOutcomes.getOrDefault(item.key(), "");
        if (!outcome.isEmpty()) return outcome;
        if (item.status.equals("pending")) return "not_drawn";
        if (item.status.equals("done") || item.status.equals("already")) return "unknown";
        return item.status;
    }

    private boolean isReviewNotDrawn(String outcome) {
        return outcome.equals("not_drawn") || outcome.equals("pending");
    }

    private boolean isReviewWin(String outcome) { return outcome.equals("win"); }

    private boolean isReviewProblem(String outcome) {
        return outcome.equals("mismatch") || outcome.equals("unknown") ||
                outcome.equals("error");
    }

    private boolean isKnownResult(String outcome) {
        return outcome.equals("win") || outcome.equals("lose") || outcome.equals("expired");
    }

    private boolean matchesReviewFilter(String outcome) {
        if (reviewFilter.equals("all")) return true;
        if (reviewFilter.equals("win")) return isReviewWin(outcome);
        if (reviewFilter.equals("not_drawn")) return isReviewNotDrawn(outcome);
        if (reviewFilter.equals("problem")) return isReviewProblem(outcome);
        return reviewFilter.equals(outcome);
    }

    private void setReviewFilter(String filter) {
        reviewFilter = filter;
        displayLimit = displayPageSize();
        updateReviewPanel();
        if (mode.equals("all")) pruneHiddenSelections();
        renderList();
    }

    private void updateOverview() {
        if (summary == null) return;
        if (mode.equals("test")) {
            int total = 0;
            int handled = 0;
            for (DrawItem item : items) if (item.origin.equals("test")) {
                total++;
                if (!RunScope.eligible(item)) handled++;
            }
            summary.setText("測試工具");
            sourceTime.setText("私人 LINE 測試與 K爸離線模擬\n自訂測試共 " + total + " 筆・已處理 " + handled + " 筆");
            return;
        }
        if (mode.equals("done")) {
            int known = 0;
            int problems = 0;
            for (DrawItem item : items) if (item.origin.equals("source")) {
                String outcome = effectiveOutcome(item);
                if (isKnownResult(outcome)) known++;
                if (isReviewProblem(outcome)) problems++;
            }
            summary.setText("本週結果 · 已知結果 " + known + " 筆");
            sourceTime.setText("需確認 " + problems + " 筆・狀態已拆分為未開賣、未加好友、未抽籤、中獎與未中獎\n" +
                    "全台重掃會自動排除已知結果");
            return;
        }
        Set<String> stores = new HashSet<>();
        for (DrawItem item : items) if (item.origin.equals("source")) stores.add(item.city + "|" + item.store);
        int published = Math.max(stores.size(), DrawData.sourceStores(this));
        int eligible = 0;
        int handled = 0;
        for (DrawItem item : items) if (item.origin.equals("source")) {
            if (RunScope.eligible(item)) eligible++;
            else handled++;
        }
        summary.setText("本週任務 · " + eligible + " 筆需處理");
        long at = DrawData.refreshedAt(this);
        String refreshed = at == 0 ? "尚未重整" : new SimpleDateFormat("M/d HH:mm", Locale.TAIWAN).format(new Date(at));
        sourceTime.setText("已更新 " + published + " / 78 家門市・已處理 " + handled + " 筆\n" +
                DrawData.sourceUpdated(this) + "・上次同步：" + refreshed);
    }

    private void updateFilters() {
        ArrayList<String> cities = availableCities();
        selectedCities.retainAll(cities);
        for (String city : cities) if (selectedAllCityGroups.contains(RegionCatalog.regionOf(city)))
            selectedCities.add(city);
        selectedProducts.retainAll(availableProducts());
        Map<String, String> productLabels = ProductCatalog.labels(items);
        cityFilter.setText(citySelectionLabel());
        productFilter.setText(selectionLabel(selectedProducts, RunScope.ALL_PRODUCTS, productLabels));
        priorityButton.setText(cityFirst ? "先縣市 → 品項 ▾" : "先品項 → 縣市 ▾");
        boolean settings = mode.equals("test");
        boolean results = mode.equals("done");
        addTest.setVisibility(settings ? View.VISIBLE : View.GONE);
        mockTest.setVisibility(settings ? View.VISIBLE : View.GONE);
        resultsTools.setVisibility(results ? View.VISIBLE : View.GONE);
        filterRows.setVisibility(!settings && !results ? View.VISIBLE : View.GONE);
        timeSection.setVisibility(!settings && !results ? View.VISIBLE : View.GONE);
        selectionArea.setVisibility(results ? View.GONE : View.VISIBLE);
        records.setText(results ? "掃描報告" : settings ? "測試紀錄" : "紀錄");
        updateTimeChoices();
    }

    private void updateTimeChoices() {
        if (timeChoices == null) return;
        TreeSet<String> times = new TreeSet<>();
        for (DrawItem item : items) if (item.origin.equals("source"))
            times.add(DrawTimeFilter.openingTime(item.period));
        if (!selectedTime.isEmpty() && !times.contains(selectedTime)) selectedTime = "";
        timeChoices.removeAllViews();
        addTimeChoice("全部時間", "");
        for (String time : times) addTimeChoice(time, time);
    }

    private void addTimeChoice(String label, String value) {
        Button chip = button(label);
        boolean active = selectedTime.equals(value);
        chip.setTextColor(active ? Color.WHITE : navy);
        chip.setBackgroundColor(active ? 0xFF315F9D : 0xFFE8EEF6);
        chip.setOnClickListener(v -> {
            selectedTime = value;
            displayLimit = displayPageSize();
            updateTimeChoices();
            pruneHiddenSelections();
            renderList();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(36));
        params.rightMargin = dp(6);
        timeChoices.addView(chip, params);
    }

    private ArrayList<String> availableCities() {
        Set<String> present = new HashSet<>();
        for (DrawItem item : items) if (item.origin.equals("source"))
            present.add(RegionCatalog.canonicalCity(item.city));
        ArrayList<String> options = new ArrayList<>();
        for (String known : RegionCatalog.CITIES) if (present.remove(known)) options.add(known);
        ArrayList<String> other = new ArrayList<>(present);
        other.sort(String::compareTo);
        options.addAll(other);
        return options;
    }

    private ArrayList<String> availableProducts() {
        Set<String> present = new HashSet<>();
        for (DrawItem item : items) if (item.origin.equals("source"))
            present.add(RunScope.productCategory(item));
        ArrayList<String> options = new ArrayList<>(present);
        options.sort(String::compareTo);
        return options;
    }

    private String selectionLabel(Set<String> selected, String emptyLabel, Map<String, String> labels) {
        if (selected.isEmpty()) return emptyLabel + " ▾";
        ArrayList<String> shown = new ArrayList<>();
        for (String value : selected) shown.add(labels == null ? value : labels.getOrDefault(value, value));
        if (shown.size() > 2) return shown.get(0) + "、" + shown.get(1) + " 等 " + shown.size() + " 項 ▾";
        return String.join("、", shown) + " ▾";
    }

    private String citySelectionLabel() {
        if (selectedCities.isEmpty()) return RegionCatalog.ALL_CITIES + " ▾";
        ArrayList<String> available = availableCities();
        for (String region : RegionCatalog.REGIONS) {
            if (region.equals(RegionCatalog.ALL)) continue;
            int total = CitySelection.availableInRegion(available, region);
            if (total > 0 && selectedCities.size() == total &&
                    CitySelection.selectedInRegion(selectedCities, available, region) == total)
                return region + "全部（" + total + " 縣市） ▾";
        }
        return selectionLabel(selectedCities, RegionCatalog.ALL_CITIES, null);
    }

    private void chooseCities() {
        ArrayList<String> options = availableCities();
        if (options.isEmpty()) {
            Toast.makeText(this, "目前沒有可選縣市", Toast.LENGTH_SHORT).show();
            return;
        }
        LinkedHashSet<String> draft = new LinkedHashSet<>(selectedCities);
        ScrollView picker = new ScrollView(this);
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        form.addView(text("未勾選代表全部縣市；點分區可一次勾選目前有資料的縣市。", 12, 0xFF718096));
        LinkedHashSet<String> regions = new LinkedHashSet<>();
        for (String region : RegionCatalog.REGIONS) if (!region.equals(RegionCatalog.ALL)) regions.add(region);
        for (String city : options) regions.add(RegionCatalog.regionOf(city));
        for (String region : regions) {
            int total = CitySelection.availableInRegion(options, region);
            if (total == 0) continue;
            Button group = button("");
            group.setTextColor(navy);
            group.setBackgroundColor(0xFFE8EEF6);
            LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(-1, dp(40));
            groupParams.topMargin = dp(9);
            form.addView(group, groupParams);
            ArrayList<CheckBox> children = new ArrayList<>();
            Runnable updateGroup = () -> {
                int count = CitySelection.selectedInRegion(draft, options, region);
                group.setText(region + " · " + (count == total ? "取消全選" :
                        count == 0 ? "選取全部" : "已選 " + count + "/" + total + "，點此全選"));
            };
            for (String city : options) if (RegionCatalog.regionOf(city).equals(region)) {
                CheckBox child = new CheckBox(this);
                child.setText(city);
                child.setTextSize(15);
                child.setChecked(draft.contains(city));
                child.setOnCheckedChangeListener((button, checked) -> {
                    if (checked) draft.add(city);
                    else draft.remove(city);
                    updateGroup.run();
                });
                form.addView(child);
                children.add(child);
            }
            group.setOnClickListener(v -> {
                CitySelection.toggleRegion(draft, options, region);
                for (CheckBox child : children) child.setChecked(draft.contains(child.getText().toString()));
                updateGroup.run();
            });
            updateGroup.run();
        }
        picker.addView(form);
        new AlertDialog.Builder(this).setTitle("選擇縣市")
                .setView(picker)
                .setNeutralButton("顯示全部", (dialog, which) -> {
                    selectedCities.clear();
                    selectedAllCityGroups.clear();
                    filtersChanged();
                })
                .setPositiveButton("套用", (dialog, which) -> {
                    selectedCities.clear();
                    selectedCities.addAll(draft);
                    selectedAllCityGroups.clear();
                    for (String region : regions) {
                        int total = CitySelection.availableInRegion(options, region);
                        if (total > 0 && CitySelection.selectedInRegion(draft, options, region) == total)
                            selectedAllCityGroups.add(region);
                    }
                    filtersChanged();
                })
                .setNegativeButton("取消", null).show();
    }

    private void chooseProducts() {
        ArrayList<String> options = availableProducts();
        Map<String, String> labels = ProductCatalog.labels(items);
        ArrayList<String> display = new ArrayList<>();
        for (String value : options) display.add(labels.getOrDefault(value, value));
        chooseMulti("選擇品項", options, display, selectedProducts, this::filtersChanged);
    }

    private void choosePriority() {
        ArrayList<String> cityOrder = new ArrayList<>(selectedCities);
        ArrayList<String> productOrder = new ArrayList<>(selectedProducts);
        boolean[] draftCityFirst = {cityFirst};
        ScrollView picker = new ScrollView(this);
        LinearLayout form = vertical();
        form.setPadding(dp(18), dp(8), dp(18), dp(8));
        form.addView(text("篩選決定抽哪些；這裡只調整已選項目的先後。未指定篩選時維持清單原順序。", 12, 0xFF718096));
        LinearLayout dimension = new LinearLayout(this);
        Button byCity = button("縣市優先");
        Button byProduct = button("品項優先");
        Runnable paintDimension = () -> {
            byCity.setBackgroundColor(draftCityFirst[0] ? 0xFF315F9D : 0xFFE8EEF6);
            byCity.setTextColor(draftCityFirst[0] ? Color.WHITE : navy);
            byProduct.setBackgroundColor(draftCityFirst[0] ? 0xFFE8EEF6 : 0xFF315F9D);
            byProduct.setTextColor(draftCityFirst[0] ? navy : Color.WHITE);
        };
        byCity.setOnClickListener(v -> { draftCityFirst[0] = true; paintDimension.run(); });
        byProduct.setOnClickListener(v -> { draftCityFirst[0] = false; paintDimension.run(); });
        dimension.addView(byCity, new LinearLayout.LayoutParams(0, dp(42), 1));
        dimension.addView(byProduct, new LinearLayout.LayoutParams(0, dp(42), 1));
        form.addView(dimension);
        paintDimension.run();
        LinearLayout cityRows = vertical();
        LinearLayout productRows = vertical();
        form.addView(cityRows);
        form.addView(productRows);
        renderPriorityRows(cityRows, "縣市順序", cityOrder, null);
        renderPriorityRows(productRows, "品項順序", productOrder, ProductCatalog.labels(items));
        picker.addView(form);
        new AlertDialog.Builder(this).setTitle("抽選優先順序")
                .setView(picker)
                .setPositiveButton("套用", (dialog, which) -> {
                    cityFirst = draftCityFirst[0];
                    selectedCities.clear();
                    selectedCities.addAll(cityOrder);
                    selectedProducts.clear();
                    selectedProducts.addAll(productOrder);
                    updateFilters();
                    renderList();
                })
                .setNegativeButton("取消", null).show();
    }

    private void renderPriorityRows(LinearLayout parent, String title, ArrayList<String> values,
                                    Map<String, String> labels) {
        parent.removeAllViews();
        TextView heading = text(title, 14, navy);
        heading.setTypeface(null, Typeface.BOLD);
        heading.setPadding(0, dp(14), 0, dp(5));
        parent.addView(heading);
        if (values.isEmpty()) {
            parent.addView(text("全部顯示；沒有指定優先次序", 12, 0xFF718096));
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            final int position = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            String value = values.get(i);
            TextView name = text((i + 1) + ". " + (labels == null ? value : labels.getOrDefault(value, value)), 13, navy);
            name.setMaxLines(2);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            Button up = button("↑");
            up.setEnabled(i > 0);
            up.setOnClickListener(v -> {
                Collections.swap(values, position, position - 1);
                renderPriorityRows(parent, title, values, labels);
            });
            row.addView(up, new LinearLayout.LayoutParams(dp(43), dp(39)));
            Button down = button("↓");
            down.setEnabled(i < values.size() - 1);
            down.setOnClickListener(v -> {
                Collections.swap(values, position, position + 1);
                renderPriorityRows(parent, title, values, labels);
            });
            row.addView(down, new LinearLayout.LayoutParams(dp(43), dp(39)));
            parent.addView(row);
        }
    }

    private void chooseMulti(String title, ArrayList<String> values, ArrayList<String> labels,
                             Set<String> selected, Runnable changed) {
        if (values.isEmpty()) {
            Toast.makeText(this, "目前沒有可選項目", Toast.LENGTH_SHORT).show();
            return;
        }
        LinkedHashSet<String> draft = new LinkedHashSet<>(selected);
        boolean[] checked = new boolean[values.size()];
        for (int i = 0; i < values.size(); i++) checked[i] = selected.contains(values.get(i));
        new AlertDialog.Builder(this).setTitle(title)
                .setMultiChoiceItems(labels.toArray(new String[0]), checked, (dialog, which, isChecked) -> {
                    if (isChecked) draft.add(values.get(which));
                    else draft.remove(values.get(which));
                })
                .setNeutralButton("顯示全部", (dialog, which) -> {
                    selected.clear();
                    changed.run();
                })
                .setPositiveButton("套用", (dialog, which) -> {
                    selected.clear();
                    selected.addAll(draft);
                    changed.run();
                })
                .setNegativeButton("取消", null).show();
    }

    private void filtersChanged() {
        displayLimit = displayPageSize();
        updateFilters();
        pruneHiddenSelections();
        renderList();
    }

    private ArrayList<DrawItem> visible() {
        ArrayList<DrawItem> shown = visibleScope();
        if (selectedOnly) shown.removeIf(item -> !item.selected || !RunScope.eligible(item));
        return shown;
    }

    private ArrayList<DrawItem> visibleScope() {
        if (mode.equals("test")) return StoreGroups.ordered(RunScope.visible(items, mode,
                java.util.Collections.emptySet(), java.util.Collections.emptySet(), java.util.Collections.emptySet()));
        if (mode.equals("done")) {
            ArrayList<DrawItem> results = RunScope.visible(items, "all",
                    java.util.Collections.emptySet(), java.util.Collections.emptySet(), java.util.Collections.emptySet());
            results.removeIf(item -> {
                String outcome = effectiveOutcome(item);
                return !matchesReviewFilter(outcome);
            });
            return StoreGroups.ordered(results);
        }
        ArrayList<DrawItem> shown = RunScope.visible(items, mode,
                java.util.Collections.emptySet(), selectedCities, selectedProducts);
        if (mode.equals("all")) shown.removeIf(item -> !RunScope.eligible(item));
        if (!selectedTime.isEmpty())
            shown.removeIf(item -> !selectedTime.equals(DrawTimeFilter.openingTime(item.period)));
        if (!reviewFilter.equals("all")) shown.removeIf(item -> {
            String outcome = effectiveOutcome(item);
            return !matchesReviewFilter(outcome);
        });
        return StoreGroups.ordered(shown);
    }

    private void pruneHiddenSelections() {
        Set<String> shownKeys = new HashSet<>();
        for (DrawItem item : visibleScope()) shownKeys.add(item.key());
        boolean changed = false;
        for (DrawItem item : items) {
            if (item.selected && !shownKeys.contains(item.key())) {
                item.selected = false;
                changed = true;
            }
        }
        if (changed) scheduleSelectionSave();
    }

    private void renderList() {
        if (list == null) return;
        list.removeAllViews();
        ArrayList<DrawItem> shown = visible();
        shownItems = shown;
        if (shown.isEmpty()) {
            TextView empty = text("目前沒有符合條件的抽選項目", 14, 0xFF718096);
            empty.setPadding(dp(10), dp(25), dp(10), dp(25));
            list.addView(empty);
        }
        ArrayList<DrawItem> page = new ArrayList<>(shown.subList(0, Math.min(displayLimit, shown.size())));
        for (StoreGroups.CityGroup area : StoreGroups.group(page)) {
            String areaName = area.city.equals("測試") ? "方爸測試" :
                    area.city.isEmpty() ? "未標示地區" : RegionCatalog.regionOf(area.city) + " · " + area.city;
            TextView location = text(areaName, 15, navy);
            location.setTypeface(null, Typeface.BOLD);
            location.setPadding(dp(2), dp(12), 0, dp(8));
            list.addView(location);
            for (StoreGroups.StoreGroup store : area.stores) {
                LinearLayout card = vertical();
                card.setPadding(dp(12), dp(11), dp(12), dp(12));
                GradientDrawable cardBackground = new GradientDrawable();
                cardBackground.setColor(Color.WHITE);
                cardBackground.setCornerRadius(dp(10));
                cardBackground.setStroke(dp(1), 0xFFDCE5EF);
                card.setBackground(cardBackground);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
                cardParams.bottomMargin = dp(10);
                list.addView(card, cardParams);
                LinearLayout storeHeader = new LinearLayout(this);
                storeHeader.setGravity(Gravity.CENTER_VERTICAL);
                TextView name = text(store.name, 15, navy);
                name.setTypeface(null, Typeface.BOLD);
                storeHeader.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                Button selectStore = button("此店全選");
                selectStore.setTextSize(11);
                selectStore.setPadding(0, 0, 0, 0);
                selectStore.setVisibility(selectedOnly ? View.GONE : View.VISIBLE);
                selectStore.setOnClickListener(v -> selectStorePending(area.city, store.name));
                storeHeader.addView(selectStore, new LinearLayout.LayoutParams(dp(86), dp(34)));
                int storeCount = 0;
                for (DrawItem candidate : shown) if (sameStore(candidate, area.city, store.name)) storeCount++;
                TextView count = text(storeCount + " 筆", 12, 0xFF718096);
                count.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
                storeHeader.addView(count, new LinearLayout.LayoutParams(dp(42), dp(34)));
                card.addView(storeHeader);
                for (DrawItem item : store.items) renderItem(card, item);
            }
        }
        if (shown.size() > page.size()) {
            Button more = button("顯示更多（已顯示 " + page.size() + " / " + shown.size() + " 筆）");
            more.setOnClickListener(v -> {
                displayLimit += displayPageSize();
                renderList();
            });
            list.addView(more, new LinearLayout.LayoutParams(-1, dp(48)));
        }
        updateSelection();
    }

    private boolean sameStore(DrawItem item, String city, String store) {
        return RegionCatalog.canonicalCity(item.city).equals(city) && item.store.equals(store);
    }

    private void selectStorePending(String city, String store) {
        int count = 0;
        for (DrawItem item : shownItems) {
            if (!sameStore(item, city, store) || !RunScope.bulkSelectable(item)) continue;
            if (!item.selected) { item.selected = true; count++; }
        }
        if (count == 0) Toast.makeText(this, "此店沒有尚待抽選的項目", Toast.LENGTH_SHORT).show();
        else scheduleSelectionSave();
        renderList();
    }

    private void renderItem(LinearLayout storeCard, DrawItem item) {
            boolean processed = !RunScope.eligible(item);
            LinearLayout row = vertical();
            row.setPadding(dp(9), dp(7), dp(9), dp(8));
            GradientDrawable rowBackground = new GradientDrawable();
            rowBackground.setColor(processed ? 0xFFE9EDF2 : 0xFFF6F9FD);
            rowBackground.setCornerRadius(dp(7));
            row.setBackground(rowBackground);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = dp(6);
            storeCard.addView(row, rowParams);
            CheckBox box = new CheckBox(this);
            box.setText(item.product);
            box.setTextSize(16);
            box.setTextColor(processed ? 0xFF8793A2 : 0xFF223A5A);
            box.setChecked(item.selected);
            box.setEnabled(RunScope.eligible(item));
            box.setOnCheckedChangeListener((button, checked) -> {
                item.selected = checked;
                scheduleSelectionSave();
                if (selectedOnly && !checked) renderList();
                else updateSelection();
            });
            row.addView(box);
            TextView link = text(item.url, 12, processed ? 0xFF8A96A5 : accent);
            link.setPaintFlags(link.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
            link.setPadding(0, dp(4), 0, dp(4));
            link.setContentDescription("開啟連結 " + item.url);
            link.setOnClickListener(v -> openShortLink(item));
            row.addView(link);
            String detail = item.period.isEmpty() ? "" : item.period + "\n";
            detail += "狀態：" + statusLabel(item.status);
            row.addView(text(detail, 12, 0xFF6A7B92));
            if (mode.equals("test")) {
                Button remove = button("移除這筆測試");
                remove.setOnClickListener(v -> {
                    if (DrawAccessibilityService.isRunning()) {
                        Toast.makeText(this, "連抽進行中，請先停止再修改測試清單", Toast.LENGTH_LONG).show();
                        return;
                    }
                    new AlertDialog.Builder(this).setTitle("移除測試連結？")
                            .setMessage(item.product + "\n" + item.url + "\n移除後不會自動重新出現；之後仍可手動貼入。")
                            .setPositiveButton("移除", (dialog, which) -> {
                                if (DrawData.removeTest(this, item.url)) {
                                    Toast.makeText(this, "已移除測試連結", Toast.LENGTH_SHORT).show();
                                    render();
                                }
                            })
                            .setNegativeButton("取消", null).show();
                });
                row.addView(remove);
            }
            if (!item.screenshot.isEmpty()) {
                Button screenshot = button("查看中獎截圖");
                screenshot.setOnClickListener(v -> {
                    try {
                        Intent viewer = new Intent(Intent.ACTION_VIEW);
                        viewer.setDataAndType(Uri.parse(item.screenshot), "image/png");
                        viewer.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        startActivity(viewer);
                    } catch (Exception error) {
                        Toast.makeText(this, "無法開啟截圖，請至相簿的 LineDraw 資料夾查看", Toast.LENGTH_LONG).show();
                    }
                });
                row.addView(screenshot);
            }
    }

    private String statusLabel(String status) {
        switch (status) {
            case "scheduled": return "未開賣";
            case "not_friend": return "未加好友";
            case "pending": return "未抽籤";
            case "done": return "已送出抽獎・結果待確認";
            case "win": return "已中獎";
            case "lose": return "未中獎";
            case "already": return "已抽籤・結果待確認";
            case "expired": return "已結束";
            case "error": return "異常・需確認";
            default: return "需確認";
        }
    }

    private void updateSelection() {
        ArrayList<DrawItem> shown = visibleScope();
        if (mode.equals("done")) {
            start.setText("掃描待確認");
            start.setEnabled(true);
            return;
        }
        int count = 0;
        int eligible = 0;
        int bulk = 0;
        int bulkSelected = 0;
        for (DrawItem item : shown) {
            if (!RunScope.eligible(item)) continue;
            eligible++;
            if (item.selected) count++;
            if (RunScope.bulkSelectable(item)) {
                bulk++;
                if (item.selected) bulkSelected++;
            }
        }
        selectedCount.setText("目前範圍：" + eligible + " 筆・已選 " + count + " 筆");
        selectedOnlyButton.setText(selectedOnly ? "顯示全部" : "查看已選（" + count + "）");
        selectedOnlyButton.setEnabled(count > 0 || selectedOnly);
        int queueCount = RunScope.queue(shown, mode).size();
        start.setText(queueCount > 0 ? "開始連抽（" + queueCount + "）" : "開始連抽");
        start.setEnabled(queueCount > 0);
        boolean all = (bulk > 0 && bulkSelected == bulk) || (bulk == 0 && count > 0);
        selectAll.setText(all ? "取消目前選取" : "全選待抽");
        selectAll.setEnabled(bulk > 0 || count > 0);
    }

    private void toggleAll() {
        ArrayList<DrawItem> shown = visibleScope();
        boolean all = true;
        for (DrawItem item : shown) if (RunScope.bulkSelectable(item) && !item.selected) all = false;
        for (DrawItem item : shown) {
            if (RunScope.bulkSelectable(item)) item.selected = !all;
            else item.selected = false;
        }
        selectedOnly = !all;
        scheduleSelectionSave();
        renderList();
        if (!all) Toast.makeText(this, "已選取待抽項目，畫面只顯示本次任務", Toast.LENGTH_SHORT).show();
    }

    private void refreshSource() {
        if (refreshing) return;
        if (DrawAccessibilityService.isRunning()) {
            Toast.makeText(this, "連抽或重掃進行中，結束後再同步", Toast.LENGTH_LONG).show();
            return;
        }
        long remaining = syncRemaining();
        if (remaining > 0) {
            Toast.makeText(this, "請在 " + ((remaining + 999) / 1000) + " 秒後再同步", Toast.LENGTH_SHORT).show();
            updateSyncButton();
            return;
        }
        DrawData.markSyncRequest(this, System.currentTimeMillis());
        refreshing = true;
        updateSyncButton();
        Toast.makeText(this, "同步中...", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                DrawData.refresh(this);
                runOnUiThread(() -> {
                    refreshing = false;
                    lastSyncFailed = false;
                    render();
                    Toast.makeText(this, "已同步", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    refreshing = false;
                    lastSyncFailed = true;
                    updateSyncButton();
                    Toast.makeText(this, "同步失敗：" + error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "linedraw-refresh").start();
    }

    private long syncRemaining() {
        long elapsed = System.currentTimeMillis() - DrawData.lastSyncRequest(this);
        return elapsed < 0 ? 0 : Math.max(0, SYNC_COOLDOWN_MS - elapsed);
    }

    private void updateSyncButton() {
        if (syncButton == null) return;
        uiHandler.removeCallbacks(syncTicker);
        long remaining = syncRemaining();
        syncButton.setEnabled(!refreshing && remaining == 0);
        boolean succeeded = DrawData.refreshedAt(this) >= DrawData.lastSyncRequest(this) &&
                DrawData.lastSyncRequest(this) > 0;
        syncButton.setText(refreshing ? "同步中..." :
                lastSyncFailed && remaining > 0 ? "同步失敗" :
                succeeded && remaining > 0 ? "已同步" : "⟳ 同步");
        if (remaining > 0) uiHandler.postDelayed(syncTicker, 1000);
    }

    private void openShortLink(DrawItem item) {
        try {
            DrawData.markManualOpen(this, item);
            Intent open = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
            open.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(open);
        } catch (Exception error) {
            DrawData.takeManualOpen(this);
            Toast.makeText(this, "無法開啟這筆連結", Toast.LENGTH_LONG).show();
        }
    }

    private void promptManualResult(String key) {
        DrawItem matched = null;
        for (DrawItem item : items) if (item.key().equals(key)) { matched = item; break; }
        if (matched == null) return;
        DrawItem item = matched;
        new AlertDialog.Builder(this).setTitle("剛才這筆有抽完嗎？")
                .setMessage(item.store + " · " + item.product +
                        "\n\n開啟連結不等於完成抽獎。可以只讀重新查看 LINE 的結果；看不清時請自行確認，不會自動按「使用優惠券」。")
                .setPositiveButton("只讀核對", (dialog, which) -> {
                    if (!DrawAccessibilityService.beginManualCheck(item)) showAccessibilityUnavailable();
                })
                .setNeutralButton("我確定已抽", (dialog, which) -> {
                    item.status = "done";
                    item.selected = false;
                    DrawData.updateItem(this, item);
                    render();
                })
                .setNegativeButton("尚未／稍後", null).show();
    }

    private void startRun() {
        ArrayList<DrawItem> shown = visibleScope();
        ArrayList<DrawItem> selectedQueue = RunScope.queue(shown, mode);
        ArrayList<DrawItem> queue = mode.equals("test") ? selectedQueue : QueuePriority.order(selectedQueue,
                new ArrayList<>(selectedCities), new ArrayList<>(selectedProducts), cityFirst);
        if (queue.isEmpty()) { Toast.makeText(this, "請先勾選要抽的連結；未選取的不會啟動", Toast.LENGTH_LONG).show(); return; }
        if (!DrawAccessibilityService.available()) {
            showAccessibilityUnavailable();
            return;
        }
        String scope = mode.equals("test") ? "方爸測試" :
                citySelectionLabel().replace(" ▾", "") + "・" +
                selectionLabel(selectedProducts, RunScope.ALL_PRODUCTS, ProductCatalog.labels(items)).replace(" ▾", "");
        if (!OrientationGuard.canLock(this)) {
            new AlertDialog.Builder(this).setTitle("LINE 直式鎖定尚未授權")
                    .setMessage("洗頭機自身已鎖直式。要讓 LINE 也自動轉回直式，請先允許修改系統設定；結束後會恢復原設定。")
                    .setPositiveButton("前往授權", (dialog, which) -> openRotationPermission())
                    .setNegativeButton("暫時不鎖 LINE", (dialog, which) -> confirmStart(queue, scope)).show();
            return;
        }
        confirmStart(queue, scope);
    }

    private void confirmStart(ArrayList<DrawItem> queue, String scope) {
        double seconds = queue.size() * (1.4 + DrawData.closeDelay(this) + DrawData.delay(this));
        String estimate = seconds < 60 ? Math.max(1, Math.round(seconds)) + " 秒" :
                Math.max(1, Math.round(seconds / 60.0)) + " 分鐘";
        StringBuilder preview = new StringBuilder();
        for (int i = 0; i < Math.min(8, queue.size()); i++) {
            DrawItem item = queue.get(i);
            preview.append(i + 1).append(". ").append(RegionCatalog.canonicalCity(item.city))
                    .append(" · ").append(item.store).append(" · ").append(item.product).append('\n');
        }
        if (queue.size() > 8) preview.append("…其餘 ").append(queue.size() - 8).append(" 筆依此順序繼續");
        new AlertDialog.Builder(this).setTitle("開始連抽 " + queue.size() + " 筆？")
                .setMessage("範圍：" + scope + "\n預估時間：約 " + estimate +
                        "\n開抽時間：" + (selectedTime.isEmpty() ? "全部時間" : selectedTime) +
                        "\n\n預計順序：\n" + preview +
                        "\n只處理目前勾選的項目；已抽過會關閉並跳過。完成狀態只代表已操作，可隨時暫停或停止。\n\n安全原則：永遠不操作「使用優惠券」。")
                .setPositiveButton("開始", (dialog, which) -> {
                    if (!DrawAccessibilityService.begin(queue))
                        Toast.makeText(this, "已有連抽正在執行，請先停止", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("取消", null).show();
    }

    private void testLinksDialog() {
        EditText links = new EditText(this);
        links.setHint("貼上新的 lin.ee 或 line.me 連結；可一次貼多筆");
        links.setMinLines(7);
        links.setGravity(Gravity.TOP);
        links.setPadding(dp(18), dp(8), dp(18), dp(8));
        new AlertDialog.Builder(this).setTitle("新增方爸測試連結")
                .setMessage("只會加入測試分頁；重複連結不會再新增。")
                .setView(links)
                .setPositiveButton("儲存", (dialog, which) -> {
                    int added = DrawData.addTestLinks(this, links.getText().toString());
                    Toast.makeText(this, "新增 " + added + " 筆測試連結", Toast.LENGTH_LONG).show();
                    render();
                }).setNegativeButton("取消", null).show();
    }

    private void mockDialog() {
        new AlertDialog.Builder(this).setTitle("K爸測試，不會有真實訂單")
                .setMessage("內建可抽選、已抽過、中獎結果、已失效、載入錯誤、加入好友六種畫面；完全離線，不開啟 LINE，也不產生可兌換的優惠券。")
                .setNeutralButton("查看單頁", (dialog, which) -> {
                    Intent preview = new Intent(this, MockCouponActivity.class);
                    preview.putExtra("scenario", "ready");
                    startActivity(preview);
                })
                .setPositiveButton("自動驗收六筆", (dialog, which) -> {
                    if (!DrawAccessibilityService.available()) {
                        showAccessibilityUnavailable();
                        return;
                    }
                    if (!DrawAccessibilityService.begin(MockCatalog.items()))
                        Toast.makeText(this, "目前有流程正在執行", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("取消", null).show();
    }

    private void showAccessibilityUnavailable() {
        boolean cloneProfile = android.os.Process.myUid() / 100000 != 0;
        String detail = cloneProfile
                ? "此分身目前沒有可用的輔助使用服務。若設定顯示已開啟仍無法執行，這個 MuMu 分身無法獨立控制畫面；第二組帳號請使用獨立的 MuMu 多開實例。"
                : PermissionHealth.accessibilityEnabled(this)
                ? "權限顯示已開啟，但服務目前沒有連線。這通常是省電、系統清理、更新 APP 或模擬器切換後中止服務，不是缺少套件。請到設定將「方爸全自動洗頭機控制」關閉再開啟一次。"
                : "連抽需要啟用「方爸全自動洗頭機控制」。Android 不允許 APP 自己打開這項高權限；啟用後回來再按開始。";
        new AlertDialog.Builder(this).setTitle("輔助使用服務不可用")
                .setMessage(detail)
                .setPositiveButton("前往設定", (dialog, which) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("取消", null).show();
    }

    private void settingsDialog() {
        ScrollView settingsScroll = new ScrollView(this);
        LinearLayout form = vertical();
        form.setPadding(dp(18), 0, dp(18), dp(18));
        settingsScroll.addView(form);
        EditText closeSeconds = new EditText(this);
        closeSeconds.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        closeSeconds.setText(String.valueOf(DrawData.closeDelay(this)));
        form.addView(text("抽完停等上限（每筆浮動 0.3 秒～此值；最多 30 秒）", 14, navy));
        form.addView(closeSeconds);
        EditText nextSeconds = new EditText(this);
        nextSeconds.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        nextSeconds.setText(String.valueOf(DrawData.delay(this)));
        form.addView(text("每筆完成等待上限（每筆浮動 0.3 秒～此值；最多 10 秒）", 14, navy));
        form.addView(nextSeconds);
        Button floating = button(Settings.canDrawOverlays(this) ? "浮動進度列：已授權" : "授權浮動進度列顯示於 LINE 上方");
        floating.setOnClickListener(v -> openOverlayPermission());
        form.addView(floating);
        Button portrait = button(OrientationGuard.canLock(this) ? "LINE 直式鎖定：已授權" : "授權連抽期間自動鎖直式");
        portrait.setOnClickListener(v -> openRotationPermission());
        form.addView(portrait);

        Button permission = button("權限健檢 · " + PermissionHealth.shortStatus(this));
        permission.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("執行環境健檢")
                .setMessage(PermissionHealth.fullStatus(this))
                .setPositiveButton("前往輔助設定", (d, w) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNeutralButton("APP 系統設定", (d, w) -> startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("關閉", null).show());
        form.addView(permission);

        Button help = button("使用說明");
        form.addView(help);
        CheckBox lowMemory = new CheckBox(this);
        lowMemory.setText("低階手機模式（每頁 50 筆、降低畫面辨識解析度）");
        lowMemory.setChecked(DrawData.lowMemoryMode(this));
        form.addView(lowMemory);
        Button clearWeek = button("清除本周資料");
        clearWeek.setTextColor(0xFFB3261E);
        form.addView(clearWeek, new LinearLayout.LayoutParams(-1, dp(48)));

        ImageView signature = new ImageView(this);
        signature.setImageResource(R.drawable.ken_monitor_cat);
        signature.setScaleType(ImageView.ScaleType.FIT_CENTER);
        signature.setAlpha(.88f);
        LinearLayout.LayoutParams signatureParams = new LinearLayout.LayoutParams(-1, dp(170));
        signatureParams.topMargin = dp(12);
        form.addView(signature, signatureParams);
        TextView settingsCredit = text("by KEN的監控儀表 ．脆 20220647_6", 12, 0xFF718096);
        settingsCredit.setGravity(Gravity.CENTER);
        settingsCredit.setPadding(0, dp(4), 0, dp(8));
        form.addView(settingsCredit);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("設定")
                .setMessage("例如填 0.8，每筆會在 0.3～0.8 秒之間變動。已抽過的項目不等待抽獎結果。")
                .setView(settingsScroll)
                .setPositiveButton("儲存", null)
                .setNegativeButton("取消", null).create();
        help.setOnClickListener(v -> {
            dialog.dismiss();
            uiHandler.post(() -> showOnboarding(true));
        });
        clearWeek.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("清除本周資料？")
                .setMessage("會清除正式店家清單、本周抽選狀態與掃描報告；私人測試連結、K爸測試紀錄及所有設定都會保留。清除後請按右上角「同步」取得新一周清單。")
                .setPositiveButton("確認清除", (confirm, which) -> {
                    DrawAccessibilityService.stopCurrent();
                    int removed = DrawData.clearWeeklyData(this);
                    selectedCities.clear();
                    selectedAllCityGroups.clear();
                    selectedProducts.clear();
                    selectedTime = "";
                    mode = "all";
                    dialog.dismiss();
                    render();
                    Toast.makeText(this, "已清除 " + removed + " 筆本周資料，請重新同步", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("取消", null).show());
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                    try {
                        float close = Float.parseFloat(closeSeconds.getText().toString());
                        float next = Float.parseFloat(nextSeconds.getText().toString());
                        if (!Float.isFinite(close) || !Float.isFinite(next) || close < .3f || close > 30f || next < .3f || next > 10f) {
                            Toast.makeText(this, "抽完停等上限需 0.3～30 秒；每筆完成等待上限需 0.3～10 秒", Toast.LENGTH_LONG).show();
                            return;
                        }
                        DrawData.setCloseDelay(this, close);
                        DrawData.setDelay(this, next);
                        DrawData.setLowMemoryMode(this, lowMemory.isChecked());
                        displayLimit = displayPageSize();
                        Toast.makeText(this, "浮動等待已更新", Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    } catch (NumberFormatException error) {
                        Toast.makeText(this, "請輸入數字", Toast.LENGTH_SHORT).show();
                    }
                }));
        dialog.show();
    }

    private void openOverlayPermission() {
        Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(permission);
    }

    private void openRotationPermission() {
        Intent permission = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(permission);
    }

    private void showOnboarding(boolean replay) {
        if (shell == null) return;
        final String[] titles = {
                "STEP 1　同步最新清單",
                "STEP 2　設定抽選範圍",
                "STEP 3　確認並開始連抽",
                "STEP 4　掌握執行進度"
        };
        final String[] details = {
                "點右上角「同步」，取得目前店家、品項與開抽時間。\n\n↗ 右上角同步按鈕",
                "複選縣市與品項，再選開抽時間；未選的項目不會執行。\n\n↓ 本週抽選篩選區",
                "按「全選待抽」後只顯示本次任務；核對順序後再開始。\n\n↘ 頁面底部開始按鈕",
                "浮動進度列可暫停或停止。「結果」可重掃並查看最近三筆報告。\n\n安全原則：永遠不碰「使用優惠券」。"
        };
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.TRANSPARENT);
        overlay.setClickable(true);
        overlay.setFocusable(true);

        View shade = new View(this);
        shade.setBackgroundColor(0x33000000);
        overlay.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout panel = vertical();
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(24), dp(26), dp(24), dp(24));
        GradientDrawable panelBackground = new GradientDrawable();
        panelBackground.setColor(0xF21A2E50);
        panelBackground.setCornerRadius(dp(18));
        panelBackground.setStroke(dp(1), 0xFF4B688F);
        panel.setBackground(panelBackground);
        TextView title = text("", 23, Color.WHITE);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);
        TextView detail = text("", 16, 0xFFF1F6FF);
        detail.setGravity(Gravity.CENTER);
        detail.setLineSpacing(0, 1.18f);
        detail.setPadding(dp(12), dp(18), dp(12), dp(18));
        panel.addView(detail);
        TextView step = text("", 13, 0xFFD6E7FF);
        step.setGravity(Gravity.CENTER);
        panel.addView(step);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        panelParams.leftMargin = dp(24);
        panelParams.rightMargin = dp(24);
        panelParams.bottomMargin = dp(62);
        overlay.addView(panel, panelParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(18), dp(12), dp(18), dp(18));
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button skip = button("跳過");
        Button next = button("下一步");
        actions.addView(skip, new LinearLayout.LayoutParams(0, dp(50), 1));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        nextParams.leftMargin = dp(10);
        actions.addView(next, nextParams);
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        overlay.addView(actions, actionParams);
        shell.addView(overlay, new FrameLayout.LayoutParams(-1, -1));

        int[] current = {0};
        Runnable close = () -> {
            shell.removeView(overlay);
            if (!replay) DrawData.setOnboardingSeen(this);
        };
        Runnable[] renderStep = new Runnable[1];
        renderStep[0] = () -> {
            title.setText(titles[current[0]]);
            detail.setText(details[current[0]]);
            step.setText((current[0] + 1) + " / " + titles.length);
            next.setText(current[0] == titles.length - 1 ? "開始使用" : "下一步");
        };
        skip.setOnClickListener(v -> close.run());
        next.setOnClickListener(v -> {
            if (current[0] >= titles.length - 1) close.run();
            else { current[0]++; renderStep[0].run(); }
        });
        renderStep[0].run();
    }

    private void recordsDialog() {
        StringBuilder message = new StringBuilder();
        for (DrawItem item : items) {
            if (item.status.equals("pending")) continue;
            message.append(item.store).append(" · ").append(item.product).append("：").append(statusLabel(item.status)).append('\n');
        }
        new AlertDialog.Builder(this).setTitle("抽選紀錄")
                .setMessage(message.length() == 0 ? "尚無紀錄" : message.toString())
                .setNeutralButton("全台重掃", (dialog, which) -> confirmReview())
                .setPositiveButton("掃描報告", (dialog, which) -> reportsDialog())
                .setNegativeButton("關閉", null).show();
    }

    private void testRecordsDialog() {
        ArrayList<DrawData.ReviewReportEntry> reports = DrawData.testReports(this);
        if (reports.isEmpty()) {
            StringBuilder current = new StringBuilder("尚無測試執行紀錄。\n\n");
            for (DrawItem item : items) {
                if (!item.origin.equals("test") || item.status.equals("pending")) continue;
                current.append("• ").append(item.product).append("：")
                        .append(statusLabel(item.status)).append('\n');
            }
            if (current.toString().equals("尚無測試執行紀錄。\n\n"))
                current.append("完成私人 LINE 測試或 K爸模擬驗收後，會在此保留最近三筆。");
            new AlertDialog.Builder(this).setTitle("測試紀錄")
                    .setMessage(current.toString()).setPositiveButton("知道了", null).show();
            return;
        }
        String[] labels = new String[reports.size()];
        SimpleDateFormat format = new SimpleDateFormat("M/d HH:mm", Locale.TAIWAN);
        for (int i = 0; i < reports.size(); i++) {
            DrawData.ReviewReportEntry report = reports.get(i);
            labels[i] = format.format(new Date(report.at)) + "　" + report.text.split("\\n", 2)[0];
        }
        new AlertDialog.Builder(this).setTitle("測試紀錄（最近三筆）")
                .setItems(labels, (dialog, which) -> {
                    DrawData.ReviewReportEntry report = reports.get(which);
                    new AlertDialog.Builder(this)
                            .setTitle(format.format(new Date(report.at)) + " 測試紀錄")
                            .setMessage(report.text).setPositiveButton("關閉", null).show();
                }).setNegativeButton("關閉", null).show();
    }

    private void reportsDialog() {
        ArrayList<DrawData.ReviewReportEntry> reports = DrawData.reviewReports(this);
        if (reports.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("掃描報告")
                    .setMessage("尚無全台重掃報告。完成掃描後會保留最近三筆。")
                    .setPositiveButton("知道了", null).show();
            return;
        }
        String[] labels = new String[reports.size()];
        SimpleDateFormat format = new SimpleDateFormat("M/d HH:mm", Locale.TAIWAN);
        for (int i = 0; i < reports.size(); i++) {
            DrawData.ReviewReportEntry report = reports.get(i);
            String first = report.text.split("\\n", 2)[0];
            labels[i] = format.format(new Date(report.at)) + "　" + first;
        }
        new AlertDialog.Builder(this).setTitle("掃描報告（最近三筆）")
                .setItems(labels, (dialog, which) -> showReport(reports.get(which)))
                .setNegativeButton("關閉", null).show();
    }

    private void showReport(DrawData.ReviewReportEntry report) {
        String title = new SimpleDateFormat("M/d HH:mm 掃描報告", Locale.TAIWAN).format(new Date(report.at));
        new AlertDialog.Builder(this).setTitle(title).setMessage(report.text)
                .setPositiveButton("關閉", null).show();
    }

    private void confirmReview() {
        if (!DrawAccessibilityService.available()) { showAccessibilityUnavailable(); return; }
        ArrayList<DrawItem> review = new ArrayList<>();
        ArrayList<DrawItem> awaitingResult = new ArrayList<>();
        int skippedKnown = 0;
        for (DrawItem item : items) {
            if (item.origin.equals("source") && DrawData.validLink(item.url)) {
                String outcome = effectiveOutcome(item);
                if (isKnownResult(outcome)) {
                    skippedKnown++;
                    continue;
                }
                review.add(item);
                if (item.status.equals("done") || item.status.equals("already") ||
                        isReviewProblem(outcome)) awaitingResult.add(item);
            }
        }
        if (review.isEmpty()) {
            Toast.makeText(this, "所有連結都已有明確結果，不需要重掃", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this).setTitle("掃描待確認項目 " + review.size() + " 筆？")
                .setMessage("已自動排除中獎、未中獎與已結束共 " + skippedKnown +
                        " 筆，不會重複掃描已知結果。\n\n只讀取未開賣、未加好友、未抽籤或結果待確認狀態；不按參加抽獎，也絕不按使用優惠券。模糊畫面會列為需確認。\n\n只查已送出但結果未知的 " + awaitingResult.size() + " 筆會更快。")
                .setPositiveButton("掃描待確認", (dialog, which) -> {
                    if (!DrawAccessibilityService.beginReview(review))
                        Toast.makeText(this, "已有流程正在執行，請先停止", Toast.LENGTH_LONG).show();
                })
                .setNeutralButton("只查已抽籤", (dialog, which) -> {
                    if (awaitingResult.isEmpty()) Toast.makeText(this, "目前沒有結果待確認的連結", Toast.LENGTH_LONG).show();
                    else if (!DrawAccessibilityService.beginReview(awaitingResult))
                        Toast.makeText(this, "已有流程正在執行，請先停止", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("取消", null).show();
    }

}
