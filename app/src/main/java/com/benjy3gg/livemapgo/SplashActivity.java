package com.benjy3gg.livemapgo;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.media.RingtoneManager;
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
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.TransactionDetails;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.mukesh.permissions.AppPermissions;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.auth.GoogleUserCredentialProvider;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import fr.quentinklein.slt.LocationTracker;
import fr.quentinklein.slt.LocationUtils;
import fr.quentinklein.slt.TrackerSettings;
import okhttp3.OkHttpClient;

public class SplashActivity extends AppCompatActivity implements LocationListener, AuthenticationListener, BillingProcessor.IBillingHandler {

    //Permissions
    private static final String SKU_PREMIUM = "com.benjy3gg.pokeradar.removeads";
    public static final int REQ_CODE_OVERLAY = 1234;
    public static final int REQ_CODE_LOCATION = 1235;
    private static final int SOUND_REQ = 1236;
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
    private TextView vSplashTokenFound;
    private BillingProcessor bp;
    private Menu mMenu;
    private SharedPreferences.Editor mEditor;
    private AdView mAdView;
    private FirebaseAnalytics mFirebaseAnalytics;
    private Runnable myRunnable;
    private Snackbar mLoginSnackbar;
    private ImageView vSplashIcon;
    private Snackbar mStartPokeballSnackbar;
    private boolean mAskProviders;
    private String chosenRingtone;

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mLocationTracker != null) {
            mLocationTracker.stopListening();
        }
        startService(new Intent(this, FetchService.class).putExtra("type", "destroy"));
        startService(new Intent(this, PokeService.class).putExtra("type", "destroy"));
        if (bp != null)
            bp.release();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem checkable = menu.findItem(R.id.checkable_menu);
        checkable.setChecked(mSharedPrefs.getBoolean("vibrate", false));
        MenuItem sound = menu.findItem(R.id.checkable_menu_sound);
        sound.setChecked(mSharedPrefs.getBoolean("sound", false));
        MenuItem premium = menu.findItem(R.id.menu_premium);
        premium.setVisible(mSharedPrefs.getBoolean("premium", false));
        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        mMenu = menu;
        getMenuInflater().inflate(R.menu.menu, menu);
        super.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement

        if (id == R.id.menu_filters) {
            Intent intent = new Intent(this, ListActivity.class);
            this.startActivity(intent);
            return true;
        } else if (id == R.id.checkable_menu) {
            SharedPreferences.Editor editor = mSharedPrefs.edit();
            editor.putBoolean("vibrate", !item.isChecked());
            editor.commit();
            item.setChecked(mSharedPrefs.getBoolean("vibrate", false));
            return true;
        } else if (id == R.id.checkable_menu_sound) {
            //SharedPreferences.Editor editor = mSharedPrefs.edit();
            //editor.putBoolean("sound", !item.isChecked());
            //editor.commit();
            //item.setChecked(mSharedPrefs.getBoolean("sound", false));
            Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Tone");
            String selectedTone =  mSharedPrefs.getString("ringtone", "");
            if(selectedTone.equals("")) {
                selectedTone = "NONE";
            }
            Uri tone =  Uri.parse(selectedTone);

            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, tone);
            this.startActivityForResult(intent, SOUND_REQ);
            return true;
        }else if (id == R.id.menu_premium) {
            if (isPremium()) {
                Toast.makeText(this, "You already have premium", Toast.LENGTH_SHORT).show();
            } else {
                if (bp != null) {
                    bp.purchase(this, SKU_PREMIUM);
                }
            }
            return true;

        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBillingInitialized() {
        /*
         * Called when BillingProcessor was initialized and it's ready to purchase
         */
        bp.loadOwnedPurchasesFromGoogle();
        if (bp.isPurchased(SKU_PREMIUM)) {
            mEditor = mSharedPrefs.edit();
            mEditor.putBoolean("premium", true);
            mEditor.apply();
            hideAds();
        }
    }

    @Override
    public void onProductPurchased(String productId, TransactionDetails details) {
        /*
         * Called when requested PRODUCT ID was successfully purchased
         */

        if (bp.isPurchased(SKU_PREMIUM)) {
            mEditor = mSharedPrefs.edit();
            mEditor.putBoolean("premium", true);
            mEditor.apply();
            hideAds();
            mFirebaseAnalytics.logEvent("BOUGHT PREMIUM", new Bundle());
        }
    }

    @Override
    public void onBillingError(int errorCode, Throwable error) {
        /*
         * Called when some error occurred. See Constants class for more details
         *
         * Note - this includes handling the case where the user canceled the buy dialog:
         * errorCode = Constants.BILLING_RESPONSE_RESULT_USER_CANCELED
         */

    }

    @Override
    public void onPurchaseHistoryRestored() {
        /*
         * Called when purchase history was restored and the list of all owned PRODUCT ID's
         * was loaded from Google Play
         */
        if (bp.isPurchased(SKU_PREMIUM)) {
            mEditor = mSharedPrefs.edit();
            mEditor.putBoolean("premium", true);
            mEditor.apply();
            hideAds();
        }
    }

    public void removeShoppingIcon() {
        if (mMenu != null) {
            mMenu.getItem(3).setVisible(false);
            //mMenu.removeItem(R.id.menu_premium);
            invalidateOptionsMenu();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        //Important variables
        mRuntimePermission = new AppPermissions(this);
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        mFirebaseAnalytics.logEvent("START MAPSACTIVIY", new Bundle());

        bHasOverlayPermission = PermissionUtils.canDrawOverlays(this);
        bHasLocationPermission = PermissionUtils.hasLocationPermissions(mRuntimePermission);
        bLocationEnabled = (LocationUtils.isGpsProviderEnabled(this) && LocationUtils.isNetworkProviderEnabled(this));


        mSharedPrefs = getSharedPreferences("credentials", Context.MODE_PRIVATE);
        EventBus.getDefault().register(this);

        //Views
        vSplashContent = (CoordinatorLayout) findViewById(R.id.splashParent);
        vSplashInfo = (TextView) findViewById(R.id.splashInfo);
        vSplashProgress = (ProgressBar) findViewById(R.id.splashProgress);
        vSplashInfo.setText(Utils.getVersionCode(this));
        vSplashIcon = (ImageView) findViewById(R.id.splashIcon);

        createSnackbars();

        mHandler = new Handler();
        myRunnable = new Runnable() {
            @Override
            public void run() {
                mHandler.postDelayed(myRunnable, 1000);
                waitForPermissions();
            }
        };
        mHandler.postDelayed(myRunnable, 1000);

        //Permissions

        MobileAds.initialize(getApplicationContext(), "ca-app-pub-7144884667135062~9262401038");
        String base64EncodedPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuXhM+jUrijfSaO07IZxXHkorDx5zP1qdvvPE/tFFsH7yrCC3O6OdksMWX8UxheD0QscqPlee+yojm4HWc6lNf6tVL6YKbL895rZ3p+l41svhUIAuoovar55D6oXGNxVrPrGToqGVIc7QpXfmdRjndc1wsxFAM08N+f+iIQKsikFkp6TykAEvzbOWbHINdudmn3yuXQAOSCFSvGoCzcY6mNjO109/OItjc524afRv2TWIgqggXBYpnvW/GXDfOlOBzuHivJwvvXTLGpw/8DU/YCBYQM21EQmDn4GP4DgnEycSc3R7pMvu82/QYvrZ5nN4WZBPKFIJhTO0A4SaLn9OuwIDAQAB";
        // compute your public key and store it in base64EncodedPublicKey

        bp = new BillingProcessor(this, base64EncodedPublicKey, this);

        //TODO: add in release

        mAdView = (AdView) findViewById(R.id.adView);
        if (isPremium()) {
            hideAds();
        } else {
            showAds();
        }

        /*
        AdView mAdView = (AdView) findViewById(R.id.adView);
        AdRequest.Builder adRequest = new AdRequest.Builder().addTestDevice("651B1B259A67AF560F4EDDEE3D82AD2B");
        AdRequest aaa=adRequest.build();
        mAdView.loadAd(aaa);*/
    }

    @Override
    protected void onPause() {
        if(mLocationTracker != null && mLocationTracker.isListening()) {
            mLocationTracker.stopListening();
        }
        super.onPause();
    }

    public void hideAds() {
        if(mAdView != null) {
            mAdView.setVisibility(View.INVISIBLE);
        }
        //TODO: CHANGE POKEBALL TO GOLD
        vSplashIcon.setImageResource(R.drawable.big_icon_supporter);
        removeShoppingIcon();
    }

    public void showAds() {
        if(mAdView != null) {
            mAdView.setVisibility(View.VISIBLE);
            AdRequest adRequest = new AdRequest.Builder().build();
            mAdView.loadAd(adRequest);
        }
        removeShoppingIcon();
    }

    private void createSnackbars() {
        mOverlaySnackbar = Snackbar.make(vSplashContent, R.string.app_needs_overlay, Snackbar.LENGTH_INDEFINITE).setAction(R.string.permission_grant, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                requestOverlayPermission();
            }
        });
        mOverlaySnackbar.setCallback(new Snackbar.Callback() {
            @Override public void onDismissed(Snackbar snackbar, int event) {
                if (event == DISMISS_EVENT_SWIPE) mOverlaySnackbar.show();
            }
        });

        mLocationSnackbar = Snackbar.make(vSplashContent, R.string.app_needs_location, Snackbar.LENGTH_INDEFINITE).setAction(R.string.permission_grant, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PermissionUtils.getLocationPermissions(mRuntimePermission);
            }
        });
        mLocationSnackbar.setCallback(new Snackbar.Callback() {
            @Override public void onDismissed(Snackbar snackbar, int event) {
                if (event == DISMISS_EVENT_SWIPE) mLocationSnackbar.show();
            }
        });
        mEnableLoationSnackbar = Snackbar.make(vSplashContent, R.string.app_needs_gps, Snackbar.LENGTH_INDEFINITE).setAction(R.string.enable_location, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                enableLocation();
            }
        });
        mEnableLoationSnackbar.setCallback(new Snackbar.Callback() {
            @Override public void onDismissed(Snackbar snackbar, int event) {
                if (event == DISMISS_EVENT_SWIPE) mEnableLoationSnackbar.show();
            }
        });
        mLoginSnackbar = Snackbar.make(vSplashContent, R.string.app_needs_login, Snackbar.LENGTH_INDEFINITE).setAction(R.string.snackbar_action_login, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //TODO: createDialog() was here
                createDialog();
            }
        }).setCallback(new Snackbar.Callback() {
            @Override public void onDismissed(Snackbar snackbar, int event) {
                if (event == DISMISS_EVENT_SWIPE) snackbar.show();
            }
        });
        mStartPokeballSnackbar = Snackbar.make(vSplashContent, "You closed the pokeball!", Snackbar.LENGTH_INDEFINITE).setAction("Restart!", new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                restartPokeball();
            }
        }).setCallback(new Snackbar.Callback() {
            @Override public void onDismissed(Snackbar snackbar, int event) {
                if (event == DISMISS_EVENT_SWIPE) snackbar.show();
            }
        });
    }

    public void restartPokeball() {
        GoFoundEvent event = new GoFoundEvent(mGo);
        ChangeBubbleEvent open_event = new ChangeBubbleEvent("open");
        startPokeService();
        startFetchService();
        EventBus.getDefault().postSticky(event);
        EventBus.getDefault().post(open_event);
        vSplashInfo.setText("Restarted the pokeball!");
    }


    public void requestOverlayPermission() {
        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
        startActivityForResult(intent, REQ_CODE_OVERLAY);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch (requestCode) {
            case REQ_CODE_OVERLAY:
                bHasOverlayPermission = true;
                Toast.makeText(this, R.string.permission_overlay_granted, Toast.LENGTH_SHORT).show();
                break;
            case SOUND_REQ:
                Uri uri = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
                if(uri != null) {
                    mSharedPrefs.edit().putString("ringtone", uri.toString()).commit();
                }else {
                    mSharedPrefs.edit().putString("ringtone", "").commit();
                }
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
            mLocationSnackbar.dismiss();
            enableLocation();
        }
    }

    public void enableLocation() {
        activateLocationUpdate();
        if (!isLocationEnabled()) {
            mAskProviders = true;
            LocationUtils.askEnableProviders(this, R.string.gps_enable_text, R.string.gps_enable_positive, R.string.gps_enable_negative);
        } else {
            bLocationEnabled = true;
            mLocationSnackbar.dismiss();
        }
    }

    public boolean isLocationEnabled() {
        if (!LocationUtils.isGpsProviderEnabled(this) && !LocationUtils.isNetworkProviderEnabled(this)) {
            bLocationEnabled = false;
            return false;
        } else {
            bLocationEnabled = true;
            mLocationSnackbar.dismiss();
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
            mLocationTracker = new LocationTracker(this, settings) {
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
            mLocationTracker.startListening();
            mLocationTracker.quickFix();
        } catch (SecurityException e) {
            //showLocationSnackBar("SecurityException");
        }
    }

    public void waitForPermissions() {
        Log.d("PERMISSIONS", "O: " + bHasOverlayPermission + " LP: " + bHasLocationPermission + " L: " + bLocationEnabled);
        vSplashProgress.setVisibility(View.VISIBLE);
        vSplashInfo.setText("/----------Waiting for Permissions--------/");
        if (!bHasOverlayPermission) {
            vSplashInfo.setText("/----------Waiting for Overlay--------/");
            if(!mOverlaySnackbar.isShown()) mOverlaySnackbar.show();
        } else {
            if (!bHasLocationPermission) {
                vSplashInfo.setText("/----------Waiting for LocationPermission--------/");
                mOverlaySnackbar.dismiss();
                if(!mLocationSnackbar.isShown()) mLocationSnackbar.show();
            } else {
                if (!bLocationEnabled) {
                    if(!mAskProviders) enableLocation();
                    vSplashInfo.setText("/----------Waiting for Location--------/");
                    mLocationSnackbar.dismiss();
                    if(!mEnableLoationSnackbar.isShown()) mEnableLoationSnackbar.show();
                }else {
                    Toast.makeText(this, "All Permissions granted!", Toast.LENGTH_SHORT).show();
                    //TODO: createDialog() was here
                    createDialog();
                    vSplashInfo.setText("/----------Waiting for Login--------/");
                    mHandler.removeCallbacks(myRunnable);
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

    public void createCredChooserDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_chooser, null);

        final Button btnChooseGoogle = (Button) dialogView.findViewById(R.id.btnChooseGoogle);
        final Button btnChoosePTC = (Button) dialogView.findViewById(R.id.btnChoosePTC);

        builder.setView(dialogView)
                .setNegativeButton(R.string.ptc, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        //TODO: createDialog() was here
                       createDialog();
                    }
                });

        AlertDialog dialog = builder.create();
        dialog.show();

    }

    public void createDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        //TODO: Add logout button


        View dialogView = getLayoutInflater().inflate(R.layout.dialog_login, null);
        final EditText editUsername = (EditText) dialogView.findViewById(R.id.username);
        final EditText editPassword = (EditText) dialogView.findViewById(R.id.password);


        String savedUsername = mSharedPrefs.getString("username", "");
        String savedPassword = mSharedPrefs.getString("password", "");

        editUsername.setText(savedUsername);
        editPassword.setText(savedPassword);

        TextView textTokenFound = (TextView) dialogView.findViewById(R.id.googleTokenFound);
        if(mSharedPrefs.getString("googleToken", null) != null) {
            textTokenFound.setVisibility(View.VISIBLE);
            textTokenFound.setText(R.string.google_token_found);
        }

        builder.setView(dialogView)
                .setPositiveButton(R.string.google, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        //mUsername = editUsername.getText().toString();
                        //mPassword = editPassword.getText().toString();
                        Toast.makeText(SplashActivity.this, "Google login", Toast.LENGTH_LONG).show();
                        initializeGo(mUsername, mPassword, "google");

                    }
                })
                .setNegativeButton(R.string.ptc, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        Toast.makeText(SplashActivity.this, "PTC login", Toast.LENGTH_LONG).show();
                        mUsername = editUsername.getText().toString();
                        mPassword = editPassword.getText().toString();
                        if(mUsername != "" && mPassword != "") {
                            initializeGo(mUsername, mPassword, "ptc");
                        }else {
                            mLoginSnackbar.show();
                        }
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        mLoginSnackbar.show();
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
        bLocationEnabled = false;
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
        bLocationEnabled = true;
    }

    @Override
    public void onGoAvailable(PokemonGo go) {
        mGo = go;
        Bundle params = new Bundle();
        try {
            params.putString("provider", mGo.getAuthInfo().getProvider());
        } catch (RemoteServerException e) {
        } catch (LoginFailedException e) {
        }
        mFirebaseAnalytics.logEvent("login_done", params);
        GoFoundEvent event = new GoFoundEvent(go);
        ChangeBubbleEvent open_event = new ChangeBubbleEvent("open");
        startPokeService();
        startFetchService();
        EventBus.getDefault().postSticky(event);
        EventBus.getDefault().post(open_event);
        vSplashInfo.setText(R.string.login_successful);
        vSplashProgress.setVisibility(View.GONE);
    }

    @Override
    public void onGoFailed(String reason) {
        Bundle params = new Bundle();
        mFirebaseAnalytics.logEvent("login_failed", params);
        Toast.makeText(this, "Login failed: " + reason, Toast.LENGTH_SHORT).show();
        Snackbar.make(vSplashContent, "Please login! ;)", Snackbar.LENGTH_INDEFINITE).setAction(R.string.snackbar_action_login, new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //TODO: createDialog() was here
                createDialog();
            }
        }).setCallback(new Snackbar.Callback() {
            @Override public void onDismissed(Snackbar snackbar, int event) {
                if (event == DISMISS_EVENT_SWIPE) snackbar.show();
            }
        }).show();
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
        editToken.setEnabled(false);

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

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                //view.evaluateJavascript();
                String title = view.getTitle();
                int start = title.indexOf("Success code="); //for the length of code=
                int end = title.indexOf("&");
                if(start != -1 && end != -1) {
                    String token = title.substring(start+13, end);
                    if(token != null && !token.equals("")) {
                        Toast.makeText(SplashActivity.this, "Token: " + token, Toast.LENGTH_LONG);
                        editToken.setText(token);
                    }
                }
            }
        });

        builder.setView(ll)
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mSharedPrefs.edit().putString("authReq", editToken.getText().toString()).commit();
                        initializeGoByAuth();
                    }
                })
                .setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialogInterface) {
                        Snackbar.make(vSplashContent, "You need to login!", Snackbar.LENGTH_INDEFINITE).setAction(R.string.snackbar_action_login, new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                //TODO: createDialog() was here
                                createDialog();
                            }
                        }).show();
                    }
                });

        if(isPremium()) {

        }


        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private boolean isPremium() {
        return mSharedPrefs.getBoolean("premium", false);
    }

    @Override
    protected void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageEvent(MessageEvent event) {
        if(event.title.equals("pokeball_trashed")) {
            mStartPokeballSnackbar.show();
        }
        Toast.makeText(this, "Service: " + event.title, Toast.LENGTH_SHORT).show();
    }

}
