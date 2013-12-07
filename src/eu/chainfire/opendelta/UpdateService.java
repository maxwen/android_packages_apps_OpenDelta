/* 
 * Copyright (C) 2013 Jorrit "Chainfire" Jongma
 * Copyright (C) 2013 The OmniROM Project
 */
/* 
 * This file is part of OpenDelta.
 * 
 * OpenDelta is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * OpenDelta is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with OpenDelta. If not, see <http://www.gnu.org/licenses/>.
 */

package eu.chainfire.opendelta;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import eu.chainfire.opendelta.BatteryState.OnBatteryStateListener;
import eu.chainfire.opendelta.NetworkState.OnNetworkStateListener;
import eu.chainfire.opendelta.Scheduler.OnWantUpdateCheckListener;
import eu.chainfire.opendelta.ScreenState.OnScreenStateListener;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.Process;
import android.os.StatFs;
import android.os.SystemClock;
import android.preference.PreferenceManager;

public class 
	UpdateService 
extends 
	Service
implements
	OnNetworkStateListener,
	OnBatteryStateListener,
	OnScreenStateListener,
	OnWantUpdateCheckListener,
	OnSharedPreferenceChangeListener
{
	public static void start(Context context) {
		start(context, null);
	}
	
	public static void startCheck(Context context) {
		start(context, ACTION_CHECK);
	}
	
	public static void startFlash(Context context) {
		start(context, ACTION_FLASH);		
	}
	
	private static void start(Context context, String action) {
		Intent i = new Intent(context, UpdateService.class);
		i.setAction(action);
		context.startService(i);
	}
	
	public static PendingIntent alarmPending(Context context, int id) {
		Intent intent = new Intent(context, UpdateService.class);
		intent.setAction(ACTION_ALARM);
		intent.putExtra(EXTRA_ALARM_ID, id);
		return PendingIntent.getService(context, id, intent, 0);
	}
	
	public static final String ACTION_SYSTEM_UPDATE_SETTINGS = "android.settings.SYSTEM_UPDATE_SETTINGS";
	public static final String PERMISSION_ACCESS_CACHE_FILESYSTEM = "android.permission.ACCESS_CACHE_FILESYSTEM";
	public static final String PERMISSION_REBOOT = "android.permission.REBOOT";
	
	public static final String BROADCAST_INTENT = "eu.chainfire.opendelta.intent.BROADCAST_STATE";
	public static final String EXTRA_STATE = "eu.chainfire.opendelta.extra.ACTION_STATE";
	public static final String EXTRA_LAST_CHECK = "eu.chainfire.opendelta.extra.LAST_CHECK";
	public static final String EXTRA_PROGRESS = "eu.chainfire.opendelta.extra.PROGRESS";
	public static final String EXTRA_CURRENT = "eu.chainfire.opendelta.extra.CURRENT";
	public static final String EXTRA_TOTAL = "eu.chainfire.opendelta.extra.TOTAL";
	public static final String EXTRA_FILENAME = "eu.chainfire.opendelta.extra.FILENAME";
	public static final String EXTRA_MS = "eu.chainfire.opendelta.extra.MS";

	public static final String STATE_ACTION_NONE = "action_none";
	public static final String STATE_ACTION_CHECKING = "action_checking";
	public static final String STATE_ACTION_CHECKING_MD5 = "action_checking_md5";
	public static final String STATE_ACTION_SEARCHING = "action_searching";
	public static final String STATE_ACTION_SEARCHING_MD5 = "action_searching_md5";
	public static final String STATE_ACTION_DOWNLOADING = "action_downloading";
	public static final String STATE_ACTION_APPLYING = "action_applying";
	public static final String STATE_ACTION_APPLYING_PATCH = "action_applying_patch";
	public static final String STATE_ACTION_APPLYING_MD5 = "action_applying_md5";
	public static final String STATE_ACTION_READY = "action_ready";	
	public static final String STATE_ERROR_DISK_SPACE = "error_disk_space";
	public static final String STATE_ERROR_UNKNOWN = "error_unknown";
	
	private static final String ACTION_CHECK = "eu.chainfire.opendelta.action.CHECK";
	private static final String ACTION_FLASH = "eu.chainfire.opendelta.action.FLASH";
	private static final String ACTION_ALARM = "eu.chainfire.opendelta.action.ALARM";
	private static final String EXTRA_ALARM_ID = "eu.chainfire.opendelta.extra.ALARM_ID"; 

	private static final int NOTIFICATION_BUSY = 1;
	private static final int NOTIFICATION_UPDATE = 2;
	
	private static final String PREF_READY_FILENAME_NAME = "ready_filename";
	private static final String PREF_READY_FILENAME_DEFAULT = null;
	
	private static final String PREF_LAST_CHECK_TIME_NAME = "last_check_time";
	private static final long PREF_LAST_CHECK_TIME_DEFAULT = 0L;
	
	public static final String PREF_AUTO_UPDATE_NETWORKS_NAME = "auto_update_networks";
	public static final int PREF_AUTO_UPDATE_NETWORKS_DEFAULT = NetworkState.ALLOW_WIFI | NetworkState.ALLOW_ETHERNET;	
	
	public static boolean isStateBusy(String state) {
		return !(
				state.equals(STATE_ACTION_NONE) ||
				state.equals(STATE_ACTION_READY) ||
				state.equals(STATE_ERROR_UNKNOWN) ||
				state.equals(STATE_ERROR_DISK_SPACE)
		);		
	}
	
	private String property_version;
	private String property_device;
	private String filename_base;
	private String path_base;
	private String path_flash_after_update;
	private String url_base_delta;
	private String url_base_update;
	private String url_base_full;
	private boolean apply_signature;
	
	private HandlerThread handlerThread;
	private Handler handler;
	
	private String state = STATE_ACTION_NONE;
	
	private NetworkState networkState = null;
	private BatteryState batteryState = null;
	private ScreenState screenState = null;
	
	private Scheduler scheduler = null;
	
	private PowerManager.WakeLock wakeLock = null;
	private WifiManager.WifiLock wifiLock = null;
	
	private NotificationManager notificationManager = null;
	private SharedPreferences prefs = null;
	
	/* Using reflection voodoo instead calling the hidden class directly, to dev/test outside of AOSP tree */
	private String getProperty(String key, String defValue) {
		try {
			Class<?> SystemProperties = getClassLoader().loadClass("android.os.SystemProperties");		
			Method get = SystemProperties.getMethod("get", new Class[] { String.class, String.class });
			return (String)get.invoke(null, new Object[] { key, defValue });
		} catch (Exception e) {
			Logger.ex(e);
		}
		return null;
	}
	
	/* Using reflection voodoo instead calling the hidden class directly, to dev/test outside of AOSP tree */
	private boolean setPermissions(String path, int mode, int uid, int gid) {
		try {
			Class<?> FileUtils = getClassLoader().loadClass("android.os.FileUtils");
			Method setPermissions = FileUtils.getDeclaredMethod("setPermissions", new Class[] { String.class, int.class, int.class, int.class });
			return ((Integer)setPermissions.invoke(null, new Object[] { path, Integer.valueOf(mode), Integer.valueOf(uid), Integer.valueOf(gid) }) == 0);			
		} catch (Exception e) {
			Logger.ex(e);
		}
		return false;
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		
		Logger.setDebugLogging(getResources().getBoolean(R.bool.debug_output));

		property_version = getProperty(getString(R.string.property_version), "");
		property_device = getProperty(getString(R.string.property_device), "");
		filename_base = String.format(Locale.ENGLISH, getString(R.string.filename_base), property_version);
		path_base = String.format(Locale.ENGLISH, "%s%s%s%s", Environment.getExternalStorageDirectory().getAbsolutePath(), File.separator, getString(R.string.path_base), File.separator);
		path_flash_after_update = String.format(Locale.ENGLISH, "%s%s%s", path_base, "FlashAfterUpdate", File.separator);
		url_base_delta = String.format(Locale.ENGLISH, getString(R.string.url_base_delta), property_device);
		url_base_update = String.format(Locale.ENGLISH, getString(R.string.url_base_update), property_device);
		url_base_full = String.format(Locale.ENGLISH, getString(R.string.url_base_full), property_device);
		apply_signature = getResources().getBoolean(R.bool.apply_signature);

		Logger.d("property_version: %s", property_version);
		Logger.d("property_device: %s", property_device);
		Logger.d("filename_base: %s", filename_base);		
		Logger.d("path_base: %s", path_base);
		Logger.d("path_flash_after_update: %s", path_flash_after_update);
		Logger.d("url_base_delta: %s", url_base_delta);
		Logger.d("url_base_update: %s", url_base_update);
		Logger.d("url_base_full: %s", url_base_full);
		
		wakeLock = ((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OpenDelta WakeLock");
		wifiLock = ((WifiManager)getSystemService(WIFI_SERVICE)).createWifiLock(WifiManager.WIFI_MODE_FULL, "OpenDelta WifiLock");
		
		handlerThread = new HandlerThread("OpenDelta Service Thread");
		handlerThread.start();
		handler = new Handler(handlerThread.getLooper());
		
		notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		scheduler = new Scheduler(this, this);

		networkState = new NetworkState();
		networkState.start(this, this, prefs.getInt(PREF_AUTO_UPDATE_NETWORKS_NAME, PREF_AUTO_UPDATE_NETWORKS_DEFAULT));
		
		batteryState = new BatteryState();
		batteryState.start(this, this, 50, true);
		
		screenState = new ScreenState();
		screenState.start(this, this);
		
		prefs.registerOnSharedPreferenceChangeListener(this);
				
		autoState();
	}

	@Override
	public void onDestroy() {
		prefs.unregisterOnSharedPreferenceChangeListener(this);
		networkState.stop();
		batteryState.stop();
		screenState.stop();
		handlerThread.quitSafely();
		
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			if (ACTION_CHECK.equals(intent.getAction())) {
				checkForUpdates(true);
			} else if (ACTION_FLASH.equals(intent.getAction())) {
				flashUpdate();
			} else if (ACTION_ALARM.equals(intent.getAction())) {
				scheduler.alarm(intent.getIntExtra(EXTRA_ALARM_ID, -1));
			}
		}
		
		return START_STICKY;
	}
	
	private synchronized void updateState(String state, Float progress, Long current, Long total, String filename, Long ms) {
		this.state = state;
		
		Intent i = new Intent(BROADCAST_INTENT);
		i.putExtra(EXTRA_STATE, state);
		if (progress != null) i.putExtra(EXTRA_PROGRESS, progress);
		if (current != null) i.putExtra(EXTRA_CURRENT, current);
		if (total != null) i.putExtra(EXTRA_TOTAL, total);
		if (filename != null) i.putExtra(EXTRA_FILENAME, filename);
		if (ms != null) i.putExtra(EXTRA_MS, ms);
		sendStickyBroadcast(i);
	}
	
	@Override
	public void onNetworkState(boolean state) {
		Logger.d("network state --> %d", state ? 1 : 0);
	}

	@Override
	public void onBatteryState(boolean state) {
		Logger.d("battery state --> %d", state ? 1 : 0);
	}
	
	@Override
	public void onScreenState(boolean state) {
		Logger.d("screen state --> %d", state ? 1 : 0);
		scheduler.onScreenState(state);
	}
			
	@Override
	public boolean onWantUpdateCheck() {
		Logger.i("Scheduler requests check for updates");
		return checkForUpdates(false);
	}

	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (PREF_AUTO_UPDATE_NETWORKS_NAME.equals(key)) {
			networkState.updateFlags(sharedPreferences.getInt(PREF_AUTO_UPDATE_NETWORKS_NAME, PREF_AUTO_UPDATE_NETWORKS_DEFAULT));
		}
	}

	private void autoState() {
		String filename = prefs.getString(PREF_READY_FILENAME_NAME, PREF_READY_FILENAME_DEFAULT);
		
		if (filename != null) {
			if (!(new File(filename)).exists()) {
				filename = null;
				prefs.edit().putString(PREF_READY_FILENAME_NAME, PREF_READY_FILENAME_DEFAULT).commit();
			}
		}
		
		if (filename == null) {
			Logger.i("System up to date");
			stopNotification();
			updateState(STATE_ACTION_NONE, null, null, null, null, prefs.getLong(PREF_LAST_CHECK_TIME_NAME, PREF_LAST_CHECK_TIME_DEFAULT));
		} else {
			Logger.i("Update found: %s", filename);
			startNotification();
			updateState(STATE_ACTION_READY, null, null, null, (new File(filename)).getName(), prefs.getLong(PREF_LAST_CHECK_TIME_NAME, PREF_LAST_CHECK_TIME_DEFAULT));
		}		
	}
	
	private PendingIntent getNotificationIntent() {
		Intent notificationIntent = new Intent(this, MainActivity.class);
		notificationIntent.setAction(ACTION_SYSTEM_UPDATE_SETTINGS);
		return PendingIntent.getActivity(this, 0, notificationIntent, 0);
	}
	
	private void startNotification() {
		notificationManager.
			notify(
				NOTIFICATION_UPDATE,
				(new Notification.Builder(this)).
					setSmallIcon(R.drawable.stat_notify_update).
					setContentTitle(getString(R.string.title)).
					setContentText(getString(R.string.notify_update_available)).
					setTicker(getString(R.string.notify_update_available)).
					setShowWhen(false).
					setContentIntent(getNotificationIntent()).
					build()
			); 
	}
	
	private void stopNotification() {
		notificationManager.cancel(NOTIFICATION_UPDATE);
	}	
		
	private byte[] downloadUrlMemory(String url) {
		Logger.d("download: %s", url);
		try {
			HttpParams params = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(params, 10000);
			HttpConnectionParams.setSoTimeout(params, 10000);
			HttpClient client = new DefaultHttpClient(params);
			HttpGet request = new HttpGet(url);
			HttpResponse response = client.execute(request);
			int len = (int)response.getEntity().getContentLength();
			if ((len >= 0) && (len < 1024 * 1024)) {
				byte[] ret = new byte[len];
				InputStream in = response.getEntity().getContent();
				int pos = 0;
				while (pos < len) {
					int r = in.read(ret, pos, len - pos);
					pos += r;
					if (r <= 0) return null;
				}
				return ret;
			}
			return null;
		} catch (Exception e) {
			Logger.ex(e);
			return null;
		}
	}
		
	private boolean downloadUrlFile(String url, File f, String matchMD5, DeltaInfo.ProgressListener progressListener) {
		Logger.d("download: %s", url);

		MessageDigest digest = null;
		if (matchMD5 != null) try { digest = MessageDigest.getInstance("MD5"); } catch (Exception e) { }

		if (f.exists()) f.delete();
		try {
			HttpParams params = new BasicHttpParams();
			HttpConnectionParams.setConnectionTimeout(params, 10000);
			HttpConnectionParams.setSoTimeout(params, 10000);
			HttpClient client = new DefaultHttpClient(params);
			HttpGet request = new HttpGet(url);
			HttpResponse response = client.execute(request);
			long len = (int)response.getEntity().getContentLength();
			long recv = 0;
			if ((len > 0) && (len < 4L * 1024L * 1024L * 1024L)) {
				byte[] buffer = new byte[262144];
				
				InputStream is = response.getEntity().getContent();
				FileOutputStream os = new FileOutputStream(f, false);
				try {
					int r;				
					while ((r = is.read(buffer)) > 0) {
						os.write(buffer, 0, r);
						if (digest != null) digest.update(buffer, 0, r);

						recv += (long)r;
						if (progressListener != null) progressListener.onProgress(((float)recv / (float)len) * 100f, recv, len);
					}					
				} finally {
					os.close();
				}
				
				if (digest != null) {
					String MD5 = new BigInteger(1, digest.digest()).toString(16).toLowerCase(Locale.ENGLISH);
					while (MD5.length() < 32) MD5 = "0" + MD5;
					return MD5.equals(matchMD5);
				}				
				return true;
			}
			return false;
		} catch (Exception e) {
			Logger.ex(e);
			return false;
		}
	}
	
	private DeltaInfo.ProgressListener getMD5Progress(String state, String filename) {
		final long[] last = new long[] { 0, SystemClock.elapsedRealtime() };
		final String _state = state;
		final String _filename = filename;
		
		return new DeltaInfo.ProgressListener() {						
			@Override
			public void onProgress(float progress, long current, long total) {
				long now = SystemClock.elapsedRealtime();
				if (now >= last[0] + 16L) {
					updateState(_state, progress, current, total, _filename, SystemClock.elapsedRealtime() - last[1]);
					last[0] = now;
				}
			}
		};
	}
	
	private String findZIPOnSD(DeltaInfo.FileFull zip, File base) {
		if (base == null) base = Environment.getExternalStorageDirectory();		
		
		Logger.d("scanning: %s", base.getAbsolutePath());
		File[] list = base.listFiles();
		if (list != null) {
			for (File f : list) {
				if (!f.isDirectory() && f.getName().endsWith(".zip")) {
					Logger.d("checking: %s", f.getAbsolutePath());
					
					boolean ok = (zip.match(f, true, getMD5Progress(STATE_ACTION_SEARCHING_MD5, f.getName())) != null);
					updateState(STATE_ACTION_SEARCHING, null, null, null, null, null);
					
					if (ok) return f.getAbsolutePath();
				}
			}						
			
			for (File f : list) {
				if (f.isDirectory()) {
					String ret = findZIPOnSD(zip, f);
					if (ret != null) return ret;
				}
			}
		}
		
		return null;
	}
	
	private long sizeOnDisk(long size) {
		// Assuming 256k block size here, should be future proof for a little bit
		long blocks = (size + 262143L) / 262144L;
		return blocks * 262144L;
	}
	
	private boolean downloadDeltaFile(String url_base, DeltaInfo.FileBase fileBase, DeltaInfo.FileSizeMD5 match, DeltaInfo.ProgressListener progressListener, boolean force) {
		if (fileBase.getTag() == null) {
			if (force || networkState.getState()) {
				String url = url_base + fileBase.getName();
				String fn = path_base + fileBase.getName();
				File f = new File(fn);
				Logger.d("download: %s --> %s", url, fn);
				
				if (downloadUrlFile(url, f, match.getMD5(), progressListener)) {
					fileBase.setTag(fn);
					Logger.d("success");
					return true;
				} else {
					f.delete();
					Logger.d("download error");
					return false;
				}
			} else {
				Logger.d("aborting download due to network state");
				return false;								
			}
		} else {
			Logger.d("have %s already", fileBase.getName());
			return true;
		}
	}
	
	private Thread getThreadedProgress(String filename, String display, long start, long currentOut, long totalOut) {
		final File _file = new File(filename);
		final String _display = display;
		final long _currentOut = currentOut;
		final long _totalOut = totalOut;
		final long _start = start;
		
		return new Thread(new Runnable() {			
			@Override
			public void run() {		
				while (true) {
					try {
						long current = _currentOut + _file.length();						
						updateState(STATE_ACTION_APPLYING_PATCH, ((float)current / (float)_totalOut) * 100f, current, _totalOut, _display, SystemClock.elapsedRealtime() - _start);
					
						Thread.sleep(16);
					} catch (InterruptedException e) {
						break;
					}
				}
			}
		});		
	}
	
	private boolean zipadjust(String filenameIn, String filenameOut, long start, long currentOut, long totalOut) {
		Logger.d("zipadjust [%s] --> [%s]", filenameIn, filenameOut);

		// checking filesizes in the background as progress, because these
		// native functions don't have callbacks (yet) to do this
		
		(new File(filenameOut)).delete();
		
		Thread progress = getThreadedProgress(filenameOut, (new File(filenameIn)).getName(), start, currentOut, totalOut);
		progress.start();
		
		int ok = Native.zipadjust(filenameIn, filenameOut, 1);		

		progress.interrupt();
		try { progress.join(); } catch (Exception e) { }
		
		Logger.d("zipadjust --> %d", ok);
		
		return (ok == 1);
	}
	
	private boolean dedelta(String filenameSource, String filenameDelta, String filenameOut, long start, long currentOut, long totalOut) {
		Logger.d("dedelta [%s] --> [%s] --> [%s]", filenameSource, filenameDelta, filenameOut);

		// checking filesizes in the background as progress, because these
		// native functions don't have callbacks (yet) to do this
		
		(new File(filenameOut)).delete();

		Thread progress = getThreadedProgress(filenameOut, (new File(filenameDelta)).getName(), start, currentOut, totalOut);
		progress.start();
		
		int ok = Native.dedelta(filenameSource, filenameDelta, filenameOut);		

		progress.interrupt();
		try { progress.join(); } catch (Exception e) { }
		
		Logger.d("dedelta --> %d", ok);
		
		return (ok == 1);
	}

	private boolean checkForUpdates(boolean userInitiated) {
		/* Unless the user is specifically asking to check
		 * for updates, we only check for them if we have
		 * a connection matching the user's set preferences, 
		 * we're charging and/or have juice aplenty, 
		 * and the screen is off  
		 */
		
		if (
				(networkState == null) ||
				(batteryState == null) ||
				(screenState == null)
		) return false;

		if (
			!isStateBusy(state) && 
				(userInitiated || (
					networkState.getState() && 
					batteryState.getState() &&
					!screenState.getState()
				)
			)
		) {
			Logger.i("Starting check for updates");
			checkForUpdatesAsync(userInitiated);
			return true;
		} else {
			Logger.i("Ignoring request to check for updates");
		}
		return false;
	}
	
	private long getDeltaDownloadSize(List<DeltaInfo> deltas) {
		updateState(STATE_ACTION_CHECKING, null, null, null, null, null);		

		long deltaDownloadSize = 0L;					
		for (DeltaInfo di : deltas) {
			String fn = path_base + di.getUpdate().getName();
			if (di.getUpdate().match(new File(fn), true, getMD5Progress(STATE_ACTION_CHECKING_MD5, di.getUpdate().getName())) == di.getUpdate().getUpdate()) {
				di.getUpdate().setTag(fn);
			} else {
				deltaDownloadSize += di.getUpdate().getUpdate().getSize();							
			}
		}
		
		DeltaInfo lastDelta = deltas.get(deltas.size() - 1);						
		{
			if (apply_signature) {
				String fn = path_base + lastDelta.getSignature().getName();
				if (lastDelta.getSignature().match(new File(fn), true, getMD5Progress(STATE_ACTION_CHECKING_MD5, lastDelta.getSignature().getName())) == lastDelta.getSignature().getUpdate()) {
					lastDelta.getSignature().setTag(fn);
				} else {
					deltaDownloadSize += lastDelta.getSignature().getUpdate().getSize();							
				}
			}
		}
		
		updateState(STATE_ACTION_CHECKING, null, null, null, null, null);
		
		return deltaDownloadSize;
	}
	
	private long getFullDownloadSize(List<DeltaInfo> deltas) {
		DeltaInfo lastDelta = deltas.get(deltas.size() - 1);						

		long fullDownloadSize = 0;
		{
			String fn = path_base + lastDelta.getOut().getName();
			if (lastDelta.getOut().match(new File(fn), true, getMD5Progress(STATE_ACTION_CHECKING_MD5, lastDelta.getOut().getName())) == lastDelta.getOut().getOfficial()) {
				lastDelta.getOut().setTag(fn);
			} else {
				fullDownloadSize += lastDelta.getOut().getOfficial().getSize();
			}
			updateState(STATE_ACTION_CHECKING, null, null, null, null, null);
		}					
		return fullDownloadSize;
	}
	
	private long getRequiredSpace(List<DeltaInfo> deltas, boolean getFull) {
		DeltaInfo lastDelta = deltas.get(deltas.size() - 1);						

		long requiredSpace = 0;
		if (getFull) {
			requiredSpace += sizeOnDisk(lastDelta.getOut().getTag() != null ? 0 : lastDelta.getOut().getOfficial().getSize());
		} else {
			// The resulting number will be a tad more than worst case what we actually need, but not dramatically so
			
			for (DeltaInfo di : deltas) {
				if (di.getUpdate().getTag() == null)
					requiredSpace += sizeOnDisk(di.getUpdate().getUpdate().getSize());
			}
			if (apply_signature) {
				requiredSpace += sizeOnDisk(lastDelta.getSignature().getUpdate().getSize());
			}
			
			long biggest = 0;
			for (DeltaInfo di : deltas) biggest = Math.max(biggest, sizeOnDisk(di.getUpdate().getApplied().getSize()));
			
			requiredSpace += 2 * sizeOnDisk(biggest);
		}
		
		return requiredSpace;
	}
	
	private String findInitialFile(List<DeltaInfo> deltas, String possibleMatch, boolean[] needsProcessing) {
		// Find the currently flashed ZIP, or a newer one
		
		DeltaInfo firstDelta = deltas.get(0);
		
		updateState(STATE_ACTION_SEARCHING, null, null, null, null, null);

		String initialFile = null;

		// Check if an original flashable ZIP is in our preferred location
		String expectedLocation = path_base + firstDelta.getIn().getName();
		DeltaInfo.FileSizeMD5 match = null;
		if (expectedLocation.equals(possibleMatch)) {								
			match = firstDelta.getIn().match(new File(expectedLocation), false, null);
			if (match != null) {
				initialFile = possibleMatch;
			}
		}
				
		if (match == null) {
			match = firstDelta.getIn().match(new File(expectedLocation), true, getMD5Progress(STATE_ACTION_SEARCHING_MD5, firstDelta.getIn().getName()));
			if (match != null) {
				initialFile = expectedLocation;
			}
		}
		updateState(STATE_ACTION_SEARCHING, null, null, null, null, null);
			
		// If the user flashed manually, the file is probably not in our preferred location
		// (assuming it wasn't sideloaded), so search the (internal) storage for it.
		// Should at some point be extended to search (true) external storage as well.
		if (initialFile == null) {
			initialFile = findZIPOnSD(firstDelta.getIn(), null);
			if (initialFile != null) {
				match = firstDelta.getIn().match(new File(initialFile), false, null);
			}
		}
			
		if ((needsProcessing != null) && (needsProcessing.length > 0)) {
			needsProcessing[0] = (initialFile != null) && (match != firstDelta.getIn().getStore());
		}
		
		return initialFile;
	}
	
	private boolean downloadFiles(List<DeltaInfo> deltas, boolean getFull, long totalDownloadSize, boolean force) {
		// Download all the files we do not have yet

		DeltaInfo lastDelta = deltas.get(deltas.size() - 1);

		final String[] filename = new String[] { null };
		updateState(STATE_ACTION_DOWNLOADING, 0f, 0L, totalDownloadSize, null, null);
			
		final long[] last = new long[] { 0, totalDownloadSize, 0, SystemClock.elapsedRealtime() };
		DeltaInfo.ProgressListener progressListener = new DeltaInfo.ProgressListener() {							
			@Override
			public void onProgress(float progress, long current, long total) {
				current += last[0];
				total = last[1];
				progress = ((float)current / (float)total) * 100f;
				long now = SystemClock.elapsedRealtime();
				if (now >= last[2] + 16L) {
					updateState(STATE_ACTION_DOWNLOADING, progress, current, total, filename[0], SystemClock.elapsedRealtime() - last[3]);
					last[2] = now;
				}
			}
		};
			
		if (getFull) {
			filename[0] = lastDelta.getOut().getName();
			if (!downloadDeltaFile(url_base_full, lastDelta.getOut(), lastDelta.getOut().getOfficial(), progressListener, force)) {
				updateState(STATE_ERROR_UNKNOWN, null, null, null, null, null);
				Logger.d("download error");
				return false;
			}
		} else {
			for (DeltaInfo di : deltas) {
				filename[0] = di.getUpdate().getName();
				if (!downloadDeltaFile(url_base_update, di.getUpdate(), di.getUpdate().getUpdate(), progressListener, force)) {
					updateState(STATE_ERROR_UNKNOWN, null, null, null, null, null);
					Logger.d("download error");
					return false;
				}
				last[0] += di.getUpdate().getUpdate().getSize();
			}
										
			if (apply_signature) {
				filename[0] = lastDelta.getSignature().getName();
				if (!downloadDeltaFile(url_base_update, lastDelta.getSignature(), lastDelta.getSignature().getUpdate(), progressListener, force)) {
					updateState(STATE_ERROR_UNKNOWN, null, null, null, null, null);
					Logger.d("download error");
					return false;
				}
			}
		}
		updateState(STATE_ACTION_DOWNLOADING, 100f, totalDownloadSize, totalDownloadSize, null, null);
		
		return true;
	}
	
	private boolean applyPatches(List<DeltaInfo> deltas, String initialFile, boolean initialFileNeedsProcessing) {
		// Create storeSigned outfile from infile + deltas
		
		DeltaInfo firstDelta = deltas.get(0);
		DeltaInfo lastDelta = deltas.get(deltas.size() - 1);
		
		int tempFile = 0;
		String[] tempFiles = new String[] { path_base + "temp1", path_base + "temp2" };
		try {								
			long start = SystemClock.elapsedRealtime();
			long current = 0L;
			long total = 0L;
			
			if (initialFileNeedsProcessing) total += firstDelta.getIn().getStore().getSize();
			for (DeltaInfo di : deltas) total += di.getUpdate().getApplied().getSize();
			if (apply_signature) total += lastDelta.getSignature().getApplied().getSize();
			
			if (initialFileNeedsProcessing) {
				if (!zipadjust(initialFile, tempFiles[tempFile], start, current, total)) {
					updateState(STATE_ERROR_UNKNOWN, null, null, null, null, null);
					Logger.d("zipadjust error");
					return false;
				}
				tempFile = (tempFile + 1) % 2;
				current += firstDelta.getIn().getStore().getSize();
			}
			
			for (DeltaInfo di : deltas) {
				String inFile = tempFiles[(tempFile + 1) % 2];
				if (!initialFileNeedsProcessing && (di == firstDelta)) inFile = initialFile;
				String outFile = tempFiles[tempFile];
				if (!apply_signature && (di == lastDelta)) outFile = path_base + lastDelta.getOut().getName();
					
				if (!dedelta(inFile, path_base + di.getUpdate().getName(), outFile, start, current, total)) {
					updateState(STATE_ERROR_UNKNOWN, null, null, null, null, null);
					Logger.d("dedelta error");
					return false;
				}
				tempFile = (tempFile + 1) % 2;
				current += di.getUpdate().getApplied().getSize();
			}

			if (apply_signature) {
				if (!dedelta(tempFiles[(tempFile + 1) % 2], path_base + lastDelta.getSignature().getName(), path_base + lastDelta.getOut().getName(), start, current, total)) {
					updateState(STATE_ERROR_UNKNOWN, null, null, null, null, null);
					Logger.d("dedelta error");
					return false;
				}
				tempFile = (tempFile + 1) % 2;
				current += lastDelta.getSignature().getApplied().getSize();
			}
		} finally {
			(new File(tempFiles[0])).delete();
			(new File(tempFiles[1])).delete();
		}
		
		return true;
	}
	
	@SuppressLint("SdCardPath")
	private void flashUpdate() {
		if (getPackageManager().checkPermission(PERMISSION_ACCESS_CACHE_FILESYSTEM, getPackageName()) != PackageManager.PERMISSION_GRANTED) {
			Logger.d("[%s] required beyond this point", PERMISSION_ACCESS_CACHE_FILESYSTEM);
			return;
		}

		if (getPackageManager().checkPermission(PERMISSION_REBOOT, getPackageName()) != PackageManager.PERMISSION_GRANTED) {
			Logger.d("[%s] required beyond this point", PERMISSION_REBOOT);
			return;
		}

		String flashFilename = prefs.getString(PREF_READY_FILENAME_NAME, PREF_READY_FILENAME_DEFAULT);
		if ((flashFilename == null) || !flashFilename.startsWith(path_base)) return;
					
		// Remove the path to the storage from the filename, so we get a path relative to the root of the storage
		String path_sd = Environment.getExternalStorageDirectory() + File.separator;
		flashFilename = flashFilename.substring(path_sd.length());

		// Find additional ZIPs to flash
		List<String> extras = new ArrayList<String>();
		{
			File[] files = (new File(path_flash_after_update)).listFiles();
			if (files != null) {
				for (File f : files) {
					if (f.getName().toLowerCase(Locale.ENGLISH).endsWith(".zip")) {
						String filename = f.getAbsolutePath();
						if (filename.startsWith(path_base)) {
							extras.add(filename.substring(path_sd.length()));										
						}
					}
				}
			}
			Collections.sort(extras);
		}					
					
		try {
			// We're using TWRP's openrecoveryscript as primary, and CWM's extendedcommand as fallback
			// Using AOSP's command would break older TWRPs. extendedcommand is broken on 'official' CWM
			// builds, though.
							
			// TWRP - OpenRecoveryScript - the recovery will find the correct storage root for the ZIPs,
			// life is nice and easy.
			if ((flashFilename != null) && (!flashFilename.equals(""))) {			
				FileOutputStream os = new FileOutputStream("/cache/recovery/openrecoveryscript", false);
				try {
					os.write(String.format("install %s\n", flashFilename).getBytes("UTF-8"));
					for (String file : extras) {
						os.write(String.format("install %s\n", file).getBytes("UTF-8"));
					}
					os.write(("wipe cache\n").getBytes("UTF-8"));
				} finally {
					os.close();
				}
			}
			setPermissions("/cache/recovery/openrecoveryscript", 0644, Process.myUid(), 2001 /*AID_CACHE*/);
						
			// CWM - ExtendedCommand - provide paths to both internal and external storage locations,
			// it's nigh impossible to know in practice which will be correct, not just because the internal
			// storage location varies based on the external storage being present, but also because it's not 
			// uncommon for community-built versions to have them reversed. It'll give some horrible looking
			// results, but it seems to continue installing even if one ZIP fails and produce the wanted 
			// result. Better than nothing ...
			if ((flashFilename != null) && (!flashFilename.equals(""))) {			
				FileOutputStream os = new FileOutputStream("/cache/recovery/extendedcommand", false);
				try {
					os.write(String.format("install_zip(\"%s%s\");\n", "/sdcard/", flashFilename).getBytes("UTF-8"));
					os.write(String.format("install_zip(\"%s%s\");\n", "/emmc/", flashFilename).getBytes("UTF-8"));
					for (String file : extras) {
						os.write(String.format("install_zip(\"%s%s\");\n", "/sdcard/", file).getBytes("UTF-8"));
						os.write(String.format("install_zip(\"%s%s\");\n", "/emmc/", file).getBytes("UTF-8"));
					}
					os.write(("run_program(\"/sbin/busybox\", \"rm\", \"-rf\", \"/cache/*\");\n").getBytes("UTF-8"));
				} finally {
					os.close();
				}
			}
			setPermissions("/cache/recovery/extendedcommand", 0644, Process.myUid(), 2001 /*AID_CACHE*/);
						
			((PowerManager)getSystemService(Context.POWER_SERVICE)).reboot("recovery");
		} catch (Exception e) {
			Logger.ex(e);
		}
	}

	private void checkForUpdatesAsync(boolean userInitiated) {
		updateState(STATE_ACTION_CHECKING, null, null, null, null, null);
		wakeLock.acquire();
		wifiLock.acquire();
		
		stopNotification();
		
		Notification notification = (new Notification.Builder(this)).
			setSmallIcon(R.drawable.stat_notify_update).
			setContentTitle(getString(R.string.title)).
			setContentText(getString(R.string.notify_checking)).
			setTicker(getString(R.string.notify_checking)).
			setShowWhen(false).
			setContentIntent(getNotificationIntent()).
			build();
		startForeground(NOTIFICATION_BUSY, notification);
		
		final boolean force = userInitiated;
		
		handler.post(new Runnable() {			
			@Override
			public void run() {
				try {
					List<DeltaInfo> deltas = new ArrayList<DeltaInfo>();
					
					String flashFilename = null;
					try { (new File(path_base)).mkdir(); } catch(Exception e) { }
					try { (new File(path_flash_after_update)).mkdir(); } catch(Exception e) { }
					
					// Create a list of deltas to apply to get from our current version to the latest
					String fetch = String.format(Locale.ENGLISH, "%s%s.delta", url_base_delta, filename_base);
					while (true) {
						DeltaInfo delta = null;

						try {
							delta = new DeltaInfo(downloadUrlMemory(fetch), false);
						} catch (Exception e) {
							Logger.ex(e);
						}

						if (delta == null) {
							// see if we have a revoked version instead, we still need it for chaining future deltas, but will not allow flashing this one
							try {
								delta = new DeltaInfo(downloadUrlMemory(fetch.replace(".delta", ".delta_revoked")), true);								
							} catch (Exception e) {
								// neither a delta nor a revoked delta was found
								break;
							}
						}
						
						Logger.d("delta --> [%s]", delta.getOut().getName());
						fetch = String.format(Locale.ENGLISH, "%s%s.delta", url_base_delta, delta.getOut().getName().replace(".zip", ""));
						deltas.add(delta);
					}
					
					if (deltas.size() > 0) {
						// See if we have done past work and have newer ZIPs than the original of what's currently flashed

						int last = -1;
						for (int i = deltas.size() - 1; i >= 0; i--) {
							DeltaInfo di = deltas.get(i);
							String fn = path_base + di.getOut().getName();
							if (di.getOut().match(new File(fn), true, getMD5Progress(STATE_ACTION_CHECKING_MD5, di.getOut().getName())) != null) {
								Logger.d("match found: %s", di.getOut().getName());
								flashFilename = fn;
								last = i;
								break;
							}
						}
						
						if (last > -1) {
							for (int i = 0; i <= last; i++) {
								deltas.remove(0);
							}
						}
					}
					
					while ((deltas.size() > 0) && (deltas.get(deltas.size() - 1).isRevoked())) {
						// Make sure the last delta is not revoked
						deltas.remove(deltas.size() - 1);
					}
					
					if (deltas.size() > 0) {
						DeltaInfo lastDelta = deltas.get(deltas.size() - 1);

						long deltaDownloadSize = getDeltaDownloadSize(deltas);
						long fullDownloadSize = getFullDownloadSize(deltas);						
						
						Logger.d("download size --> deltas[%d] vs full[%d]", deltaDownloadSize, fullDownloadSize);

						// Find the currently flashed ZIP, or a newer one
						String initialFile = null;
						boolean initialFileNeedsProcessing = false;
						{
							boolean[] needsProcessing = new boolean[] { false };
							initialFile = findInitialFile(deltas, flashFilename, needsProcessing);
							initialFileNeedsProcessing = needsProcessing[0];
						}
						flashFilename = null;
						
						Logger.d("initial: %s", initialFile != null ? initialFile : "not found");
						
						// If we don't have a file to start out with, or the combined deltas get big, just get the latest full ZIP
						boolean getFull = ((initialFile == null) || (deltaDownloadSize > fullDownloadSize));
												
						long requiredSpace = getRequiredSpace(deltas, getFull);
						long freeSpace = (new StatFs(path_base)).getAvailableBytes();						
						if (freeSpace < requiredSpace) {
							updateState(STATE_ERROR_DISK_SPACE, null, freeSpace, requiredSpace, null, null);
							Logger.d("not enough space!");
							return;
						}
						
						long downloadSize = getFull ? fullDownloadSize : deltaDownloadSize;
							
						// Download all the files we do not have yet
						if (!downloadFiles(deltas, getFull, downloadSize, force)) return;

						// Reconstruct flashable ZIP
						if (!getFull && !applyPatches(deltas, initialFile, initialFileNeedsProcessing)) return;
							
						// Verify using MD5
						if (lastDelta.getOut().match(new File(path_base + lastDelta.getOut().getName()), true, getMD5Progress(STATE_ACTION_APPLYING_MD5, lastDelta.getOut().getName())) == null) {
							updateState(STATE_ERROR_UNKNOWN, null, null, null, null, null);
							Logger.d("final verification error");
							return;									
						}
						Logger.d("final verification complete");
							
						// Cleanup
						for (DeltaInfo di : deltas) {
							(new File(path_base + di.getUpdate().getName())).delete();
							(new File(path_base + di.getSignature().getName())).delete();
							if (di != lastDelta) (new File(path_base + di.getOut().getName())).delete();
						}
						if (initialFile != null) {
							if (initialFile.startsWith(path_base)) (new File(initialFile)).delete();
						}
						
						flashFilename = path_base + lastDelta.getOut().getName();
					}
					
					if (flashFilename != null) {
						prefs.edit().putString(PREF_READY_FILENAME_NAME, flashFilename).commit();
					}	
					
					prefs.edit().putLong(PREF_LAST_CHECK_TIME_NAME, System.currentTimeMillis()).commit();
				} finally {
					stopForeground(true);
					wifiLock.release();
					wakeLock.release();
					autoState();
				}				
			}
		});
	}
}
