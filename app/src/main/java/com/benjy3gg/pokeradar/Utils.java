package com.benjy3gg.pokeradar;

/**
 * Created by benjy3gg on 23.07.2016.
 */
import android.content.Context;
import android.os.Build;
import android.provider.Settings;

public class Utils {

    public static String LogTag = "benjy3gg";
    public static String EXTRA_MSG = "extra_msg";


    public static boolean canDrawOverlays(Context context){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }else{
            return Settings.canDrawOverlays(context);
        }


    }


}
