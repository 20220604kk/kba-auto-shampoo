package tw.linedraw.quick;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;

final class AppUpdateManager {
    private static final String RELEASE_API =
            "https://api.github.com/repos/20220604kk/kba-auto-shampoo/releases/latest";
    private static final String PREFS = "online-updater";
    private static final String DOWNLOAD_ID = "download-id";
    private static final String DOWNLOAD_PATH = "download-path";
    private static final String LAST_CHECK = "last-check";
    private static final String PERMISSION_PROMPTED = "permission-prompted";
    private static final long AUTO_INTERVAL_MS = 12L * 60L * 60L * 1000L;

    private AppUpdateManager() {}

    static void check(Activity activity, boolean manual) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        if (!manual && now - prefs.getLong(LAST_CHECK, 0) < AUTO_INTERVAL_MS) return;
        prefs.edit().putLong(LAST_CHECK, now).apply();
        if (manual) Toast.makeText(activity, "正在檢查新版…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(RELEASE_API).openConnection();
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "application/vnd.github+json");
                connection.setRequestProperty("User-Agent", "KBA-Auto-Shampoo-Android");
                int status = connection.getResponseCode();
                if (status != 200) throw new IllegalStateException("更新服務 HTTP " + status);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                try (InputStream input = connection.getInputStream()) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) >= 0) bytes.write(buffer, 0, count);
                }
                byte[] payload = bytes.toByteArray();
                JSONObject release = new JSONObject(new String(payload, StandardCharsets.UTF_8));
                String tag = release.optString("tag_name", "").trim();
                String notes = release.optString("body", "").trim();
                JSONArray assets = release.optJSONArray("assets");
                JSONObject apk = null;
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject candidate = assets.optJSONObject(i);
                        if (candidate != null && candidate.optString("name", "")
                                .toLowerCase(Locale.ROOT).endsWith(".apk")) {
                            apk = candidate;
                            break;
                        }
                    }
                }
                String current = currentVersion(activity);
                if (tag.isEmpty() || compareVersions(tag, current) <= 0) {
                    if (manual) activity.runOnUiThread(() -> Toast.makeText(activity,
                            "目前已是最新版 v" + current, Toast.LENGTH_LONG).show());
                    return;
                }
                JSONObject finalApk = apk;
                activity.runOnUiThread(() -> showAvailable(activity, tag, notes, finalApk));
            } catch (Exception error) {
                if (manual) activity.runOnUiThread(() -> Toast.makeText(activity,
                        "暫時無法檢查更新：" + safeMessage(error), Toast.LENGTH_LONG).show());
            }
        }, "app-update-check").start();
    }

    private static void showAvailable(Activity activity, String tag, String notes, JSONObject asset) {
        String message = "新版 " + tag + " 已可下載。";
        if (!notes.isEmpty()) message += "\n\n" + trimNotes(notes);
        if (asset == null) message += "\n\n此版本尚未附上 APK，請稍後再試。";
        new AlertDialog.Builder(activity)
                .setTitle("洗頭貓找到新版本")
                .setMessage(message)
                .setPositiveButton(asset == null ? "知道了" : "下載更新", (dialog, which) -> {
                    if (asset != null) download(activity, tag, asset);
                })
                .setNegativeButton(asset == null ? null : "稍後", null)
                .show();
    }

    private static void download(Activity activity, String tag, JSONObject asset) {
        try {
            String url = asset.getString("browser_download_url");
            String name = asset.optString("name", "KBA-Auto-Shampoo-" + tag + ".apk")
                    .replaceAll("[^A-Za-z0-9._-]", "_");
            File directory = activity.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
            if (directory == null) throw new IllegalStateException("找不到下載資料夾");
            File target = new File(directory, name);
            if (target.exists() && !target.delete())
                throw new IllegalStateException("無法取代舊的更新檔");
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url))
                    .setTitle("K爸全自動洗頭機 " + tag)
                    .setDescription("新版下載完成後會開啟安裝畫面")
                    .setMimeType("application/vnd.android.package-archive")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationUri(Uri.fromFile(target));
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            long id = manager.enqueue(request);
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putLong(DOWNLOAD_ID, id).putString(DOWNLOAD_PATH, target.getAbsolutePath())
                    .putBoolean(PERMISSION_PROMPTED, false).apply();
            Toast.makeText(activity, "開始下載新版，完成後會提示安裝", Toast.LENGTH_LONG).show();
        } catch (Exception error) {
            Toast.makeText(activity, "無法下載更新：" + safeMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    static void handleDownload(Activity activity, long completedId) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (completedId != prefs.getLong(DOWNLOAD_ID, -1)) return;
        resumePendingInstall(activity);
    }

    static void resumePendingInstall(Activity activity) {
        SharedPreferences prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long id = prefs.getLong(DOWNLOAD_ID, -1);
        String path = prefs.getString(DOWNLOAD_PATH, "");
        if (id < 0 || path.isEmpty() || !new File(path).isFile()) return;
        try {
            DownloadManager manager = (DownloadManager) activity.getSystemService(Context.DOWNLOAD_SERVICE);
            DownloadManager.Query query = new DownloadManager.Query().setFilterById(id);
            try (android.database.Cursor cursor = manager.query(query)) {
                if (cursor == null || !cursor.moveToFirst()) return;
                int state = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                if (state != DownloadManager.STATUS_SUCCESSFUL) return;
            }
            verifyArchive(activity, path);
            if (Build.VERSION.SDK_INT >= 26 && !activity.getPackageManager().canRequestPackageInstalls()) {
                if (prefs.getBoolean(PERMISSION_PROMPTED, false)) return;
                prefs.edit().putBoolean(PERMISSION_PROMPTED, true).apply();
                Toast.makeText(activity, "請允許洗頭機安裝更新，返回後會繼續", Toast.LENGTH_LONG).show();
                activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + activity.getPackageName())));
                return;
            }
            Uri uri = manager.getUriForDownloadedFile(id);
            if (uri == null) throw new IllegalStateException("找不到已下載的 APK");
            Intent install = new Intent(Intent.ACTION_VIEW, uri);
            install.setDataAndType(uri, "application/vnd.android.package-archive");
            install.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(install);
            prefs.edit().remove(DOWNLOAD_ID).remove(DOWNLOAD_PATH).remove(PERMISSION_PROMPTED).apply();
        } catch (Exception error) {
            Toast.makeText(activity, "更新檔驗證失敗：" + safeMessage(error), Toast.LENGTH_LONG).show();
        }
    }

    private static void verifyArchive(Activity activity, String path) throws Exception {
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES :
                PackageManager.GET_SIGNATURES;
        PackageInfo archive = activity.getPackageManager().getPackageArchiveInfo(path, flags);
        PackageInfo current = activity.getPackageManager().getPackageInfo(activity.getPackageName(), flags);
        if (archive == null || archive.packageName == null ||
                !archive.packageName.equals(activity.getPackageName()))
            throw new SecurityException("不是洗頭機的安裝檔");
        long archiveCode = Build.VERSION.SDK_INT >= 28 ? archive.getLongVersionCode() : archive.versionCode;
        long currentCode = Build.VERSION.SDK_INT >= 28 ? current.getLongVersionCode() : current.versionCode;
        if (archiveCode <= currentCode) throw new SecurityException("下載版本沒有比目前版本新");
        Signature[] archiveSigners = signatures(archive);
        Signature[] currentSigners = signatures(current);
        if (archiveSigners.length == 0 || currentSigners.length == 0 ||
                !archiveSigners[0].toCharsString().equals(currentSigners[0].toCharsString()))
            throw new SecurityException("更新檔簽章不一致，已停止安裝");
    }

    private static Signature[] signatures(PackageInfo info) {
        if (Build.VERSION.SDK_INT >= 28 && info.signingInfo != null)
            return info.signingInfo.getApkContentsSigners();
        return info.signatures == null ? new Signature[0] : info.signatures;
    }

    private static String currentVersion(Context context) throws Exception {
        String value = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        return value == null ? "0" : value;
    }

    static int compareVersions(String left, String right) {
        ArrayList<String> a = versionParts(left);
        ArrayList<String> b = versionParts(right);
        int count = Math.max(a.size(), b.size());
        for (int i = 0; i < count; i++) {
            if (i >= a.size()) return -1;
            if (i >= b.size()) return 1;
            String av = a.get(i), bv = b.get(i);
            boolean an = av.matches("\\d+"), bn = bv.matches("\\d+");
            int compared;
            if (an && bn) compared = Long.compare(Long.parseLong(av), Long.parseLong(bv));
            else compared = av.compareToIgnoreCase(bv);
            if (compared != 0) return compared;
        }
        return 0;
    }

    private static ArrayList<String> versionParts(String value) {
        String cleaned = value == null ? "0" : value.trim().replaceFirst("^[vV]", "")
                .replaceAll("[^A-Za-z0-9]+", ".")
                .replaceAll("(?<=\\d)(?=[A-Za-z])|(?<=[A-Za-z])(?=\\d)", ".");
        ArrayList<String> result = new ArrayList<>();
        for (String part : cleaned.split("\\.")) if (!part.isEmpty()) result.add(part);
        return result;
    }

    private static String trimNotes(String notes) {
        String plain = notes.replaceAll("[#*_`>]", "").trim();
        return plain.length() <= 500 ? plain : plain.substring(0, 500) + "…";
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null || error.getMessage().trim().isEmpty() ?
                "請稍後再試" : error.getMessage();
    }
}
