package tw.linedraw.quick;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DrawAccessibilityService extends AccessibilityService {
    private static final String TAG = "LineDrawTest";
    private static DrawAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<DrawItem> queue = new ArrayList<>();
    private WindowManager windowManager;
    private Context overlayContext;
    private View overlay;
    private TextView overlayText;
    private TextView overlayItem;
    private TextView overlayMeta;
    private ImageView progressIcon;
    private ProgressBar progressLine;
    private Button pauseButton;
    private int overlayDisplayId = Display.INVALID_DISPLAY;
    private int targetDisplayId = Display.DEFAULT_DISPLAY;
    private int index;
    private int processedCount;
    private boolean running;
    private boolean paused;
    private boolean finishing;
    private boolean verifiedCurrent;
    private boolean friendJoinAttempted;
    private int friendJoinAttempts;
    private boolean reviewMode;
    private boolean manualCheck;
    private boolean gestureAttempted;
    private boolean linkLaunched;
    private boolean visualInFlight;
    private long lastVisualAt;
    private String visualCandidate = "";
    private int visualCandidateCount;
    private int visualTransitionCount;
    private VisualCouponReader visualReader;
    private String pendingDrawLabel = "";
    private final Rect pendingDrawBounds = new Rect();
    private int reviewWins;
    private int reviewLosses;
    private int reviewNotDrawn;
    private int reviewNotFriend;
    private int reviewScheduled;
    private int reviewBusy;
    private int reviewNotFound;
    private int reviewUncertain;
    private int reviewEnded;
    private final ArrayList<String> reviewWinners = new ArrayList<>();
    private final ArrayList<String> reviewProblems = new ArrayList<>();
    private final Map<String, String> reviewOutcomes = new HashMap<>();
    private final Map<String, String> resolvedTargets = new ConcurrentHashMap<>();
    private final Set<String> resolvingTargets = ConcurrentHashMap.newKeySet();
    private int requestedStartDisplayId = Display.INVALID_DISPLAY;
    private String previousCouponText = "";
    private String lastVisualPageText = "";
    private boolean fastHandoffEnabled;
    private boolean fastHandoffPending;
    private boolean inspectScheduled;
    private int consecutiveHandoffFailures;
    private String stage = "idle";
    private long stageAt;
    private long lastInspectLogAt;
    private long closeAt;
    private long nextAt;
    private long pausedAt;
    private long pausedTotalMs;
    private long runStartedAt;
    private String overlayStatus = "";
    private final Runnable overlayTick = new Runnable() {
        @Override public void run() {
            if (!running) return;
            paintOverlay();
            handler.postDelayed(this, 250);
        }
    };

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    static boolean available() { return instance != null; }
    static boolean isRunning() { return instance != null && instance.running; }
    static boolean hasPersistentOverlay() {
        return instance != null && instance.running && instance.overlay != null &&
                instance.overlay.getVisibility() == View.VISIBLE;
    }

    static void preferDisplay(int displayId) {
        DrawAccessibilityService service = instance;
        if (service != null && displayId != Display.INVALID_DISPLAY)
            service.requestedStartDisplayId = displayId;
    }

    static final class RunUiSnapshot {
        final boolean running;
        final boolean paused;
        final int current;
        final int total;
        final int processed;
        final String product;
        final String phase;
        final String status;
        final long remainingMs;
        final long elapsedMs;
        final double averageSeconds;

        RunUiSnapshot(boolean running, boolean paused, int current, int total, int processed,
                      String product, String phase, String status, long remainingMs,
                      long elapsedMs, double averageSeconds) {
            this.running = running;
            this.paused = paused;
            this.current = current;
            this.total = total;
            this.processed = processed;
            this.product = product;
            this.phase = phase;
            this.status = status;
            this.remainingMs = remainingMs;
            this.elapsedMs = elapsedMs;
            this.averageSeconds = averageSeconds;
        }
    }

    static RunUiSnapshot uiSnapshot() {
        DrawAccessibilityService service = instance;
        return service == null ? new RunUiSnapshot(false, false, 0, 0, 0,
                "", "", "", 0, 0, 0) : service.buildUiSnapshot();
    }

    static void togglePauseCurrent() {
        if (instance != null) instance.togglePauseRun();
    }

    static void setHostAppForeground(boolean foreground) {
        DrawAccessibilityService service = instance;
        if (service == null) return;
        service.handler.post(() -> {
            // v0.3.2 uses one persistent console above both the app and LINE.
            if (service.running && service.overlay != null)
                service.overlay.setVisibility(View.VISIBLE);
        });
    }

    static boolean begin(ArrayList<DrawItem> items) {
        if (instance == null || instance.running || items.isEmpty()) return false;
        instance.start(items, false, false);
        return true;
    }

    static boolean beginReview(ArrayList<DrawItem> items) {
        if (instance == null || instance.running || items.isEmpty()) return false;
        instance.start(items, true, false);
        return true;
    }

    static boolean beginManualCheck(DrawItem item) {
        if (instance == null || instance.running || item == null) return false;
        ArrayList<DrawItem> one = new ArrayList<>();
        one.add(item);
        instance.start(one, true, true);
        return true;
    }

    static void stopCurrent() { if (instance != null) instance.stopRun(); }

    @Override public void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        OrientationGuard.restore(this);
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (running && event != null) {
            if (isRunSurface(event.getPackageName()) &&
                    event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
                    Build.VERSION.SDK_INT >= 30) {
                // AccessibilityRecord#getDisplayId exists only on Android 13.
                // Calling it on Android 11/12 crashed the whole accessibility service.
                int displayId = Build.VERSION.SDK_INT >= 33 ? event.getDisplayId() : detectRunDisplay();
                if (displayId != Display.INVALID_DISPLAY && displayId != targetDisplayId &&
                        activePackageOnDisplay(displayId, event.getPackageName())) {
                    targetDisplayId = displayId;
                    moveOverlayToDisplay(displayId);
                }
            }
        }
        if (running && !paused) scheduleInspect(220);
    }

    private boolean isRunSurface(CharSequence packageName) {
        if (packageName == null) return false;
        String value = packageName.toString();
        return value.equals("jp.naver.line.android") || value.equals(getPackageName());
    }

    private boolean activePackageOnDisplay(int displayId, CharSequence packageName) {
        if (packageName == null) return false;
        if (Build.VERSION.SDK_INT < 30) return displayId == Display.DEFAULT_DISPLAY;
        try {
            SparseArray<List<AccessibilityWindowInfo>> all = getWindowsOnAllDisplays();
            List<AccessibilityWindowInfo> windows = all.get(displayId);
            if (windows == null) return false;
            for (AccessibilityWindowInfo info : windows) {
                if (info == null || (!info.isActive() && !info.isFocused())) continue;
                AccessibilityNodeInfo root = info.getRoot();
                if (root == null) continue;
                try {
                    CharSequence activePackage = root.getPackageName();
                    if (activePackage != null && packageName.toString().contentEquals(activePackage)) return true;
                } finally { root.recycle(); }
            }
        } catch (Exception error) {
            Log.w(TAG, "unable to verify active display " + displayId, error);
        }
        return false;
    }

    private GradientDrawable overlayBackground(int color, int stroke, float radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radius));
        if (stroke != android.graphics.Color.TRANSPARENT) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    @Override public void onInterrupt() { pauseRun(); }

    @Override public void onDestroy() {
        stopRun();
        instance = null;
        super.onDestroy();
    }

    private void start(ArrayList<DrawItem> items, boolean readOnly, boolean singleManualCheck) {
        stopRun();
        queue.addAll(items);
        reviewMode = readOnly;
        manualCheck = singleManualCheck;
        reviewWins = reviewLosses = reviewNotDrawn = reviewNotFriend = reviewScheduled =
                reviewBusy = reviewNotFound = reviewUncertain = reviewEnded = 0;
        reviewWinners.clear();
        reviewProblems.clear();
        reviewOutcomes.clear();
        resolvedTargets.clear();
        resolvingTargets.clear();
        // A one-link or one-store review must not erase conclusions collected for
        // the rest of the week. Each checked link replaces only its own outcome.
        if (readOnly) reviewOutcomes.putAll(DrawData.loadReviewOutcomes(this));
        index = 0;
        processedCount = 0;
        pausedTotalMs = 0;
        runStartedAt = System.currentTimeMillis();
        running = true;
        paused = false;
        finishing = false;
        fastHandoffEnabled = DrawData.fastHandoff(this);
        fastHandoffPending = false;
        consecutiveHandoffFailures = 0;
        lastVisualPageText = "";
        if (!OrientationGuard.lock(this)) Log.w(TAG, "device portrait lock unavailable; app itself remains portrait");
        showOverlay();
        handler.post(overlayTick);
        prepareHostThenOpen();
    }

    private void prepareHostThenOpen() {
        DrawItem first = current();
        if (first == null || first.origin.equals("mock")) {
            openCurrent();
            return;
        }
        prefetchTarget(0);
        try {
            Intent lineHome = getPackageManager().getLaunchIntentForPackage("jp.naver.line.android");
            if (lineHome != null) {
                lineHome.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT |
                        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                startActivity(lineHome);
                handler.postDelayed(this::openCurrent, 320L);
                return;
            }
        } catch (Exception error) {
            Log.w(TAG, "unable to prime LINE task", error);
        }
        openCurrent();
    }

    private DrawItem current() { return index < queue.size() ? queue.get(index) : null; }

    private void openCurrent() {
        DrawItem item = current();
        if (!running || item == null) { completeRun(); return; }
        stage = "opening";
        stageAt = System.currentTimeMillis();
        finishing = false;
        verifiedCurrent = false;
        friendJoinAttempted = false;
        friendJoinAttempts = 0;
        gestureAttempted = false;
        linkLaunched = false;
        visualInFlight = false;
        visualCandidate = "";
        visualCandidateCount = 0;
        visualTransitionCount = 0;
        pendingDrawLabel = "";
        AccessibilityNodeInfo previous = couponRoot();
        previousCouponText = previous == null ? (fastHandoffPending ? lastVisualPageText : "") :
                normalized(collectText(previous, new StringBuilder(), 0).toString());
        if (previous != null) previous.recycle();
        // Opening a link is not evidence of an error. Preserve its last known state
        // until this visit produces a concrete result.
        Log.i(TAG, (reviewMode ? "review" : "open") + " item " + (index + 1) + "/" + queue.size());
        updateOverlay((reviewMode ? "只讀重掃「" : "開啟「") + item.product + "」");
        if (item.origin.equals("mock")) {
            Intent mock = new Intent(this, MockCouponActivity.class);
            mock.putExtra("scenario", MockCatalog.scenarioFromUrl(item.url));
            mock.putExtra("product", item.product);
            mock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(mock);
            linkLaunched = true;
            stageAt = System.currentTimeMillis();
            scheduleInspect(650);
            return;
        }
        launchCurrentLink(item);
    }

    private void launchCurrentLink(DrawItem item) {
        String cacheKey = item.url.split("#", 2)[0];
        String cached = resolvedTargets.get(cacheKey);
        if (cached != null) {
            launchResolvedTarget(item, cached);
            return;
        }
        if (!resolvingTargets.add(cacheKey)) {
            handler.postDelayed(() -> launchCurrentLink(item), 80L);
            return;
        }
        new Thread(() -> {
            String target = resolveLiff(cacheKey);
            resolvedTargets.put(cacheKey, target);
            resolvingTargets.remove(cacheKey);
            handler.post(() -> launchResolvedTarget(item, target));
        }, "linedraw-resolve").start();
    }

    private void launchResolvedTarget(DrawItem item, String target) {
        if (!running || current() != item) return;
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
            intent.setPackage("jp.naver.line.android");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                    (fastHandoffPending ? Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP
                            : Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
            startActivity(intent);
            linkLaunched = true;
            stageAt = System.currentTimeMillis();
            prefetchTarget(index + 1);
        } catch (Exception first) {
            try {
                Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
                browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(browser);
                linkLaunched = true;
                stageAt = System.currentTimeMillis();
                prefetchTarget(index + 1);
            } catch (Exception second) { finishItem("error", "連結無法開啟"); }
        }
        scheduleInspect(650);
    }

    private void prefetchTarget(int position) {
        if (position < 0 || position >= queue.size()) return;
        DrawItem item = queue.get(position);
        String cacheKey = item.url.split("#", 2)[0];
        if (item.origin.equals("mock") || !DrawData.validLink(item.url) ||
                resolvedTargets.containsKey(cacheKey) || !resolvingTargets.add(cacheKey)) return;
        new Thread(() -> {
            try { resolvedTargets.put(cacheKey, resolveLiff(cacheKey)); }
            finally { resolvingTargets.remove(cacheKey); }
        }, "linedraw-prefetch-" + position).start();
    }

    private String resolveLiff(String shortUrl) {
        if (!DrawData.validLink(shortUrl)) return shortUrl;
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(shortUrl).openConnection();
            connection.setInstanceFollowRedirects(false);
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            String location = connection.getHeaderField("Location");
            if (location != null && new URL(location).getHost().equalsIgnoreCase("liff.line.me")) return location;
        } catch (Exception ignored) {
        } finally { if (connection != null) connection.disconnect(); }
        return shortUrl;
    }

    private final Runnable inspectRunnable = new Runnable() {
        @Override public void run() {
            inspectScheduled = false;
            if (!running || paused || finishing || stage.equals("handoff") || stage.equals("closing") ||
                    stage.equals("recovering") || current() == null) return;
            inspect();
            if (running && !paused && !finishing) scheduleInspect(reviewMode ? 240 : 600);
        }
    };

    private void scheduleInspect(long delayMs) {
        if (inspectScheduled) return;
        inspectScheduled = true;
        handler.postDelayed(inspectRunnable, Math.max(0L, delayMs));
    }

    private void cancelInspect() {
        inspectScheduled = false;
        handler.removeCallbacks(inspectRunnable);
    }

    private void inspect() {
        if (stage.equals("submitted") || stage.equals("joining") || stage.equals("recovering")) return;
        if (stage.equals("opening") && !linkLaunched) return;
        if (stage.equals("confirming")) { confirmSubmission(); return; }
        if (stage.equals("opening") && fastHandoffPending &&
                System.currentTimeMillis() - stageAt > HandoffPolicy.TRANSITION_TIMEOUT_MS) {
            recoverFastHandoff();
            return;
        }
        if (System.currentTimeMillis() - stageAt > openingTimeoutMs()) {
            closeAndFinish(reviewMode ? "unknown" : "error", "等待畫面逾時・未確認");
            return;
        }
        DrawItem item = current();
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null) {
            try {
                String activeText = collectText(active, new StringBuilder(), 0).toString();
                if (CouponPageSignals.isNotFound(activeText)) {
                    finishItem("not_found", "連結失效（404）・保留待檢查");
                    return;
                }
                if (!reviewMode && joinFriendIfRequired(active, item)) return;
            }
            finally { active.recycle(); }
        }
        AccessibilityNodeInfo root = couponRoot();
        if (root == null) {
            inspectLineBridge();
            inspectVisual();
            logInspect("no coupon root");
            return;
        }
        try {
            String packageName = root.getPackageName() == null ? "" : root.getPackageName().toString();
            String text = collectText(root, new StringBuilder(), 0).toString();
            String primaryText = text.split("其他優惠券", 2)[0];
            if (CouponPageSignals.isNotFound(primaryText)) {
                finishItem("not_found", "連結失效（404）・保留待檢查");
                return;
            }
            if (fastHandoffPending) {
                if (normalized(text).equals(previousCouponText)) return;
                fastHandoffPending = false;
                consecutiveHandoffFailures = 0;
                updateOverlay("下一筆已載入");
            }
            logInspect("package=" + packageName + " draw=" + primaryText.contains("參加抽獎") + " processed=" + hasProcessedText(primaryText));
            boolean line = packageName.equals("jp.naver.line.android") ||
                    (item != null && item.origin.equals("mock") && packageName.equals(getPackageName()));
            if (!line) {
                AccessibilityNodeInfo bridge = findText(root, "打開LINE", "開啟LINE", "Open LINE");
                if (bridge != null) clickNode(bridge);
                return;
            }
            if (reviewMode) {
                if (normalized(text).equals(previousCouponText)) return;
                if (System.currentTimeMillis() - stageAt < 350) return;
                AccessibilityNodeInfo readyButton = findPrimaryAction(root, "參加抽獎", "参加抽奖", "參加抽選", "立即抽選", "加入好友並參加抽獎");
                String outcome = ReviewSignals.classify(primaryText, hasPrimaryRedemptionControl(root));
                if (outcome.isEmpty() && readyButton != null) outcome = "not_drawn";
                if (outcome.equals("not_drawn") && readyButton == null)
                    outcome = "unknown";
                if (!outcome.isEmpty()) closeAndFinish(outcome, "只讀檢查：" + outcome);
                return;
            }
            if (CouponPageSignals.isBusy(primaryText)) {
                closeAndFinish("busy", "LINE 系統忙碌・保留稍後重試");
                return;
            }
            if (CouponPageSignals.isEnded(primaryText) &&
                    !normalized(text).equals(previousCouponText) &&
                    System.currentTimeMillis() - stageAt >= 1200) {
                closeAndFinish("expired", "優惠券已結束・跳下一筆");
                return;
            }
            if (item != null && !verifiedCurrent) {
                if (item.origin.equals("mock")) {
                    if (!text.contains("K爸測試，不會有真實訂單") || !text.contains(item.product)) return;
                } else if (TestCatalog.isDefault(item)) {
                    if (!matchesTestCoupon(text, item)) return;
                } else if ((!text.contains("有效期間") && !text.contains("參加抽獎")) ||
                        normalized(text).equals(previousCouponText)) return;
                verifiedCurrent = true;
            }
            if (hasPrimaryRedemptionControl(root)) {
                closeAndFinish("already", "已出現使用／兌換按鈕；只關閉，不點擊");
                return;
            }
            if (primaryText.contains("發生不明錯誤") || primaryText.contains("An unknown error occurred")) {
                closeAndFinish("error", "LINE 顯示不明錯誤");
                return;
            }
            if (CouponPageSignals.isNotStarted(primaryText)) {
                closeAndFinish("scheduled", "尚未開賣・保留待抽");
                return;
            }
            if (stage.equals("opening")) {
                if (System.currentTimeMillis() - stageAt < 1200) return;
                if (hasProcessedText(primaryText) || hasGreyProcessedButton(root)) {
                    closeAndFinish("already", "已抽過・跳下一筆");
                    return;
                }
                AccessibilityNodeInfo draw = findPrimaryAction(root, "參加抽獎", "参加抽奖", "參加抽選", "立即抽選");
                if (draw != null && draw.isEnabled()) {
                    attemptDraw(draw, item);
                }
            }
        } finally { root.recycle(); }
    }

    private boolean joinFriendIfRequired(AccessibilityNodeInfo root, DrawItem item) {
        if (!stage.equals("opening") || friendJoinAttempted || item == null ||
                System.currentTimeMillis() - stageAt < 1200) return false;
        String packageName = root.getPackageName() == null ? "" : root.getPackageName().toString();
        boolean mock = item.origin.equals("mock") && packageName.equals(getPackageName());
        if (!mock && !packageName.equals("jp.naver.line.android")) return false;
        String text = collectText(root, new StringBuilder(), 0).toString();
        if (mock && (!text.contains("K爸測試，不會有真實訂單") || !text.contains(item.product))) return false;
        if (!mock && normalized(text).equals(previousCouponText)) return false;
        String primary = text.split("其他優惠券", 2)[0];
        if (!containsFriendPrompt(primary)) return false;
        if (hasPrimaryRedemptionControl(root)) return false;
        if (friendJoinAttempts >= 2) {
            closeAndFinish("not_friend", "加入好友兩次未成功・保留稍後重試");
            return true;
        }
        AccessibilityNodeInfo friendDraw = findPrimaryAction(root, "加入好友並參加抽獎");
        if (friendDraw != null && friendDraw.isEnabled()) {
            friendJoinAttempted = true;
            friendJoinAttempts++;
            if (attemptDraw(friendDraw, item)) {
                Log.i(TAG, "join friend and draw item " + (index + 1));
                return true;
            }
            friendJoinAttempted = false;
            return false;
        }
        AccessibilityNodeInfo join = findText(root, "加入好友", "加好友", "新增好友", "加入官方帳號", "Add friend", "Add as friend");
        if (join == null || !clickNode(join)) return false;
        friendJoinAttempted = true;
        friendJoinAttempts++;
        stage = "joining";
        stageAt = System.currentTimeMillis();
        updateOverlay("加入好友後確認同一筆抽獎");
        Log.i(TAG, "join friend item " + (index + 1));
        handler.postDelayed(() -> resumeAfterFriend(item), 1600);
        return true;
    }

    private void resumeAfterFriend(DrawItem item) {
        if (!running || current() != item || !stage.equals("joining")) return;
        if (paused) { handler.postDelayed(() -> resumeAfterFriend(item), 250); return; }
        AccessibilityNodeInfo root = getRootInActiveWindow();
        boolean ready = false;
        if (root != null) {
            String packageName = root.getPackageName() == null ? "" : root.getPackageName().toString();
            String text = collectText(root, new StringBuilder(), 0).toString();
            String primary = ReviewSignals.primary(text);
            ready = (packageName.equals("jp.naver.line.android") ||
                    (item.origin.equals("mock") && packageName.equals(getPackageName()))) &&
                    primary.contains("參加抽獎") && !containsFriendPrompt(primary);
            root.recycle();
        }
        stage = "opening";
        stageAt = System.currentTimeMillis() - (ready ? 1200 : 0);
        verifiedCurrent = false;
        if (ready) {
            updateOverlay("好友已加入・確認抽獎頁");
            scheduleInspect(0);
        } else {
            updateOverlay("重新開啟同一筆連結");
            // The first click may be acknowledged by Accessibility before LINE
            // actually joins the account. Allow one verified retry after reopening.
            friendJoinAttempted = false;
            if (item.origin.equals("mock")) {
                // The offline fixture changes in place; let the normal timeout report an unresolved prompt.
                scheduleInspect(0);
            } else launchCurrentLink(item);
        }
    }

    private boolean attemptDraw(AccessibilityNodeInfo draw, DrawItem item) {
        String label = allowedActionLabel(draw);
        if (!CouponSafety.isAllowedClickLabel(label)) return false;
        draw.getBoundsInScreen(pendingDrawBounds);
        if (!clickNode(draw)) return false;
        pendingDrawLabel = label;
        gestureAttempted = false;
        stage = "confirming";
        stageAt = System.currentTimeMillis();
        updateOverlay("確認「參加抽獎」是否真的觸發");
        Log.i(TAG, "accessibility draw action item " + (index + 1) + "; awaiting page transition");
        scheduleInspect(650);
        return true;
    }

    private void confirmSubmission() {
        DrawItem item = current();
        if (item == null) return;
        long elapsed = System.currentTimeMillis() - stageAt;
        AccessibilityNodeInfo root = couponRoot();
        boolean accepted = false;
        AccessibilityNodeInfo stillDraw = null;
        if (root != null) {
            try {
                String full = collectText(root, new StringBuilder(), 0).toString();
                String text = ReviewSignals.primary(full);
                stillDraw = findPrimaryAction(root, pendingDrawLabel);
                accepted = ReviewSignals.isSubmissionAcknowledged(text) ||
                        full.contains("抽選中") || full.contains("抽獎中") ||
                        hasPrimaryRedemptionControl(root) ||
                        (isCouponRoot(root) && stillDraw == null && elapsed >= 500);
                if (!accepted && elapsed >= 1100 && !gestureAttempted && stillDraw != null) {
                    gestureAttempted = true;
                    stageAt = System.currentTimeMillis();
                    if (tapExactDrawButton(root, stillDraw)) {
                        updateOverlay("按鈕未變化，改以畫面點擊重試一次");
                        Log.i(TAG, "gesture fallback item " + (index + 1));
                        return;
                    }
                    failUnconfirmed("手機未接受抽獎按鈕點擊");
                    return;
                }
            } finally { root.recycle(); }
        } else inspectVisual();
        if (accepted) {
            stage = "submitted";
            stageAt = System.currentTimeMillis();
            Log.i(TAG, "confirmed draw transition item " + (index + 1));
            waitAfterDraw(item);
        } else if (elapsed > (gestureAttempted ? 2800 : 4500)) {
            failUnconfirmed("按鈕未出現抽選變化；此筆未標已抽");
        }
    }

    private boolean tapExactDrawButton(AccessibilityNodeInfo root, AccessibilityNodeInfo button) {
        if (hasPrimaryRedemptionControl(root) || !button.isEnabled()) return false;
        String label = allowedActionLabel(button);
        if (!CouponSafety.isAllowedClickLabel(label) || !CouponSafety.normalized(label).equals(CouponSafety.normalized(pendingDrawLabel))) return false;
        Rect bounds = new Rect();
        Rect page = new Rect();
        button.getBoundsInScreen(bounds);
        root.getBoundsInScreen(page);
        if (bounds.isEmpty() || page.isEmpty() || bounds.centerY() < page.top + page.height() * 2 / 3 ||
                Math.abs(bounds.centerX() - pendingDrawBounds.centerX()) > dp(80) ||
                Math.abs(bounds.centerY() - pendingDrawBounds.centerY()) > dp(80)) return false;
        Path path = new Path();
        path.moveTo(bounds.centerX(), bounds.centerY());
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 80)).build();
        return dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCancelled(GestureDescription gestureDescription) {
                if (running && stage.equals("confirming")) failUnconfirmed("系統拒絕畫面點擊");
            }
        }, handler);
    }

    private String allowedActionLabel(AccessibilityNodeInfo node) {
        String text = node.getText() == null ? "" : node.getText().toString();
        if (CouponSafety.isAllowedClickLabel(text)) return text;
        String description = node.getContentDescription() == null ? "" : node.getContentDescription().toString();
        return CouponSafety.isAllowedClickLabel(description) ? description : "";
    }

    private void failUnconfirmed(String message) {
        if (!running || !stage.equals("confirming")) return;
        DrawItem item = current();
        if (item != null) {
            item.status = "error";
            DrawData.updateItem(this, item);
        }
        haltForSafety(message);
    }

    private AccessibilityNodeInfo findPrimaryAction(AccessibilityNodeInfo root, String... labels) {
        if (root == null) return null;
        Rect page = new Rect();
        root.getBoundsInScreen(page);
        return findPrimaryAction(root, page, 0, labels);
    }

    private AccessibilityNodeInfo findPrimaryAction(AccessibilityNodeInfo node, Rect page, int depth, String... labels) {
        if (node == null || depth > 40) return null;
        String text = CouponSafety.normalized(node.getText() == null ? "" : node.getText().toString());
        String description = CouponSafety.normalized(node.getContentDescription() == null ? "" : node.getContentDescription().toString());
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (!bounds.isEmpty() && bounds.centerY() >= page.top + page.height() * 2 / 3) {
            for (String label : labels) {
                String target = CouponSafety.normalized(label);
                if (text.equals(target) || description.equals(target)) return node;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo found = findPrimaryAction(child, page, depth + 1, labels);
            if (found != null) return found;
            child.recycle();
        }
        return null;
    }

    private void waitAfterDraw(DrawItem item) {
        long wait = WaitPolicy.sampleMillis(DrawData.closeDelay(this));
        closeAt = System.currentTimeMillis() + wait;
        updateOverlay(String.format(Locale.TAIWAN, "抽完停等 %.2f 秒", wait / 1000.0));
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!running || current() != item || !stage.equals("submitted")) return;
                if (paused) { handler.postDelayed(this, 250); return; }
                long remaining = closeAt - System.currentTimeMillis();
                if (remaining > 0) { handler.postDelayed(this, remaining); return; }
                closeAndFinish("done", "已按抽獎・結果稍後自行查看");
            }
        }, wait);
    }

    private AccessibilityNodeInfo couponRoot() {
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (isCouponRoot(active)) return active;
        if (active != null) active.recycle();
        if (Build.VERSION.SDK_INT < 30) {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows == null) return null;
            for (AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = window == null ? null : window.getRoot();
                if (isCouponRoot(root)) return root;
                if (root != null) root.recycle();
            }
            return null;
        }
        SparseArray<List<AccessibilityWindowInfo>> displays = getWindowsOnAllDisplays();
        for (int display = 0; display < displays.size(); display++) {
            List<AccessibilityWindowInfo> windows = displays.valueAt(display);
            if (windows == null) continue;
            for (AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = window.getRoot();
                if (isCouponRoot(root)) return root;
                if (root != null) root.recycle();
            }
        }
        return null;
    }

    private boolean isCouponRoot(AccessibilityNodeInfo root) {
        if (root == null || root.getPackageName() == null) return false;
        String pkg = root.getPackageName().toString();
        if (current() != null && current().origin.equals("mock")) {
            if (!getPackageName().equals(pkg) || !hasMockMarker(root, 0)) return false;
            String mockText = collectText(root, new StringBuilder(), 0).toString();
            return mockText.contains("K爸測試，不會有真實訂單") && mockText.contains("優惠券抽選模擬");
        }
        if (!pkg.equals("jp.naver.line.android")) return false;
        String text = collectText(root, new StringBuilder(), 0).toString();
        return text.contains("LINE Official Account Coupon") || text.contains("官方帳號優惠券") ||
                text.contains("參加抽獎") || text.contains("沒有抽中") || text.contains("已抽選") ||
                CouponPageSignals.isEnded(text) ||
                (stage.equals("confirming") && (text.contains("抽獎中") || text.contains("抽選中")));
    }

    private boolean hasMockMarker(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 4) return false;
        CharSequence description = node.getContentDescription();
        if (description != null && "K爸測試優惠券頁".contentEquals(description)) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            boolean found = hasMockMarker(child, depth + 1);
            child.recycle();
            if (found) return true;
        }
        return false;
    }

    private void inspectLineBridge() {
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active == null) return;
        try {
            String pkg = active.getPackageName() == null ? "" : active.getPackageName().toString();
            if (pkg.equals(getPackageName()) || pkg.equals("jp.naver.line.android")) return;
            AccessibilityNodeInfo bridge = findText(active, "打開LINE", "開啟LINE", "Open LINE");
            if (bridge != null) clickNode(bridge);
        } finally { active.recycle(); }
    }

    // LINE on some physical phones exposes only its native Close control to Accessibility.
    // In that case, OCR must see an exact allowlisted bottom action before any gesture.
    private void inspectVisual() {
        DrawItem item = current();
        if (Build.VERSION.SDK_INT < 30) return;
        long visualInterval = DrawData.lowMemoryMode(this) ? 900L : (reviewMode ? 400L : 650L);
        if (item == null || item.origin.equals("mock") || visualInFlight ||
                System.currentTimeMillis() - lastVisualAt < visualInterval || !lineIsForeground()) return;
        visualInFlight = true;
        lastVisualAt = System.currentTimeMillis();
        if (visualReader == null) visualReader = new VisualCouponReader(DrawData.lowMemoryMode(this));
        visualReader.read(this, new VisualCouponReader.Callback() {
            @Override public void onRead(VisualCouponReader.Frame frame) {
                visualInFlight = false;
                if (!running || paused || current() != item) return;
                String visualText = normalized(frame.text);
                lastVisualPageText = visualText;
                if (fastHandoffPending && frame.couponPage()) {
                    if (visualText.equals(previousCouponText)) return;
                    fastHandoffPending = false;
                    consecutiveHandoffFailures = 0;
                    updateOverlay("下一筆已載入");
                }
                Log.i(TAG, "visual " + stage + " coupon=" + frame.couponPage() +
                        " bottom=" + frame.bottomLabel);
                // LINE briefly replaces the whole coupon with a loading page. Its OCR
                // can lack the coupon header, but "抽獎中" confirms the tap was accepted.
                if (stage.equals("confirming") &&
                        (frame.text.contains("抽獎中") || frame.text.contains("抽選中"))) {
                    stage = "submitted";
                    stageAt = System.currentTimeMillis();
                    Log.i(TAG, "visual draw loading acknowledged item " + (index + 1));
                    waitAfterDraw(item);
                    return;
                }
                if (stage.equals("opening") && System.currentTimeMillis() - stageAt >= 350 &&
                        CouponPageSignals.isEnded(ReviewSignals.primary(frame.text))) {
                    closeAndFinish("expired", "優惠券已結束・跳下一筆");
                    return;
                }
                if (stage.equals("opening") && System.currentTimeMillis() - stageAt >= 350 &&
                        CouponPageSignals.isBusy(ReviewSignals.primary(frame.text))) {
                    closeAndFinish("busy", "LINE 系統忙碌・保留稍後重試");
                    return;
                }
                if (!frame.couponPage()) return;
                if (reviewMode) { inspectVisualReview(frame); return; }
                if (stage.equals("opening")) inspectVisualOpening(frame, item);
                else if (stage.equals("confirming")) inspectVisualConfirming(frame, item);
            }

            @Override public void onError(String reason) {
                visualInFlight = false;
                if (running && current() == item) Log.w(TAG, "visual unavailable: " + reason);
            }
        });
    }

    private boolean lineIsForeground() {
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active == null) return false;
        boolean line = active.getPackageName() != null &&
                "jp.naver.line.android".contentEquals(active.getPackageName());
        active.recycle();
        return line;
    }

    private void inspectVisualOpening(VisualCouponReader.Frame frame, DrawItem item) {
        if (System.currentTimeMillis() - stageAt < 1200) return;
        String text = frame.text;
        if (CouponPageSignals.isNotStarted(text)) {
            closeAndFinish("scheduled", "尚未開賣・保留待抽");
            return;
        }
        if (CouponPageSignals.isBusy(text)) {
            closeAndFinish("busy", "LINE 系統忙碌・保留稍後重試");
            return;
        }
        if (hasProcessedText(text) || CouponSafety.isRedemptionLabel(frame.bottomLabel)) {
            closeAndFinish("already", "已抽過・跳下一筆；不碰使用優惠券");
            return;
        }
        String label = frame.bottomLabel;
        if (!isVisualDrawOrJoin(label) || !safeVisualBounds(frame)) {
            visualCandidate = "";
            visualCandidateCount = 0;
            return;
        }
        if (label.equals(visualCandidate)) visualCandidateCount++;
        else { visualCandidate = label; visualCandidateCount = 1; }
        if (visualCandidateCount < 2) return;
        if (CouponSafety.isFriendJoinLabel(label)) {
            if (friendJoinAttempted) return;
            if (friendJoinAttempts >= 2) {
                closeAndFinish("not_friend", "加入好友兩次未成功・保留稍後重試");
                return;
            }
            friendJoinAttempted = true;
            friendJoinAttempts++;
            stage = "joining";
            stageAt = System.currentTimeMillis();
            if (!tapVisual(frame, label)) { haltForSafety("加入好友按鈕未能點擊"); return; }
            updateOverlay("加入好友後重開同一筆抽獎");
            handler.postDelayed(() -> resumeAfterFriend(item), 1600);
            return;
        }
        pendingDrawLabel = label;
        pendingDrawBounds.set(frame.bottomBounds);
        gestureAttempted = false;
        visualTransitionCount = 0;
        stage = "confirming";
        stageAt = System.currentTimeMillis();
        if (!tapVisual(frame, label)) { failUnconfirmed("無法點擊已辨識的抽獎按鈕"); return; }
        updateOverlay("已點抽獎・等待畫面確認");
    }

    private void inspectVisualConfirming(VisualCouponReader.Frame frame, DrawItem item) {
        long elapsed = System.currentTimeMillis() - stageAt;
        String label = frame.bottomLabel;
        if (label.equals(pendingDrawLabel)) {
            visualTransitionCount = 0;
            if (elapsed >= 1800 && !gestureAttempted) {
                gestureAttempted = true;
                if (!safeVisualBounds(frame) || !tapVisual(frame, label)) {
                    failUnconfirmed("抽獎按鈕未變化，重試受阻");
                    return;
                }
                stageAt = System.currentTimeMillis();
                updateOverlay("按鈕未變化，重試一次");
            } else if (gestureAttempted && elapsed >= 3500) {
                failUnconfirmed("重試後抽獎按鈕仍未變化；未標記完成");
            }
            return;
        }
        if (isVisualDrawOrJoin(label)) {
            failUnconfirmed("畫面出現不同的抽獎按鈕，請手動確認");
            return;
        }
        // A redemption action is evidence of a changed page, never a click target.
        boolean acknowledged = ReviewSignals.isSubmissionAcknowledged(frame.text) ||
                CouponSafety.isRedemptionLabel(label);
        if (acknowledged) visualTransitionCount = 2;
        else if (elapsed > 700) visualTransitionCount++;
        if (visualTransitionCount >= 2) {
            stage = "submitted";
            stageAt = System.currentTimeMillis();
            Log.i(TAG, "visual confirmed draw transition item " + (index + 1));
            waitAfterDraw(item);
        }
    }

    private void inspectVisualReview(VisualCouponReader.Frame frame) {
        if (System.currentTimeMillis() - stageAt < 350) return;
        String outcome = ReviewSignals.classify(frame.text,
                CouponSafety.isRedemptionLabel(frame.bottomLabel));
        if (outcome.equals("not_drawn") && !isVisualDrawOrJoin(frame.bottomLabel)) outcome = "";
        if (outcome.isEmpty() && isVisualDrawOrJoin(frame.bottomLabel)) outcome = "not_drawn";
        if (!outcome.isEmpty()) closeAndFinish(outcome, "只讀檢查：" + outcome);
    }

    private boolean isVisualDrawOrJoin(String label) {
        return CouponSafety.isFriendJoinLabel(label) || CouponSafety.isFriendDrawLabel(label) ||
                label.equals("參加抽獎") || label.equals("参加抽奖") ||
                label.equals("參加抽選") || label.equals("立即抽選");
    }

    private boolean containsFriendPrompt(String text) {
        String primary = ReviewSignals.primary(text == null ? "" : text);
        return primary.contains("加入好友") || primary.contains("加好友") ||
                primary.contains("新增好友") || primary.contains("加入官方帳號") ||
                primary.toLowerCase(Locale.ROOT).contains("add friend");
    }

    private boolean safeVisualBounds(VisualCouponReader.Frame frame) {
        Rect bounds = frame.bottomBounds;
        return !bounds.isEmpty() && bounds.centerY() >= frame.height * 0.78 &&
                bounds.centerY() < frame.height * 0.98 &&
                bounds.centerX() >= frame.width * 0.15 &&
                bounds.centerX() <= frame.width * 0.85;
    }

    private boolean tapVisual(VisualCouponReader.Frame frame, String label) {
        if (!lineIsForeground() || !frame.couponPage() || !safeVisualBounds(frame) ||
                !CouponSafety.isAllowedClickLabel(label) || CouponSafety.isRedemptionLabel(label)) return false;
        Path path = new Path();
        path.moveTo(frame.bottomBounds.centerX(), frame.bottomBounds.centerY());
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 90)).build();
        Log.i(TAG, "visual tap " + label + " at " + frame.bottomBounds.centerX() +
                "," + frame.bottomBounds.centerY());
        return dispatchGesture(gesture, new GestureResultCallback() {
            @Override public void onCancelled(GestureDescription gestureDescription) {
                if (running && stage.equals("confirming")) failUnconfirmed("系統拒絕畫面點擊");
            }
        }, handler);
    }

    private boolean matchesTestCoupon(String text, DrawItem item) {
        String page = normalized(text);
        String name = normalized(TestCatalog.expectedPageProduct(item));
        int title = page.indexOf(name);
        int otherCoupons = page.indexOf("其他優惠券");
        return title >= 0 && (otherCoupons < 0 || title < otherCoupons);
    }

    private String normalized(String text) { return text.replaceAll("\\s+", ""); }

    private boolean hasProcessedText(String text) {
        return text.contains("可惜...沒有抽中") || text.contains("沒有抽中！") ||
                text.contains("沒有中獎") || text.contains("未中獎") || text.contains("恭喜中獎") ||
                text.contains("已抽過") || text.contains("已抽選") ||
                text.contains("已參加抽選") || text.contains("已參加抽獎");
    }

    // Never activate a coupon's redemption or use action. Explanatory text alone is not actionable.
    private String nodeLabel(AccessibilityNodeInfo node) {
        return (node.getText() == null ? "" : node.getText().toString()) + " " +
                (node.getContentDescription() == null ? "" : node.getContentDescription().toString());
    }

    private boolean hasPrimaryRedemptionControl(AccessibilityNodeInfo root) {
        Rect page = new Rect();
        root.getBoundsInScreen(page);
        return hasPrimaryRedemptionControl(root, page, 0);
    }

    private boolean hasPrimaryRedemptionControl(AccessibilityNodeInfo node, Rect page, int depth) {
        if (node == null || depth > 40) return false;
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (node.isEnabled() && node.isClickable() && CouponSafety.isRedemptionLabel(nodeLabel(node)) &&
                bounds.width() > page.width() / 2 &&
                bounds.bottom >= page.top + page.height() * 3 / 4) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            boolean found = hasPrimaryRedemptionControl(child, page, depth + 1);
            child.recycle();
            if (found) return true;
        }
        return false;
    }

    private boolean containsRedemptionLabel(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 8) return false;
        if (CouponSafety.isRedemptionLabel(nodeLabel(node))) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            boolean found = containsRedemptionLabel(child, depth + 1);
            child.recycle();
            if (found) return true;
        }
        return false;
    }

    private void haltForSafety(String reason) {
        if (reviewMode) {
            DrawData.saveReviewOutcomes(this, reviewOutcomes);
            DrawData.saveReviewReport(this, "重掃中途停止：" + reason + "\n\n" + reviewReport());
        }
        running = false;
        paused = true;
        stage = "safety-stop";
        cancelInspect();
        releaseVisualReader();
        OrientationGuard.restore(this);
        if (pauseButton != null) {
            pauseButton.setEnabled(false);
            pauseButton.setText("已停止");
        }
        updateOverlay("安全停止：" + reason);
        Log.w(TAG, "safety stop: " + reason);
    }

    private boolean hasGreyProcessedButton(AccessibilityNodeInfo root) {
        Rect page = new Rect();
        root.getBoundsInScreen(page);
        return hasGreyProcessedButton(root, page, 0);
    }

    private boolean hasGreyProcessedButton(AccessibilityNodeInfo node, Rect page, int depth) {
        if (node == null || depth > 40) return false;
        String kind = node.getClassName() == null ? "" : node.getClassName().toString();
        String label = node.getText() == null ? "" : node.getText().toString().trim();
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if (kind.endsWith("Button") && !node.isEnabled() && !label.isEmpty() &&
                !label.equals("參加抽獎") && bounds.width() > page.width() / 2 &&
                bounds.bottom >= page.top + page.height() * 3 / 4 && bounds.height() > 0) return true;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            boolean found = hasGreyProcessedButton(child, page, depth + 1);
            child.recycle();
            if (found) return true;
        }
        return false;
    }

    private void logInspect(String detail) {
        long now = System.currentTimeMillis();
        if (now - lastInspectLogAt < 2000) return;
        lastInspectLogAt = now;
        Log.i(TAG, "inspect " + stage + " " + detail);
    }

    private StringBuilder collectText(AccessibilityNodeInfo node, StringBuilder out, int depth) {
        if (node == null || depth > 40 || out.length() > 20000) return out;
        if (node.getText() != null) out.append(node.getText()).append(' ');
        if (node.getContentDescription() != null) out.append(node.getContentDescription()).append(' ');
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) { collectText(child, out, depth + 1); child.recycle(); }
        }
        return out;
    }

    private AccessibilityNodeInfo findText(AccessibilityNodeInfo node, String... labels) {
        if (node == null) return null;
        String text = CouponSafety.normalized(node.getText() == null ? "" : node.getText().toString());
        String description = CouponSafety.normalized(node.getContentDescription() == null ? "" : node.getContentDescription().toString());
        for (String candidate : labels) {
            String expected = CouponSafety.normalized(candidate);
            if (text.equals(expected) || description.equals(expected)) return node;
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo found = findText(child, labels);
            if (found != null) return found;
            child.recycle();
        }
        return null;
    }

    private boolean clickNode(AccessibilityNodeInfo node) {
        boolean allowed = CouponSafety.isAllowedClickLabel(node.getText() == null ? "" : node.getText().toString()) ||
                CouponSafety.isAllowedClickLabel(node.getContentDescription() == null ? "" : node.getContentDescription().toString());
        if (!allowed) {
            haltForSafety("點擊目標不在允許清單");
            return false;
        }
        AccessibilityNodeInfo candidate = node;
        Rect initial = new Rect();
        node.getBoundsInScreen(initial);
        if (initial.isEmpty()) return false;
        int maxHeight = Math.max(initial.height() * 5, Math.round(160 * getResources().getDisplayMetrics().density));
        for (int i = 0; i < 4 && candidate != null; i++) {
            if (containsRedemptionLabel(candidate, 0)) {
                haltForSafety("禁止點擊使用／兌換優惠券控制");
                return false;
            }
            Rect bounds = new Rect();
            candidate.getBoundsInScreen(bounds);
            if (candidate.isClickable() && candidate.isEnabled() && !bounds.isEmpty() &&
                    bounds.height() <= maxHeight && bounds.contains(initial.centerX(), initial.centerY()) &&
                    candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true;
            candidate = candidate.getParent();
        }
        return false;
    }

    private void closeAndFinish(String status, String description) {
        if (!running || finishing || stage.equals("handoff") || stage.equals("closing")) return;
        if (HandoffPolicy.shouldDirect(fastHandoffEnabled, manualCheck, index, queue.size())) {
            long wait = reviewMode ? 0L : WaitPolicy.sampleMillis(DrawData.delay(this));
            stage = "handoff";
            nextAt = System.currentTimeMillis() + wait;
            updateOverlay(wait == 0 ? "背景開啟下一筆" :
                    String.format(Locale.TAIWAN, "快速接力 · %.2f 秒", wait / 1000.0));
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!running || current() == null || !stage.equals("handoff")) return;
                    if (paused) { handler.postDelayed(this, 250); return; }
                    long remaining = nextAt - System.currentTimeMillis();
                    if (remaining > 0) { handler.postDelayed(this, remaining); return; }
                    fastHandoffPending = true;
                    finishItem(status, description);
                }
            }, wait);
            return;
        }
        if (!reviewMode && index + 1 < queue.size()) {
            long wait = WaitPolicy.sampleMillis(DrawData.delay(this));
            stage = "handoff";
            nextAt = System.currentTimeMillis() + wait;
            updateOverlay(String.format(Locale.TAIWAN, "準備下一筆 · %.2f 秒", wait / 1000.0));
            handler.postDelayed(new Runnable() {
                @Override public void run() {
                    if (!running || current() == null || !stage.equals("handoff")) return;
                    if (paused) { handler.postDelayed(this, 250); return; }
                    long remaining = nextAt - System.currentTimeMillis();
                    if (remaining > 0) { handler.postDelayed(this, remaining); return; }
                    beginCloseAndFinish(status, description);
                }
            }, wait);
            return;
        }
        beginCloseAndFinish(status, description);
    }

    private void beginCloseAndFinish(String status, String description) {
        if (!running || finishing || stage.equals("closing")) return;
        fastHandoffPending = false;
        stage = "closing";
        stageAt = System.currentTimeMillis();
        updateOverlay("關閉優惠券，準備下一筆");
        clickCloseControl();
        handler.postDelayed(() -> verifyClosed(status, description, 0, 0), reviewMode ? 180 : 450);
    }

    private void recoverFastHandoff() {
        if (!running || !fastHandoffPending || !stage.equals("opening") || current() == null) return;
        DrawItem item = current();
        fastHandoffPending = false;
        consecutiveHandoffFailures++;
        if (consecutiveHandoffFailures >= 2) fastHandoffEnabled = false;
        stage = "recovering";
        stageAt = System.currentTimeMillis();
        updateOverlay(fastHandoffEnabled ? "快速切換未回應・重新開啟" : "已自動改用相容模式");
        clickCloseControl();
        handler.postDelayed(() -> verifyClosedForRetry(item, 0), 420L);
    }

    private void verifyClosedForRetry(DrawItem item, int attempts) {
        if (!running || current() != item || !stage.equals("recovering")) return;
        if (paused) { handler.postDelayed(() -> verifyClosedForRetry(item, attempts), 250L); return; }
        AccessibilityNodeInfo root = couponRoot();
        boolean stillOpen = isCouponRoot(root) || lineCouponCloseVisible();
        if (root != null) root.recycle();
        if (!stillOpen) {
            updateOverlay("相容模式重新開啟同一筆");
            openCurrent();
            return;
        }
        if (attempts >= 3) {
            haltForSafety("快速接力失敗，優惠券也未能安全關閉");
            return;
        }
        if (attempts < 2) clickCloseControl();
        else performGlobalAction(GLOBAL_ACTION_BACK);
        handler.postDelayed(() -> verifyClosedForRetry(item, attempts + 1), 500L);
    }

    private void verifyClosed(String status, String description, int attempts, int absentChecks) {
        if (!running || !stage.equals("closing")) return;
        if (paused) {
            handler.postDelayed(() -> verifyClosed(status, description, attempts, absentChecks), 250);
            return;
        }
        AccessibilityNodeInfo root = couponRoot();
        boolean couponStillOpen = isCouponRoot(root) || lineCouponCloseVisible();
        if (root != null) root.recycle();
        if (!couponStillOpen) {
            if (!reviewMode || absentChecks >= 1) finishItem(status, description);
            else handler.postDelayed(() -> verifyClosed(status, description, attempts, 1), 180);
            return;
        }
        if (attempts >= 3) {
            haltForSafety("優惠券未能確認關閉；請手動檢查");
            return;
        }
        boolean action = attempts < 2 ? clickCloseControl() : performGlobalAction(GLOBAL_ACTION_BACK);
        updateOverlay(action ? "等待優惠券關閉" : "重試關閉優惠券");
        handler.postDelayed(() -> verifyClosed(status, description, attempts + 1, 0), reviewMode ? 350 : 550);
    }

    private boolean clickCloseControl() {
        AccessibilityNodeInfo root = couponRoot();
        if (root == null) {
            root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null ||
                    !"jp.naver.line.android".contentEquals(root.getPackageName())) {
                if (root != null) root.recycle();
                return false;
            }
        }
        try {
            Rect page = new Rect();
            root.getBoundsInScreen(page);
            AccessibilityNodeInfo close = findCloseControl(root, page, 0);
            return close != null && clickNode(close);
        } finally { root.recycle(); }
    }

    private boolean lineCouponCloseVisible() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return false;
        try {
            if (root.getPackageName() == null ||
                    !"jp.naver.line.android".contentEquals(root.getPackageName())) return false;
            Rect page = new Rect();
            root.getBoundsInScreen(page);
            return findCloseControl(root, page, 0) != null;
        } finally { root.recycle(); }
    }

    private AccessibilityNodeInfo findCloseControl(AccessibilityNodeInfo node, Rect page, int depth) {
        if (node == null || depth > 40) return null;
        String label = node.getContentDescription() == null ? "" : node.getContentDescription().toString().trim();
        if (label.isEmpty() && node.getText() != null) label = node.getText().toString().trim();
        Rect bounds = new Rect();
        node.getBoundsInScreen(bounds);
        if ((label.equals("關閉") || label.equalsIgnoreCase("Close") || label.equals("閉じる")) &&
                bounds.top < page.top + page.height() / 4 && !bounds.isEmpty()) return node;
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) continue;
            AccessibilityNodeInfo found = findCloseControl(child, page, depth + 1);
            if (found != null) return found;
            child.recycle();
        }
        return null;
    }

    private void finishItem(String status, String description) {
        if (finishing || current() == null) return;
        finishing = true;
        DrawItem item = current();
        Log.i(TAG, "finish item " + (index + 1) + " status=" + status);
        if (reviewMode) {
            reviewOutcomes.put(item.key(), status);
            if (status.equals("win")) {
                reviewWins++;
                reviewWinners.add(item.store + " · " + item.product + "\n" + item.url);
                item.status = "win";
                DrawData.updateItem(this, item);
            } else if (status.equals("lose")) {
                reviewLosses++;
                item.status = "lose";
                DrawData.updateItem(this, item);
            } else if (status.equals("scheduled")) {
                reviewScheduled++;
                if (!isTerminalStatus(item.status)) {
                    item.status = "scheduled";
                    DrawData.updateItem(this, item);
                }
            } else if (status.equals("not_friend")) {
                reviewNotFriend++;
                if (!isTerminalStatus(item.status)) {
                    item.status = "not_friend";
                    DrawData.updateItem(this, item);
                }
            } else if (status.equals("busy")) {
                reviewBusy++;
                if (!isTerminalStatus(item.status)) {
                    item.status = "busy";
                    DrawData.updateItem(this, item);
                }
            } else if (status.equals("not_found")) {
                reviewNotFound++;
                if (!isTerminalStatus(item.status)) {
                    item.status = "not_found";
                    DrawData.updateItem(this, item);
                }
                if (reviewProblems.size() < 30)
                    reviewProblems.add(item.store + " · " + item.product + "：" + reviewReason(status));
            } else if (status.equals("not_drawn") || status.equals("pending")) {
                reviewNotDrawn++;
                if (!isTerminalStatus(item.status)) {
                    // The live page is the source of truth. If it still offers
                    // "參加抽獎", this link is simply not drawn yet and must be
                    // selectable again instead of being buried as an error.
                    item.status = "pending";
                    DrawData.updateItem(this, item);
                }
            } else if (status.equals("expired")) {
                reviewEnded++;
                if (!item.status.equals("win") && !item.status.equals("lose")) {
                    item.status = "expired";
                    DrawData.updateItem(this, item);
                }
            } else {
                reviewUncertain++;
                if (reviewProblems.size() < 30)
                    reviewProblems.add(item.store + " · " + item.product + "：" + reviewReason(status));
            }
        } else {
            item.status = status;
            item.selected = false;
        }
        processedCount++;
        updateOverlay(description);
        persistAndAdvance(item);
    }

    private void persistAndAdvance(DrawItem item) {
        if (!reviewMode && !item.origin.equals("mock")) DrawData.updateItem(this, item);
        stage = "next";
        nextAt = System.currentTimeMillis();
        updateOverlay(index + 1 < queue.size() ? "背景開啟下一筆" : "本次任務完成");
        handler.post(new Runnable() {
            @Override public void run() {
                if (!running) return;
                if (paused) {
                    handler.postDelayed(this, 250L);
                    return;
                }
                index++;
                openCurrent();
            }
        });
    }

    private boolean isTerminalStatus(String status) {
        return status.equals("win") || status.equals("lose") || status.equals("expired");
    }

    private void completeRun() {
        running = false;
        stage = "done";
        releaseVisualReader();
        OrientationGuard.restore(this);
        if (reviewMode) {
            DrawData.saveReviewOutcomes(this, reviewOutcomes);
            DrawData.saveReviewReport(this, reviewReport());
        } else {
            String report = regularRunReport();
            DrawData.saveRunReport(this, report);
            boolean testRun = isTestRun();
            DrawData.saveRunReportType(this, testRun);
            if (testRun) DrawData.saveTestReport(this, testRunReport());
        }
        updateOverlay("已處理 " + queue.size() + " / " + queue.size());
        handler.postDelayed(() -> {
            hideOverlay();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP |
                    Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
        }, 1500);
    }

    private void pauseRun() {
        if (paused) return;
        paused = true;
        pausedAt = System.currentTimeMillis();
        Log.i(TAG, "run paused at item " + (index + 1));
        if (pauseButton != null) pauseButton.setText("繼續");
        updateOverlay("已暫停");
    }

    private void resumeRun() {
        if (!paused || !running) return;
        long pauseDuration = System.currentTimeMillis() - pausedAt;
        pausedTotalMs += pauseDuration;
        stageAt += pauseDuration;
        closeAt += pauseDuration;
        nextAt += pauseDuration;
        paused = false;
        Log.i(TAG, "run resumed at item " + (index + 1));
        if (pauseButton != null) pauseButton.setText("暫停");
        scheduleInspect(0);
        updateOverlay("繼續抽選");
    }

    private void togglePauseRun() {
        if (!running) return;
        if (paused) resumeRun();
        else pauseRun();
    }

    private long remainingMs() {
        if (!running || paused) return 0;
        long now = System.currentTimeMillis();
        if (stage.equals("opening")) return Math.max(0, stageAt + openingTimeoutMs() - now);
        if (stage.equals("submitted")) return Math.max(0, closeAt - now);
        if (stage.equals("handoff") || stage.equals("next")) return Math.max(0, nextAt - now);
        return 0;
    }

    private long openingTimeoutMs() {
        long configured = Math.max(3000L, Math.round(DrawData.errorDelay(this) * 1000f));
        return reviewMode ? Math.min(configured, 10000L) : configured;
    }

    private RunUiSnapshot buildUiSnapshot() {
        int total = queue.size();
        DrawItem item = current();
        int current = running && item != null ? Math.min(total, index + 1) : Math.min(total, processedCount);
        long elapsed = runStartedAt == 0 ? 0 : Math.max(0, System.currentTimeMillis() - runStartedAt);
        long pausedTime = pausedTotalMs + (paused ? Math.max(0, System.currentTimeMillis() - pausedAt) : 0);
        long activeMs = Math.max(0, elapsed - pausedTime);
        double average = processedCount == 0 ? 0 : activeMs / 1000.0 / processedCount;
        String phaseLabel = !running ? "已完成" : paused ? "已暫停" : reviewMode ? "只讀重掃中" : "執行中";
        return new RunUiSnapshot(running, paused, current, total, processedCount,
                item == null ? "" : item.product, phaseLabel, overlayStatus,
                remainingMs(), elapsed, average);
    }

    private void stopRun() {
        if (running && reviewMode && !reviewOutcomes.isEmpty()) {
            DrawData.saveReviewOutcomes(this, reviewOutcomes);
            DrawData.saveReviewReport(this, "使用者停止重掃；以下僅為已檢查部分。\n\n" + reviewReport());
        }
        running = false;
        paused = false;
        finishing = false;
        queue.clear();
        cancelInspect();
        handler.removeCallbacks(overlayTick);
        releaseVisualReader();
        OrientationGuard.restore(this);
        hideOverlay();
    }

    private void releaseVisualReader() {
        if (visualReader != null) { visualReader.close(); visualReader = null; }
        visualInFlight = false;
    }

    private String reviewReport() {
        StringBuilder result = new StringBuilder((manualCheck ? "手動連結只讀核對 " : "只讀重掃 ") + queue.size() + " 筆\n\n");
        result.append("確認中獎：").append(reviewWins).append(" 筆\n");
        result.append("確認未中：").append(reviewLosses).append(" 筆\n");
        result.append("未開賣：").append(reviewScheduled).append(" 筆\n");
        result.append("未加好友：").append(reviewNotFriend).append(" 筆\n");
        result.append("系統忙碌：").append(reviewBusy).append(" 筆\n");
        result.append("連結失效（404）：").append(reviewNotFound).append(" 筆\n");
        result.append("未抽籤：").append(reviewNotDrawn).append(" 筆\n");
        result.append("已結束／免重抽：").append(reviewEnded).append(" 筆\n");
        result.append("無法判定：").append(reviewUncertain).append(" 筆\n\n");
        if (reviewWinners.isEmpty()) result.append("本次沒有確認中獎的連結。\n");
        else {
            result.append("確認中獎清單：\n");
            for (String winner : reviewWinners) result.append("• ").append(winner).append("\n");
        }
        if (!reviewProblems.isEmpty()) {
            result.append("需檢查（最多列出 30 筆）：\n");
            for (String problem : reviewProblems) result.append("• ").append(problem).append("\n");
        }
        result.append("\n只讀取頁面；不按參加抽獎、不按使用優惠券。無法判定不等於未中獎。");
        return result.toString();
    }

    private String regularRunReport() {
        int operated = 0;
        int already = 0;
        int ended = 0;
        int scheduled = 0;
        int notFriend = 0;
        int busy = 0;
        int problems = 0;
        int notFound = 0;
        for (DrawItem item : queue) {
            if (item.status.equals("done")) operated++;
            else if (item.status.equals("already")) already++;
            else if (item.status.equals("expired")) ended++;
            else if (item.status.equals("scheduled")) scheduled++;
            else if (item.status.equals("not_friend")) notFriend++;
            else if (item.status.equals("busy")) busy++;
            else if (item.status.equals("not_found")) notFound++;
            else if (item.status.equals("error")) problems++;
        }
        return "本次任務完成 " + queue.size() + " 筆\n" +
                "耗時：" + formattedActiveDuration() + "\n\n" +
                "已按參加抽獎：" + operated + " 筆\n" +
                "已抽過並跳過：" + already + " 筆\n" +
                "未開賣：" + scheduled + " 筆\n" +
                "未加好友：" + notFriend + " 筆\n" +
                "系統忙碌：" + busy + " 筆\n" +
                "連結失效（404）：" + notFound + " 筆\n" +
                "已結束：" + ended + " 筆\n" +
                "需要確認：" + problems + " 筆\n\n" +
                "本流程不判讀即時中獎結果，也永遠不操作「使用優惠券」。可到「結果」執行只讀重掃。";
    }

    private boolean isTestRun() {
        if (queue.isEmpty()) return false;
        for (DrawItem item : queue)
            if (!item.origin.equals("test") && !item.origin.equals("mock")) return false;
        return true;
    }

    private String testRunReport() {
        StringBuilder report = new StringBuilder("測試流程完成 ")
                .append(queue.size()).append(" 筆\n耗時：")
                .append(formattedActiveDuration()).append("\n\n");
        for (DrawItem item : queue) {
            report.append("• ").append(item.product).append("：")
                    .append(testStatusLabel(item.status)).append('\n');
        }
        report.append("\n測試紀錄與正式本週抽選完全分開；永遠不操作「使用優惠券」。");
        return report.toString();
    }

    private String testStatusLabel(String status) {
        if (status.equals("done")) return "已完成測試抽選";
        if (status.equals("already")) return "已抽過／安全跳過";
        if (status.equals("expired")) return "已結束";
        if (status.equals("scheduled")) return "未開賣";
        if (status.equals("not_friend")) return "未加好友";
        if (status.equals("busy")) return "系統忙碌";
        if (status.equals("win")) return "中獎畫面";
        if (status.equals("lose")) return "未中獎畫面";
        if (status.equals("error")) return "需檢查";
        if (status.equals("not_found")) return "連結失效（404）";
        return status;
    }

    private String reviewReason(String status) {
        if (status.equals("scheduled")) return "尚未開賣";
        if (status.equals("not_friend")) return "尚未加入官方帳號";
        if (status.equals("busy")) return "LINE 系統忙碌，稍後可重試";
        if (status.equals("expired")) return "優惠券已結束／失效";
        if (status.equals("error")) return "頁面載入或關閉失敗";
        if (status.equals("not_found")) return "短網址已失效（404）";
        return "畫面未明確顯示抽選結果";
    }

    private String formattedActiveDuration() {
        long elapsed = runStartedAt == 0 ? 0 : Math.max(0, System.currentTimeMillis() - runStartedAt);
        long pausedTime = pausedTotalMs + (paused ? Math.max(0, System.currentTimeMillis() - pausedAt) : 0);
        long seconds = Math.max(0, elapsed - pausedTime) / 1000L;
        long minutes = seconds / 60L;
        long hours = minutes / 60L;
        seconds %= 60L;
        minutes %= 60L;
        if (hours > 0) return hours + " 小時 " + minutes + " 分 " + seconds + " 秒";
        if (minutes > 0) return minutes + " 分 " + seconds + " 秒";
        return seconds + " 秒";
    }

    private void showOverlay() {
        if (overlay != null) return;
        targetDisplayId = requestedStartDisplayId == Display.INVALID_DISPLAY
                ? detectRunDisplay() : requestedStartDisplayId;
        showOverlayOnDisplay(targetDisplayId, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
    }

    private int detectRunDisplay() {
        if (Build.VERSION.SDK_INT < 30) return Display.DEFAULT_DISPLAY;
        int activeOwnDisplay = Display.INVALID_DISPLAY;
        int activeLineDisplay = Display.INVALID_DISPLAY;
        int visibleOwnDisplay = Display.INVALID_DISPLAY;
        try {
            SparseArray<List<AccessibilityWindowInfo>> all = getWindowsOnAllDisplays();
            for (int i = 0; i < all.size(); i++) {
                int displayId = all.keyAt(i);
                List<AccessibilityWindowInfo> windows = all.valueAt(i);
                if (windows == null) continue;
                for (AccessibilityWindowInfo info : windows) {
                    AccessibilityNodeInfo root = info == null ? null : info.getRoot();
                    if (root == null) continue;
                    try {
                        CharSequence packageName = root.getPackageName();
                        if (packageName == null) continue;
                        String value = packageName.toString();
                        boolean active = info.isActive() || info.isFocused();
                        if (value.equals(getPackageName())) {
                            if (active) activeOwnDisplay = displayId;
                            else visibleOwnDisplay = displayId;
                        } else if (value.equals("jp.naver.line.android") && active) {
                            activeLineDisplay = displayId;
                        }
                    } finally { root.recycle(); }
                }
            }
        } catch (Exception error) {
            Log.w(TAG, "unable to detect active display", error);
        }
        if (activeOwnDisplay != Display.INVALID_DISPLAY) return activeOwnDisplay;
        if (activeLineDisplay != Display.INVALID_DISPLAY) return activeLineDisplay;
        if (visibleOwnDisplay != Display.INVALID_DISPLAY) return visibleOwnDisplay;
        return Display.DEFAULT_DISPLAY;
    }

    private void moveOverlayToDisplay(int displayId) {
        if (!running || displayId == Display.INVALID_DISPLAY || displayId == overlayDisplayId) return;
        handler.post(() -> {
            if (!running || displayId == overlayDisplayId) return;
            Log.i(TAG, "move overlay display " + overlayDisplayId + " -> " + displayId);
            hideOverlay();
            showOverlayOnDisplay(displayId, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY);
            paintOverlay();
        });
    }

    private Context windowContextFor(int displayId, int type) {
        try {
            DisplayManager manager = getSystemService(DisplayManager.class);
            Display display = manager == null ? null : manager.getDisplay(displayId);
            if (display == null) return null;
            Context displayContext = createDisplayContext(display);
            return Build.VERSION.SDK_INT >= 30 ? displayContext.createWindowContext(type, null) : displayContext;
        } catch (Exception error) {
            Log.w(TAG, "window context unavailable for display " + displayId, error);
            return null;
        }
    }

    private void showOverlayOnDisplay(int displayId, int type) {
        if (overlay != null) return;
        Context uiContext = windowContextFor(displayId, type);
        if (uiContext == null) {
            displayId = Display.DEFAULT_DISPLAY;
            uiContext = windowContextFor(displayId, type);
        }
        if (uiContext == null) uiContext = this;
        overlayContext = uiContext;
        windowManager = uiContext.getSystemService(WindowManager.class);
        LinearLayout card = new LinearLayout(uiContext);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(11), dp(10), dp(11), dp(8));
        card.setBackground(overlayBackground(0xF2FFFFFF, 0xAAFFFFFF, 20));
        card.setElevation(dp(12));
        LinearLayout row = new LinearLayout(uiContext);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        progressIcon = new ImageView(uiContext);
        progressIcon.setImageResource(R.drawable.fangba_app_icon);
        progressIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        progressIcon.setBackground(overlayBackground(0xFFE1F7ED, 0xAAFFFFFF, 12));
        progressIcon.setClipToOutline(true);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(36), dp(36));
        iconParams.rightMargin = dp(9);
        row.addView(progressIcon, iconParams);
        LinearLayout labels = new LinearLayout(uiContext);
        labels.setOrientation(LinearLayout.VERTICAL);
        overlayText = new TextView(uiContext);
        overlayText.setTextColor(0xFF11223B);
        overlayText.setTextSize(12);
        overlayText.setTypeface(null, Typeface.BOLD);
        overlayText.setIncludeFontPadding(false);
        overlayText.setSingleLine(true);
        overlayText.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(overlayText);
        overlayItem = new TextView(uiContext);
        overlayItem.setTextColor(0xFF425773);
        overlayItem.setTextSize(10);
        overlayItem.setSingleLine(true);
        overlayItem.setEllipsize(TextUtils.TruncateAt.END);
        overlayItem.setVisibility(View.GONE);
        labels.addView(overlayItem, new LinearLayout.LayoutParams(0, 0));
        overlayMeta = new TextView(uiContext);
        overlayMeta.setTextColor(0xFF6D7F97);
        overlayMeta.setTextSize(10);
        overlayMeta.setIncludeFontPadding(false);
        overlayMeta.setSingleLine(true);
        overlayMeta.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(overlayMeta);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        pauseButton = new Button(uiContext);
        pauseButton.setText("暫停");
        pauseButton.setAllCaps(false);
        pauseButton.setTextSize(10);
        pauseButton.setTextColor(0xFF163052);
        pauseButton.setBackground(overlayBackground(0xFFE1F7ED, 0xFFBFDDCF, 99));
        pauseButton.setPadding(dp(7), 0, dp(7), 0);
        pauseButton.setOnClickListener(v -> togglePauseRun());
        LinearLayout.LayoutParams pauseParams = new LinearLayout.LayoutParams(dp(58), dp(36));
        pauseParams.leftMargin = dp(7);
        row.addView(pauseButton, pauseParams);
        Button stop = new Button(uiContext);
        stop.setText("停止");
        stop.setAllCaps(false);
        stop.setTextSize(10);
        stop.setTextColor(0xFFB44740);
        stop.setBackground(overlayBackground(0xFFFFEFEF, 0xFFEFCBC8, 99));
        stop.setPadding(dp(7), 0, dp(7), 0);
        stop.setOnClickListener(v -> stopRun());
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(dp(54), dp(36));
        stopParams.leftMargin = dp(6);
        row.addView(stop, stopParams);
        card.addView(row);
        progressLine = new ProgressBar(uiContext, null, android.R.attr.progressBarStyleHorizontal);
        progressLine.setMax(Math.max(1, queue.size()));
        progressLine.setProgressTintList(ColorStateList.valueOf(0xFF2A7568));
        progressLine.setProgressBackgroundTintList(ColorStateList.valueOf(0xFFE8E2D8));
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, dp(5));
        lineParams.topMargin = dp(7);
        card.addView(progressLine, lineParams);
        int overlayWidth = Math.max(dp(300), uiContext.getResources().getDisplayMetrics().widthPixels - dp(16));
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                overlayWidth, -2, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        // Keep LINE's top-right close control unobstructed.
        params.y = dp(58);
        final int actualDisplayId = displayId;
        try {
            if (windowManager == null) throw new IllegalStateException("WindowManager unavailable");
            windowManager.addView(card, params);
            overlay = card;
            overlay.setVisibility(View.VISIBLE);
            overlayDisplayId = actualDisplayId;
            targetDisplayId = actualDisplayId;
            Log.i(TAG, "overlay type=" + type + " display=" + actualDisplayId);
        }
        catch (Exception error) {
            Log.w(TAG, "overlay unavailable type=" + type + " display=" + actualDisplayId, error);
            clearOverlayReferences();
            if (type == WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY && Settings.canDrawOverlays(this)) {
                showOverlayOnDisplay(actualDisplayId, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
            }
        }
    }

    private void updateOverlay(String status) {
        overlayStatus = status;
        paintOverlay();
    }

    private void paintOverlay() {
        if (overlayText == null) return;
        RunUiSnapshot snapshot = buildUiSnapshot();
        if (progressIcon != null) progressIcon.setAlpha(snapshot.paused ? 0.58f : 1f);
        if (progressLine != null) progressLine.setProgress(Math.max(snapshot.processed, snapshot.current));
        if (pauseButton != null) pauseButton.setText(snapshot.paused ? "繼續" : "暫停");
        String product = snapshot.product.isEmpty() ? snapshot.phase : snapshot.product;
        overlayText.setText(snapshot.current + " / " + snapshot.total + " · " + product);
        overlayItem.setText("");
        StringBuilder meta = new StringBuilder(snapshot.phase);
        if (snapshot.remainingMs > 0)
            meta.append(String.format(Locale.TAIWAN, " · 剩 %.1f 秒", snapshot.remainingMs / 1000.0));
        meta.append(" · 耗時 ").append(formatDuration(snapshot.elapsedMs));
        if (snapshot.averageSeconds > 0)
            meta.append(String.format(Locale.TAIWAN, " · %.1f 秒/筆", snapshot.averageSeconds));
        overlayMeta.setText(meta.toString());
    }

    private String formatDuration(long milliseconds) {
        long totalSeconds = Math.max(0, milliseconds / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.TAIWAN, "%02d:%02d", minutes, seconds);
    }

    private void hideOverlay() {
        if (overlay != null && windowManager != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) {}
        }
        clearOverlayReferences();
    }

    private void clearOverlayReferences() {
        overlay = null;
        overlayContext = null;
        overlayDisplayId = Display.INVALID_DISPLAY;
        windowManager = null;
        overlayText = null;
        overlayItem = null;
        overlayMeta = null;
        progressIcon = null;
        progressLine = null;
        pauseButton = null;
    }
}
