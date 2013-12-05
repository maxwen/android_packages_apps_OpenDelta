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

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import android.os.Bundle;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.text.Html;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class MainActivity extends Activity {
	private TextView title = null;
	private TextView sub = null;
	private ProgressBar progress = null;
	private Button checkNow = null;
	private Button flashNow = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		try {
			getActionBar().setIcon(getPackageManager().getApplicationIcon("com.android.settings"));
		} catch (Exception e) {
		}		
		
		getActionBar().setDisplayHomeAsUpEnabled(true);
				
		UpdateService.start(this);
						
		setContentView(R.layout.activity_main);
		
		title = (TextView)findViewById(R.id.text_title);
		sub = (TextView)findViewById(R.id.text_sub);
		progress = (ProgressBar)findViewById(R.id.progress);
		checkNow = (Button)findViewById(R.id.button_check_now);
		flashNow = (Button)findViewById(R.id.button_flash_now);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}
	
	private void showAbout() {
		int thisYear = Calendar.getInstance().get(Calendar.YEAR);
		String opendelta = (thisYear == 2013) ? "2013" :  "2013-" + String.valueOf(thisYear);
		String xdelta = (thisYear == 1997) ? "1997" :  "1997-" + String.valueOf(thisYear);
		
		AlertDialog dialog = (new AlertDialog.Builder(this)).
			setTitle(R.string.app_name).
			setMessage(Html.fromHtml(getString(R.string.about_content).replace("_COPYRIGHT_OPENDELTA_", opendelta).replace("_COPYRIGHT_XDELTA_", xdelta))).
			setNeutralButton(android.R.string.ok, null).
			setCancelable(true).
			show();
		TextView textView = (TextView)dialog.findViewById(android.R.id.message);
		if (textView != null) textView.setTypeface(title.getTypeface());
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case android.R.id.home:
			finish();
			return true;
		case R.id.action_about:
			showAbout();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}
	
	private IntentFilter updateFilter = new IntentFilter(UpdateService.BROADCAST_INTENT);
	private BroadcastReceiver updateReceiver = new BroadcastReceiver() {
		private String formatLastChecked(String filename, long ms) {
			Date date = new Date(ms);
			if (filename == null) {
				if (ms == 0) {
					return getString(R.string.last_checked_never);
				} else {
					return getString(R.string.last_checked,
						DateFormat.getDateFormat(MainActivity.this).format(date),
						DateFormat.getTimeFormat(MainActivity.this).format(date)
					);
				}
			} else {
				if (ms == 0) {
					return "";
				} else {
					return getString(R.string.last_checked_filename,
						filename,
						DateFormat.getDateFormat(MainActivity.this).format(date),
						DateFormat.getTimeFormat(MainActivity.this).format(date)
					);
				}				
			}
		}
		
		@Override
		public void onReceive(Context context, Intent intent) {
			String title = "";
			String sub = "";
			long current = 0L;
			long total = 1L;
			boolean enableCheck = false;
			boolean enableFlash = false;
			
			String state = intent.getStringExtra(UpdateService.EXTRA_STATE);
			// don't try this at home
			if (state != null) try { title = getString(getResources().getIdentifier("state_" + state, "string", getPackageName())); } catch (Exception e) { }
			
			if (UpdateService.STATE_ERROR_DISK_SPACE.equals(state)) {
				current = intent.getLongExtra(UpdateService.EXTRA_CURRENT, current);			
				total = intent.getLongExtra(UpdateService.EXTRA_TOTAL, total);
				
				current /= 1024L * 1024L; 
				total /= 1024L * 1024L;
				
				sub = getString(R.string.error_disk_space_sub, current, total);
			} else if (UpdateService.STATE_ERROR_UNKNOWN.equals(state)) {
				enableCheck = true;
			} else if (UpdateService.STATE_ACTION_NONE.equals(state)) {
				enableCheck = true;
				sub = formatLastChecked(null, intent.getLongExtra(UpdateService.EXTRA_MS, 0));
			} else if (UpdateService.STATE_ACTION_READY.equals(state)) {
				enableCheck = true;
				enableFlash = true;
				sub = formatLastChecked(intent.getStringExtra(UpdateService.EXTRA_FILENAME), intent.getLongExtra(UpdateService.EXTRA_MS, 0));
			} else {
				current = intent.getLongExtra(UpdateService.EXTRA_CURRENT, current);			
				total = intent.getLongExtra(UpdateService.EXTRA_TOTAL, total);

				// long --> int overflows FTL (progress.setXXX)
				boolean progressInK = false;
				if (total > 1024L * 1024L * 1024L) {
					progressInK = true;
					current /= 1024L;
					total /= 1024L;
				}
						
				String filename = intent.getStringExtra(UpdateService.EXTRA_FILENAME);
				if (filename != null) {
					long ms = intent.getLongExtra(UpdateService.EXTRA_MS, 0);
				
					if ((ms <= 500) || (current <= 0) || (total <= 0)) {
						sub = String.format(Locale.ENGLISH, "%s, %.0f %%", filename, intent.getFloatExtra(UpdateService.EXTRA_PROGRESS, 0));
					} else {
						float kibps = ((float)current / 1024f) / ((float)ms / 1000f);
						if (progressInK) kibps *= 1024f;
						int sec = (int)(((((float)total / (float)current) * (float)ms) - ms) / 1000f);
					
						if (kibps < 10000) {
							sub = String.format(Locale.ENGLISH, "%s, %.0f %%, %.0f KiB/s, %02d:%02d", filename, intent.getFloatExtra(UpdateService.EXTRA_PROGRESS, 0), kibps, sec / 60, sec % 60);
						} else {
							sub = String.format(Locale.ENGLISH, "%s, %.0f %%, %.0f MiB/s, %02d:%02d", filename, intent.getFloatExtra(UpdateService.EXTRA_PROGRESS, 0), kibps / 1024f, sec / 60, sec % 60);
						}
					}
				}
			}
			
			MainActivity.this.title.setText(title);
			MainActivity.this.sub.setText(sub);
			
			progress.setProgress((int)current);
			progress.setMax((int)total);	
			
			checkNow.setVisibility(enableCheck ? View.VISIBLE : View.GONE);
			flashNow.setVisibility(enableFlash ? View.VISIBLE : View.GONE);
		}		
	};
	
	@Override
	protected void onStart() {
		super.onStart();
		registerReceiver(updateReceiver, updateFilter);
	}

	@Override
	protected void onStop() {
		unregisterReceiver(updateReceiver);
		super.onStop();
	}
	
	public void onButtonCheckNowClick(View v) {
		UpdateService.startCheck(this);		
	}

	public void onButtonFlashNowClick(View v) {
		UpdateService.startFlash(this);
		checkNow.setEnabled(false);
		flashNow.setEnabled(false);
	}
}
