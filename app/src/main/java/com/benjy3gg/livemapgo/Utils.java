package com.benjy3gg.livemapgo;

import android.content.Context;
import android.content.pm.PackageManager;

public class Utils {

    public static String getVersionCode(Context ctx) {
        try {
            return "pokeRadar v" + ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return "pokeRadar vX.X";
    }

}
