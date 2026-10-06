package tw.linedraw.quick;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.hardware.HardwareBuffer;
import android.view.Display;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

/** Reads only visible text; the caller decides whether any gesture is safe. */
final class VisualCouponReader {
    interface Callback { void onRead(Frame frame); void onError(String reason); }

    static final class Frame {
        final String text;
        final String bottomLabel;
        final Rect bottomBounds;
        final int width;
        final int height;

        Frame(String text, String bottomLabel, Rect bottomBounds, int width, int height) {
            this.text = text;
            this.bottomLabel = bottomLabel;
            this.bottomBounds = bottomBounds;
            this.width = width;
            this.height = height;
        }

        boolean couponPage() {
            return text.contains("官方帳號優惠券") || text.contains("官方帳號優惠卷") ||
                    text.contains("LINE Official Account Coupon");
        }
    }

    private final TextRecognizer recognizer = TextRecognition.getClient(
            new ChineseTextRecognizerOptions.Builder().build());
    private final int maxWidth;
    private final int maxHeight;

    VisualCouponReader(boolean lowMemory) {
        maxWidth = lowMemory ? 640 : 900;
        maxHeight = lowMemory ? 1280 : 1800;
    }

    void close() { recognizer.close(); }

    void read(AccessibilityService service, Callback callback) {
        try {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.getMainExecutor(),
                    new AccessibilityService.TakeScreenshotCallback() {
                @Override public void onSuccess(AccessibilityService.ScreenshotResult result) {
                    HardwareBuffer buffer = result.getHardwareBuffer();
                    if (buffer == null) { callback.onError("畫面截圖為空"); return; }
                    Bitmap hardware = null;
                    Bitmap bitmap = null;
                    int screenWidth = 0;
                    int screenHeight = 0;
                    try {
                        hardware = Bitmap.wrapHardwareBuffer(buffer, result.getColorSpace());
                        if (hardware == null) { callback.onError("畫面截圖無法讀取"); return; }
                        screenWidth = hardware.getWidth();
                        screenHeight = hardware.getHeight();
                        bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false);
                    } catch (Exception error) {
                        callback.onError("畫面截圖轉換失敗");
                        return;
                    } finally {
                        if (hardware != null) hardware.recycle();
                        buffer.close();
                    }
                    if (bitmap == null) { callback.onError("畫面截圖為空"); return; }
                    double scale = Math.min(1.0, Math.min((double) maxWidth / screenWidth,
                            (double) maxHeight / screenHeight));
                    int targetWidth = Math.max(1, (int) Math.round(screenWidth * scale));
                    int targetHeight = Math.max(1, (int) Math.round(screenHeight * scale));
                    final Bitmap image;
                    try {
                        image = scale < 1.0 ?
                                Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true) : bitmap;
                    } catch (RuntimeException error) {
                        bitmap.recycle();
                        callback.onError("畫面縮放失敗");
                        return;
                    }
                    if (image != bitmap) bitmap.recycle();
                    final int actualScreenWidth = screenWidth;
                    final int actualScreenHeight = screenHeight;
                    try {
                        recognizer.process(InputImage.fromBitmap(image, 0))
                                .addOnSuccessListener(text -> {
                                    try { callback.onRead(toFrame(text, image.getWidth(), image.getHeight(),
                                            actualScreenWidth, actualScreenHeight)); }
                                    catch (RuntimeException error) { callback.onError("畫面辨識資料無法處理"); }
                                    finally { image.recycle(); }
                                })
                                .addOnFailureListener(error -> {
                                    image.recycle();
                                    callback.onError("畫面文字辨識失敗");
                                });
                    } catch (RuntimeException error) {
                        image.recycle();
                        callback.onError("畫面辨識服務已停止");
                    }
                }

                @Override public void onFailure(int errorCode) {
                    callback.onError("系統無法擷取 LINE 畫面：" + errorCode);
                }
            });
        } catch (Exception error) { callback.onError("系統不允許讀取 LINE 畫面"); }
    }

    private Frame toFrame(Text recognized, int width, int height, int screenWidth, int screenHeight) {
        String full = CouponSafety.normalized(recognized.getText());
        String label = "";
        Rect bounds = new Rect();
        for (Text.TextBlock block : recognized.getTextBlocks()) {
            for (Text.Line line : block.getLines()) {
                Rect rect = line.getBoundingBox();
                if (rect == null || rect.centerY() < height * 0.78 ||
                        rect.centerX() < width * 0.15 || rect.centerX() > width * 0.85) continue;
                String candidate = CouponSafety.normalized(line.getText());
                if (rect.centerY() > bounds.centerY()) {
                    label = candidate;
                    bounds.set(rect);
                }
            }
        }
        if (!bounds.isEmpty()) bounds.set(
                Math.round(bounds.left * (float) screenWidth / width),
                Math.round(bounds.top * (float) screenHeight / height),
                Math.round(bounds.right * (float) screenWidth / width),
                Math.round(bounds.bottom * (float) screenHeight / height));
        return new Frame(full, label, bounds, screenWidth, screenHeight);
    }
}
