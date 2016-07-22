package com.benjy3gg.pokeradar;

import android.Manifest;
import android.animation.Animator;
import android.animation.IntEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;

import com.etiennelawlor.discreteslider.library.ui.DiscreteSlider;
import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.protobuf.CodedOutputStream;
import com.google.protobuf.InvalidProtocolBufferException;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.auth.PTCLogin;
import com.pokegoapi.exceptions.LoginFailedException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

import POGOProtos.Data.PokemonDataOuterClass;
import POGOProtos.Enums.PokemonIdOuterClass;
import POGOProtos.Map.Pokemon.WildPokemonOuterClass;
import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass;
import okhttp3.OkHttpClient;

import android.os.StrictMode;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;
import android.widget.ToggleButton;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, LocationListener {

    private GoogleMap mMap;
    PokemonGo go;
    private int OVERLAY_PERMISSION_REQ_CODE = 1234;
    private static final int GPS_PERMISSION_REQ_CODE = 1235;
    String TAG = "PENIS";
    OkHttpClient client;
    RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo auth;

    private LocationManager locationManager;
    private String provider;
    MyTimerTask myTask;
    private Timer myTimer;
    boolean fullscreen = false;
    long lastPressTime = 0;
    RelativeLayout rl;
    RelativeLayout v;
    private WindowManager.LayoutParams params;
    private WindowManager wm;
    private ImageView img;
    private MapFragment mapFragment;
    private EditText editUsername;
    private EditText editPassword;
    private EditText editApikey;
    private Marker mSelectedMarker;
    private Marker mPositionMarker;
    private ValueAnimator vAnimator;
    private Circle vCircle;
    private Polygon vPolygon;
    private SharedPreferences sharedPref;
    private boolean mDoReverse = false;
    public HashMap<String, WildPokemonExt> map = new HashMap<>();
    HashMap<String, Marker> markers = new HashMap<>();
    public List<Boolean> shouldNotify = new ArrayList<>();
    public List<Boolean> shouldShow = new ArrayList<>();
    private Button btn;
    private int spanX;
    private int spanY;
    private int stepY;
    private int stepX;
    private LatLng mCurrentlocation;
    private Handler updateHandler;
    private int updateDelay;
    private boolean firstLocationUpdate = true;
    private RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo auth_l;
    private Button btnToken;
    private DiscreteSlider sliderWidth;
    private DiscreteSlider sliderHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        spanX = 4;
        spanY = 3;
        stepX = 1;
        stepY = 1;

        allowOverlay();
        requestGpsPermission();
        setupLayout();
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
    }

    public void setupLayout() {
        sharedPref = getSharedPreferences("credentials", Context.MODE_PRIVATE);

        String loadedAuth = sharedPref.getString("auth", null);
        if(loadedAuth != null) {
            try {
                auth_l = RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo.parseFrom(Base64.decode(loadedAuth, Base64.DEFAULT));
                btnToken = (Button) findViewById(R.id.loginToken);
                if(auth_l != null) {
                    btnToken.setEnabled(true);
                    btnToken.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            initializeGo(null, null, auth_l);
                        }
                    });
                }else {
                    btnToken.setEnabled(false);
                }
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }

        sliderWidth = (DiscreteSlider)findViewById(R.id.sliderWidth);
        sliderWidth.setTickMarkCount(6);
        sliderWidth.setOnDiscreteSliderChangeListener(new DiscreteSlider.OnDiscreteSliderChangeListener() {
            @Override
            public void onPositionChanged(int position) {
                spanX = position+1;
            }
        });
        sliderHeight = (DiscreteSlider)findViewById(R.id.sliderHeight);
        sliderHeight.setTickMarkCount(6);
        sliderHeight.setOnDiscreteSliderChangeListener(new DiscreteSlider.OnDiscreteSliderChangeListener() {
            @Override
            public void onPositionChanged(int position) {
                spanY = position+1;
            }
        });

        editUsername = (EditText) findViewById(R.id.editUsername);
        editPassword = (EditText) findViewById(R.id.editPassword);
        editApikey = (EditText) findViewById(R.id.editApi);
        editApikey.setVisibility(View.INVISIBLE);
        final String username = sharedPref.getString("username", "");
        final String password = sharedPref.getString("password", "");
        //final String apikey = sharedPref.getString("apikey", "");
        if (!username.equals("") && !password.equals("")) {
            editUsername.setText(username);
            editPassword.setText(password);
            //editApikey.setText(apikey);
        }

        for (int i = 1; i <= 151; i++) {
            shouldNotify.add(sharedPref.getBoolean("notify_" + i, true));
            shouldShow.add(sharedPref.getBoolean("show_" + i, true));
        }

        btn = (Button) findViewById(R.id.login);
        final ToggleButton reverse = (ToggleButton) findViewById(R.id.reverse);
        reverse.setVisibility(View.INVISIBLE);
        reverse.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mDoReverse = b;
                reverse.setChecked(b);
                reverse.setText(reverse.isChecked() ? reverse.getTextOn() : reverse.getTextOff());
            }
        });

        vAnimator = new ValueAnimator();
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String username_e = editUsername.getText().toString();
                String password_e = editPassword.getText().toString();
                if (!username_e.equals("") || !password_e.equals("")) {
                    initializeGo(username_e, password_e, null);
                } else {
                    Toast.makeText(MapsActivity.this, "Wrong credentials", Toast.LENGTH_SHORT).show();
                }
            }
        });

        Button settings = (Button) findViewById(R.id.settings);
        settings.setVisibility(View.INVISIBLE);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MapsActivity.this, ListActivity.class);
                startActivity(intent);
            }
        });
    }

    public void requestGpsPermission() {
        // Here, thisActivity is the current activity
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_CONTACTS)) {


            } else {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        GPS_PERMISSION_REQ_CODE);
            }
        } else {
            initializeGPS();
        }
    }

    public void setupTimers(LatLng loc) {
        myTask = new MyTimerTask(loc);
        myTimer = new Timer();

        myTimer.schedule(myTask, 0, 45 * 1000);

        updateHandler = new Handler();
        updateDelay = 1000; //milliseconds

        updateHandler.postDelayed(new Runnable() {
            public void run() {
                Iterator it = markers.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Marker> pair = (Map.Entry) it.next();
                    Marker m = pair.getValue();
                    long now = System.currentTimeMillis();
                    WildPokemonExt p = map.get(pair.getKey());
                    long till = p.timestampHidden;
                    if (till < now) {
                        m.remove();
                        it.remove();
                    } else {
                        int difference = (int) (till - now);
                        difference = difference / 1000;
                        int minutes = (int) Math.floor(difference / 60);

                        int seconds = difference - minutes * 60;
                        m.setSnippet(String.format("%02d:%02d", minutes, seconds));
                        //Log.i(m.getTitle(), m.getSnippet());

                    }
                    //it.remove(); // avoids a ConcurrentModificationException
                }
                if (mSelectedMarker != null) {
                    mSelectedMarker.showInfoWindow();
                }

                PolygonOptions options = calculateBoundingBox(mCurrentlocation);
                //TODO: maybe update points instead of removing the rectangle
                if (vPolygon != null) {
                    vPolygon.remove();
                }
                vPolygon = mMap.addPolygon(options);

                updateHandler.postDelayed(this, updateDelay);
            }
        }, updateDelay);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case GPS_PERMISSION_REQ_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // permission was granted, yay! Do the
                    // contacts-related task you need to do.
                    initializeGPS();
                } else {
                    Toast.makeText(this, "No GPS Permission granted", Toast.LENGTH_SHORT).show();
                    // permission denied, boo! Disable the
                    // functionality that depends on this permission.
                }
                return;
            }

            // other 'case' lines to check for other
            // permissions this app might request
        }
    }

    public void initializeFragment() {
        rl = new RelativeLayout(this);
        v = (RelativeLayout) getLayoutInflater().inflate(R.layout.activity_maps, rl);
        img = new ImageView(this);
        img.setImageResource(R.drawable.pokeball);
        RelativeLayout.LayoutParams p = new RelativeLayout.LayoutParams(150, 150);
        //p.addRule(RelativeLayout.BELOW, R.id.map);
        img.setLayoutParams(p);
        img.animate().alpha(1).setDuration(10).start();
        img.requestLayout();
        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        //getFragmentManager().beginTransaction().detach(mapFragment).commit();
        mapFragment.getMapAsync(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapFragment != null) {
            mapFragment.onResume();
        }
        Log.d(TAG, "paused");
        //moveTaskToBack(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    public void showPopup() {
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                Math.round(metrics.heightPixels / 2),
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        wm.addView(rl, params);
        params.width = 150;
        params.height = 150;
        wm.addView(img, params);

    }

    public void allowOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
            }
        }
    }

    public void initializeGPS() {
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean enabled = locationManager
                .isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!enabled) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        try {
            locationManager.requestLocationUpdates("gps", 1000, 1, this);
            Location location = locationManager.getLastKnownLocation("gps");
            if (location != null) {
                onLocationChanged(location);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SECURITY EXCEPTION: " + e.getMessage());
        }

        // Initialize the location fields

    }

    public void initializeGo(String username, String password, RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo auth) {
        client = new OkHttpClient();
        try {
            if(auth != null) {
                go = new PokemonGo(auth, client);
            }else {
                PTCLogin login = new PTCLogin(client);
                auth = login.login(username, password);
                go = new PokemonGo(auth, client);
            }
            go.getMap().setUseCache(false);
            //go.setLocation(48.6256072,9.2426758, 0.0);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("username", username);
            editor.putString("password", password);
            byte[] array = auth.toByteArray();
            String saveThis = Base64.encodeToString(array, Base64.DEFAULT);
            editor.putString("auth", saveThis);
            editor.apply();

            editUsername.setVisibility(View.INVISIBLE);
            editPassword.setVisibility(View.INVISIBLE);
            btn.setVisibility(View.INVISIBLE);
            btnToken.setVisibility(View.INVISIBLE);

            initializeFragment();
            } catch (LoginFailedException e) {
            // failed to login, invalid credentials, auth issue or server issue.
                Toast.makeText(this, "Login Error, probably down!", Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Some other error! " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
    }

    public PolygonOptions calculateBoundingBox(LatLng location) {

        //calculate boundingBox
        //topLeft
        double lat = location.latitude + (-1 * spanY * 0.001);
        double new_x = (-1 * spanX * 0.001) / Math.cos(location.longitude);
        double lng = location.longitude + new_x;
        LatLng topLeft = new LatLng(lat, lng);

        //topRight
        lat = location.latitude + (-1 * spanY * 0.001);
        new_x = (spanX * 0.001) / Math.cos(location.longitude);
        lng = location.longitude + new_x;
        LatLng topRight = new LatLng(lat, lng);

        //bottomLeft
        lat = location.latitude + spanY * 0.001;
        new_x = (-1 * spanX * 0.001) / Math.cos(location.longitude);
        lng = location.longitude + new_x;
        LatLng bottomLeft = new LatLng(lat, lng);

        //bottomRight
        lat = location.latitude + spanY * 0.001;
        new_x = (spanX * 0.001) / Math.cos(location.longitude);
        lng = location.longitude + new_x;
        LatLng bottomRight = new LatLng(lat, lng);

        return new PolygonOptions().add(topLeft).add(topRight).add(bottomRight).add(bottomLeft).add(topLeft).fillColor(Color.argb(8, 29, 132, 181)).strokeColor(Color.argb(32, 29, 132, 181));
    }

    @Override
    public void onLocationChanged(Location location) {
        LatLng loc = new LatLng(location.getLatitude(), location.getLongitude());
        mCurrentlocation = loc;

        if (myTask != null) {
            myTask.updateLocation(mCurrentlocation);
        }
        if (mMap != null) {
            if (firstLocationUpdate) {
                firstLocationUpdate = false;
            }

            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrentlocation, 15));


            if (mPositionMarker == null) {
                mPositionMarker = mMap.addMarker(new MarkerOptions().position(mCurrentlocation));
            }
            mPositionMarker.setPosition(mCurrentlocation);
        }
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
    }

    @Override
    public void onProviderEnabled(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onProviderDisabled(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     **/
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrentlocation, 15));
        if (mPositionMarker == null) {
            mPositionMarker = mMap.addMarker(new MarkerOptions().position(mCurrentlocation).alpha(0.5f));
        }
        mPositionMarker.setPosition(mCurrentlocation);

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                mSelectedMarker = null;
            }
        });

        /*
        img.setOnTouchListener(new View.OnTouchListener() {
            float dX, dY;
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getAction()) {

                    case MotionEvent.ACTION_DOWN:

                        dX = view.getX() - event.getRawX();
                        dY = view.getY() - event.getRawY();
                        break;

                    case MotionEvent.ACTION_MOVE:

                        view.animate()
                                .x(event.getRawX() + dX)
                                .y(event.getRawY() + dY)
                                .setDuration(0)
                                .start();
                        break;
                    default:
                        return true;
                }
                return true;
            }
        });
        */
        img.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                MapsActivity.this.finish();
                return true;
            }
        });
        img.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!fullscreen) {

                    img.animate().alpha(1).setDuration(500).start();
                    //rl.animate().alpha(1).translationX(0).setDuration(500).start();
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrentlocation, 15));
                    mapFragment.getView().animate().translationX(0).setDuration(500).setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {
                            mapFragment.getView().setVisibility(View.VISIBLE);
                            DisplayMetrics metrics = new DisplayMetrics();
                            getWindowManager().getDefaultDisplay().getMetrics(metrics);
                            params.width = WindowManager.LayoutParams.MATCH_PARENT;
                            params.height = Math.round(metrics.heightPixels / 2);
                            wm.updateViewLayout(rl, params);
                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            fullscreen = true;

                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animator) {

                        }
                    }).start();

                } else {
                    img.animate().alpha(1).setDuration(500).start();
                    DisplayMetrics metrics = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getMetrics(metrics);
                    //rl.animate().alpha(1).translationX(-metrics.widthPixels).setDuration(500).start();
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrentlocation, 15));
                    mapFragment.getView().animate().translationX(-metrics.widthPixels).setDuration(500).setListener(new Animator.AnimatorListener() {
                        @Override
                        public void onAnimationStart(Animator animator) {

                        }

                        @Override
                        public void onAnimationEnd(Animator animator) {
                            fullscreen = false;
                            params.width = 1;
                            params.height = 1;
                            wm.updateViewLayout(rl, params);
                            mapFragment.getView().setVisibility(View.INVISIBLE);
                        }

                        @Override
                        public void onAnimationCancel(Animator animator) {

                        }

                        @Override
                        public void onAnimationRepeat(Animator animator) {

                        }
                    }).start();

                }

            }
        });
        /*
        mMap.setOnMapLongClickListener(new GoogleMap.OnMapLongClickListener() {
            @Override
            public void onMapLongClick(LatLng latLng) {
                Toast.makeText(MapsActivity.this, "LongClick", Toast.LENGTH_SHORT).show();
                Log.d("PENIS", "LongClick");
                if(!fullscreen) {
                    DisplayMetrics metrics = new DisplayMetrics();
                    getWindowManager().getDefaultDisplay().getMetrics(metrics);
                    params.width = WindowManager.LayoutParams.MATCH_PARENT;
                    params.height = Math.round(metrics.heightPixels/2);
                    fullscreen = true;
                    mapFragment.getView().animate().alpha(1).setDuration(500).start();
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrentlocation, 15));
                    rl.requestLayout();
                }else {
                    params.width = 150;
                    params.height = 150;
                    fullscreen = false;
                    mapFragment.getView().animate().alpha(1).setDuration(500).start();
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrentlocation, 15));
                    rl.requestLayout();
                }
                wm.updateViewLayout(rl, params);
            }
        });
        */

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                if (mDoReverse) {
                    try {
                        Geocoder geoCoder = new Geocoder(MapsActivity.this);
                        List<Address> matches = geoCoder.getFromLocation(marker.getPosition().latitude, marker.getPosition().longitude, 1);
                        Address bestMatch = (matches.isEmpty() ? null : matches.get(0));
                        Toast.makeText(MapsActivity.this, bestMatch.getAddressLine(0), Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting reverse location");
                    }
                    return false;
                }
                mSelectedMarker = marker;
                return false;
            }
        });

        showPopup();
        setupTimers(mCurrentlocation);

    }

    public int getResourseId(String pVariableName, String pType) {
        try {
            return getResources().getIdentifier(pVariableName, pType, getPackageName());
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    class MyTimerTask extends TimerTask {
        MyAsyncTask atask;
        LatLng loc;

        public MyTimerTask(LatLng loc) {
            this.loc = loc;
        }

        public void updateLocation(LatLng loc) {
            this.loc = loc;
        }

        final class MyAsyncTask extends AsyncTask<LatLng, WildPokemonOuterClass.WildPokemon, List<WildPokemonOuterClass.WildPokemon>> {

            @Override
            protected void onPreExecute() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //mMap.clear();
                    }
                });

            }

            @Override
            protected List<WildPokemonOuterClass.WildPokemon> doInBackground(LatLng... params) {
                final List<MarkerOptions> list = new ArrayList<>();
                //final LatLng loc = params[0];
                LatLng search_loc;
                //y + 1, x +2 seems good
                //maybe make the search algorithm smarter -> search first where last poke expired
                //active -> change grid to spiral or atleast change the order
                //calculation in for-loop allows movement of the user during scan!!!
                /*
                List<LatLng> loc_list = new ArrayList<>();
                for (int y = -5; y <= 5; y += 1) {
                    for (int x = -6; x <= 6; x += 2) {
                        double random = new Random().nextDouble() * 0.000175;
                        double lat = loc.latitude + y * 0.001 + random;
                        double new_x = (x * 0.001 + random) / Math.cos(loc.longitude);
                        double lng = loc.longitude + new_x;
                        loc_list.add(new LatLng(lat, lng));
                    }
                }

                Collections.sort(loc_list, new Comparator<LatLng>() {
                    @Override
                    public int compare(LatLng o1, LatLng o2) {
                        float[] results = new float[1];
                        Location.distanceBetween(loc.latitude, loc.longitude,
                                o1.latitude, o1.longitude, results);
                        float d1 = results[0];
                        Location.distanceBetween(loc.latitude, loc.longitude,
                                o2.latitude, o2.longitude, results);
                        float d2 = results[0];

                        return Float.compare(d1, d2);
                    }
                });
                */

                for (int y = -spanY; y <= spanY; y += stepY) {
                    for (int x = -spanX; x <= spanX; x += stepX) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        double random = new Random().nextDouble() * 0.001;
                        random = 0.0;
                        double lat = loc.latitude + y * 0.001 + random;
                        double new_x = (x * 0.001 + random) / Math.cos(loc.longitude);
                        double lng = loc.longitude + new_x;
                        search_loc = new LatLng(lat, lng);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        //mMap.addMarker(new MarkerOptions().position(new LatLng(search_loc.latitude, search_loc.longitude)));
                        go.setLocation(search_loc.latitude, search_loc.longitude, 0);
                        //final LatLng locf = new LatLng(search_loc.latitude, search_loc.longitude);
                        final LatLng circle_loc = search_loc;

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                //mMap.addMarker(new MarkerOptions().position(new LatLng(locf.latitude, locf.longitude)));
                                if(vCircle != null) {
                                    vCircle.remove();
                                    vAnimator.cancel();
                                }
                                vCircle = mMap.addCircle(new CircleOptions().center(circle_loc).strokeColor(Color.argb(16, 29, 132, 181)).radius(100));;
                                vAnimator = new ValueAnimator();
                                vAnimator.setRepeatCount(ValueAnimator.INFINITE);
                                vAnimator.setRepeatMode(ValueAnimator.RESTART);  /* PULSE */
                                vAnimator.setIntValues(0, 100);
                                vAnimator.setDuration(500);
                                vAnimator.setEvaluator(new IntEvaluator());
                                vAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                                vAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                    @Override
                                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                        float animatedFraction = valueAnimator.getAnimatedFraction();
                                        // Log.e("", "" + animatedFraction);
                                        vCircle.setRadius(animatedFraction * 100);
                                    }
                                });
                                vAnimator.start();
                            }
                        });


                        try {
                            Collection<WildPokemonOuterClass.WildPokemon> poke = go.getMap().getMapObjects().getWildPokemons();
                            //TODO: Add notification for CatchablePokemon?
                            for (WildPokemonOuterClass.WildPokemon p : poke) {
                                if (map.get(String.valueOf(p.getEncounterId())) == null) {
                                    PokemonDataOuterClass.PokemonData f_poke = p.getPokemonData();
                                    String name = PokemonIdOuterClass.PokemonId.valueOf(p.getPokemonData().getPokemonIdValue()).name();
                                    long till = System.currentTimeMillis() + p.getTimeTillHiddenMs();
                                    long now = System.currentTimeMillis();
                                    if (till > now) {
                                        if (shouldShow.get(p.getPokemonData().getPokemonIdValue())) {
                                            publishProgress(p);
                                            map.put(String.valueOf(p.getEncounterId()), new WildPokemonExt(p, System.currentTimeMillis() + p.getTimeTillHiddenMs()));
                                        } else {
                                            Log.i(TAG, "Hidden: " + name);
                                        }

                                    }

                                }

                            }
                        } catch (Exception e) {
                            if (e.getMessage().contains("502")) {
                                Log.d(TAG, "Error:" + e);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MapsActivity.this, "Servers are probably down", Toast.LENGTH_SHORT).show();
                                    }
                                });

                                try {
                                    Thread.sleep(10000);
                                } catch (InterruptedException e1) {
                                    e1.printStackTrace();
                                }
                            }

                        }

                    }
                }
                return new ArrayList<>();
            }


            @Override
            protected void onProgressUpdate(WildPokemonOuterClass.WildPokemon... values) {
                for (WildPokemonOuterClass.WildPokemon p : values) {
                    String name = PokemonIdOuterClass.PokemonId.valueOf(p.getPokemonData().getPokemonIdValue()).name();
                    MarkerOptions m = new MarkerOptions()
                            .position(new LatLng(p.getLatitude(), p.getLongitude()))
                            .title(name)
                            .icon(BitmapDescriptorFactory.fromResource(getResourseId("prefix_" + p.getPokemonData().getPokemonIdValue(), "drawable")));
                    Marker mh = mMap.addMarker(m);
                    markers.put(String.valueOf(p.getEncounterId()), mh);
                }
            }

            /**
             * Update list ui after process finished.
             */
            protected void onPostExecute(List<MarkerOptions> list) {
                //mMap.clear();
                //for (MarkerOptions m : list) {
                //    mMap.addMarker(m);
                //}
            }
        }

        public void run() {
            atask = new MyAsyncTask();
            atask.execute(loc);
        }
    }


}
