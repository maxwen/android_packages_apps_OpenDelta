package eu.chainfire.opendelta;

public class Logger {
	private static boolean log = false; 

	public static void setLogging(boolean enabled) {
		log = enabled;
	}
	
	public static void log(String message, Object... args) {
		if (log) android.util.Log.d("OpenDelta", String.format(message, args));
	}
}
