
package eu.chainfire.opendelta;

public class Logger {
    private final static String LOG_TAG = "OpenDelta";
    
    private static boolean log = false;

    public static void setDebugLogging(boolean enabled) {
        log = enabled;
    }

    public static void d(String message, Object... args) {
        if (log)
            android.util.Log.d(LOG_TAG, String.format(message, args));
    }

    public static void ex(Exception e) {
        if (log)
            e.printStackTrace();
    }

    public static void i(String message, Object... args) {
        android.util.Log.i(LOG_TAG, String.format(message, args));
    }
}
