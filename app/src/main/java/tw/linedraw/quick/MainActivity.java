package tw.linedraw.quick;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.DragEvent;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
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

public final class MainActivity extends Activity {
    private static final int REQUEST_WEEKLY_LICENSE = 4104;
    private final int navy = Color.rgb(22, 55, 99);
    private final int light = Color.rgb(248, 245, 239);
    private final int accent = Color.rgb(42, 117, 104);
    private final int mint = Color.rgb(216, 244, 232);
    private LinearLayout content;
    private LinearLayout controlsArea;
    private LinearLayout overview;
    private LinearLayout reviewPanel;
    private LinearLayout filterContainer;
    private LinearLayout filterRows;
    private LinearLayout filterEditor;
    private LinearLayout filterSummaryCard;
    private LinearLayout timeSection;
    private LinearLayout timeChoices;
    private LinearLayout resultsTools;
    private LinearLayout selectionArea;
    private LinearLayout list;
    private TextView summary;
    private TextView sourceTime;
    private ProgressRingView progressRing;
    private TextView selectedCount;
    private TextView filterSummaryText;
    private ImageView overviewCat;
    private Button cityFilter;
    private Button productFilter;
    private Button priorityButton;
    private Button favoriteFilter;
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
    private LinearLayout runPanel;
    private ImageView runIcon;
    private TextView runTitle;
    private TextView runMeta;
    private Button runPause;
    private ProgressBar runProgress;
    private ArrayList<DrawItem> items = new ArrayList<>();
    private Map<String, String> reviewOutcomes = new HashMap<>();
    private String reviewFilter = "all";
    private boolean testNeedsReviewOnly;
    private ArrayList<DrawItem> shownItems = new ArrayList<>();
    private int displayLimit = 100;
    private static final int DISPLAY_PAGE_SIZE = 100;
    private boolean lastSyncFailed;
    private boolean selectionDirty;
    private String mode = "all";
    private String selectedTime = "";
    private boolean selectedOnly;
    private boolean filterExpanded;
    private final LinkedHashSet<String> selectedCities = new LinkedHashSet<>();
    // Remembers a fully selected header only to include newly published cities after sync.
    private final LinkedHashSet<String> selectedAllCityGroups = new LinkedHashSet<>();
    private final LinkedHashSet<String> selectedProducts = new LinkedHashSet<>();
    private boolean cityFirst = true;
    private boolean refreshing;
    private boolean resumePromptShown;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final Runnable syncTicker = this::updateSyncButton;
    private final Runnable selectionWriter = this::flushSelectionSave;
    private final Runnable runUiTicker = new Runnable() {
        @Override public void run() {
            updateRunPanel();
            uiHandler.postDelayed(this, DrawData.lowMemoryMode(MainActivity.this) ? 750 : 350);
        }
    };
    private static final long SYNC_COOLDOWN_MS = 30_000L;

    private static final class ProgressRingView extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int percent;

        ProgressRingView(Activity activity) {
            super(activity);
            float density = getResources().getDisplayMetrics().density;
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(7 * density);
            track.setStrokeCap(Paint.Cap.ROUND);
            track.setColor(0xFFE7E0D5);
            progress.setStyle(Paint.Style.STROKE);
            progress.setStrokeWidth(7 * density);
            progress.setStrokeCap(Paint.Cap.ROUND);
            progress.setColor(0xFF183B68);
            label.setColor(0xFF163052);
            label.setTextAlign(Paint.Align.CENTER);
            label.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            label.setTextSize(12 * getResources().getDisplayMetrics().scaledDensity);
        }

        void setPercent(int value) {
            percent = Math.max(0, Math.min(100, value));
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float inset = track.getStrokeWidth() / 2f + 1;
            canvas.drawArc(inset, inset, getWidth() - inset, getHeight() - inset,
                    -90, 360, false, track);
            canvas.drawArc(inset, inset, getWidth() - inset, getHeight() - inset,
                    -90, percent * 3.6f, false, progress);
            Paint.FontMetrics fm = label.getFontMetrics();
            float baseline = getHeight() / 2f - (fm.ascent + fm.descent) / 2f;
            canvas.drawText(percent + "%", getWidth() / 2f, baseline, label);
        }
    }

    /** A cheap, static alternative to live blur for older and low-end phones. */
    private static final class AmbientBackgroundView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        AmbientBackgroundView(Activity activity) { super(activity); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawColor(0xFFF8F5EF);
            float w = getWidth();
            float h = getHeight();
            paint.setShader(new RadialGradient(w * .92f, h * .13f, w * .62f,
                    0x664FD1B5, 0x004FD1B5, Shader.TileMode.CLAMP));
            canvas.drawCircle(w * .92f, h * .13f, w * .62f, paint);
            paint.setShader(new RadialGradient(w * .04f, h * .72f, w * .7f,
                    0x334F78A8, 0x004F78A8, Shader.TileMode.CLAMP));
            canvas.drawCircle(w * .04f, h * .72f, w * .7f, paint);
            paint.setShader(null);
        }
    }

    @Override protected void onCreate(Bundle state) {
        setTheme(R.style.AppTheme);
        super.onCreate(state);
        if (!DrawAccessibilityService.isRunning()) OrientationGuard.restore(this);
        boolean resetForUpdate = DrawData.applyV041Reset(this);
        getWindow().setStatusBarColor(light);
        getWindow().setNavigationBarColor(light);
        if (Build.VERSION.SDK_INT >= 23)
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        buildScreen();
        showBrandIntro(!DrawData.onboardingSeen(this));
        if (resetForUpdate) uiHandler.post(() -> Toast.makeText(this,
                "v0.4.1 已清除上一版的本周資料，請完成認證後重新同步", Toast.LENGTH_LONG).show());
        if (DrawData.refreshedAt(this) == 0 && WeeklyLicense.status(this).valid) refreshSource();
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_WEEKLY_LICENSE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        importLicense(data.getData());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingLicense(intent);
    }

    private void handleIncomingLicense(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Uri uri = null;
        if (Intent.ACTION_VIEW.equals(action)) uri = intent.getData();
        else if (Intent.ACTION_SEND.equals(action)) {
            Object shared = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (shared instanceof Uri) uri = (Uri) shared;
            if (uri == null && intent.getClipData() != null && intent.getClipData().getItemCount() > 0)
                uri = intent.getClipData().getItemAt(0).getUri();
        }
        if (uri == null) return;
        intent.setAction(null);
        importLicense(uri);
    }

    private void importLicense(Uri uri) {
        try (java.io.InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalArgumentException("無法讀取檔案");
            WeeklyLicense.Status status = WeeklyLicense.importFrom(this, input);
            render();
            new AlertDialog.Builder(this).setTitle("洗頭機 KBA 已匯入")
                    .setMessage("✓ 認證成功\n" + status.detail + "\n" + WeeklyLicense.remainingLabel(status) +
                            "\n\n現在可以同步並執行本週正式任務。")
                    .setPositiveButton("知道了", null).show();
        } catch (Exception error) {
            new AlertDialog.Builder(this).setTitle("認證匯入失敗")
                    .setMessage(error.getMessage() == null ? "請重新下載本週 .kba 認證檔。" : error.getMessage())
                    .setPositiveButton("重新選擇", (dialog, which) -> openLicensePicker())
                    .setNegativeButton("關閉", null).show();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        DrawAccessibilityService.setHostAppForeground(true);
        render();
        uiHandler.removeCallbacks(runUiTicker);
        uiHandler.post(runUiTicker);
        String report = DrawData.takeReviewReport(this);
        if (!report.isEmpty()) uiHandler.post(() -> new AlertDialog.Builder(this)
                .setTitle(report.startsWith("手動連結") ? "手動抽選核對" : "全台重掃結果")
                .setMessage(report).setPositiveButton("知道了", null).show());
        else {
            String runReport = DrawData.takeRunReport(this);
            boolean testRun = DrawData.takeRunReportWasTest(this);
            if (!runReport.isEmpty()) {
                if (testRun) setMode("test");
                uiHandler.post(() -> new AlertDialog.Builder(this)
                        .setTitle("本次抽選完成")
                        .setMessage(runReport)
                        .setPositiveButton(testRun ? "查看測試紀錄" : "查看結果",
                                (dialog, which) -> {
                                    if (testRun) {
                                        setMode("test");
                                        testRecordsDialog();
                                    } else setMode("done");
                                })
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
        uiHandler.removeCallbacks(runUiTicker);
        super.onDestroy();
    }

    @Override protected void onPause() {
        DrawAccessibilityService.setHostAppForeground(false);
        uiHandler.removeCallbacks(runUiTicker);
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

    private String appVersionName() {
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            return version == null || version.trim().isEmpty() ? "?" : version;
        } catch (Exception ignored) {
            return "?";
        }
    }

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
        view.setIncludeFontPadding(false);
        return view;
    }

    private Button button(String value) {
        Button view = new Button(this);
        view.setText(value);
        view.setAllCaps(false);
        view.setTextSize(13);
        view.setTextColor(navy);
        view.setIncludeFontPadding(false);
        view.setMinWidth(0);
        view.setMinHeight(0);
        view.setStateListAnimator(null);
        view.setOnTouchListener((target, event) -> {
            if (!target.isEnabled()) return false;
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                target.animate().scaleX(.97f).scaleY(.97f).setDuration(80)
                        .setInterpolator(new DecelerateInterpolator()).start();
            } else if (event.getActionMasked() == MotionEvent.ACTION_UP ||
                    event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                target.animate().scaleX(1f).scaleY(1f).setDuration(140)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
            return false;
        });
        return view;
    }

    private GradientDrawable panelBackground(int color) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(22));
        background.setStroke(dp(1), 0x99FFFFFF);
        return background;
    }

    private GradientDrawable roundedBackground(int color, int stroke, float radius) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        background.setCornerRadius(dp(radius));
        if (stroke != Color.TRANSPARENT) background.setStroke(dp(1), stroke);
        return background;
    }

    private void buildScreen() {
        shell = new FrameLayout(this);
        shell.setBackgroundColor(light);
        shell.setOnApplyWindowInsetsListener((view, windowInsets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                Insets bars = windowInsets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(windowInsets.getSystemWindowInsetLeft(), windowInsets.getSystemWindowInsetTop(),
                        windowInsets.getSystemWindowInsetRight(), windowInsets.getSystemWindowInsetBottom());
            }
            return windowInsets;
        });
        setContentView(shell);
        FrameLayout safeContent = new FrameLayout(this);
        safeContent.setBackgroundColor(light);
        shell.addView(safeContent, new FrameLayout.LayoutParams(-1, -1));
        safeContent.addView(new AmbientBackgroundView(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout root = vertical();
        safeContent.addView(root, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout header = vertical();
        header.setPadding(dp(14), dp(10), dp(14), dp(10));
        header.setBackground(roundedBackground(0xEFFFFFFF, 0xAAFFFFFF, 0));
        LinearLayout headerTop = new LinearLayout(this);
        headerTop.setGravity(Gravity.CENTER_VERTICAL);
        headerTop.setMinimumHeight(dp(46));
        ImageView appMark = new ImageView(this);
        appMark.setImageResource(R.drawable.fangba_app_icon);
        appMark.setScaleType(ImageView.ScaleType.CENTER_CROP);
        appMark.setBackground(roundedBackground(0xFFFFFFFF, Color.TRANSPARENT, 10));
        appMark.setClipToOutline(true);
        LinearLayout.LayoutParams markParams = new LinearLayout.LayoutParams(dp(38), dp(38));
        markParams.rightMargin = dp(9);
        headerTop.addView(appMark, markParams);
        LinearLayout brand = vertical();
        TextView title = text("K爸全自動洗頭機", 20, navy);
        title.setTypeface(null, Typeface.BOLD);
        title.setSingleLine(true);
        brand.addView(title);
        TextView subtitle = text("今天也一起順順抽 · v" + appVersionName(), 11, 0xFF667A76);
        subtitle.setSingleLine(true);
        brand.addView(subtitle);
        headerTop.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        syncButton = button("⟳ 同步");
        syncButton.setTextSize(12);
        syncButton.setTextColor(0xFF176455);
        syncButton.setBackground(roundedBackground(0xFFE1F7ED, 0x99FFFFFF, 99));
        syncButton.setPadding(dp(5), 0, dp(5), 0);
        syncButton.setOnClickListener(v -> refreshSource());
        headerTop.addView(syncButton, new LinearLayout.LayoutParams(dp(74), dp(38)));
        Button gear = button("⚙");
        gear.setContentDescription("設定");
        gear.setTextSize(22);
        gear.setTextColor(navy);
        gear.setBackground(roundedBackground(0xAAEDF2F2, 0x99FFFFFF, 99));
        gear.setOnClickListener(v -> settingsDialog());
        LinearLayout.LayoutParams gearParams = new LinearLayout.LayoutParams(dp(42), dp(38));
        gearParams.leftMargin = dp(5);
        headerTop.addView(gear, gearParams);
        header.addView(headerTop);
        root.addView(header);

        LinearLayout modes = new LinearLayout(this);
        modes.setPadding(dp(5), dp(5), dp(5), dp(5));
        modes.setBackground(roundedBackground(0xDFFFFFFF, 0xAAFFFFFF, 18));
        allTab = addModeButton(modes, "任務", "all");
        testTab = addModeButton(modes, "結果", "done");
        readyTab = addModeButton(modes, "測試", "test");
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(-1, dp(54));
        modeParams.setMargins(dp(12), dp(8), dp(12), 0);
        root.addView(modes, modeParams);
        styleTabs();

        FrameLayout mainBody = new FrameLayout(this);
        root.addView(mainBody, new LinearLayout.LayoutParams(-1, 0, 1));
        scroll = new ScrollView(this);
        mainBody.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        content = vertical();
        content.setPadding(dp(12), dp(10), dp(12), dp(18));
        scroll.addView(content);

        overview = new LinearLayout(this);
        overview.setOrientation(LinearLayout.HORIZONTAL);
        overview.setGravity(Gravity.CENTER_VERTICAL);
        overview.setPadding(dp(18), dp(15), dp(10), dp(12));
        overview.setBackground(panelBackground(0xEFFFFFFF));
        overview.setElevation(dp(2));
        overview.setMinimumHeight(dp(96));
        LinearLayout overviewCopy = vertical();
        overviewCopy.setGravity(Gravity.CENTER_VERTICAL);
        summary = text("載入中", 20, navy);
        summary.setTypeface(null, Typeface.BOLD);
        summary.setSingleLine(true);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        summary.setMinHeight(dp(32));
        overviewCopy.addView(summary);
        sourceTime = text("", 12, 0xFF718096);
        sourceTime.setMaxLines(3);
        sourceTime.setGravity(Gravity.TOP);
        overviewCopy.addView(sourceTime);
        TextView credit = text("by ken · 20220647", 9, 0xFF9AA8A0);
        credit.setGravity(Gravity.START);
        credit.setPadding(0, dp(5), 0, 0);
        overviewCopy.addView(credit);
        overview.addView(overviewCopy, new LinearLayout.LayoutParams(0, -1, 1));
        FrameLayout overviewVisual = new FrameLayout(this);
        overviewCat = new ImageView(this);
        overviewCat.setImageResource(R.drawable.ken_cat_home);
        overviewCat.setScaleType(ImageView.ScaleType.FIT_CENTER);
        overviewVisual.addView(overviewCat, new FrameLayout.LayoutParams(-1, -1));
        progressRing = new ProgressRingView(this);
        progressRing.setBackground(roundedBackground(0xF7FFFFFF, 0xFFD8E0DC, 99));
        progressRing.setElevation(dp(3));
        FrameLayout.LayoutParams ringParams = new FrameLayout.LayoutParams(dp(54), dp(54), Gravity.RIGHT | Gravity.BOTTOM);
        overviewVisual.addView(progressRing, ringParams);
        overview.addView(overviewVisual, new LinearLayout.LayoutParams(dp(118), dp(112)));
        content.addView(overview, new LinearLayout.LayoutParams(-1, dp(146)));

        reviewPanel = vertical();
        reviewPanel.setPadding(dp(12), dp(10), dp(12), dp(9));
        reviewPanel.setBackground(panelBackground(0xEFFFFFFF));
        reviewPanel.setElevation(dp(1));
        LinearLayout.LayoutParams reviewParams = new LinearLayout.LayoutParams(-1, -2);
        reviewParams.topMargin = dp(8);
        content.addView(reviewPanel, reviewParams);
        reviewPanel.setVisibility(View.INVISIBLE);

        controlsArea = vertical();
        // Only the page-specific controls change. The title, tabs, overview and
        // status cards above stay fixed; the content below uses its natural
        // height instead of manufacturing a large empty spacer.
        content.addView(controlsArea, new LinearLayout.LayoutParams(-1, -2));

        addTest = button("＋ 新增測試");
        addTest.setTextColor(navy);
        addTest.setBackground(roundedBackground(0xEFFFFFFF, 0xAAFFFFFF, 18));
        addTest.setOnClickListener(v -> testLinksDialog());
        LinearLayout.LayoutParams addTestParams = new LinearLayout.LayoutParams(-1, dp(48));
        addTestParams.topMargin = dp(10);
        controlsArea.addView(addTest, addTestParams);
        mockTest = button("K爸模擬驗收 · 不會有真實訂單");
        mockTest.setTextColor(navy);
        mockTest.setBackground(roundedBackground(0xFFE1F7ED, 0xAAFFFFFF, 18));
        mockTest.setOnClickListener(v -> mockDialog());
        LinearLayout.LayoutParams mockParams = new LinearLayout.LayoutParams(-1, dp(48));
        mockParams.topMargin = dp(7);
        controlsArea.addView(mockTest, mockParams);
        resultsTools = vertical();
        resultsTools.setPadding(0, dp(12), 0, dp(8));
        TextView resultHint = text("點一下分類，就能找到想看的結果。", 13, 0xFF53657A);
        resultsTools.addView(resultHint);
        Button reports = button("查看最近三筆掃描報告");
        reports.setOnClickListener(v -> reportsDialog());
        resultsTools.addView(reports, new LinearLayout.LayoutParams(-1, dp(48)));
        Button review = button("只檢查還不確定的項目");
        review.setOnClickListener(v -> confirmReview());
        resultsTools.addView(review, new LinearLayout.LayoutParams(-1, dp(48)));
        controlsArea.addView(resultsTools);

        filterContainer = vertical();
        filterContainer.setBackground(roundedBackground(0xEFFFFFFF, 0xAAFFFFFF, 20));
        LinearLayout.LayoutParams filterContainerParams = new LinearLayout.LayoutParams(-1, -2);
        filterContainerParams.topMargin = dp(9);
        controlsArea.addView(filterContainer, filterContainerParams);

        filterSummaryCard = new LinearLayout(this);
        filterSummaryCard.setGravity(Gravity.CENTER_VERTICAL);
        filterSummaryCard.setPadding(dp(14), dp(10), dp(8), dp(10));
        filterSummaryCard.setBackgroundColor(Color.TRANSPARENT);
        filterSummaryText = text("全部縣市 · 全部品項 · 全部時間", 13, navy);
        filterSummaryText.setTypeface(null, Typeface.BOLD);
        filterSummaryText.setSingleLine(true);
        filterSummaryText.setEllipsize(TextUtils.TruncateAt.END);
        filterSummaryCard.addView(filterSummaryText, new LinearLayout.LayoutParams(0, dp(38), 1));
        Button adjustFilter = button("調整");
        adjustFilter.setTextSize(12);
        adjustFilter.setBackground(roundedBackground(mint, 0x99FFFFFF, 99));
        filterSummaryCard.addView(adjustFilter, new LinearLayout.LayoutParams(dp(66), dp(38)));
        View.OnClickListener toggleFilters = v -> {
            filterExpanded = !filterExpanded;
            filterEditor.setVisibility(filterExpanded ? View.VISIBLE : View.GONE);
            adjustFilter.setText(filterExpanded ? "收起" : "調整");
        };
        filterSummaryCard.setOnClickListener(toggleFilters);
        adjustFilter.setOnClickListener(toggleFilters);
        filterContainer.addView(filterSummaryCard, new LinearLayout.LayoutParams(-1, dp(58)));

        filterEditor = vertical();
        filterEditor.setPadding(dp(12), dp(10), dp(12), dp(8));
        filterEditor.setBackgroundColor(Color.TRANSPARENT);
        filterContainer.addView(filterEditor, new LinearLayout.LayoutParams(-1, -2));
        filterRows = vertical();
        filterEditor.addView(filterRows);
        cityFilter = filterButton("全部縣市");
        productFilter = filterButton("全部品項");
        priorityButton = filterButton("縣市優先");
        favoriteFilter = filterButton("♡ 儲存喜好");
        favoriteFilter.setGravity(Gravity.CENTER);
        favoriteFilter.setTextSize(11);
        favoriteFilter.setPadding(dp(5), 0, dp(5), 0);
        favoriteFilter.setContentDescription("儲存或套用喜好篩選");
        filterRows.addView(labelWithControl("縣市（可複選）", cityFilter));
        filterRows.addView(labelWithControl("品項（可複選）", productFilter));
        filterRows.addView(priorityWithFavorite());
        cityFilter.setOnClickListener(v -> chooseCities());
        productFilter.setOnClickListener(v -> chooseProducts());
        priorityButton.setOnClickListener(v -> choosePriority());
        favoriteFilter.setOnClickListener(v -> useFavoriteFilter());
        favoriteFilter.setOnLongClickListener(v -> {
            manageFavoriteFilter();
            return true;
        });

        timeSection = vertical();
        timeSection.setPadding(0, dp(6), 0, dp(5));
        timeSection.addView(text("開抽時間", 13, navy));
        HorizontalScrollView timeScroll = new HorizontalScrollView(this);
        timeScroll.setHorizontalScrollBarEnabled(false);
        timeChoices = new LinearLayout(this);
        timeChoices.setGravity(Gravity.CENTER_VERTICAL);
        timeScroll.addView(timeChoices);
        timeSection.addView(timeScroll, new LinearLayout.LayoutParams(-1, dp(42)));
        filterEditor.addView(timeSection);
        filterEditor.setVisibility(View.GONE);

        selectionArea = vertical();
        selectionArea.setPadding(dp(12), dp(10), dp(12), dp(8));
        selectionArea.setBackground(roundedBackground(0xDFFFFFFF, 0xAAFFFFFF, 18));
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
        LinearLayout.LayoutParams selectionParams = new LinearLayout.LayoutParams(-1, dp(94));
        selectionParams.topMargin = dp(8);
        content.addView(selectionArea, selectionParams);
        list = vertical();
        content.addView(list);

        buildRunPanel(mainBody);

        LinearLayout footer = new LinearLayout(this);
        footer.setPadding(dp(8), dp(8), dp(8), dp(8));
        footer.setBackground(roundedBackground(0xF2FFFFFF, 0xAAFFFFFF, 20));
        footer.setElevation(dp(10));
        records = button("紀錄");
        records.setBackground(roundedBackground(0xFFF2F5F4, 0xFFDEE6E2, 14));
        records.setOnClickListener(v -> {
            if (mode.equals("done")) reportsDialog();
            else if (mode.equals("test")) testRecordsDialog();
            else recordsDialog();
        });
        footer.addView(records, new LinearLayout.LayoutParams(0, dp(52), 1));
        start = button("開始連抽");
        start.setTextColor(Color.WHITE);
        start.setTypeface(null, Typeface.BOLD);
        start.setBackground(roundedBackground(navy, Color.TRANSPARENT, 14));
        start.setOnClickListener(v -> {
            if (mode.equals("done")) confirmReview();
            else startRun();
        });
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(0, dp(52), 2);
        startParams.leftMargin = dp(7);
        footer.addView(start, startParams);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(-1, dp(68));
        footerParams.setMargins(dp(10), dp(4), dp(10), dp(8));
        root.addView(footer, footerParams);
    }

    private void buildRunPanel(FrameLayout parent) {
        runPanel = vertical();
        runPanel.setPadding(dp(10), dp(9), dp(10), dp(7));
        runPanel.setBackground(roundedBackground(0xF5FFFFFF, 0xAAFFFFFF, 20));
        runPanel.setElevation(dp(10));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        runIcon = new ImageView(this);
        runIcon.setImageResource(R.drawable.fangba_app_icon);
        runIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        runIcon.setBackground(roundedBackground(mint, Color.TRANSPARENT, 12));
        runIcon.setClipToOutline(true);
        LinearLayout.LayoutParams runIconParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        runIconParams.rightMargin = dp(9);
        row.addView(runIcon, runIconParams);
        LinearLayout labels = vertical();
        runTitle = text("準備執行", 13, 0xFF11223B);
        runTitle.setTypeface(null, Typeface.BOLD);
        runTitle.setSingleLine(true);
        runTitle.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(runTitle);
        runMeta = text("等待任務狀態", 10, 0xFF6D7F97);
        runMeta.setSingleLine(true);
        runMeta.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(runMeta);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        runPause = button("暫停");
        runPause.setTextSize(11);
        runPause.setTextColor(0xFF163052);
        runPause.setBackground(roundedBackground(mint, 0xFFBFDCCF, 99));
        runPause.setOnClickListener(v -> DrawAccessibilityService.togglePauseCurrent());
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(dp(62), dp(38));
        pauseParams.leftMargin = dp(7);
        row.addView(runPause, pauseParams);
        Button runStop = button("停止");
        runStop.setTextSize(11);
        runStop.setTextColor(0xFFB44740);
        runStop.setBackground(roundedBackground(0xFFFFEFEF, 0xFFEFCBC8, 99));
        runStop.setOnClickListener(v -> DrawAccessibilityService.stopCurrent());
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(dp(58), dp(38));
        stopParams.leftMargin = dp(6);
        row.addView(runStop, stopParams);
        runPanel.addView(row);
        runProgress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        runProgress.setProgressTintList(ColorStateList.valueOf(0xFF315F9D));
        runProgress.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFE5EBF3));
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(-1, dp(5));
        progressParams.topMargin = dp(7);
        runPanel.addView(runProgress, progressParams);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        panelParams.setMargins(dp(8), dp(8), dp(8), 0);
        parent.addView(runPanel, panelParams);
        runPanel.setVisibility(View.GONE);
    }

    private void updateRunPanel() {
        if (runPanel == null) return;
        DrawAccessibilityService.RunUiSnapshot snapshot = DrawAccessibilityService.uiSnapshot();
        boolean useNativeFallback = snapshot.running && !DrawAccessibilityService.hasPersistentOverlay();
        runPanel.setVisibility(useNativeFallback ? View.VISIBLE : View.GONE);
        if (!useNativeFallback) return;
        String product = snapshot.product.isEmpty() ? snapshot.phase : snapshot.product;
        runTitle.setText(snapshot.current + " / " + snapshot.total + " · " + product);
        StringBuilder meta = new StringBuilder(snapshot.phase);
        if (snapshot.remainingMs > 0)
            meta.append(String.format(Locale.TAIWAN, " · 剩 %.1f 秒", snapshot.remainingMs / 1000.0));
        meta.append(" · 耗時 ").append(formatDuration(snapshot.elapsedMs));
        if (snapshot.averageSeconds > 0)
            meta.append(String.format(Locale.TAIWAN, " · %.1f 秒/筆", snapshot.averageSeconds));
        runMeta.setText(meta.toString());
        runPause.setText(snapshot.paused ? "繼續" : "暫停");
        if (runIcon != null) runIcon.setAlpha(snapshot.paused ? 0.58f : 1f);
        runProgress.setMax(Math.max(1, snapshot.total));
        runProgress.setProgress(Math.max(snapshot.processed, snapshot.current));
    }

    private String formatDuration(long milliseconds) {
        long totalSeconds = Math.max(0, milliseconds / 1000);
        return String.format(Locale.TAIWAN, "%02d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private Button addModeButton(LinearLayout parent, String title, String value) {
        Button view = button(title);
        view.setOnClickListener(v -> setMode(value));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        parent.addView(view, params);
        return view;
    }

    private void styleTabs() {
        if (allTab == null || testTab == null || readyTab == null) return;
        allTab.setBackground(roundedBackground(mode.equals("all") ? navy : Color.TRANSPARENT,
                Color.TRANSPARENT, 99));
        allTab.setTextColor(mode.equals("all") ? Color.WHITE : navy);
        testTab.setBackground(roundedBackground(mode.equals("done") ? navy : Color.TRANSPARENT,
                Color.TRANSPARENT, 99));
        testTab.setTextColor(mode.equals("done") ? Color.WHITE : navy);
        readyTab.setBackground(roundedBackground(mode.equals("test") ? navy : Color.TRANSPARENT,
                Color.TRANSPARENT, 99));
        readyTab.setTextColor(mode.equals("test") ? Color.WHITE : navy);
    }

    private void setMode(String value) {
        mode = value;
        reviewFilter = "all";
        testNeedsReviewOnly = false;
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
        background.setColor(0xEEFFFFFF);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), 0xFFE0E6E2);
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

    private LinearLayout priorityWithFavorite() {
        LinearLayout row = vertical();
        row.setPadding(0, dp(7), 0, dp(5));
        row.addView(text("抽選順序", 13, navy));
        LinearLayout controls = new LinearLayout(this);
        controls.addView(priorityButton, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams favoriteParams = new LinearLayout.LayoutParams(dp(94), dp(44));
        favoriteParams.leftMargin = dp(6);
        controls.addView(favoriteFilter, favoriteParams);
        row.addView(controls, new LinearLayout.LayoutParams(-1, dp(44)));
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

    private int interruptedSelectionCount() {
        int count = 0;
        for (DrawItem item : DrawData.load(this))
            if (item.selected && RunScope.eligible(item)) count++;
        return count;
    }

    private void offerInterruptedRun() {
        if (resumePromptShown || DrawAccessibilityService.isRunning() || isFinishing()) return;
        int remaining = interruptedSelectionCount();
        if (remaining == 0) return;
        resumePromptShown = true;
        new AlertDialog.Builder(this).setTitle("找到上次未完成的任務")
                .setMessage("仍有 " + remaining + " 筆保持勾選。已完成的項目不會重新執行，可直接查看並接續。")
                .setPositiveButton("查看並繼續", (dialog, which) -> {
                    mode = "all";
                    selectedOnly = true;
                    render();
                    scroll.post(() -> scroll.smoothScrollTo(0, selectionArea.getTop()));
                })
                .setNeutralButton("取消這批", (dialog, which) -> {
                    DrawData.clearSelections(this);
                    render();
                })
                .setNegativeButton("稍後", null).show();
    }

    private void updateReviewPanel() {
        if (reviewPanel == null) return;
        reviewPanel.removeAllViews();
        int wins = 0;
        int losses = 0;
        int ended = 0;
        int scheduled = 0;
        int notFriend = 0;
        int busy = 0;
        int notDrawn = 0;
        int problem = 0;
        for (DrawItem item : items) {
            if (!item.origin.equals("source")) continue;
            // The draw tab reflects actionable item state only. Historical scan
            // conclusions belong to the Results tab and must not inflate the
            // "待重新檢查" count here.
            String outcome = mode.equals("all")
                    ? (item.status.equals("pending") ? "not_drawn" : item.status)
                    : effectiveOutcome(item);
            if (isReviewWin(outcome)) wins++;
            else if (outcome.equals("lose")) losses++;
            else if (outcome.equals("expired")) ended++;
            else if (outcome.equals("scheduled")) scheduled++;
            else if (outcome.equals("not_friend")) notFriend++;
            else if (outcome.equals("busy")) busy++;
            else if (isReviewNotDrawn(outcome)) notDrawn++;
            else if (isReviewProblem(outcome)) problem++;
        }
        reviewPanel.setVisibility(View.VISIBLE);
        if (mode.equals("all")) {
            int pendingTotal = 0;
            int selectedNow = 0;
            Set<String> filteredStores = new HashSet<>();
            ArrayList<DrawItem> filtered = visibleScope();
            for (DrawItem item : items)
                if (item.origin.equals("source") && RunScope.eligible(item)) pendingTotal++;
            for (DrawItem item : filtered) {
                if (!RunScope.eligible(item)) continue;
                filteredStores.add(RegionCatalog.canonicalCity(item.city) + "|" + item.store);
                if (item.selected) selectedNow++;
            }
            TextView title = text("這次想抽什麼？", 14, navy);
            title.setTypeface(null, Typeface.BOLD);
            reviewPanel.addView(title);
            LinearLayout statusRow = new LinearLayout(this);
            addStatusCell(statusRow, String.valueOf(pendingTotal), "未抽籤", 0xFFE8F2FF, 0xFF315F9B);
            addStatusCell(statusRow, String.valueOf(filtered.size()), "最想要", 0xFFE8F6EE, 0xFF167047);
            addStatusCell(statusRow, String.valueOf(filteredStores.size()), "店家", 0xFFF1ECFA, 0xFF67508B);
            addStatusCell(statusRow, String.valueOf(selectedNow), "本次已選", 0xFFFFF4DB, 0xFF946007);
            reviewPanel.addView(statusRow, new LinearLayout.LayoutParams(-1, dp(58)));
            addReadinessCard(null);
            return;
        }
        if (mode.equals("test")) {
            int testTotal = 0;
            int testHandled = 0;
            int testProblem = 0;
            for (DrawItem item : items) if (item.origin.equals("test")) {
                testTotal++;
                if (!RunScope.eligible(item)) testHandled++;
                if (item.status.equals("error")) testProblem++;
            }
            TextView title = text("先試跑，正式抽更安心", 14, navy);
            title.setTypeface(null, Typeface.BOLD);
            reviewPanel.addView(title);
            LinearLayout statusRow = new LinearLayout(this);
            addStatusCell(statusRow, String.valueOf(testTotal), "私人測試", 0xFFFFFFFF, navy);
            addStatusCell(statusRow, "30", "K爸模擬", 0xFFFFFFFF, navy);
            addStatusCell(statusRow, String.valueOf(testHandled), "已處理", 0xFFE6F5ED, 0xFF167047);
            addTestAttentionCell(statusRow, testProblem);
            reviewPanel.addView(statusRow, new LinearLayout.LayoutParams(-1, dp(58)));
            return;
        }
        int action = scheduled + notFriend + busy + notDrawn + problem;
        TextView title = text("抽選結果", 14, navy);
        title.setTypeface(null, Typeface.BOLD);
        reviewPanel.addView(title);
        LinearLayout first = new LinearLayout(this);
        LinearLayout second = new LinearLayout(this);
        addResultFilter(first, "全部", "all");
        addResultFilter(first, "已中獎 " + wins, "win");
        addResultFilter(first, "未中獎 " + losses, "lose");
        addResultFilter(first, "需處理 " + action, "action");
        addResultFilter(second, "未抽籤 " + notDrawn, "not_drawn");
        addResultFilter(second, "未開賣 " + scheduled, "scheduled");
        addResultFilter(second, "未加好友 " + notFriend, "not_friend");
        addResultFilter(second, "已結束 " + ended, "expired");
        reviewPanel.addView(first, new LinearLayout.LayoutParams(-1, dp(36)));
        reviewPanel.addView(second, new LinearLayout.LayoutParams(-1, dp(36)));
    }

    private void addStatusCell(LinearLayout row, String number, String label, int backgroundColor, int textColor) {
        LinearLayout cell = vertical();
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(roundedBackground(backgroundColor, 0xAAFFFFFF, 14));
        TextView count = text(number, 14, textColor);
        count.setTypeface(null, Typeface.BOLD);
        count.setGravity(Gravity.CENTER);
        TextView name = text(label, 10, 0xFF6D7F97);
        name.setGravity(Gravity.CENTER);
        cell.addView(count);
        cell.addView(name);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        params.topMargin = dp(4);
        row.addView(cell, params);
    }

    private void addTestAttentionCell(LinearLayout row, int problemCount) {
        FrameLayout slot = new FrameLayout(this);
        LinearLayout cell = vertical();
        cell.setGravity(Gravity.CENTER);
        int background = testNeedsReviewOnly ? 0xFFFFE8E8 : 0xFFFFF4DB;
        cell.setBackground(roundedBackground(background, 0xAAFFFFFF, 14));
        TextView count = text(String.valueOf(problemCount), 14,
                testNeedsReviewOnly ? 0xFFB3261E : 0xFF946007);
        count.setTypeface(null, Typeface.BOLD);
        count.setGravity(Gravity.CENTER);
        TextView name = text("需確認", 10, testNeedsReviewOnly ? 0xFFB3261E : 0xFF6D7F97);
        name.setGravity(Gravity.CENTER);
        cell.addView(count);
        cell.addView(name);
        slot.addView(cell, new FrameLayout.LayoutParams(-1, -1));

        if (problemCount > 0) {
            TextView badge = text("!", 13, Color.WHITE);
            badge.setTypeface(null, Typeface.BOLD);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(roundedBackground(0xFFE53935, 0xFFFFFFFF, 99));
            badge.setElevation(dp(4));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(dp(22), dp(22),
                    Gravity.TOP | Gravity.RIGHT);
            slot.addView(badge, badgeParams);
            slot.setContentDescription("有 " + problemCount + " 筆需確認，點一下篩選");
        } else if (!testNeedsReviewOnly) {
            slot.setContentDescription("目前沒有需確認資料");
        }
        if (problemCount > 0 || testNeedsReviewOnly) {
            slot.setOnClickListener(v -> {
                testNeedsReviewOnly = !testNeedsReviewOnly;
                selectedOnly = false;
                displayLimit = displayPageSize();
                updateReviewPanel();
                renderList();
                Toast.makeText(this, testNeedsReviewOnly
                        ? "已只顯示需確認的 " + problemCount + " 筆"
                        : "已顯示全部測試資料", Toast.LENGTH_SHORT).show();
            });
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(50), 1);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        params.topMargin = dp(4);
        row.addView(slot, params);
    }

    private void addResultFilter(LinearLayout row, String label, String filter) {
        Button chip = button(label);
        chip.setTextSize(10);
        boolean active = reviewFilter.equals(filter);
        chip.setBackground(roundedBackground(active ? navy : 0xFFECEFEA,
                Color.TRANSPARENT, 99));
        chip.setTextColor(active ? Color.WHITE : navy);
        chip.setOnClickListener(v -> setReviewFilter(filter));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(34), 1);
        params.leftMargin = dp(2);
        params.rightMargin = dp(2);
        row.addView(chip, params);
    }

    private String effectiveOutcome(DrawItem item) {
        String outcome = reviewOutcomes.getOrDefault(item.key(), "");
        if (!outcome.isEmpty()) return outcome;
        if (item.status.equals("pending")) return "not_drawn";
        if (item.status.equals("busy")) return "busy";
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
        if (reviewFilter.equals("action")) return isReviewNotDrawn(outcome) || isReviewProblem(outcome) ||
                outcome.equals("scheduled") || outcome.equals("not_friend") || outcome.equals("busy");
        return reviewFilter.equals(outcome);
    }

    private void addReadinessCard(String suffix) {
        int problems = environmentProblemCount(true);
        String label = problems == 0 ? "洗頭貓貓準備好了嗎？　OK！" :
                "洗頭貓貓準備好了嗎？　NO！";
        TextView readiness = text(label, 16, problems == 0 ? 0xFF12683A : 0xFF8A5B08);
        readiness.setTypeface(null, Typeface.BOLD);
        readiness.setGravity(Gravity.CENTER);
        readiness.setPadding(dp(9), 0, dp(9), 0);
        readiness.setSingleLine(true);
        readiness.setBackground(roundedBackground(problems == 0 ? 0xFFE8F6EE : 0xFFFFF6DF,
                problems == 0 ? 0xFFCDE8D8 : 0xFFEFD9AB, 14));
        readiness.setOnClickListener(v -> showEnvironmentHealth());
        reviewPanel.addView(readiness, new LinearLayout.LayoutParams(-1, dp(46)));
    }

    private int environmentProblemCount(boolean requireLicense) {
        int count = 0;
        if (requireLicense && !WeeklyLicense.status(this).valid) count++;
        if (!PermissionHealth.accessibilityEnabled(this) || !DrawAccessibilityService.available()) count++;
        if (!PermissionHealth.lineInstalled(this)) count++;
        if (!Settings.canDrawOverlays(this)) count++;
        if (!OrientationGuard.canLock(this)) count++;
        return count;
    }

    private String environmentChecklist(boolean requireLicense, int selected) {
        WeeklyLicense.Status license = WeeklyLicense.status(this);
        return (requireLicense ? (license.valid ? "✓" : "✕") + " 洗頭機 KBA：" +
                (license.valid ? WeeklyLicense.remainingLabel(license) : license.label) + "\n" : "") +
                (DrawAccessibilityService.available() ? "✓" : "✕") + " 輔助服務：" + PermissionHealth.shortStatus(this) + "\n" +
                (PermissionHealth.lineInstalled(this) ? "✓" : "✕") + " LINE：" +
                (PermissionHealth.lineInstalled(this) ? "已安裝" : "未偵測到") + "\n" +
                (Settings.canDrawOverlays(this) ? "✓" : "△") + " 浮動進度列：" +
                (Settings.canDrawOverlays(this) ? "已授權" : "尚未授權") + "\n" +
                (OrientationGuard.canLock(this) ? "✓" : "△") + " LINE 直式鎖定：" +
                (OrientationGuard.canLock(this) ? "已授權" : "尚未授權") +
                (selected >= 0 ? "\n✓ 本次任務：" + selected + " 筆" : "");
    }

    private void showEnvironmentHealth() {
        new AlertDialog.Builder(this).setTitle("開抽前健檢")
                .setMessage(environmentChecklist(true, -1) +
                        "\n\n✕ 必須先處理；△ 不會阻止抽選，但可能看不到懸浮控制台或無法鎖定 LINE 直式。")
                .setPositiveButton("前往權限設定", (dialog, which) ->
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNeutralButton("匯入 KBA", (dialog, which) -> openLicensePicker())
                .setNegativeButton("關閉", null).show();
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
            summary.setText("先試跑，再放心抽");
            sourceTime.setText("私人測試 " + total + " 筆 · 已完成 " + handled + " 筆\nK爸模擬不會建立真實訂單");
            overviewCat.setImageResource(R.drawable.ken_cat_scan);
            progressRing.setVisibility(View.GONE);
            return;
        }
        if (mode.equals("done")) {
            int known = 0;
            int problems = 0;
            int wins = 0;
            int total = 0;
            for (DrawItem item : items) if (item.origin.equals("source")) {
                total++;
                String outcome = effectiveOutcome(item);
                if (isKnownResult(outcome)) known++;
                if (isReviewWin(outcome)) wins++;
                if (isReviewProblem(outcome)) problems++;
            }
            summary.setText(wins > 0 ? "這週中了 " + wins + " 筆！" : "這週的結果整理好了");
            sourceTime.setText(known + " 筆已有結果 · " + problems + " 筆需要再確認\n只顯示你真正需要處理的事");
            overviewCat.setImageResource(R.drawable.ken_cat_success);
            progressRing.setVisibility(View.VISIBLE);
            progressRing.setPercent(total == 0 ? 0 : Math.round(known * 100f / total));
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
        int total = eligible + handled;
        summary.setText("這週有 " + total + " 次抽選機會");
        long at = DrawData.refreshedAt(this);
        String refreshed = at == 0 ? "尚未重整" : new SimpleDateFormat("M/d HH:mm", Locale.TAIWAN).format(new Date(at));
        sourceTime.setText(handled + " 筆完成 · " + eligible + " 筆待抽\n" +
                published + " 家門市 · 同步於 " + refreshed);
        overviewCat.setImageResource(R.drawable.ken_cat_home);
        progressRing.setVisibility(View.VISIBLE);
        progressRing.setPercent(Math.round(Math.min(78, published) * 100f / 78f));
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
        updateFavoriteButton();
        String citySummary = selectedCities.isEmpty() ? "全部縣市" : selectedCities.size() + " 個縣市";
        String productSummary = selectedProducts.isEmpty() ? "全部品項" : selectedProducts.size() + " 個品項";
        String timeSummary = selectedTime.isEmpty() ? "全部時間" : selectedTime;
        filterSummaryText.setText(citySummary + " · " + productSummary + " · " + timeSummary);
        boolean settings = mode.equals("test");
        boolean results = mode.equals("done");
        addTest.setVisibility(settings ? View.VISIBLE : View.GONE);
        mockTest.setVisibility(settings ? View.VISIBLE : View.GONE);
        resultsTools.setVisibility(results ? View.VISIBLE : View.GONE);
        filterContainer.setVisibility(!settings && !results ? View.VISIBLE : View.GONE);
        filterEditor.setVisibility(!settings && !results && filterExpanded ? View.VISIBLE : View.GONE);
        selectionArea.setVisibility(results ? View.GONE : View.VISIBLE);
        records.setText(results ? "掃描報告" : settings ? "測試紀錄" : "紀錄");
        updateTimeChoices();
    }

    private void updateTimeChoices() {
        if (timeChoices == null) return;
        if (!selectedTime.isEmpty() && !DrawTimeFilter.ALLOWED_TIMES.contains(selectedTime)) selectedTime = "";
        timeChoices.removeAllViews();
        addTimeChoice("全部時間", "");
        for (String time : DrawTimeFilter.ALLOWED_TIMES) addTimeChoice(time, time);
    }

    private void addTimeChoice(String label, String value) {
        Button chip = button(label);
        boolean active = selectedTime.equals(value);
        chip.setTextColor(active ? Color.WHITE : navy);
        chip.setBackground(roundedBackground(active ? 0xFF356AA9 : 0xFFE8EEF6,
                0xFFD3DDEA, 8));
        chip.setOnClickListener(v -> {
            selectedTime = value;
            filtersChanged();
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
        return String.join("、", shown) + " ▾";
    }

    private String citySelectionLabel() {
        if (selectedCities.isEmpty()) return RegionCatalog.ALL_CITIES + " ▾";
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
        TextView dragHint = text("長按名稱即可拖曳排序，也可使用右側箭頭微調。", 11, 0xFF718096);
        dragHint.setPadding(0, 0, 0, dp(4));
        parent.addView(dragHint);
        for (int i = 0; i < values.size(); i++) {
            final int position = i;
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            String value = values.get(i);
            TextView name = text((i + 1) + ". " + (labels == null ? value : labels.getOrDefault(value, value)), 13, navy);
            name.setMaxLines(2);
            name.setTag(value);
            name.setLongClickable(true);
            name.setOnLongClickListener(v -> {
                v.setAlpha(.48f);
                ClipData payload = ClipData.newPlainText("priority-order", value);
                boolean started = v.startDragAndDrop(payload, new View.DragShadowBuilder(v), v, 0);
                if (!started) v.setAlpha(1f);
                return started;
            });
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
            row.setOnDragListener((target, event) -> {
                Object local = event.getLocalState();
                if (!(local instanceof View) || !(((View) local).getTag() instanceof String)) return false;
                String dragged = (String) ((View) local).getTag();
                switch (event.getAction()) {
                    case DragEvent.ACTION_DRAG_STARTED:
                        return values.contains(dragged);
                    case DragEvent.ACTION_DRAG_ENTERED:
                        target.setBackground(roundedBackground(0xFFE8F1FB, 0xFFB8CBE2, 7));
                        return true;
                    case DragEvent.ACTION_DRAG_EXITED:
                        target.setBackgroundColor(Color.TRANSPARENT);
                        return true;
                    case DragEvent.ACTION_DROP:
                        int from = values.indexOf(dragged);
                        int to = values.indexOf(value);
                        if (from >= 0 && to >= 0 && from != to) {
                            values.remove(from);
                            values.add(Math.min(to, values.size()), dragged);
                            renderPriorityRows(parent, title, values, labels);
                        }
                        return true;
                    case DragEvent.ACTION_DRAG_ENDED:
                        ((View) local).setAlpha(1f);
                        target.setBackgroundColor(Color.TRANSPARENT);
                        return true;
                    default:
                        return true;
                }
            });
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
        updateReviewPanel();
        renderList();
    }

    private void updateFavoriteButton() {
        if (favoriteFilter == null) return;
        int saved = DrawData.loadFilterFavorites(this).size();
        favoriteFilter.setText(saved > 0 ? "♥ 喜好 " + saved : "♡ 儲存喜好");
        favoriteFilter.setTextColor(saved > 0 ? 0xFFB4234D : navy);
        favoriteFilter.setContentDescription(saved > 0 ?
                "選擇喜好方案；長按可管理" : "儲存目前喜好篩選");
    }

    private boolean hasFavoriteCriteria() {
        return !selectedCities.isEmpty() || !selectedProducts.isEmpty() ||
                !selectedTime.isEmpty() || !cityFirst;
    }

    private void saveCurrentFavorite() {
        if (!hasFavoriteCriteria()) {
            Toast.makeText(this, "請先選擇縣市、品項、時間或抽選順序", Toast.LENGTH_LONG).show();
            return;
        }
        ArrayList<DrawData.FilterFavorite> saved = DrawData.loadFilterFavorites(this);
        if (saved.size() >= 3) {
            Toast.makeText(this, "最多儲存三組；請長按愛心刪除或更新既有方案", Toast.LENGTH_LONG).show();
            manageFavoriteFilter();
            return;
        }
        EditText name = new EditText(this);
        name.setHint("例如：北部早場");
        name.setSingleLine(true);
        name.setText("喜好 " + (saved.size() + 1));
        name.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this).setTitle("儲存喜好方案")
                .setMessage("會記住縣市、品項、時間與抽選順序。")
                .setView(name)
                .setPositiveButton("儲存", (dialog, which) -> saveNamedFavorite(name.getText().toString()))
                .setNegativeButton("取消", null).show();
    }

    private void useFavoriteFilter() {
        ArrayList<DrawData.FilterFavorite> favorites = DrawData.loadFilterFavorites(this);
        if (favorites.isEmpty()) {
            saveCurrentFavorite();
            return;
        }
        String[] labels = new String[favorites.size()];
        for (int i = 0; i < favorites.size(); i++) labels[i] = "♥ " + favorites.get(i).name;
        new AlertDialog.Builder(this).setTitle("套用喜好方案")
                .setItems(labels, (dialog, which) -> applyFavorite(favorites.get(which)))
                .setNeutralButton(favorites.size() < 3 ? "儲存目前" : "管理方案",
                        (dialog, which) -> {
                            if (favorites.size() < 3) saveCurrentFavorite();
                            else manageFavoriteFilter();
                        })
                .setNegativeButton("取消", null).show();
    }

    private void applyFavorite(DrawData.FilterFavorite favorite) {
        selectedCities.clear();
        selectedCities.addAll(favorite.cities);
        selectedCities.retainAll(availableCities());
        selectedProducts.clear();
        selectedProducts.addAll(favorite.products);
        selectedProducts.retainAll(availableProducts());
        cityFirst = favorite.cityFirst;
        selectedTime = DrawTimeFilter.ALLOWED_TIMES.contains(favorite.time) ? favorite.time : "";
        rebuildSelectedCityGroups();
        selectedOnly = false;
        filtersChanged();
        Toast.makeText(this, "已套用「" + favorite.name + "」", Toast.LENGTH_SHORT).show();
    }

    private void saveNamedFavorite(String name) {
        String safeName = name == null ? "" : name.trim();
        if (safeName.isEmpty()) safeName = "我的喜好";
        if (!DrawData.saveFilterFavorite(this, safeName, selectedCities, selectedProducts, cityFirst, selectedTime)) {
            Toast.makeText(this, "最多只能儲存三組喜好方案", Toast.LENGTH_LONG).show();
            return;
        }
        updateFavoriteButton();
        Toast.makeText(this, "已儲存「" + safeName + "」", Toast.LENGTH_LONG).show();
    }

    private void manageFavoriteFilter() {
        ArrayList<DrawData.FilterFavorite> favorites = DrawData.loadFilterFavorites(this);
        if (favorites.isEmpty()) {
            saveCurrentFavorite();
            return;
        }
        String[] labels = new String[favorites.size()];
        for (int i = 0; i < favorites.size(); i++) labels[i] = favorites.get(i).name;
        new AlertDialog.Builder(this).setTitle("管理喜好方案")
                .setItems(labels, (dialog, which) -> manageFavorite(favorites.get(which), which))
                .setNeutralButton(favorites.size() < 3 ? "新增目前方案" : "已達三組上限",
                        (dialog, which) -> { if (favorites.size() < 3) saveCurrentFavorite(); })
                .setNegativeButton("關閉", null).show();
    }

    private void manageFavorite(DrawData.FilterFavorite favorite, int index) {
        new AlertDialog.Builder(this).setTitle(favorite.name)
                .setMessage("可套用、以目前畫面更新，或刪除這組方案。")
                .setPositiveButton("套用", (dialog, which) -> applyFavorite(favorite))
                .setNeutralButton("更新", (dialog, which) -> {
                    DrawData.saveFilterFavorite(this, favorite.name, selectedCities, selectedProducts,
                            cityFirst, selectedTime);
                    updateFavoriteButton();
                    Toast.makeText(this, "已更新「" + favorite.name + "」", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("刪除", (dialog, which) -> {
                    DrawData.removeFilterFavorite(this, index);
                    updateFavoriteButton();
                    Toast.makeText(this, "已刪除「" + favorite.name + "」", Toast.LENGTH_SHORT).show();
                }).show();
    }

    private void rebuildSelectedCityGroups() {
        selectedAllCityGroups.clear();
        ArrayList<String> available = availableCities();
        for (String region : RegionCatalog.REGIONS) {
            if (region.equals(RegionCatalog.ALL)) continue;
            int total = CitySelection.availableInRegion(available, region);
            if (total > 0 && CitySelection.selectedInRegion(selectedCities, available, region) == total)
                selectedAllCityGroups.add(region);
        }
    }

    private ArrayList<DrawItem> visible() {
        ArrayList<DrawItem> shown = visibleScope();
        if (selectedOnly) shown.removeIf(item -> !item.selected || !RunScope.eligible(item));
        return shown;
    }

    private ArrayList<DrawItem> visibleScope() {
        if (mode.equals("test")) {
            ArrayList<DrawItem> tests = RunScope.visible(items, mode,
                    java.util.Collections.emptySet(), java.util.Collections.emptySet(), java.util.Collections.emptySet());
            if (testNeedsReviewOnly) tests.removeIf(item -> !item.status.equals("error"));
            return StoreGroups.ordered(tests);
        }
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
            LinearLayout emptyState = vertical();
            emptyState.setGravity(Gravity.CENTER);
            emptyState.setPadding(dp(12), dp(18), dp(12), dp(24));
            ImageView cat = new ImageView(this);
            cat.setImageResource(R.drawable.ken_cat_empty);
            cat.setScaleType(ImageView.ScaleType.FIT_CENTER);
            emptyState.addView(cat, new LinearLayout.LayoutParams(-1, dp(150)));
            TextView empty = text("這裡暫時沒有任務", 16, navy);
            empty.setTypeface(null, Typeface.BOLD);
            empty.setGravity(Gravity.CENTER);
            emptyState.addView(empty);
            TextView emptyHint = text("換個篩選看看吧", 12, 0xFF718096);
            emptyHint.setGravity(Gravity.CENTER);
            emptyHint.setPadding(0, dp(5), 0, 0);
            emptyState.addView(emptyHint);
            list.addView(emptyState);
        }
        ArrayList<DrawItem> page = new ArrayList<>(shown.subList(0, Math.min(displayLimit, shown.size())));
        for (StoreGroups.CityGroup area : StoreGroups.group(page)) {
            String areaName = area.city.equals("測試") ? "測試洗頭貓貓" :
                    area.city.isEmpty() ? "未標示地區" : RegionCatalog.regionOf(area.city) + " · " + area.city;
            TextView location = text(areaName, 15, navy);
            location.setTypeface(null, Typeface.BOLD);
            location.setPadding(dp(2), dp(12), 0, dp(8));
            list.addView(location);
            for (StoreGroups.StoreGroup store : area.stores) {
                LinearLayout card = vertical();
                card.setPadding(dp(8), dp(8), dp(8), dp(8));
                GradientDrawable cardBackground = new GradientDrawable();
                cardBackground.setColor(0xEEFFFFFF);
                cardBackground.setCornerRadius(dp(20));
                cardBackground.setStroke(dp(1), 0xAAFFFFFF);
                card.setBackground(cardBackground);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
                cardParams.bottomMargin = dp(10);
                list.addView(card, cardParams);
                LinearLayout storeHeader = new LinearLayout(this);
                storeHeader.setGravity(Gravity.CENTER_VERTICAL);
                TextView name = text(store.name, 15, navy);
                name.setTypeface(null, Typeface.BOLD);
                storeHeader.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
                Button selectStore = button(mode.equals("done") ? "檢查此店" : "此店全選");
                selectStore.setTextSize(11);
                selectStore.setPadding(0, 0, 0, 0);
                selectStore.setTextColor(navy);
                selectStore.setBackground(roundedBackground(mint, 0xFFCEE4DA, 99));
                selectStore.setVisibility(selectedOnly ? View.GONE : View.VISIBLE);
                selectStore.setOnClickListener(v -> {
                    if (mode.equals("done")) confirmStoreReview(area.city, store.name);
                    else selectStorePending(area.city, store.name);
                });
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
            String displayStatus = item.status;
            if (mode.equals("done")) {
                String outcome = effectiveOutcome(item);
                if (isReviewNotDrawn(outcome)) displayStatus = "pending";
                else if (isReviewProblem(outcome)) displayStatus = "error";
                else displayStatus = outcome;
            }
            boolean processed = mode.equals("done") ? isKnownResult(effectiveOutcome(item)) : !RunScope.eligible(item);
            LinearLayout row = vertical();
            row.setPadding(dp(9), dp(8), dp(9), dp(9));
            GradientDrawable rowBackground = new GradientDrawable();
            rowBackground.setColor(processed ? 0xFFECEDE9 : item.selected ? 0xFFE5F5EF : 0xFFF7F8F5);
            rowBackground.setCornerRadius(dp(16));
            rowBackground.setStroke(dp(1), item.selected ? 0xFF76B9A6 : Color.TRANSPARENT);
            row.setBackground(rowBackground);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = dp(6);
            storeCard.addView(row, rowParams);
            LinearLayout headline = new LinearLayout(this);
            headline.setGravity(Gravity.CENTER_VERTICAL);
            if (mode.equals("done")) {
                TextView product = text(item.product, 14, processed ? 0xFF53657A : 0xFF223A5A);
                product.setTypeface(null, Typeface.BOLD);
                headline.addView(product, new LinearLayout.LayoutParams(0, -2, 1));
            } else {
                CheckBox box = new CheckBox(this);
                box.setText(item.product);
                box.setTextSize(14);
                box.setTextColor(processed ? 0xFF8793A2 : 0xFF223A5A);
                box.setChecked(item.selected);
                box.setEnabled(RunScope.eligible(item));
                box.setOnCheckedChangeListener((button, checked) -> {
                    item.selected = checked;
                    scheduleSelectionSave();
                    if (selectedOnly && !checked) renderList();
                    else updateSelection();
                });
                headline.addView(box, new LinearLayout.LayoutParams(0, -2, 1));
            }
            TextView status = text(shortStatusLabel(displayStatus), 10, statusTextColor(displayStatus));
            status.setGravity(Gravity.CENTER);
            status.setPadding(dp(7), dp(3), dp(7), dp(3));
            status.setBackground(roundedBackground(statusBackgroundColor(displayStatus), 0xFFD5DFEB, 99));
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, dp(28));
            statusParams.leftMargin = dp(5);
            headline.addView(status, statusParams);
            row.addView(headline);
            TextView link = text("開啟 LINE 連結 ↗", 12, processed ? 0xFF8A96A5 : accent);
            link.setPadding(0, dp(4), 0, dp(4));
            link.setContentDescription("開啟連結 " + item.url);
            link.setOnClickListener(v -> openShortLink(item));
            row.addView(link);
            if (!item.period.isEmpty()) row.addView(text(item.period, 11, 0xFF6A7B92));
            if (mode.equals("done") && !isKnownResult(effectiveOutcome(item))) {
                Button inspect = button("重新檢查這筆");
                inspect.setOnClickListener(v -> confirmItemReview(item));
                row.addView(inspect, new LinearLayout.LayoutParams(-1, dp(40)));
            }
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

    private String shortStatusLabel(String status) {
        switch (status) {
            case "scheduled": return "未開賣";
            case "not_friend": return "未加好友";
            case "busy": return "系統忙碌";
            case "pending": return "未抽籤";
            case "done": return "待確認";
            case "win": return "已中獎";
            case "lose": return "未中獎";
            case "already": return "已抽籤";
            case "expired": return "已結束";
            case "error": return "待重新檢查";
            default: return "待重新檢查";
        }
    }

    private int statusBackgroundColor(String status) {
        switch (status) {
            case "win": return 0xFFDDF6E9;
            case "scheduled": return 0xFFFFF3DA;
            case "not_friend": return 0xFFFFF7E9;
            case "busy": return 0xFFFDE9E7;
            case "lose":
            case "expired": return 0xFFFDE9E7;
            case "pending": return 0xFFE8F2FF;
            default: return 0xFFFFFFFF;
        }
    }

    private int statusTextColor(String status) {
        switch (status) {
            case "win": return 0xFF0A7045;
            case "scheduled": return 0xFF946007;
            case "not_friend": return 0xFF76521A;
            case "busy": return 0xFFA03E39;
            case "lose":
            case "expired": return 0xFFA03E39;
            case "pending": return 0xFF315F9B;
            default: return 0xFF60748E;
        }
    }

    private String statusLabel(String status) {
        switch (status) {
            case "scheduled": return "未開賣";
            case "not_friend": return "未加好友";
            case "busy": return "系統忙碌・稍後可重試";
            case "pending": return "未抽籤";
            case "done": return "已送出抽獎・結果待確認";
            case "win": return "已中獎";
            case "lose": return "未中獎";
            case "already": return "已抽籤・結果待確認";
            case "expired": return "已結束";
            case "error": return "待重新檢查";
            default: return "待重新檢查";
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
        if (mode.equals("all")) updateReviewPanel();
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
        if (!requireWeeklyLicense()) return;
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
        ArrayList<DrawItem> beforeSync = new ArrayList<>(items);
        new Thread(() -> {
            try {
                ArrayList<DrawItem> refreshed = DrawData.refresh(this);
                SyncDiff.Result diff = SyncDiff.compare(beforeSync, refreshed);
                runOnUiThread(() -> {
                    refreshing = false;
                    lastSyncFailed = false;
                    render();
                    new AlertDialog.Builder(this).setTitle("已同步本週清單")
                            .setMessage(diff.message() + "\n\n喜好方案與抽選順序已保留；若來源跨週，舊喜好才會自動清除。")
                            .setPositiveButton("知道了", null).show();
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
                    preferRunDisplay();
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
        if (!mode.equals("test") && !requireWeeklyLicense()) return;
        ArrayList<DrawItem> shown = visibleScope();
        ArrayList<DrawItem> selectedQueue = RunScope.queue(shown, mode);
        ArrayList<DrawItem> queue = mode.equals("test") ? selectedQueue : QueuePriority.order(selectedQueue,
                new ArrayList<>(selectedCities), new ArrayList<>(selectedProducts), cityFirst);
        if (queue.isEmpty()) { Toast.makeText(this, "請先勾選要抽的連結；未選取的不會啟動", Toast.LENGTH_LONG).show(); return; }
        if (!DrawAccessibilityService.available()) {
            showAccessibilityUnavailable();
            return;
        }
        boolean needsLine = false;
        for (DrawItem item : queue) if (!item.origin.equals("mock")) { needsLine = true; break; }
        if (needsLine && !PermissionHealth.lineInstalled(this)) {
            new AlertDialog.Builder(this).setTitle("尚未偵測到 LINE")
                    .setMessage("這批任務需要原生 LINE。請先安裝或確認目前模擬器分身內已有 LINE。")
                    .setPositiveButton("APP 系統設定", (dialog, which) -> startActivity(new Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName()))))
                    .setNegativeButton("取消", null).show();
            return;
        }
        String scope = mode.equals("test") ? "測試洗頭貓貓" :
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
        double seconds = queue.size() * (1.0 + DrawData.closeDelay(this) + DrawData.delay(this));
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
                .setMessage("開抽前健檢\n" + environmentChecklist(!mode.equals("test"), queue.size()) +
                        "\n\n範圍：" + scope + "\n預估時間：約 " + estimate +
                        "\n開抽時間：" + (selectedTime.isEmpty() ? "全部時間" : selectedTime) +
                        "\n\n預計順序：\n" + preview +
                        "\n只處理目前勾選的項目；快速接力會直接在 LINE 載入下一筆，失敗才自動改用逐筆關閉。完成狀態只代表已操作，可隨時暫停或停止。\n\n安全原則：永遠不操作「使用優惠券」。")
                .setPositiveButton("通過健檢，開始", (dialog, which) -> {
                    flushSelectionSave();
                    preferRunDisplay();
                    if (!DrawAccessibilityService.begin(queue))
                        Toast.makeText(this, "已有連抽正在執行，請先停止", Toast.LENGTH_LONG).show();
                    else moveTaskToBack(true);
                })
                .setNegativeButton("取消", null).show();
    }

    private void testLinksDialog() {
        EditText links = new EditText(this);
        links.setHint("貼上新的 lin.ee 或 line.me 連結；可一次貼多筆");
        links.setMinLines(7);
        links.setGravity(Gravity.TOP);
        links.setPadding(dp(18), dp(8), dp(18), dp(8));
        new AlertDialog.Builder(this).setTitle("新增測試洗頭貓貓連結")
                .setMessage("只會加入測試分頁；重複連結不會再新增。")
                .setView(links)
                .setPositiveButton("儲存", (dialog, which) -> {
                    int added = DrawData.addTestLinks(this, links.getText().toString());
                    Toast.makeText(this, "新增 " + added + " 筆測試連結", Toast.LENGTH_LONG).show();
                    render();
                }).setNegativeButton("取消", null).show();
    }

    private void mockDialog() {
        EditText countInput = new EditText(this);
        countInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        countInput.setText("6");
        countInput.setSelectAllOnFocus(true);
        countInput.setHint("1～30");
        countInput.setPadding(dp(18), dp(10), dp(18), dp(10));
        new AlertDialog.Builder(this).setTitle("K爸測試，不會有真實訂單")
                .setMessage("選擇要驗收的筆數（1～30）。系統會循環測試可抽選、已抽過、中獎結果、已失效、載入錯誤、加入好友六種畫面；完全離線，不開啟 LINE。")
                .setView(countInput)
                .setNeutralButton("查看單頁", (dialog, which) -> {
                    Intent preview = new Intent(this, MockCouponActivity.class);
                    preview.putExtra("scenario", "ready");
                    startActivity(preview);
                })
                .setPositiveButton("開始模擬驗收", (dialog, which) -> {
                    if (!DrawAccessibilityService.available()) {
                        showAccessibilityUnavailable();
                        return;
                    }
                    int count;
                    try { count = Integer.parseInt(countInput.getText().toString().trim()); }
                    catch (NumberFormatException error) { count = 6; }
                    count = Math.max(1, Math.min(MockCatalog.MAX_COUNT, count));
                    preferRunDisplay();
                    if (!DrawAccessibilityService.begin(MockCatalog.items(count)))
                        Toast.makeText(this, "目前有流程正在執行", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("取消", null).show();
    }

    private void showAccessibilityUnavailable() {
        boolean cloneProfile = android.os.Process.myUid() / 100000 != 0;
        String detail = cloneProfile
                ? "此分身目前沒有可用的輔助使用服務。若設定顯示已開啟仍無法執行，這個 MuMu 分身無法獨立控制畫面；第二組帳號請使用獨立的 MuMu 多開實例。"
                : PermissionHealth.accessibilityEnabled(this)
                ? "權限顯示已開啟，但服務目前沒有連線。這通常是省電、系統清理、更新 APP 或模擬器切換後中止服務，不是缺少套件。請到設定將「K爸全自動洗頭機控制」關閉再開啟一次。"
                : "連抽需要啟用「K爸全自動洗頭機控制」。Android 不允許 APP 自己打開這項高權限；啟用後回來再按開始。" +
                (Build.VERSION.SDK_INT >= 33 ? "\n\n若系統顯示受限制，請先到 APP 資訊右上角選單，選擇「允許受限制的設定」。" : "");
        new AlertDialog.Builder(this).setTitle("輔助使用服務不可用")
                .setMessage(detail)
                .setPositiveButton("前往設定", (dialog, which) -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("取消", null).show();
    }

    private SeekBar addSpeedSlider(LinearLayout parent, String label, float min, float max,
                                   float initial, float[] values, int index) {
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(label, 14, navy);
        heading.addView(name, new LinearLayout.LayoutParams(0, dp(30), 1));
        TextView value = text(String.format(Locale.TAIWAN, "%.1f 秒", initial), 14, accent);
        value.setTypeface(null, Typeface.BOLD);
        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        heading.addView(value, new LinearLayout.LayoutParams(dp(86), dp(30)));
        parent.addView(heading);
        SeekBar slider = new SeekBar(this);
        slider.setMax(Math.round((max - min) * 10f));
        slider.setProgress(Math.round((Math.max(min, Math.min(max, initial)) - min) * 10f));
        slider.setProgressTintList(ColorStateList.valueOf(accent));
        slider.setThumbTintList(ColorStateList.valueOf(navy));
        slider.setTag(min);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float seconds = min + progress / 10f;
                values[index] = seconds;
                value.setText(String.format(Locale.TAIWAN, "%.1f 秒", seconds));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(42));
        params.bottomMargin = dp(5);
        parent.addView(slider, params);
        values[index] = initial;
        return slider;
    }

    private void setSpeedSlider(SeekBar slider, float seconds) {
        float min = slider.getTag() instanceof Float ? (Float) slider.getTag() : .1f;
        slider.setProgress(Math.round((seconds - min) * 10f));
    }

    private void settingsDialog() {
        ScrollView settingsScroll = new ScrollView(this);
        LinearLayout form = vertical();
        form.setPadding(dp(18), 0, dp(18), dp(18));
        settingsScroll.addView(form);
        CheckBox fastHandoff = new CheckBox(this);
        form.addView(text("速度模式", 14, navy));
        LinearLayout speedRow = new LinearLayout(this);
        String[] selectedSpeed = {DrawData.speedMode(this)};
        Button speedFast = button("快速");
        Button speedBalanced = button("平衡");
        Button speedCompatible = button("相容");
        Button[] speedButtons = {speedFast, speedBalanced, speedCompatible};
        String[] speedValues = {"fast", "balanced", "compatible"};
        for (int i = 0; i < speedButtons.length; i++) {
            styleSpeedButton(speedButtons[i], speedValues[i].equals(selectedSpeed[0]));
            speedRow.addView(speedButtons[i], new LinearLayout.LayoutParams(0, dp(42), 1));
        }
        form.addView(speedRow, new LinearLayout.LayoutParams(-1, dp(42)));
        float[] timingValues = {DrawData.closeDelay(this), DrawData.delay(this), DrawData.errorDelay(this)};
        SeekBar closeSlider = addSpeedSlider(form, "抽完停留", .1f, 30f,
                timingValues[0], timingValues, 0);
        SeekBar nextSlider = addSpeedSlider(form, "下一筆間隔", .1f, 10f,
                timingValues[1], timingValues, 1);
        SeekBar errorSlider = addSpeedSlider(form, "錯誤等待", 3f, 30f,
                timingValues[2], timingValues, 2);
        Runnable repaintSpeedButtons = () -> {
            for (int i = 0; i < speedButtons.length; i++)
                styleSpeedButton(speedButtons[i], speedValues[i].equals(selectedSpeed[0]));
        };
        Runnable useFast = () -> {
            selectedSpeed[0] = "fast";
            setSpeedSlider(closeSlider, .1f);
            setSpeedSlider(nextSlider, .1f);
            setSpeedSlider(errorSlider, 3f);
            fastHandoff.setChecked(true);
            repaintSpeedButtons.run();
        };
        Runnable useBalanced = () -> {
            selectedSpeed[0] = "balanced";
            setSpeedSlider(closeSlider, .5f);
            setSpeedSlider(nextSlider, .8f);
            setSpeedSlider(errorSlider, 8f);
            fastHandoff.setChecked(true);
            repaintSpeedButtons.run();
        };
        Runnable useCompatible = () -> {
            selectedSpeed[0] = "compatible";
            setSpeedSlider(closeSlider, 1.4f);
            setSpeedSlider(nextSlider, 2f);
            setSpeedSlider(errorSlider, 15f);
            fastHandoff.setChecked(false);
            repaintSpeedButtons.run();
        };
        speedFast.setOnClickListener(v -> {
            LinearLayout warning = vertical();
            warning.setPadding(dp(22), 0, dp(22), dp(4));
            ImageView warningCat = new ImageView(this);
            warningCat.setImageResource(R.drawable.ken_cat_scan);
            warningCat.setScaleType(ImageView.ScaleType.FIT_CENTER);
            warning.addView(warningCat, new LinearLayout.LayoutParams(-1, dp(128)));
            TextView warningText = text("快速模式會把一般等待降到 0.1 秒。較慢的手機或 LINE 尚未載入完成時可能漏掉畫面；出現異常請改用平衡模式。", 14, 0xFF566B67);
            warningText.setGravity(Gravity.CENTER);
            warningText.setLineSpacing(0, 1.12f);
            warning.addView(warningText);
            new AlertDialog.Builder(this)
                    .setTitle("太快會有風險喔")
                    .setView(warning)
                    .setPositiveButton("確定", (dialog, which) -> useFast.run())
                    .setNegativeButton("平衡", (dialog, which) -> useBalanced.run())
                    .show();
        });
        speedBalanced.setOnClickListener(v -> useBalanced.run());
        speedCompatible.setOnClickListener(v -> useCompatible.run());
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

        WeeklyLicense.Status licenseStatus = WeeklyLicense.status(this);
        Button weeklyLicense = button("洗頭機 KBA · " + licenseStatus.label + " · " +
                WeeklyLicense.remainingLabel(licenseStatus) + "\n" + licenseStatus.detail);
        weeklyLicense.setAllCaps(false);
        weeklyLicense.setMinHeight(dp(64));
        weeklyLicense.setContentDescription("洗頭機 KBA 與匯入教學");
        weeklyLicense.setOnClickListener(v -> showKbaTutorial());
        form.addView(weeklyLicense);

        Button help = button("使用說明");
        form.addView(help);
        for (Button action : new Button[]{floating, portrait, permission, weeklyLicense, help}) {
            action.setBackground(roundedBackground(0xEEFFFFFF, 0xFFE0E6E2, 14));
            action.setTextColor(navy);
        }
        CheckBox lowMemory = new CheckBox(this);
        lowMemory.setText("低階手機相容");
        lowMemory.setChecked(DrawData.lowMemoryMode(this));
        form.addView(lowMemory);
        fastHandoff.setText("快速接力模式");
        fastHandoff.setChecked(DrawData.fastHandoff(this));
        form.addView(fastHandoff);
        Button clearWeek = button("清除本周資料");
        clearWeek.setTextColor(0xFFB3261E);
        form.addView(clearWeek, new LinearLayout.LayoutParams(-1, dp(48)));

        LinearLayout threads = new LinearLayout(this);
        threads.setGravity(Gravity.CENTER);
        threads.setPadding(dp(14), 0, dp(14), 0);
        threads.setBackground(roundedBackground(0xEEFFFFFF, 0xFFE0E6E2, 14));
        ImageView threadsIcon = new ImageView(this);
        threadsIcon.setImageResource(R.drawable.ic_threads);
        LinearLayout.LayoutParams threadsIconParams = new LinearLayout.LayoutParams(dp(24), dp(24));
        threadsIconParams.rightMargin = dp(10);
        threads.addView(threadsIcon, threadsIconParams);
        TextView threadsText = text("追蹤 KEN爸的脆", 14, navy);
        threadsText.setTypeface(null, Typeface.BOLD);
        threads.addView(threadsText);
        threads.setContentDescription("前往 Threads 追蹤 KEN爸");
        threads.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.threads.com/@20220647_6?hl=zh-tw")));
            } catch (Exception error) {
                Toast.makeText(this, "目前無法開啟 Threads", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams threadsParams = new LinearLayout.LayoutParams(-1, dp(54));
        threadsParams.topMargin = dp(10);
        form.addView(threads, threadsParams);
        LinearLayout dialogBody = vertical();
        dialogBody.setPadding(0, 0, 0, dp(8));
        dialogBody.addView(settingsScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout dialogActions = new LinearLayout(this);
        dialogActions.setOrientation(LinearLayout.HORIZONTAL);
        dialogActions.setPadding(dp(12), dp(8), dp(12), dp(4));
        Button cancelSettings = button("取消");
        Button saveSettings = button("儲存");
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(48), 1f);
        actionParams.setMargins(dp(4), 0, dp(4), 0);
        dialogActions.addView(cancelSettings, actionParams);
        dialogActions.addView(saveSettings, new LinearLayout.LayoutParams(actionParams));
        dialogBody.addView(dialogActions);

        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        int bodyHeight = Math.min(dp(720), Math.max(dp(400), screenHeight - dp(150)));
        dialogBody.setLayoutParams(new LinearLayout.LayoutParams(-1, bodyHeight));
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("設定")
                .setView(dialogBody).create();
        help.setOnClickListener(v -> {
            dialog.dismiss();
            uiHandler.post(() -> showOnboarding(true));
        });
        cancelSettings.setOnClickListener(v -> dialog.dismiss());
        clearWeek.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("清除本周資料？")
                .setMessage("會清除正式店家清單、本周抽選狀態與掃描報告。已儲存的喜好篩選會保留。測試洗頭貓貓連結也會保留，但全部恢復為可抽選；測試紀錄會清除。等待時間等一般設定會保留。清除後請按右上角「同步」取得新一周清單。")
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
        saveSettings.setOnClickListener(v -> {
                    DrawData.setCloseDelay(this, timingValues[0]);
                    DrawData.setDelay(this, timingValues[1]);
                    DrawData.setErrorDelay(this, timingValues[2]);
                    DrawData.setSpeedModeName(this, selectedSpeed[0]);
                    DrawData.setLowMemoryMode(this, lowMemory.isChecked());
                    DrawData.setFastHandoff(this, fastHandoff.isChecked());
                    displayLimit = displayPageSize();
                    Toast.makeText(this, "速度設定已儲存", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                });
        dialog.show();
    }

    private void styleSpeedButton(Button button, boolean active) {
        button.setTextColor(active ? Color.WHITE : navy);
        button.setBackground(roundedBackground(active ? 0xFF356AA9 : 0xFFE8EEF6, 0xFFD3DDEA, 8));
    }

    private void openOverlayPermission() {
        Intent permission = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivity(permission);
    }

    private boolean requireWeeklyLicense() {
        WeeklyLicense.Status status = WeeklyLicense.status(this);
        if (status.valid) return true;
        new AlertDialog.Builder(this).setTitle("需要洗頭機 KBA")
                .setMessage(status.detail + "\n\n未認證仍可使用「測試」，但不能同步或執行本週正式任務。")
                .setPositiveButton("匯入認證", (dialog, which) -> openLicensePicker())
                .setNeutralButton("匯入教學", (dialog, which) -> showKbaTutorial())
                .setNegativeButton("取消", null).show();
        return false;
    }

    private void openLicensePicker() {
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        picker.setType("*/*");
        picker.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "application/octet-stream", "text/plain"});
        startActivityForResult(picker, REQUEST_WEEKLY_LICENSE);
    }

    private void showKbaTutorial() {
        WeeklyLicense.Status status = WeeklyLicense.status(this);
        new AlertDialog.Builder(this).setTitle("洗頭機 KBA｜匯入教學")
                .setMessage(status.detail + "\n\n最快方式\nSTEP 1　到 Discord 找「頻道小精靈」發布的洗頭機 KBA。\n\nSTEP 2　下載後直接點檔案，選擇「K爸全自動洗頭機」即可自動匯入。\n\n找不到開啟選項時\nSTEP 3　回到這裡按「選擇 KBA」，選取剛下載的檔案。\n\n成功後會立即顯示剩餘期限；測試功能不需要認證。")
                .setPositiveButton("選擇 KBA", (dialog, which) -> openLicensePicker())
                .setNegativeButton("關閉", null).show();
    }

    private void openRotationPermission() {
        Intent permission = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS,
                Uri.parse("package:" + getPackageName()));
        startActivity(permission);
    }

    private void showBrandIntro(boolean needsOnboarding) {
        if (shell == null) return;
        FrameLayout intro = new FrameLayout(this);
        intro.setBackgroundColor(light);
        intro.addView(new AmbientBackgroundView(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout lockup = vertical();
        lockup.setGravity(Gravity.CENTER);
        ImageView cat = new ImageView(this);
        cat.setImageResource(R.drawable.fangba_app_icon);
        cat.setScaleType(ImageView.ScaleType.CENTER_CROP);
        cat.setBackground(roundedBackground(0xEEFFFFFF, 0xAAFFFFFF, 28));
        cat.setClipToOutline(true);
        lockup.addView(cat, new LinearLayout.LayoutParams(dp(132), dp(132)));
        TextView name = text("KEN爸的全自動洗頭機", 23, navy);
        name.setTypeface(null, Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(22), 0, 0);
        lockup.addView(name);
        TextView note = text("今天也一起順順抽", 13, 0xFF667A76);
        note.setGravity(Gravity.CENTER);
        note.setPadding(0, dp(8), 0, 0);
        lockup.addView(note);
        intro.addView(lockup, new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER));
        shell.addView(intro, new FrameLayout.LayoutParams(-1, -1));
        cat.setScaleX(.84f);
        cat.setScaleY(.84f);
        cat.setAlpha(0f);
        name.setTranslationY(dp(10));
        name.setAlpha(0f);
        intro.post(() -> {
            cat.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(280)
                    .setInterpolator(new DecelerateInterpolator()).start();
            name.animate().alpha(1f).translationY(0).setStartDelay(90).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator()).start();
            intro.postDelayed(() -> intro.animate().alpha(0f).setDuration(220)
                    .withEndAction(() -> {
                        shell.removeView(intro);
                        handleIncomingLicense(getIntent());
                        uiHandler.postDelayed(this::offerInterruptedRun, 300);
                        if (needsOnboarding) uiHandler.postDelayed(() -> showOnboarding(false), 550);
                    }).start(), 980);
        });
    }

    private void showOnboarding(boolean replay) {
        if (shell == null) return;
        final String[] titles = {
                "先取得這週清單",
                "挑你想抽的",
                "確認後開始",
                "隨時看進度"
        };
        final String[] details = {
                "按右上角「同步」，貓貓會替你抓回最新店家與品項。",
                "點「調整」選縣市、品項與時間；也能把常用組合存成愛心。",
                "全選待抽或勾選幾筆，核對後按底下的開始連抽。",
                "懸浮控制台會陪你到 LINE，可暫停或停止；完成後到結果看結論。"
        };
        final int[] cats = {R.drawable.ken_cat_home, R.drawable.ken_cat_scan,
                R.drawable.ken_cat_success, R.drawable.ken_cat_home};
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(0xCCF8F5EF);
        overlay.setClickable(true);
        overlay.setFocusable(true);
        overlay.addView(new AmbientBackgroundView(this), new FrameLayout.LayoutParams(-1, -1));

        LinearLayout panel = vertical();
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(24), dp(22), dp(24), dp(24));
        panel.setBackground(roundedBackground(0xF4FFFFFF, 0xCCFFFFFF, 28));
        panel.setElevation(dp(8));
        ImageView cat = new ImageView(this);
        cat.setScaleType(ImageView.ScaleType.FIT_CENTER);
        panel.addView(cat, new LinearLayout.LayoutParams(-1, dp(230)));
        TextView title = text("", 23, navy);
        title.setTypeface(null, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title);
        TextView detail = text("", 15, 0xFF566B67);
        detail.setGravity(Gravity.CENTER);
        detail.setLineSpacing(0, 1.18f);
        detail.setPadding(dp(12), dp(14), dp(12), dp(16));
        panel.addView(detail);
        TextView step = text("", 12, accent);
        step.setGravity(Gravity.CENTER);
        panel.addView(step);
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
        panelParams.leftMargin = dp(24);
        panelParams.rightMargin = dp(24);
        panelParams.bottomMargin = dp(74);
        overlay.addView(panel, panelParams);

        LinearLayout actions = new LinearLayout(this);
        actions.setPadding(dp(18), dp(12), dp(18), dp(18));
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button skip = button("跳過");
        Button next = button("下一步");
        skip.setBackground(roundedBackground(0xEEFFFFFF, 0xFFE0E6E2, 14));
        next.setTextColor(Color.WHITE);
        next.setBackground(roundedBackground(navy, Color.TRANSPARENT, 14));
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
            cat.setImageResource(cats[current[0]]);
            step.setText("STEP " + (current[0] + 1) + "  ·  " + (current[0] + 1) + " / " + titles.length);
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

    private void confirmItemReview(DrawItem item) {
        if (!DrawAccessibilityService.available()) { showAccessibilityUnavailable(); return; }
        new AlertDialog.Builder(this)
                .setTitle("重新檢查這筆？")
                .setMessage(item.store + "\n" + item.product +
                        "\n\n只讀取目前頁面狀態；不按參加抽獎，也絕不按使用優惠券。")
                .setPositiveButton("開始檢查", (dialog, which) -> {
                    preferRunDisplay();
                    if (!DrawAccessibilityService.beginManualCheck(item))
                        Toast.makeText(this, "已有流程正在執行，請先停止", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("取消", null).show();
    }

    private void confirmStoreReview(String city, String store) {
        if (!DrawAccessibilityService.available()) { showAccessibilityUnavailable(); return; }
        ArrayList<DrawItem> review = new ArrayList<>();
        for (DrawItem item : items) {
            if (!item.origin.equals("source") || !DrawData.validLink(item.url) ||
                    !sameStore(item, city, store) || isKnownResult(effectiveOutcome(item))) continue;
            review.add(item);
        }
        if (review.isEmpty()) {
            Toast.makeText(this, "這家店沒有待檢查的連結", Toast.LENGTH_LONG).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("檢查此店 " + review.size() + " 筆？")
                .setMessage("只讀取頁面狀態，已知中獎、未中獎與已結束會自動排除。\n\n不按參加抽獎，也絕不按使用優惠券。")
                .setPositiveButton("開始檢查", (dialog, which) -> {
                    preferRunDisplay();
                    if (!DrawAccessibilityService.beginReview(review))
                        Toast.makeText(this, "已有流程正在執行，請先停止", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("取消", null).show();
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
                    preferRunDisplay();
                    if (!DrawAccessibilityService.beginReview(review))
                        Toast.makeText(this, "已有流程正在執行，請先停止", Toast.LENGTH_LONG).show();
                })
                .setNeutralButton("只查已抽籤", (dialog, which) -> {
                    if (awaitingResult.isEmpty()) Toast.makeText(this, "目前沒有結果待確認的連結", Toast.LENGTH_LONG).show();
                    else {
                        preferRunDisplay();
                        if (!DrawAccessibilityService.beginReview(awaitingResult))
                            Toast.makeText(this, "已有流程正在執行，請先停止", Toast.LENGTH_LONG).show();
                    }
                })
                .setNegativeButton("取消", null).show();
    }

    private void preferRunDisplay() {
        if (Build.VERSION.SDK_INT >= 30 && getDisplay() != null)
            DrawAccessibilityService.preferDisplay(getDisplay().getDisplayId());
        else DrawAccessibilityService.preferDisplay(Display.DEFAULT_DISPLAY);
    }

}
