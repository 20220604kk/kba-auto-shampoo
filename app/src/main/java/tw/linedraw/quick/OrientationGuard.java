package tw.linedraw.quick;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

/** Temporarily locks the entire device upright while LINE is in the foreground. */
final class OrientationGuard {
    private static final String TAG = "LineDrawTest";
    private static final String PREFS = "linedraw-orientation-v1";
    private static final String ACTIVE = "active";
    private static final String AUTO = "auto";
    private static final String ROTATION = "rotation";

    private OrientationGuard() {}

    static boolean canLock(Context context) { return Settings.System.canWrite(context); }

    static synchronized boolean lock(Context context) {
        if (!canLock(context)) return false;
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(ACTIVE, false)) return true;
        int auto;
        int rotation;
        try {
            auto = Settings.System.getInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION);
            rotation = Settings.System.getInt(context.getContentResolver(), Settings.System.USER_ROTATION);
            // Save first, so a service restart can put the user's original choice back.
            if (!prefs.edit().putInt(AUTO, auto).putInt(ROTATION, rotation).putBoolean(ACTIVE, true).commit()) return false;
            boolean autoLocked = Settings.System.putInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION, 0);
            boolean upright = Settings.System.putInt(context.getContentResolver(), Settings.System.USER_ROTATION, 0);
            if (autoLocked && upright) return true;
        } catch (Exception error) { Log.w(TAG, "portrait lock unavailable", error); }
        restore(context);
        return false;
    }

    static synchronized void restore(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(ACTIVE, false) || !canLock(context)) return;
        try {
            // Restore the user's chosen angle before re-enabling auto-rotation.
            boolean rotation = Settings.System.putInt(context.getContentResolver(), Settings.System.USER_ROTATION,
                    prefs.getInt(ROTATION, 0));
            boolean auto = Settings.System.putInt(context.getContentResolver(), Settings.System.ACCELEROMETER_ROTATION,
                    prefs.getInt(AUTO, 1));
            if (rotation && auto) prefs.edit().clear().commit();
        } catch (Exception error) { Log.w(TAG, "portrait setting could not be restored", error); }
    }
}
