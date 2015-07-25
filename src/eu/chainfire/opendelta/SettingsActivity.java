/*
 *  Copyright (C) 2015 The OmniROM Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */
package eu.chainfire.opendelta;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.DialogInterface.OnMultiChoiceClickListener;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.Html;
import android.view.MenuItem;

public class SettingsActivity extends PreferenceActivity implements
        OnPreferenceChangeListener  {
    private static final String KEY_NETWORKS = "networks_config";
    public static final String PREF_AUTO_DOWNLOAD = "auto_download_actions";
    public static final String PREF_CHARGE_ONLY = "charge_only";
    public static final String PREF_BATTERY_LEVEL = "battery_level_string";
    private static final String KEY_SECURE_MODE = "secure_mode";
    private static final String KEY_CATEGORY_DOWNLOAD = "category_download";

    private Preference mNetworksConfig;
    private ListPreference mAutoDownload;
    private ListPreference mBatteryLevel;
    private CheckBoxPreference mChargeOnly;
    private CheckBoxPreference mSecureMode;
    private Config mConfig;
    private PreferenceCategory mAutoDownloadCategory;

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mConfig = Config.getInstance(this);

        getActionBar().setDisplayHomeAsUpEnabled(true);

        addPreferencesFromResource(R.xml.settings);
        mNetworksConfig = (Preference) findPreference(KEY_NETWORKS);
        mAutoDownload = (ListPreference) findPreference(PREF_AUTO_DOWNLOAD);
        mAutoDownload.setOnPreferenceChangeListener(this);
        mAutoDownload.setSummary(mAutoDownload.getEntry());
        mBatteryLevel = (ListPreference) findPreference(PREF_BATTERY_LEVEL);
        mBatteryLevel.setOnPreferenceChangeListener(this);
        mBatteryLevel.setSummary(mBatteryLevel.getEntry());
        mChargeOnly = (CheckBoxPreference) findPreference(PREF_CHARGE_ONLY);
        mBatteryLevel.setEnabled(!prefs.getBoolean(PREF_CHARGE_ONLY, true));
        mSecureMode = (CheckBoxPreference) findPreference(KEY_SECURE_MODE);
        mSecureMode.setEnabled(mConfig.getSecureModeEnable());
        mSecureMode.setChecked(mConfig.getSecureModeCurrent());
        mAutoDownloadCategory = (PreferenceCategory) findPreference(KEY_CATEGORY_DOWNLOAD);

        String autoDownload = prefs.getString(PREF_AUTO_DOWNLOAD, UpdateService.PREF_AUTO_DOWNLOAD_CHECK_STRING);
        int autoDownloadValue = Integer.valueOf(autoDownload).intValue();
        mAutoDownloadCategory.setEnabled(autoDownloadValue > UpdateService.PREF_AUTO_DOWNLOAD_CHECK);
    }

    @Override
    public boolean onOptionsItemSelected (MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
 
        if (preference == mNetworksConfig) {
            showNetworks();
            return true;
        } else if (preference == mChargeOnly) {
            boolean value = ((CheckBoxPreference) preference).isChecked();
            mBatteryLevel.setEnabled(!value);
            return true;
        } else if (preference == mSecureMode) {
            boolean value = ((CheckBoxPreference) preference).isChecked();
            mConfig.setSecureModeCurrent(value);
            (new AlertDialog.Builder(this)).
                setTitle(value ? R.string.secure_mode_enabled_title : R.string.secure_mode_disabled_title).
                setMessage(Html.fromHtml(getString(value ? R.string.secure_mode_enabled_description : R.string.secure_mode_disabled_description))).
                setCancelable(true).
                setNeutralButton(android.R.string.ok, null).
                show();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mAutoDownload) {
            String value = (String) newValue;
            int idx = mAutoDownload.findIndexOfValue(value);
            mAutoDownload.setSummary(mAutoDownload.getEntries()[idx]);
            mAutoDownload.setValueIndex(idx);
            int autoDownloadValue = Integer.valueOf(value).intValue();
            mAutoDownloadCategory.setEnabled(autoDownloadValue > UpdateService.PREF_AUTO_DOWNLOAD_CHECK);
            return true;
        } else if (preference == mBatteryLevel) {
            String value = (String) newValue;
            int idx = mBatteryLevel.findIndexOfValue(value);
            mBatteryLevel.setSummary(mBatteryLevel.getEntries()[idx]);
            mBatteryLevel.setValueIndex(idx);
            return true;
        }
        return false;
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
}
