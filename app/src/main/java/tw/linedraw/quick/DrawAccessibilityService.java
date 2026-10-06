package tw.linedraw.quick;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;
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

public final class DrawAccessibilityService extends AccessibilityService {
    private static final String TAG = "LineDrawTest";
    private static DrawAccessibilityService instance;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<DrawItem> queue = new ArrayList<>();
    private WindowManager windowManager;
    private View overlay;
    private TextView overlayText;
    private TextView overlayItem;
    private TextView overlayMeta;
    private ProgressBar progressIcon;
    private ProgressBar progressLine;
    private Button pauseButton;
    private int index;
    private int processedCount;
    private boolean running;
    private boolean paused;
    private boolean finishing;
    private boolean verifiedCurrent;
    private boolean friendJoinAttempted;
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
    private int reviewUncertain;
    private int reviewEnded;
    private int reviewMismatches;
    private final ArrayList<String> reviewWinners = new ArrayList<>();
    private final ArrayList<String> reviewProblems = new ArrayList<>();
    private final Map<String, String> reviewOutcomes = new HashMap<>();
    private String previousCouponText = "";
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
        if (running && !paused && !handler.hasCallbacks(inspectRunnable)) handler.postDelayed(inspectRunnable, 220);
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
                reviewUncertain = reviewMismatches = reviewEnded = 0;
        reviewWinners.clear();
        reviewProblems.clear();
        reviewOutcomes.clear();
        if (singleManualCheck) reviewOutcomes.putAll(DrawData.loadReviewOutcomes(this));
        else if (readOnly) DrawData.saveReviewOutcomes(this, reviewOutcomes);
        index = 0;
        processedCount = 0;
        pausedTotalMs = 0;
        runStartedAt = System.currentTimeMillis();
        running = true;
        paused = false;
        finishing = false;
        if (!OrientationGuard.lock(this)) Log.w(TAG, "device portrait lock unavailable; app itself remains portrait");
        showOverlay();
        handler.post(overlayTick);
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
        gestureAttempted = false;
        linkLaunched = false;
        visualInFlight = false;
        visualCandidate = "";
        visualCandidateCount = 0;
        visualTransitionCount = 0;
        pendingDrawLabel = "";
        AccessibilityNodeInfo previous = couponRoot();
        previousCouponText = previous == null ? "" : normalized(collectText(previous, new StringBuilder(), 0).toString());
        if (previous != null) previous.recycle();
        if (!reviewMode) {
            item.status = "error";
            item.selected = false;
            if (!item.origin.equals("mock")) DrawData.updateItem(this, item);
        }
        Log.i(TAG, (reviewMode ? "review" : "open") + " item " + (index + 1) + "/" + queue.size());
        updateOverlay((reviewMode ? "只讀重掃「" : "開啟「") + item.product + "」");
        if (item.origin.equals("mock")) {
            Intent mock = new Intent(this, MockCouponActivity.class);
            mock.putExtra("scenario", item.url.substring("mock://".length()));
            mock.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(mock);
            linkLaunched = true;
            stageAt = System.currentTimeMillis();
            handler.postDelayed(inspectRunnable, 650);
            return;
        }
        launchCurrentLink(item);
    }

    private void launchCurrentLink(DrawItem item) {
        new Thread(() -> {
            String target = resolveLiff(item.url);
            handler.post(() -> {
                if (!running || current() != item) return;
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(target));
                    intent.setPackage("jp.naver.line.android");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    linkLaunched = true;
                    stageAt = System.currentTimeMillis();
                } catch (Exception first) {
                    try {
                        Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse(item.url));
                        browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(browser);
                        linkLaunched = true;
                        stageAt = System.currentTimeMillis();
                    } catch (Exception second) { finishItem("error", "連結無法開啟"); }
                }
                if (!handler.hasCallbacks(inspectRunnable)) handler.postDelayed(inspectRunnable, 650);
            });
        }, "linedraw-resolve").start();
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
            if (!running || paused || finishing || stage.equals("closing") || current() == null) return;
            inspect();
            if (running && !paused && !finishing) handler.postDelayed(this, reviewMode ? 240 : 600);
        }
    };

    private void inspect() {
        if (stage.equals("submitted") || stage.equals("joining")) return;
        if (stage.equals("opening") && !linkLaunched) return;
        if (stage.equals("confirming")) { confirmSubmission(); return; }
        if (System.currentTimeMillis() - stageAt > (reviewMode ? 10000 : 20000)) {
            closeAndFinish(reviewMode ? "unknown" : "error", "等待畫面逾時・未確認");
            return;
        }
        DrawItem item = current();
        AccessibilityNodeInfo active = getRootInActiveWindow();
        if (active != null) {
            try { if (!reviewMode && joinFriendIfRequired(active, item)) return; }
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
            if (CouponPageSignals.isEnded(primaryText) &&
                    !normalized(text).equals(previousCouponText) &&
                    System.currentTimeMillis() - stageAt >= 1200) {
                closeAndFinish("expired", "優惠券已結束・跳下一筆");
                return;
            }
            if (item != null && !verifiedCurrent) {
                if (item.origin.equals("mock")) {
                    if (!text.contains("K爸測試，不會有真實訂單") || !text.contains(item.product)) return;
                } else if (item.origin.equals("test") && item.product.startsWith("方爸測試 ")) {
                    if (!matchesTestCoupon(text, item.product)) return;
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
        if (!primary.contains("加入好友") && !primary.contains("加好友") &&
                !primary.contains("新增好友") && !primary.contains("加入官方帳號") &&
                !primary.toLowerCase(Locale.ROOT).contains("add friend")) return false;
        if (hasPrimaryRedemptionControl(root)) return false;
        AccessibilityNodeInfo friendDraw = findPrimaryAction(root, "加入好友並參加抽獎");
        if (friendDraw != null && friendDraw.isEnabled()) {
            friendJoinAttempted = true;
            if (attemptDraw(friendDraw, item)) {
                Log.i(TAG, "join friend and draw item " + (index + 1));
                return true;
            }
            friendJoinAttempted = false;
            return false;
        }
        AccessibilityNodeInfo join = findText(root, "加入好友", "加好友", "新增好友", "加入官方帳號", "Add friend", "Add as friend");
        if (join == null || !join.isEnabled() || !clickNode(join)) return false;
        friendJoinAttempted = true;
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
            ready = (packageName.equals("jp.naver.line.android") ||
                    (item.origin.equals("mock") && packageName.equals(getPackageName()))) &&
                    text.contains("參加抽獎") &&
                    findText(root, "加入好友", "加好友", "新增好友", "加入官方帳號", "Add friend", "Add as friend") == null;
            root.recycle();
        }
        stage = "opening";
        stageAt = System.currentTimeMillis() - (ready ? 1200 : 0);
        verifiedCurrent = false;
        if (ready) {
            updateOverlay("好友已加入・確認抽獎頁");
            handler.post(inspectRunnable);
        } else {
            updateOverlay("重新開啟同一筆連結");
            if (item.origin.equals("mock")) {
                // The offline fixture changes in place; let the normal timeout report an unresolved prompt.
                handler.post(inspectRunnable);
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
        handler.postDelayed(inspectRunnable, 650);
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
        if (item == null || item.origin.equals("mock") || visualInFlight ||
                System.currentTimeMillis() - lastVisualAt < (reviewMode ? 400 : 650) || !lineIsForeground()) return;
        visualInFlight = true;
        lastVisualAt = System.currentTimeMillis();
        if (visualReader == null) visualReader = new VisualCouponReader(DrawData.lowMemoryMode(this));
        visualReader.read(this, new VisualCouponReader.Callback() {
            @Override public void onRead(VisualCouponReader.Frame frame) {
                visualInFlight = false;
                if (!running || paused || current() != item) return;
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
            friendJoinAttempted = true;
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

    private boolean matchesTestCoupon(String text, String product) {
        String page = normalized(text);
        String name = normalized(product);
        int title = page.indexOf(name);
        int otherCoupons = page.indexOf("其他優惠券");
        return title >= 0 && (otherCoupons < 0 || title < otherCoupons);
    }

    private String normalized(String text) { return text.replaceAll("\\s+", ""); }

    private boolean hasProcessedText(String text) {
        return text.contains("可惜...沒有抽中") || text.contains("沒有抽中！") ||
                text.contains("未中獎") || text.contains("恭喜中獎") ||
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
        handler.removeCallbacks(inspectRunnable);
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
        if (!running || finishing || stage.equals("closing")) return;
        stage = "closing";
        stageAt = System.currentTimeMillis();
        updateOverlay("關閉優惠券，準備下一筆");
        clickCloseControl();
        handler.postDelayed(() -> verifyClosed(status, description, 0, 0), reviewMode ? 180 : 450);
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
            if (absentChecks >= 1) finishItem(status, description);
            else handler.postDelayed(() -> verifyClosed(status, description, attempts, 1), reviewMode ? 180 : 300);
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
            } else if (status.equals("not_drawn") || status.equals("pending")) {
                reviewNotDrawn++;
                if (status.equals("not_drawn") && isClaimedProcessed(item.status)) {
                    reviewMismatches++;
                    reviewOutcomes.put(item.key(), "mismatch");
                    if (reviewProblems.size() < 30) reviewProblems.add(item.store + " · " + item.product + "：先前標完成，但頁面仍可抽");
                    item.status = "error";
                    DrawData.updateItem(this, item);
                } else if (!isTerminalStatus(item.status)) {
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
        long delay = reviewMode ? 60L : WaitPolicy.sampleMillis(DrawData.delay(this));
        stage = "next";
        nextAt = System.currentTimeMillis() + delay;
        updateOverlay(String.format(Locale.TAIWAN, "每筆完成等待 %.2f 秒", delay / 1000.0));
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                if (!running) return;
                if (paused) { handler.postDelayed(this, 250); return; }
                long remaining = nextAt - System.currentTimeMillis();
                if (remaining > 0) { handler.postDelayed(this, remaining); return; }
                index++;
                openCurrent();
            }
        }, delay);
    }

    private boolean isTerminalStatus(String status) {
        return status.equals("win") || status.equals("lose") || status.equals("expired");
    }

    private boolean isClaimedProcessed(String status) {
        return status.equals("done") || status.equals("already") ||
                status.equals("win") || status.equals("lose");
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
            if (isTestRun()) DrawData.saveTestReport(this, testRunReport());
        }
        updateOverlay("已處理 " + queue.size() + " / " + queue.size());
        handler.postDelayed(() -> {
            hideOverlay();
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
        }, 1500);
    }

    private void pauseRun() {
        if (paused) return;
        paused = true;
        pausedAt = System.currentTimeMillis();
        if (pauseButton != null) pauseButton.setText("繼續");
        updateOverlay("已暫停");
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
        handler.removeCallbacks(inspectRunnable);
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
        result.append("未抽籤：").append(reviewNotDrawn).append(" 筆\n");
        result.append("已結束／免重抽：").append(reviewEnded).append(" 筆\n");
        result.append("無法判定：").append(reviewUncertain).append(" 筆\n\n");
        result.append("疑似漏按且已重新列待處理：").append(reviewMismatches).append(" 筆\n\n");
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
        int problems = 0;
        for (DrawItem item : queue) {
            if (item.status.equals("done")) operated++;
            else if (item.status.equals("already")) already++;
            else if (item.status.equals("expired")) ended++;
            else if (item.status.equals("scheduled")) scheduled++;
            else if (item.status.equals("not_friend")) notFriend++;
            else if (item.status.equals("error")) problems++;
        }
        return "本次任務完成 " + queue.size() + " 筆\n\n" +
                "已按參加抽獎：" + operated + " 筆\n" +
                "已抽過並跳過：" + already + " 筆\n" +
                "未開賣：" + scheduled + " 筆\n" +
                "未加好友：" + notFriend + " 筆\n" +
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
                .append(queue.size()).append(" 筆\n\n");
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
        if (status.equals("win")) return "中獎畫面";
        if (status.equals("lose")) return "未中獎畫面";
        if (status.equals("error")) return "需檢查";
        return status;
    }

    private String reviewReason(String status) {
        if (status.equals("scheduled")) return "尚未開賣";
        if (status.equals("not_friend")) return "尚未加入官方帳號";
        if (status.equals("expired")) return "優惠券已結束／失效";
        if (status.equals("error")) return "頁面載入或關閉失敗";
        return "畫面未明確顯示抽選結果";
    }

    private void showOverlay() {
        if (overlay != null) return;
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(8), dp(5), dp(8), dp(5));
        card.setBackgroundColor(0xF2EAF3FF);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        progressIcon = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        progressIcon.setIndeterminateTintList(ColorStateList.valueOf(0xFF08A84F));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(26), dp(26));
        iconParams.rightMargin = dp(8);
        row.addView(progressIcon, iconParams);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        overlayText = new TextView(this);
        overlayText.setTextColor(0xFF11223B);
        overlayText.setTextSize(13);
        overlayText.setTypeface(null, Typeface.BOLD);
        labels.addView(overlayText);
        overlayItem = new TextView(this);
        overlayItem.setTextColor(0xFF425773);
        overlayItem.setTextSize(11);
        overlayItem.setSingleLine(true);
        overlayItem.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(overlayItem);
        overlayMeta = new TextView(this);
        overlayMeta.setTextColor(0xFF425773);
        overlayMeta.setTextSize(11);
        overlayMeta.setSingleLine(true);
        overlayMeta.setEllipsize(TextUtils.TruncateAt.END);
        labels.addView(overlayMeta);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
        pauseButton = new Button(this);
        pauseButton.setText("暫停");
        pauseButton.setTextSize(11);
        pauseButton.setOnClickListener(v -> {
            paused = !paused;
            if (paused) pausedAt = System.currentTimeMillis();
            else {
                long pauseDuration = System.currentTimeMillis() - pausedAt;
                pausedTotalMs += pauseDuration;
                stageAt += pauseDuration;
                closeAt += pauseDuration;
                nextAt += pauseDuration;
            }
            pauseButton.setText(paused ? "繼續" : "暫停");
            if (!paused) handler.post(inspectRunnable);
            updateOverlay(paused ? "已暫停" : "繼續抽選");
        });
        row.addView(pauseButton, new LinearLayout.LayoutParams(dp(63), dp(40)));
        Button stop = new Button(this);
        stop.setText("停止");
        stop.setTextSize(11);
        stop.setOnClickListener(v -> stopRun());
        row.addView(stop, new LinearLayout.LayoutParams(dp(59), dp(40)));
        card.addView(row);
        progressLine = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressLine.setMax(Math.max(1, queue.size()));
        progressLine.setProgressTintList(ColorStateList.valueOf(0xFF315F9D));
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(-1, dp(4));
        lineParams.topMargin = dp(4);
        card.addView(progressLine, lineParams);
        int type = Settings.canDrawOverlays(this) ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY :
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                -1, -2, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP;
        // Keep LINE's top-right close control unobstructed.
        params.y = dp(58);
        try { windowManager.addView(card, params); overlay = card; Log.i(TAG, "overlay type=" + type); }
        catch (Exception error) {
            Log.w(TAG, "overlay unavailable", error);
            if (type == WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY) {
                params.type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY;
                try { windowManager.addView(card, params); overlay = card; Log.i(TAG, "overlay accessibility fallback"); }
                catch (Exception fallback) { Log.w(TAG, "accessibility overlay unavailable", fallback); }
            }
        }
    }

    private void updateOverlay(String status) {
        overlayStatus = status;
        paintOverlay();
    }

    private void paintOverlay() {
        if (overlayText == null) return;
        if (progressIcon != null) progressIcon.setVisibility(running && !paused ? View.VISIBLE : View.INVISIBLE);
        if (progressLine != null) progressLine.setProgress(processedCount);
        String countdown = "";
        if (running && !paused) {
            long remaining = stage.equals("opening") ? stageAt + 20000 - System.currentTimeMillis() :
                    stage.equals("submitted") ? closeAt - System.currentTimeMillis() :
                    stage.equals("next") ? nextAt - System.currentTimeMillis() : 0;
            if (stage.equals("opening") || stage.equals("submitted") || stage.equals("next"))
                countdown = " · " + String.format(Locale.TAIWAN, "%.1f 秒", Math.max(0, remaining) / 1000.0);
        }
        String phase = !running ? "已完成" : paused ? "已暫停" : reviewMode ? "只讀重掃中" : "執行中";
        overlayText.setText("已處理 " + processedCount + " / " + queue.size() + " · " + phase);
        DrawItem item = current();
        overlayItem.setText(item == null || !running ? "" :
                "目前第 " + (index + 1) + " 筆 · " + item.store + " · " + item.product);
        String speed = "";
        if (processedCount >= 2) {
            long pausedTime = pausedTotalMs + (paused ? System.currentTimeMillis() - pausedAt : 0);
            long activeMs = Math.max(0, System.currentTimeMillis() - runStartedAt - pausedTime);
            speed = String.format(Locale.TAIWAN, " · 均速 %.1f 秒/筆", activeMs / 1000.0 / processedCount);
        }
        overlayMeta.setText(overlayStatus + countdown + speed);
    }

    private void hideOverlay() {
        if (overlay != null && windowManager != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) {}
        }
        overlay = null;
        overlayText = null;
        overlayItem = null;
        overlayMeta = null;
        progressIcon = null;
        progressLine = null;
        pauseButton = null;
    }
}
