package com.benjy3gg.pokeradar;

import android.animation.Animator;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.location.Address;
import android.location.Geocoder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;

import net.rehacktive.waspdb.WaspDb;
import net.rehacktive.waspdb.WaspFactory;
import net.rehacktive.waspdb.WaspHash;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Timer;

import POGOProtos.Enums.PokemonIdOuterClass;

public class PokeService extends IntentService implements OnMapReadyCallback {
    private WindowManager windowManager;
    private GoogleMap mMap;
    private MapView mapView;
    private RelativeLayout v;
    private Resources resources;
    private static String TAG = "FETCHSERVICE";
    private WaspHash db_pokemons;
    private Handler updateHandler;
    private int updateDelay;
    HashMap<String, Marker> markers = new HashMap<>();
    HashMap<String, PokemonSimple> pokemons = new HashMap<>();
    private boolean mFullscreen = false;
    private LatLng mCurrentLocation;
    private Marker mLocationMarker;
    private Marker mSelectedMarker;
    private Polygon mPolygon;
    private SharedPreferences sharedPref;
    private int spanX;
    private int spanY;
    private int stepY;
    private int stepX;

    @SuppressWarnings("deprecation")

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPref = getSharedPreferences("credentials", Context.MODE_PRIVATE);
        getSetLastKnownLocation();
        initializeDatabase();
        Log.d(Utils.LogTag, "ChatHeadService.onCreate()");
        LayoutInflater li = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        v = (RelativeLayout) li.inflate(R.layout.activity_maps, null);
        mapView = (MapView) v.findViewById(R.id.map);
        mapView.onCreate(null);
        mapView.getMapAsync(this);

        spanX = 4;
        spanY = 3;
        stepX = 1;
        stepY = 1;
    }

    public void getSetLastKnownLocation() {
        mCurrentLocation = new LatLng(sharedPref.getFloat("last_lat", 0.0f), sharedPref.getFloat("last_lng", 0.0f));
    }

    public void setupTimers() {

        updateHandler = new Handler();
        updateDelay = 1000; //milliseconds

        updateHandler.postDelayed(new Runnable() {
            public void run() {

                Iterator it = markers.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Marker> pair = (Map.Entry) it.next();
                    Marker m = pair.getValue();
                    long now = System.currentTimeMillis();
                    synchronized (pokemons) {
                        PokemonSimple p = pokemons.get(pair.getKey());

                        if(p != null) {
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
                        }
                    }
                    //it.remove(); // avoids a ConcurrentModificationException
                }
                if (mSelectedMarker != null) {
                    mSelectedMarker.showInfoWindow();
                }


                updateHandler.postDelayed(this, updateDelay);
            }
        }, updateDelay);
    }

    public int getResourceId(String pVariableName, String pType) {
        try {
            return getResources().getIdentifier(pVariableName, pType, getPackageName());
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

    //TODO: get items from database here on first load
    public void initializeDatabase() {
        /*
        String path = getFilesDir().getPath();
        String databaseName = "myDb";
        String password = "passw0rd";

        WaspDb db = WaspFactory.openOrCreateDatabase(path,databaseName,password);
        db_pokemons = db.openOrCreateHash("pokemons");
        */
    }

    public PokeService() {
        super("PokeService");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        switch (intent.getStringExtra("type")) {
            case "status":
                Toast.makeText(this, intent.getStringExtra("status"), Toast.LENGTH_SHORT).show();
                break;
            case "new_pokemon":
                Bundle getBundle = null;
                getBundle = intent.getExtras();
                //TODO: make PokemonSimple Serializable/Parcelable
                String encounterid = getBundle.getString("encounterid", null);
                Long till = getBundle.getLong("timestampHidden", -0);
                Double latitude = getBundle.getDouble("latitude", -0.0);
                Double longitude = getBundle.getDouble ("longitude", -0.0);
                int pokemonid = getBundle.getInt("pokemonid", -1);
                String name = getBundle.getString("name",  "");
                PokemonSimple p_simple = new PokemonSimple(till, encounterid, latitude,longitude, pokemonid, name);
                pokemons.put(encounterid, p_simple);
                addPokemonMarker(encounterid);
                break;
            case "bubble":
                Toast.makeText(this, intent.getStringExtra("bubble"), Toast.LENGTH_SHORT).show();
                toggleView();
                break;
            case "location":
                LatLng new_loc = new LatLng(intent.getDoubleExtra("lat", mCurrentLocation.latitude), intent.getDoubleExtra("lng", mCurrentLocation.longitude));
                setNewLocation(new_loc);
                break;
        }
        return START_NOT_STICKY;
    }

    private void addPokemonMarker(String encounterid) {
        PokemonSimple poke = pokemons.get(encounterid);
        if(poke != null) {
            String name = poke.name;
            Marker m = mMap.addMarker(new MarkerOptions()
                    .position(new LatLng(poke.latitude, poke.longitude))
                    .title(name)
                    .icon(BitmapDescriptorFactory.fromResource(getResourceId("prefix_" + poke.pokemonid, "drawable"))));
            markers.put(encounterid, m);
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

    private void setNewLocation(LatLng new_loc) {
        mCurrentLocation = new_loc;
        if(mMap != null) {
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrentLocation, 15));
            if(mLocationMarker != null) {
                mLocationMarker.remove();
            }
            mMap.addMarker(new MarkerOptions().position(mCurrentLocation));
            updateBoundingRect();
        }
    }

    private void updateBoundingRect() {
        if(mCurrentLocation != null && mMap != null) {
            PolygonOptions polygonOptions = calculateBoundingBox(mCurrentLocation);

            if(mPolygon != null) {
                mPolygon.remove();
            }

            mMap.addPolygon(polygonOptions);
        }
    }

    public void setupView() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        WindowManager.LayoutParams paramMap = new WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        paramMap.gravity = Gravity.TOP | Gravity.LEFT;

        v.setLayoutParams(paramMap);
        windowManager.addView(v, paramMap);
    }

    public void toggleView() {

        final WindowManager.LayoutParams paramMap = new WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        paramMap.gravity = Gravity.TOP | Gravity.LEFT;

        final DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);

        if (!mFullscreen) {
            v.animate().translationX(0).setDuration(500).setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                    v.setVisibility(View.VISIBLE);

                    paramMap.width = WindowManager.LayoutParams.MATCH_PARENT;
                    paramMap.height = Math.round(metrics.heightPixels / 2);
                    windowManager.updateViewLayout(v, paramMap);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    mFullscreen = true;
                }

                @Override
                public void onAnimationCancel(Animator animator) {

                }

                @Override
                public void onAnimationRepeat(Animator animator) {

                }
            }).start();
        } else {
            v.animate().translationX(-metrics.widthPixels).setDuration(500).setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {

                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    mFullscreen = false;
                    paramMap.width = 1;
                    paramMap.height = 1;
                    windowManager.updateViewLayout(v, paramMap);
                    v.setVisibility(View.INVISIBLE);
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

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if(mCurrentLocation != null) {
            if(mLocationMarker != null) {
                mLocationMarker.remove();
            }
            mMap.addMarker(new MarkerOptions().position(mCurrentLocation));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrentLocation, 15));
        }

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                mSelectedMarker = null;
            }
        });

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
            mSelectedMarker = marker;
            return false;
            }
        });

        mapView.onResume();

        setupView();
        setupTimers();

        Intent it = new Intent(this, ChatHeadService.class);
        startService(it);

        Intent it2 = new Intent(this, FetchService.class);
        it2.putExtra("type", "mapReady");
        startService(it2);



    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onDestroy() {
        mapView.onDestroy();
        windowManager.removeViewImmediate(v);
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d("Intent", "Handle");

    }

}
