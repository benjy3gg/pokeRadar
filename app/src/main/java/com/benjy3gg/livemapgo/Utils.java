package com.benjy3gg.livemapgo;

import android.content.Context;
import android.content.pm.PackageManager;
import android.util.DisplayMetrics;

public class Utils {

    public static String getVersionCode(Context ctx) {
        try {
            return "pokeRadar v" + ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return "pokeRadar vX.X";
    }

    public static int getDp(float p, Context ctx) {
        DisplayMetrics metrics = ctx.getResources().getDisplayMetrics();
        float fpixels = metrics.density * p;
        return (int) (fpixels + 0.5f);
    }

}
