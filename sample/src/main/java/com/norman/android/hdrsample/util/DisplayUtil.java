package com.norman.android.hdrsample.util;

import android.content.Context;
import android.content.res.Resources;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Display;
import android.view.WindowManager;

import java.lang.reflect.Field;

public class DisplayUtil {

    private static Float MAX_SCREEN_LUMINANCE = null;
    private static Integer MAX_SCREEN_BRIGHTNESS = null;

    private static float DEFAULT_MAX_SCREEN_LUMINANCE = 100;

    private static int DEFAULT_MAX_SCREEN_BRIGHTNESS = 255;

    private static boolean HDR_CAPABILITY_DOLBY_VISION = false;
    private static boolean HDR_CAPABILITY_HDR10 = false;
    private static boolean HDR_CAPABILITY_HLG = false;
    private static boolean HDR_CAPABILITY_HDR10_PLUS = false;

    private static Boolean HDR_CAPABILITY_SCREEN = null;

    public static synchronized float getMaxLuminance() {
        if (MAX_SCREEN_LUMINANCE == null) {
            float maxScreenLuminance = 0;
            Context context = AppUtil.getAppContext();
            WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            Display display = windowManager.getDefaultDisplay();
            Display.HdrCapabilities hdrCapabilities = display.getHdrCapabilities();
            maxScreenLuminance = Math.max(hdrCapabilities.getDesiredMaxAverageLuminance(), maxScreenLuminance);
            maxScreenLuminance = Math.max(hdrCapabilities.getDesiredMaxLuminance(), maxScreenLuminance);
            if (maxScreenLuminance <= 0) {
                MAX_SCREEN_LUMINANCE = DEFAULT_MAX_SCREEN_LUMINANCE;
            } else {
                MAX_SCREEN_LUMINANCE = maxScreenLuminance;
            }
        }
        return MAX_SCREEN_LUMINANCE;
    }

    public static synchronized int getMaxBrightness() {
        if (MAX_SCREEN_BRIGHTNESS == null) {
            int maxBrightness = 0;
            try {
                Context context = AppUtil.getAppContext();
                PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
                Field[] fields = powerManager.getClass().getDeclaredFields();
                for (Field field : fields) {
                    if (field.getName().equals("BRIGHTNESS_ON")) {
                        field.setAccessible(true);
                        maxBrightness = (int) field.get(powerManager);
                        break;
                    }
                }
            } catch (Exception e) {

            }
            try {
                if (maxBrightness <= 0) {
                    Resources system = Resources.getSystem();
                    int resId = system.getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android");
                    maxBrightness = system.getInteger(resId);
                }
            } catch (Exception e) {

            }
            if (maxBrightness <= 0) {
                MAX_SCREEN_BRIGHTNESS = DEFAULT_MAX_SCREEN_BRIGHTNESS;
            } else {
                MAX_SCREEN_BRIGHTNESS = maxBrightness;
            }
        }
        return MAX_SCREEN_BRIGHTNESS;
    }

    public static int getBrightness() {
        try {
            Context context = AppUtil.getAppContext();
            return Settings.System.getInt(context.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS);
        } catch (Settings.SettingNotFoundException e) {
            return getMaxBrightness();
        }
    }

    public static boolean isSupportDolbyVision() {
        loadHdrCapability();
        return HDR_CAPABILITY_DOLBY_VISION;
    }

    public static boolean isSupportHlg() {
        loadHdrCapability();
        return HDR_CAPABILITY_HLG;
    }

    public static boolean isSupportHdr10() {
        loadHdrCapability();
        return HDR_CAPABILITY_HDR10;
    }

    public static boolean isSupportHdr10Plus() {
        loadHdrCapability();
        return HDR_CAPABILITY_HDR10_PLUS;
    }

    public static boolean isSupportHdr() {
        loadHdrCapability();
        return HDR_CAPABILITY_SCREEN;
    }


    private static synchronized void loadHdrCapability() {
        if (HDR_CAPABILITY_SCREEN != null) {
            return;
        }
        Context context = AppUtil.getAppContext();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        Display.HdrCapabilities hdrCapabilities = display.getHdrCapabilities();
        int[] hdrTypes = hdrCapabilities.getSupportedHdrTypes();
        if (hdrTypes != null && hdrTypes.length > 0) {
            for (int hdrType : hdrTypes) {
                if (hdrType == Display.HdrCapabilities.HDR_TYPE_DOLBY_VISION) {
                    HDR_CAPABILITY_DOLBY_VISION = true;
                } else if (hdrType == Display.HdrCapabilities.HDR_TYPE_HLG) {
                    HDR_CAPABILITY_HLG = true;
                } else if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10) {
                    HDR_CAPABILITY_HDR10 = true;
                } else if (hdrType == Display.HdrCapabilities.HDR_TYPE_HDR10_PLUS) {
                    HDR_CAPABILITY_HDR10_PLUS = true;
                }
            }
            HDR_CAPABILITY_SCREEN = true;
        }
        if (HDR_CAPABILITY_SCREEN == null) {
            HDR_CAPABILITY_SCREEN = false;
        }
    }
}
