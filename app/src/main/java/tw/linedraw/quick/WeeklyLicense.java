package tw.linedraw.quick;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Arrays;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

final class WeeklyLicense {
    private static final String PREFS = "weekly-license";
    private static final String DOCUMENT = "document";
    private static final int MAX_BYTES = 64 * 1024;
    private static final byte[] KBA2_MAGIC = new byte[]{'K','B','A','2'};
    private static final byte[] KBA2_CONTEXT = "tw.linedraw.quick:kba:v2\0".getBytes(StandardCharsets.UTF_8);

    static final class Status {
        final boolean valid;
        final String label;
        final String detail;
        final Instant expiresAt;
        final boolean permanent;

        Status(boolean valid, String label, String detail, Instant expiresAt, boolean permanent) {
            this.valid = valid;
            this.label = label;
            this.detail = detail;
            this.expiresAt = expiresAt;
            this.permanent = permanent;
        }
    }

    private WeeklyLicense() {}

    static String remainingLabel(Status status) {
        if (status == null || !status.valid) return status == null ? "尚未匯入" : status.label;
        if (status.permanent) return "永久有效";
        if (status.expiresAt == null) return status.label;
        long minutes = Math.max(0, Duration.between(Instant.now(), status.expiresAt).toMinutes());
        long days = minutes / (24 * 60);
        long hours = (minutes % (24 * 60) + 59) / 60;
        if (days > 0) return "剩 " + days + " 天 " + hours + " 小時";
        if (hours > 0) return "剩 " + hours + " 小時";
        return "即將到期";
    }

    static Status status(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DOCUMENT, "");
        if (raw.isEmpty()) return new Status(false, "尚未匯入", "請從 Discord 下載洗頭機 KBA 認證檔。", null, false);
        try { return validate(context, new JSONObject(raw)); }
        catch (Exception error) { return new Status(false, "認證無效", "檔案內容損壞或簽章無法驗證。", null, false); }
    }

    static Status importFrom(Context context, InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (output.size() + read > MAX_BYTES) throw new IllegalArgumentException("認證檔過大");
            output.write(buffer, 0, read);
        }
        JSONObject document = decodeDocument(context, output.toByteArray());
        Status status = validate(context, document);
        if (!status.valid) throw new IllegalArgumentException(status.detail);
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        prefs.edit().putString(DOCUMENT, document.toString()).apply();
        return status;
    }

    private static JSONObject decodeDocument(Context context, byte[] input) throws Exception {
        if (input.length >= 4 && Arrays.equals(Arrays.copyOfRange(input, 0, 4), KBA2_MAGIC)) {
            if (input.length < 32) throw new IllegalArgumentException("KBA2 認證檔損壞");
            byte[] iv = Arrays.copyOfRange(input, 4, 16);
            byte[] encrypted = Arrays.copyOfRange(input, 16, input.length);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(KBA2_CONTEXT);
            digest.update(publicKey(context).getEncoded());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(digest.digest(), "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(KBA2_MAGIC);
            return new JSONObject(new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8));
        }
        return new JSONObject(new String(input, StandardCharsets.UTF_8));
    }

    private static Status validate(Context context, JSONObject document) throws Exception {
        String signatureText = document.optString("signature", "");
        if (document.optInt("schema", 0) != 1 || signatureText.isEmpty())
            return new Status(false, "認證無效", "不是支援的洗頭機 KBA 格式。", null, false);
        Signature verifier = Signature.getInstance("SHA256withECDSA");
        verifier.initVerify(publicKey(context));
        verifier.update(canonical(document).getBytes(StandardCharsets.UTF_8));
        if (!verifier.verify(Base64.getDecoder().decode(signatureText)))
            return new Status(false, "認證無效", "簽章驗證失敗，請重新由 Discord 下載。", null, false);
        Instant now = Instant.now();
        Instant notBefore = Instant.parse(document.getString("notBefore"));
        Instant expiresAt = Instant.parse(document.getString("expiresAt"));
        boolean permanent = "PERMANENT".equals(document.optString("week"));
        if (now.isBefore(notBefore)) return new Status(false, "尚未生效", "手機時間不正確，或這份認證尚未生效。", expiresAt, permanent);
        if (!now.isBefore(expiresAt)) return new Status(false, "認證已過期", "請從 Discord 下載最新 KBA。", expiresAt, permanent);
        long versionCode = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).getLongVersionCode();
        if (versionCode < document.getLong("minVersionCode") || versionCode > document.getLong("maxVersionCode"))
            return new Status(false, "版本不相容", "請先更新 APK，再匯入 KBA。", expiresAt, permanent);
        if (permanent) return new Status(true, "永久認證", "永久 KBA 已生效，不必每週重新匯入。", expiresAt, true);
        String expiry = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm")
                .withZone(ZoneId.systemDefault()).format(expiresAt);
        return new Status(true, "限期認證", "限期 KBA 有效至 " + expiry, expiresAt, false);
    }

    private static String canonical(JSONObject document) throws Exception {
        return "{" +
                "\"schema\":" + document.getInt("schema") + "," +
                "\"licenseId\":" + JSONObject.quote(document.getString("licenseId")) + "," +
                "\"week\":" + JSONObject.quote(document.getString("week")) + "," +
                "\"issuedAt\":" + JSONObject.quote(document.getString("issuedAt")) + "," +
                "\"notBefore\":" + JSONObject.quote(document.getString("notBefore")) + "," +
                "\"expiresAt\":" + JSONObject.quote(document.getString("expiresAt")) + "," +
                "\"minVersionCode\":" + document.getInt("minVersionCode") + "," +
                "\"maxVersionCode\":" + document.getInt("maxVersionCode") + "}";
    }

    private static PublicKey publicKey(Context context) throws Exception {
        InputStream input = context.getResources().openRawResource(R.raw.license_public);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        String pem = output.toString(StandardCharsets.US_ASCII.name())
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(pem);
        return KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(encoded));
    }
}
