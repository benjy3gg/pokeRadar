package com.benjy3gg.pokeradar;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.mukesh.permissions.AppPermissions;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.auth.GoogleUserCredentialProvider;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.util.SystemTimeImpl;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import fr.quentinklein.slt.LocationTracker;
import fr.quentinklein.slt.LocationUtils;
import fr.quentinklein.slt.TrackerSettings;
import okhttp3.OkHttpClient;

public class SplashActivity extends AppCompatActivity implements LocationListener, AuthenticationListener {

    //Permissions
    public static final int REQ_CODE_OVERLAY = 1234;
    public static final int REQ_CODE_LOCATION = 1235;
    public boolean bHasOverlayPermission = false;
    public boolean bHasLocationPermission = false;
    private CoordinatorLayout vSplashContent;
    private TextView vSplashInfo;
    private ProgressBar vSplashProgress;
    private AppPermissions mRuntimePermission;

    private int mVersionCode;
    private boolean bLocationEnabled;
    private LocationTracker mLocationTracker;
    private SharedPreferences mSharedPrefs;
    private PokemonGo mGo;
    private Handler mHandler;

    private String mUsername;
    private String mPassword;
    private Snackbar mOverlaySnackbar;
    private Snackbar mLocationSnackbar;
    private Snackbar mEnableLoationSnackbar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Important variables
        mSharedPrefs = getSharedPreferences("credentials", Context.MODE_PRIVATE);
        EventBus.getDefault().register(this);

        //Views
        vSplashContent = (CoordinatorLayout) findViewById(R.id.splashParent);
        vSplashInfo = (TextView) findViewById(R.id.splashInfo);
        vSplashProgress = (ProgressBar) findViewById(R.id.splashProgress);

        vSplashInfo.setText(Utils.getVersionCode(this));

        createSnackbars();

        mHandler = new Handler();
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                waitForPermissions();
            }
        };
        mHandler.postDelayed(myRunnable, 1000);

        //Permissions
        mRuntimePermission = new AppPermissions(this);
        if (PermissionUtils.getLocationPermissions(mRuntimePermission)) {
            bHasLocationPermission = true;
            enableLocation();
        }

        //TODO: Check permissions, if set go to LoginActivity
        if (PermissionUtils.canDrawOverlays(this)) {
            //Snackbar.make(vSplashContent, R.string.permission_overlay_granted, Snackbar.LENGTH_INDEFINITE).show();
            bHasOverlayPermission = true;
        } else {
            requestOverlayPermission();
        }
    }

    private void createSnackbars() {
        mOverlaySnackbar = Snackbar.make(vSplashContent, "App needs the overlay permission!", Snackbar.LENGTH_INDEFINITE).setAction(R.string.permission_grant, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestOverlayPermission();
            }
        });
        mLocationSnackbar = Snackbar.make(vSplashContent, "App needs the location permission!", Snackbar.LENGTH_INDEFINITE).setAction(R.string.permission_grant, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PermissionUtils.getLocationPermissions(mRuntimePermission);
            }
        });
        mEnableLoationSnackbar = Snackbar.make(vSplashContent, "You need to enable location!", Snackbar.LENGTH_INDEFINITE).setAction(R.string.enable_location, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enableLocation();
            }
        });


    }

    public void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQ_CODE_OVERLAY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case REQ_CODE_OVERLAY:
                bHasOverlayPermission = true;
                //Snackbar.make(vSplashContent, R.string.permission_overlay_granted, Snackbar.LENGTH_INDEFINITE).show();
                break;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_CODE_LOCATION) { //The request code you passed along with the request.
            //grantResults holds a list of all the results for the permissions requested.
            for (int grantResult : grantResults) {
                if (grantResult == PackageManager.PERMISSION_DENIED) {
                    bHasLocationPermission = false;
                    //Snackbar.make(vSplashContent, R.string.permission_location_denied, Snackbar.LENGTH_INDEFINITE).show();
                    return;
                }
            }
            //Snackbar.make(vSplashContent, R.string.permission_location_granted, Snackbar.LENGTH_INDEFINITE).show();
            bHasLocationPermission = true;
            enableLocation();
        }
    }

    public void enableLocation() {
        activateLocationUpdate();
        if (!isLocationEnabled()) {
            LocationUtils.askEnableProviders(this, R.string.gps_enable_text, R.string.gps_enable_positive, R.string.gps_enable_negative);
        } else {
            bLocationEnabled = true;
        }
    }

    public boolean isLocationEnabled() {
        if (!LocationUtils.isGpsProviderEnabled(this) && !LocationUtils.isNetworkProviderEnabled(this)) {
            bLocationEnabled = false;
            return false;
        } else {
            bLocationEnabled = true;
            return true;
        }
    }

    public void activateLocationUpdate() {
        try {
            TrackerSettings settings =
                    new TrackerSettings()
                            .setUseGPS(LocationUtils.isGpsProviderEnabled(this))
                            .setUseNetwork(LocationUtils.isNetworkProviderEnabled(this))
                            .setUsePassive(LocationUtils.isPassiveProviderEnabled(this))
                            .setTimeBetweenUpdates(10 * 1000)
                            .setMetersBetweenUpdates(100);
            LocationTracker tracker = new LocationTracker(this, settings) {
                @Override
                public void onLocationFound(@NonNull Location location) {
                    SplashActivity.this.onLocationChanged(location);
                }

                @Override
                public void onTimeout() {

                }

                @Override
                public void onProviderDisabled(@NonNull String provider) {
                    SplashActivity.this.onProviderDisabled(provider);
                }

                @Override
                public void onProviderEnabled(@NonNull String provider) {
                    SplashActivity.this.onProviderEnabled(provider);
                }
            };
            tracker.startListening();
            tracker.quickFix();
        } catch (SecurityException e) {
            //showLocationSnackBar("SecurityException");
        }
    }

    public void waitForPermissions() {
        vSplashProgress.setVisibility(View.VISIBLE);
        if (!bHasOverlayPermission) {
            mOverlaySnackbar.show();
        } else {
            mOverlaySnackbar.dismiss();
            if (!bHasLocationPermission) {
                mLocationSnackbar.show();
            } else {
                mLocationSnackbar.dismiss();
                if (!bLocationEnabled) {
                    mEnableLoationSnackbar.show();
                }else {
                    //ALL PERMISSIONS, YAY!!!
                    Toast.makeText(this, "All Permissions granted!", Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    public void startPokeService() {
        startService(new Intent(this, PokeService.class));
    }

    public void startFetchService() {
        startService(new Intent(this, FetchService.class));
    }

    public void initializeGoByAuth() {
        OkHttpClient client = new OkHttpClient();
        GoogleLoginTask loginTask = new GoogleLoginTask(client, null, null, null, mSharedPrefs, this);
        loginTask.execute();
    }

    public void initializeGo(String username, String password, String authType) {
        OkHttpClient client = new OkHttpClient();
        String refreshToken = mSharedPrefs.getString("auth", null);
        if (authType.equals("google")) {
            GoogleLoginTask loginTask = new GoogleLoginTask(client, refreshToken, username, password, mSharedPrefs, this);
            loginTask.execute();
        } else {
            PTCLoginTask loginTask = new PTCLoginTask(client, username, password, mSharedPrefs, this);
            loginTask.execute();

        }
    }

    public void createDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        //TODO: Add logout button


        View dialogView = getLayoutInflater().inflate(R.layout.dialog_login, null);
        final EditText editUsername = (EditText) dialogView.findViewById(R.id.username);
        final EditText editPassword = (EditText) dialogView.findViewById(R.id.password);

        builder.setView(dialogView)
                .setPositiveButton(R.string.google, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mUsername = editUsername.getText().toString();
                        mPassword = editPassword.getText().toString();
                        Toast.makeText(SplashActivity.this, "Google login", Toast.LENGTH_LONG).show();
                        initializeGo(mUsername, mPassword, "google");

                    }
                })
                .setNegativeButton(R.string.ptc, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(SplashActivity.this, "PTC login", Toast.LENGTH_LONG).show();
                        initializeGo(mUsername, mPassword, "ptc");
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        Snackbar.make(vSplashContent, "Don't want to login? ;)", Snackbar.LENGTH_INDEFINITE).setAction(R.string.snackbar_action_login, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                createDialog();
                            }
                        }).show();
                    }
                });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void showLocationSnackBar(String infoText) {
        Snackbar.make(vSplashContent, infoText, Snackbar.LENGTH_INDEFINITE).setAction(R.string.snackbar_action_login, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enableLocation();
            }
        }).show();
    }

    @Override
    public void onProviderDisabled(String s) {

    }

    @Override
    public void onLocationChanged(Location location) {
        bLocationEnabled = true;
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onGoAvailable(PokemonGo go) {
        mGo = go;
        GoFoundEvent event = new GoFoundEvent(go);
        EventBus.getDefault().post(event);
        startPokeService();
        startFetchService();
    }

    @Override
    public void onGoFailed(String reason) {
        Toast.makeText(this, "Login failed: " + reason, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onGoNeedsAuthentification() {
        //TODO: create dialog with Webview
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        //TODO: Add logout button
        final OkHttpClient http = new OkHttpClient();
        GoogleUserCredentialProvider provider;
        try {
            provider = new GoogleUserCredentialProvider(http);
        } catch (LoginFailedException e) {
            e.printStackTrace();
        } catch (RemoteServerException e) {
            e.printStackTrace();
        }

        LinearLayout ll = (LinearLayout) getLayoutInflater().inflate(R.layout.dialog_authenticate, null);
        final TextView editToken = (TextView) ll.findViewById(R.id.editToken);

        WebView wv = (WebView) ll.findViewById(R.id.webView);
        wv.loadUrl(GoogleUserCredentialProvider.LOGIN_URL);
        wv.clearCache(true);
        wv.clearHistory();
        wv.getSettings().setJavaScriptEnabled(true);
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);

                return true;
            }
        });


        builder.setView(ll)
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mSharedPrefs.edit().putString("authReq", editToken.getText().toString()).commit();
                        initializeGoByAuth();
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        Snackbar.make(vSplashContent, "You)", Snackbar.LENGTH_INDEFINITE).setAction(R.string.snackbar_action_login, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                createDialog();
                            }
                        }).show();
                    }
                });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    @Override
    protected void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        Toast.makeText(this, "Service: " + event.title, Toast.LENGTH_SHORT).show();
    }
}
