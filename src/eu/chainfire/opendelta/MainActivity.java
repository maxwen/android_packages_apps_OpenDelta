/* 
 * Copyright (C) 2013-2014 Jorrit "Chainfire" Jongma
 * Copyright (C) 2013-2014 The OmniROM Project
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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Html;
import android.text.format.DateFormat;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {
    private TextView title = null;
    private TextView sub = null;
    private ProgressBar progress = null;
    private Button checkNow = null;
    private Button flashNow = null;
    
    private Config config;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {
            getActionBar().setIcon(getPackageManager().getApplicationIcon("com.android.settings"));
        } catch (NameNotFoundException e) {
            // The standard Settings package is not present, so we can't snatch its icon
            Logger.ex(e);
        }

        getActionBar().setDisplayHomeAsUpEnabled(true);

        UpdateService.start(this);

        setContentView(R.layout.activity_main);

        title = (TextView) findViewById(R.id.text_title);
        sub = (TextView) findViewById(R.id.text_sub);
        progress = (ProgressBar) findViewById(R.id.progress);
        checkNow = (Button) findViewById(R.id.button_check_now);
        flashNow = (Button) findViewById(R.id.button_flash_now);
        
        config = Config.getInstance(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        
        if (!config.getSecureModeEnable()) {
            menu.findItem(R.id.action_secure_mode).setVisible(false);
        } else {
            menu.findItem(R.id.action_secure_mode).setChecked(config.getSecureModeCurrent());
        }
        
        return true;
    }

    private void showNetworks() {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);

        int flags = prefs.getInt(UpdateService.PREF_AUTO_UPDATE_NETWORKS_NAME,
                UpdateService.PREF_AUTO_UPDATE_NETWORKS_DEFAULT);
        final boolean[] checkedItems = new boolean[] {
                (flags & NetworkState.ALLOW_2G) == NetworkState.ALLOW_2G,
                (flags & NetworkState.ALLOW_3G) == NetworkState.ALLOW_3G,
                (flags & NetworkState.ALLOW_4G) == NetworkState.ALLOW_4G,
                (flags & NetworkState.ALLOW_WIFI) == NetworkState.ALLOW_WIFI,
                (flags & NetworkState.ALLOW_ETHERNET) == NetworkState.ALLOW_ETHERNET,
                (flags & NetworkState.ALLOW_UNKNOWN) == NetworkState.ALLOW_UNKNOWN
        };

        (new AlertDialog.Builder(this)).
                setTitle(R.string.title_networks).
                setMultiChoiceItems(new CharSequence[] {
                        getString(R.string.network_2g),
                        getString(R.string.network_3g),
                        getString(R.string.network_4g),
                        getString(R.string.network_wifi),
                        getString(R.string.network_ethernet),
                        getString(R.string.network_unknown),
                }, checkedItems, new OnMultiChoiceClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                        checkedItems[which] = isChecked;
                    }
                }).
                setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        int flags = 0;
                        if (checkedItems[0])
                            flags += NetworkState.ALLOW_2G;
                        if (checkedItems[1])
                            flags += NetworkState.ALLOW_3G;
                        if (checkedItems[2])
                            flags += NetworkState.ALLOW_4G;
                        if (checkedItems[3])
                            flags += NetworkState.ALLOW_WIFI;
                        if (checkedItems[4])
                            flags += NetworkState.ALLOW_ETHERNET;
                        if (checkedItems[5])
                            flags += NetworkState.ALLOW_UNKNOWN;
                        prefs.
                                edit().
                                putInt(UpdateService.PREF_AUTO_UPDATE_NETWORKS_NAME, flags).
                                commit();
                    }
                }).
                setNegativeButton(android.R.string.cancel, null).
                setCancelable(true).
                show();
    }

    private void showAbout() {
        int thisYear = Calendar.getInstance().get(Calendar.YEAR);
        String opendelta = (thisYear == 2013) ? "2013" : "2013-" + String.valueOf(thisYear);
        String xdelta = (thisYear == 1997) ? "1997" : "1997-" + String.valueOf(thisYear);

        AlertDialog dialog = (new AlertDialog.Builder(this)).
                setTitle(R.string.app_name).
                setMessage(
                        Html.fromHtml(
                                getString(R.string.about_content).
                                        replace("_COPYRIGHT_OPENDELTA_", opendelta).
                                        replace("_COPYRIGHT_XDELTA_", xdelta)
                                )
                ).
                setNeutralButton(android.R.string.ok, null).
                setCancelable(true).
                show();
        TextView textView = (TextView) dialog.findViewById(android.R.id.message);
        if (textView != null)
            textView.setTypeface(title.getTypeface());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            case R.id.action_networks:
                showNetworks();
                return true;
            case R.id.action_secure_mode:
                item.setChecked(config.setSecureModeCurrent(!item.isChecked()));
                
                (new AlertDialog.Builder(this)).
                    setTitle(item.isChecked() ? R.string.secure_mode_enabled_title : R.string.secure_mode_disabled_title).
                    setMessage(Html.fromHtml(getString(item.isChecked() ? R.string.secure_mode_enabled_description : R.string.secure_mode_disabled_description))).
                    setCancelable(true).
                    setNeutralButton(android.R.string.ok, null).
                    show();
                
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
                    return String.format("%s\n%s", filename, getString(R.string.last_checked,
                            DateFormat.getDateFormat(MainActivity.this).format(date),
                            DateFormat.getTimeFormat(MainActivity.this).format(date)
                            ));
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
            if (state != null) {
                try {
                    title = getString(getResources().getIdentifier(
                            "state_" + state, "string", getPackageName()));
                } catch (Exception e) {
                    // String for this state could not be found (displays empty string)
                    Logger.ex(e);                    
                }
            }

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
                sub = formatLastChecked(intent.getStringExtra(UpdateService.EXTRA_FILENAME),
                        intent.getLongExtra(UpdateService.EXTRA_MS, 0));
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
                        sub = String.format(Locale.ENGLISH, "%s\n%.0f %%", filename,
                                intent.getFloatExtra(UpdateService.EXTRA_PROGRESS, 0));
                    } else {
                        float kibps = ((float) current / 1024f) / ((float) ms / 1000f);
                        if (progressInK)
                            kibps *= 1024f;
                        int sec = (int) (((((float) total / (float) current) * (float) ms) - ms) / 1000f);

                        if (kibps < 10000) {
                            sub = String.format(Locale.ENGLISH,
                                    "%s\n%.0f %%, %.0f KiB/s, %02d:%02d", filename,
                                    intent.getFloatExtra(UpdateService.EXTRA_PROGRESS, 0), kibps,
                                    sec / 60, sec % 60);
                        } else {
                            sub = String.format(Locale.ENGLISH,
                                    "%s\n%.0f %%, %.0f MiB/s, %02d:%02d", filename,
                                    intent.getFloatExtra(UpdateService.EXTRA_PROGRESS, 0),
                                    kibps / 1024f, sec / 60, sec % 60);
                        }
                    }
                }
            }

            MainActivity.this.title.setText(title);
            MainActivity.this.sub.setText(sub);

            progress.setProgress((int) current);
            progress.setMax((int) total);

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
        flashRecoveryWarning.run();
    }
    
    private Runnable flashRecoveryWarning = new Runnable() {        
        @Override
        public void run() {
            // Show a warning message about recoveries we support, depending
            // on the state of secure mode and if we've shown the message before
            
            final Runnable next = flashWarningFlashAfterUpdateZIPs;
            
            CharSequence message = null;
            if (!config.getSecureModeCurrent() && !config.getShownRecoveryWarningNotSecure()) {
                message = Html.fromHtml(getString(R.string.recovery_notice_description_not_secure));
                config.setShownRecoveryWarningNotSecure();
            } else if (config.getSecureModeCurrent() && !config.getShownRecoveryWarningSecure()) {
                message = Html.fromHtml(getString(R.string.recovery_notice_description_secure));            
                config.setShownRecoveryWarningSecure();
            }
                
            if (message != null) {
                (new AlertDialog.Builder(MainActivity.this)).
                        setTitle(R.string.recovery_notice_title).
                        setMessage(message).
                        setCancelable(true).
                        setNegativeButton(android.R.string.cancel, null).
                        setPositiveButton(android.R.string.ok, new OnClickListener() {                        
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                next.run();
                            }
                        }).
                        show();
            } else {
                next.run();
            }
        }
    };
    
    private Runnable flashWarningFlashAfterUpdateZIPs = new Runnable() {
        @Override
        public void run() {
            // If we're in secure mode, but additional ZIPs to flash have been
            // detected, warn the user that these will not be flashed
            
            final Runnable next = flashStart;
            
            if (config.getSecureModeCurrent() && (config.getFlashAfterUpdateZIPs().size() > 0)) {
                (new AlertDialog.Builder(MainActivity.this)).
                setTitle(R.string.flash_after_update_notice_title).
                setMessage(Html.fromHtml(getString(R.string.flash_after_update_notice_description))).
                setCancelable(true).
                setNegativeButton(android.R.string.cancel, null).
                setPositiveButton(android.R.string.ok, new OnClickListener() {                        
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        next.run();
                    }
                }).
                show();                
            } else {
                next.run();
            }
        }
    };
    
    private Runnable flashStart = new Runnable() {       
        @Override
        public void run() {
            checkNow.setEnabled(false);
            flashNow.setEnabled(false);
            UpdateService.startFlash(MainActivity.this);
        }
    };
}
