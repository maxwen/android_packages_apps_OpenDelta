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

import android.content.Context;
import android.content.res.Resources;
import android.os.Environment;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Locale;

public class Config {
    private static Config instance = null;

    public static Config getInstance(Context context) {
        if (instance == null) {
            instance = new Config(context.getApplicationContext());
        }
        return instance;
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

    /*
     * Using reflection voodoo instead calling the hidden class directly, to
     * dev/test outside of AOSP tree
     */
    private String getProperty(Context context, String key, String defValue) {
        try {
            Class<?> SystemProperties = context.getClassLoader().loadClass(
                    "android.os.SystemProperties");
            Method get = SystemProperties.getMethod("get", new Class[] {
                    String.class, String.class
            });
            return (String) get.invoke(null, new Object[] {
                    key, defValue
            });
        } catch (Exception e) {
            // A lot of voodoo could go wrong here, return failure instead of
            // crash
            Logger.ex(e);
        }
        return null;
    }

    private Config(Context context) {
        Resources res = context.getResources();

        property_version = getProperty(context, res.getString(R.string.property_version), "");
        property_device = getProperty(context, res.getString(R.string.property_device), "");
        filename_base = String.format(Locale.ENGLISH, res.getString(R.string.filename_base),
                property_version);
        path_base = String.format(Locale.ENGLISH, "%s%s%s%s", 
                Environment.getExternalStorageDirectory().getAbsolutePath(), 
                File.separator,
                res.getString(R.string.path_base), 
                File.separator);
        path_flash_after_update = String.format(Locale.ENGLISH, "%s%s%s", 
                path_base,
                "FlashAfterUpdate", 
                File.separator);
        url_base_delta = String.format(Locale.ENGLISH, res.getString(R.string.url_base_delta),
                property_device);
        url_base_update = String.format(Locale.ENGLISH, res.getString(R.string.url_base_update),
                property_device);
        url_base_full = String.format(Locale.ENGLISH, res.getString(R.string.url_base_full),
                property_device);
        apply_signature = res.getBoolean(R.bool.apply_signature);

        Logger.d("property_version: %s", property_version);
        Logger.d("property_device: %s", property_device);
        Logger.d("filename_base: %s", filename_base);
        Logger.d("path_base: %s", path_base);
        Logger.d("path_flash_after_update: %s", path_flash_after_update);
        Logger.d("url_base_delta: %s", url_base_delta);
        Logger.d("url_base_update: %s", url_base_update);
        Logger.d("url_base_full: %s", url_base_full);
        Logger.d("apply_signature: %d", apply_signature ? 1 : 0);
    }
    
    public String getFilenameBase() {
        return filename_base;
    }
    
    public String getPathBase() {
        return path_base;
    }
    
    public String getPathFlashAfterUpdate() {
        return path_flash_after_update;
    }
    
    public String getUrlBaseDelta() {
        return url_base_delta;
    }
    
    public String getUrlBaseUpdate() {
        return url_base_update;
    }

    public String getUrlBaseFull() {
        return url_base_full;
    }
    
    public boolean getApplySignature() {
        return apply_signature;
    }
}
