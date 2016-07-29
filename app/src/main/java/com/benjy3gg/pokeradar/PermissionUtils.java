package com.benjy3gg.pokeradar;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import com.mukesh.permissions.AppPermissions;

/**
 * Created by benjy3gg on 28.07.2016.
 */

public class PermissionUtils {

    public static boolean canDrawOverlays(Context context){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }else{
            return Settings.canDrawOverlays(context);
        }
    }

    public static boolean hasLocationPermissions(AppPermissions pAppPermissions) {
        String[] permissions = new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION};
        if(!pAppPermissions.hasPermission(permissions)) {
            return false;
        }else {
            return true;
        }
    }

    public static boolean getLocationPermissions(AppPermissions pAppPermissions) {
        String[] permissions = new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION};
        if(!hasLocationPermissions(pAppPermissions)) {
            pAppPermissions.requestPermission(permissions, SplashActivity.REQ_CODE_LOCATION);
        }else {
            return true;
        }
        return false;
    }

}
