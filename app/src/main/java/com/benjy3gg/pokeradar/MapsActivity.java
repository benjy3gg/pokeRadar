package com.benjy3gg.pokeradar;

import android.animation.IntEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.View;

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
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.auth.PTCLogin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;

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

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, LocationListener{

    private GoogleMap mMap;
    PokemonGo go;
    private static int OVERLAY_PERMISSION_REQ_CODE = 1234;
    String TAG = "PENIS";
    OkHttpClient client;
    RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo auth;
    List<Marker> markers = new ArrayList<>();
    private LocationManager locationManager;
    private String provider;
    MyTimerTask myTask;
    private Timer myTimer;
    boolean fullscreen = false;
    long lastPressTime = 0;
    RelativeLayout rl;
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
    private SharedPreferences sharedPref;
    private boolean mDoReverse = false;
    public HashMap<String, MarkerOptions> map = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);

        sharedPref = getSharedPreferences("credentials", Context.MODE_PRIVATE);

        editUsername = (EditText)findViewById(R.id.editUsername);
        editPassword = (EditText)findViewById(R.id.editPassword);
        //editApikey = (EditText)findViewById(R.id.editApi);
        final String username = sharedPref.getString("username", "");
        final String password = sharedPref.getString("password", "");
        //final String apikey = sharedPref.getString("apikey", "");
        if(!username.equals("") && !password.equals("")) {
            editUsername.setText(username);
            editPassword.setText(password);
            //editApikey.setText(apikey);
        }

        Button btn = (Button)findViewById(R.id.login);
        final ToggleButton reverse = (ToggleButton)findViewById(R.id.reverse);

        reverse.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                mDoReverse = b;
                reverse.setChecked(b);
                reverse.setText(reverse.isChecked() ? reverse.getTextOn() : reverse.getTextOff());
            }
        });

        allowOverlay();
        vAnimator = new ValueAnimator();
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String username_e = editUsername.getText().toString();
                String password_e = editPassword.getText().toString();
                if(!username_e.equals("") || !password_e.equals("")) {
                    initializeGo(username_e, password_e);
                }else {
                    Toast.makeText(MapsActivity.this, "Wrong credentials", Toast.LENGTH_SHORT).show();
                }
            }
        });

        Button settings = (Button)findViewById(R.id.settings);
        settings.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MapsActivity.this, ListActivity.class);
                startActivity(intent);
            }
        });



        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
    }

    public void initializeFragment() {
        rl = new RelativeLayout(this);

        RelativeLayout v = (RelativeLayout) getLayoutInflater().inflate(R.layout.activity_maps, rl);
        img = new ImageView(this);
        img.setImageResource(R.drawable.pokeball);
        img.setLayoutParams(new RelativeLayout.LayoutParams(150, 150));
        img.animate().alpha(0).setDuration(10).start();
        img.requestLayout();
        rl.addView(img);
        mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        //getFragmentManager().beginTransaction().detach(mapFragment).commit();
        mapFragment.getMapAsync(this);

    }

    @Override
    protected void onPause() {
        super.onPause();
        if(mapFragment != null) {
            mapFragment.onResume();
        }
        Log.d(TAG, "paused");
        //moveTaskToBack(true);
    }

    public void showPopup() {
        params = new WindowManager.LayoutParams(
                150,
                150,
                WindowManager.LayoutParams.TYPE_SYSTEM_ALERT,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.RIGHT | Gravity.TOP;
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = Math.round(metrics.heightPixels/2);
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        wm.addView(rl, params);
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
            Location location = locationManager.getLastKnownLocation("gps");
            locationManager.requestLocationUpdates("gps", 15 * 1000, 0, this);
            if (location != null) {
                onLocationChanged(location);
            }
        }catch (SecurityException e) {
            Log.e(TAG, "SECURITY EXCEPTION: " + e.getMessage());
        }

        // Initialize the location fields

    }

    public void initializeGo(String username, String password) {
        client = new OkHttpClient();
        try {
            PTCLogin login = new PTCLogin(client);
            auth = login.login(username, password);
            go = new PokemonGo(auth, client);
            go.getMap().setUseCache(false);
            //go.setLocation(48.6256072,9.2426758, 0.0);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("username", username);
            editor.putString("password", password);
            editor.apply();

            initializeFragment();
        } catch (Exception e) {
            // failed to login, invalid credentials, auth issue or server issue.
            Toast.makeText(this, "Login Error, probably down!", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        LatLng loc = new LatLng(location.getLatitude(), location.getLongitude());
        if(myTask != null) {
            myTask.updateLocation(loc);
        }
        if(mMap != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 15));
            if(vCircle == null) {
                vCircle = mMap.addCircle(new CircleOptions().center(loc).strokeColor(Color.argb(32, 29, 132, 181)).radius(50));
            }

            if(mPositionMarker == null) {
                mPositionMarker = mMap.addMarker(new MarkerOptions().position(loc));
            }
            mPositionMarker.setPosition(loc);
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

    class MyTimerTask extends TimerTask {
        MyAsyncTask atask;
        LatLng loc;

        public MyTimerTask(LatLng loc) {
            this.loc = loc;
        }

        public void updateLocation(LatLng loc) {
            this.loc = loc;
        }

        final class MyAsyncTask extends AsyncTask<LatLng, MarkerOptions, List<MarkerOptions>> {

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
            protected List<MarkerOptions> doInBackground(LatLng... params) {
                final List<MarkerOptions> list = new ArrayList<>();
                final LatLng loc = params[0];
                //y + 1, x +2 seems good
                //maybe make the search algorithm smarter -> search first where last poke expired
                //active -> change grid to spiral or atleast change the order
                //calculation in for-loop allows movement of the user during scan!!!
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

                for(LatLng search_loc : loc_list) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    //mMap.addMarker(new MarkerOptions().position(new LatLng(search_loc.latitude, search_loc.longitude)));
                    go.setLocation(search_loc.latitude, search_loc.longitude, 0);
                    publishProgress(new MarkerOptions().position(new LatLng(search_loc.latitude, search_loc.longitude)));
                    try {
                        Collection<WildPokemonOuterClass.WildPokemon> poke = go.getMap().getMapObjects().getWildPokemons();
                        //TODO: Add notification for CatchablePokemon?
                        for (WildPokemonOuterClass.WildPokemon p : poke) {
                            String name = PokemonIdOuterClass.PokemonId.valueOf(p.getPokemonData().getPokemonIdValue()).name();
                            MarkerOptions m = new MarkerOptions()
                                    .position(new LatLng(p.getLatitude(), p.getLongitude()))
                                    .title(name + ", bis: " + p.getTimeTillHiddenMs()/1000)
                                    .icon(BitmapDescriptorFactory.fromResource(getResourseId("prefix_" + p.getPokemonData().getPokemonIdValue(), "drawable")))
                                    .snippet(""+(System.currentTimeMillis()+p.getTimeTillHiddenMs()));
                            if(map.get(String.valueOf(p.getEncounterId())) == null) {
                                long now = System.currentTimeMillis();
                                if(Long.valueOf(m.getSnippet()) > now) {
                                    publishProgress(m);
                                }

                            }
                            map.put(String.valueOf(p.getEncounterId()), m);
                        }
                    } catch (Exception e) {
                        if(e.getMessage().contains("502")) {
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

                return list;
            };

            @Override
            protected void onProgressUpdate(MarkerOptions... values) {
                for (MarkerOptions m : values) {
                    if(m.getSnippet() != null && m.getSnippet() != "") {
                        Marker mh = mMap.addMarker(m);
                        markers.add(mh);
                    }
                    vCircle.setCenter(m.getPosition());

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

    public void setup(RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo auth) {

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

        /*
        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                Toast.makeText(MapsActivity.this, "Click", Toast.LENGTH_SHORT).show();
                long pressTime = System.currentTimeMillis();
                // If double click...
                if (pressTime - lastPressTime <= 300) {
                    Toast.makeText(MapsActivity.this, "DoubleClick", Toast.LENGTH_SHORT).show();
                    Log.d("PENIS", "DoubleClick");
                    if(!fullscreen) {
                        DisplayMetrics metrics = new DisplayMetrics();
                        getWindowManager().getDefaultDisplay().getMetrics(metrics);
                        params.width = WindowManager.LayoutParams.MATCH_PARENT;
                        params.height = Math.round(metrics.heightPixels/2);
                        fullscreen = true;
                    }else {
                        params.width = 150;
                        params.height = 150;
                        fullscreen = false;
                    }
                    wm.updateViewLayout(rl, params);
                }
            }

        });*/
        showPopup();
        initializeGPS();
        LatLng loc = new LatLng(locationManager.getLastKnownLocation("gps").getLatitude(), locationManager.getLastKnownLocation("gps").getLongitude());
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(loc, 15));

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                mSelectedMarker = null;
            }
        });

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
                    img.animate().alpha(0).setDuration(500).start();
                    mapFragment.getView().animate().alpha(1).setDuration(500).start();
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
                }else {
                    params.width = 150;
                    params.height = 150;
                    fullscreen = false;
                    img.animate().alpha(1).setDuration(500).start();
                    mapFragment.getView().animate().alpha(0).setDuration(500).start();
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15));
                }
                wm.updateViewLayout(rl, params);
            }
        });
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

        myTask = new MyTimerTask(loc);
        myTimer = new Timer();

        myTimer.schedule(myTask, 0, 60*1000);

        final Handler h = new Handler();
        final int delay = 1000; //milliseconds

        h.postDelayed(new Runnable(){
            public void run(){

                Iterator<Marker> i = markers.iterator();
                while (i.hasNext()) {
                    Marker m = i.next(); // must be called before you can call i.remove()

                    long now = System.currentTimeMillis();
                    if(Long.valueOf(m.getSnippet()) < now) {
                        m.remove();
                        Log.d(TAG, "before markers: " + markers.size());
                        i.remove();
                        Log.d(TAG, "after markers: " + markers.size());
                    }else {
                        int difference = (int)(Long.valueOf(m.getSnippet()) - now);
                        difference = difference / 1000;
                        int minutes = (int) Math.floor(difference / 60);

                        int seconds = difference - minutes * 60;
                        m.setTitle( String.format("%02d:%02d", minutes, seconds));
                    }
                }
                /*
                Log.d(TAG, "markers: " + markers.size());
                for(Marker m : markers) {
                    long now = System.currentTimeMillis();
                    if(Long.valueOf(m.getSnippet()) < now) {
                        m.remove();
                    }else {
                        int difference = (int)(Long.valueOf(m.getSnippet()) - now);
                        difference = difference / 1000;
                        int minutes = (int) Math.floor(difference / 60);

                        int seconds = difference - minutes * 60;
                        m.setTitle( String.format("%02d:%02d", minutes, seconds));
                    }
                }
                */
                if(mSelectedMarker != null) {
                    mSelectedMarker.showInfoWindow();
                }

                /*
                vAnimator.cancel();
                vAnimator.setRepeatCount(ValueAnimator.INFINITE);
                vAnimator.setRepeatMode(ValueAnimator.RESTART);
                vAnimator.setIntValues(0, 100);
                vAnimator.setDuration(500);
                vAnimator.setEvaluator(new IntEvaluator());
                vAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
                vAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        float animatedFraction = valueAnimator.getAnimatedFraction();
                        // Log.e("", "" + animatedFraction);
                        vCircle.setRadius(animatedFraction * 50);
                    }
                });
                vAnimator.start();
                */
                h.postDelayed(this, delay);
            }
        }, delay);

    }

    public int getResourseId(String pVariableName, String pType)
    {
        try {
            return getResources().getIdentifier(pVariableName, pType, getPackageName());
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }


}
