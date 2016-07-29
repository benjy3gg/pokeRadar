package com.benjy3gg.livemapgo;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.BounceInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;

import net.rehacktive.waspdb.WaspHash;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class PokeService extends IntentService implements OnMapReadyCallback {
    private GoogleMap mMap;
    private MapView mapView;
    private RelativeLayout v;
    private Resources resources;
    private static String TAG = "POKESERVICE";
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
    private Circle mCircle;
    private Circle mBoundingCircle;
    private SharedPreferences sharedPref;
    private int spanX;
    private int spanY;
    private int stepY;
    private int stepX;
    private SeekBar mSeekBarHorizontal;
    private ValueAnimator vAnimator;
    private int mNumSteps;
    private NotificationManager mNotificationManager;
    private double meters_per_degree = 111111;

    private WindowManager windowManager;
    private RelativeLayout chatheadView, removeView;
    private LinearLayout txtView, txt_linearlayout;
    private ImageView chatheadImg, removeImg;
    private TextView txt1;
    private int x_init_cord, y_init_cord, x_init_margin, y_init_margin;
    private Point szWindow = new Point();
    private boolean isLeft = true;
    private Runnable myRunnable;

    public static String ACTION_SHOW_RESTART = "show restart";

    public static int UPDATE_DELAY_FOREGROUND = 1000;
    public static int UPDATE_DELAY_BACKGROUND = 60000;

    @SuppressWarnings("deprecation")

    @Override
    public void onCreate() {
        super.onCreate();
        sharedPref = getSharedPreferences("credentials", Context.MODE_PRIVATE);
        getSetLastKnownLocation();
        initializeDatabase();

        spanX = 5;
        spanY = 5;
        stepX = 1;
        stepY = 1;

        mNumSteps = 3;

        EventBus.getDefault().register(this);
        MapsInitializer.initialize(this);
        createMapView(new Bundle());

        /*
        mSeekBarVertical.setOnSeekBarChangeListener(new VerticalSeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                //setSearchSpanY(seekBar.getProgress()+1);
            }
        });
        */
    }

    @Subscribe
    public void handleSomethingElse(MessageEvent event) {
        event = event;
    }

    @Subscribe
    public void handleChangeBubbleEvent(ChangeBubbleEvent event) {
        switch (event.whattodo) {
            case "open":
                this.onCreate();
                toggleView("top");
                break;
        }
    }



    public void createMapView(Bundle mBundle) {
        LayoutInflater li = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        v = (RelativeLayout) li.inflate(R.layout.activity_maps, null);
        mapView = (MapView) v.findViewById(R.id.map);
        mapView.onCreate(null);
        mapView.getMapAsync(this);

        mSeekBarHorizontal = (SeekBar) v.findViewById(R.id.seekBarHorizontal);
        //mSeekBarVertical = (VerticalSeekBar) v.findViewById(R.id.seekBarVertical);
        //mSeekBarVertical.setVisibility(View.INVISIBLE);

        mSeekBarHorizontal.setProgress(mNumSteps);
        //mSeekBarVertical.setProgress(spanY);

        mSeekBarHorizontal.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                setSearchSpanX(seekBar.getProgress() + 1);
            }
        });
    }


    public void setSearchSpanX(int mNumSteps) {
        this.mNumSteps = mNumSteps;
        //updateBoundingRect();
        updateBoundingCircle();
        Intent it = new Intent(this, FetchService.class);
        it.putExtra("type", "mNumSteps");
        it.putExtra("mNumSteps", mNumSteps);
        startService(it);
    }

    private void updateBoundingCircle() {
        if (mMap != null) {
            if (mBoundingCircle != null) {
                mBoundingCircle.remove();
            }
            //vAnimator = new ValueAnimator();
            int circleRadiusMeters = 150 * mNumSteps;
            double circleRadiusDegrees = circleRadiusMeters / meters_per_degree;

            mBoundingCircle = mMap.addCircle(new CircleOptions().center(mCurrentLocation).radius(150 * mNumSteps).strokeColor(Color.argb(32, 29, 132, 181)));
            /*
            LatLng leftBound = new LatLng(mCurrentLocation.latitude-circleRadiusDegrees, mCurrentLocation.longitude-circleRadiusDegrees);

            LatLng rightBound = new LatLng(mCurrentLocation.latitude+circleRadiusDegrees, mCurrentLocation.longitude+circleRadiusDegrees);
            LatLngBounds bounds = LatLngBounds.builder().include(leftBound).include(rightBound).include(mCurrentLocation).build();
            DisplayMetrics displayMetrics = new DisplayMetrics();
            ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay().getMetrics(displayMetrics);
            mMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, displayMetrics.widthPixels, displayMetrics.heightPixels, 160*2));
             */
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrentLocation, 15));
        }
    }

    public void setSearchSpanY(int spanY) {
        this.spanY = spanY;
        updateBoundingRect();
        Intent it = new Intent(this, FetchService.class);
        it.putExtra("type", "spanY");
        it.putExtra("spanY", spanY);
        startService(it);
    }

    public void getSetLastKnownLocation() {
        mCurrentLocation = new LatLng(sharedPref.getFloat("last_lat", 0.0f), sharedPref.getFloat("last_lng", 0.0f));
    }

    public void setupTimers() {

        updateHandler = new Handler();
        updateDelay = UPDATE_DELAY_FOREGROUND; //milliseconds

        myRunnable = new Runnable() {
            public void run() {

                Log.i(TAG, "Updating map with delay " + updateDelay);

                Iterator it = markers.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<String, Marker> pair = (Map.Entry) it.next();
                    Marker m = pair.getValue();
                    long now = System.currentTimeMillis();
                    synchronized (pokemons) {
                        PokemonSimple p = pokemons.get(pair.getKey());

                        if (sharedPref.getBoolean("show_" + p.pokemonid, true)) {
                            m.setVisible(true);
                        } else {
                            m.setVisible(false);
                        }

                        if (p != null) {
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
        };

        updateHandler.postDelayed(myRunnable, updateDelay);
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
        if (intent != null && intent.getStringExtra("type") != null) {
            switch (intent.getStringExtra("type")) {
                case "status":
                    //Toast.makeText(this, intent.getStringExtra("status"), Toast.LENGTH_SHORT).show();
                    if (intent.getStringExtra("type").equals("initialized")) {
                        createMapView(intent.getBundleExtra("bundle"));
                        //Intent it = new Intent(this, ChatHeadService.class);
                        //startService(it);
                    }
                    break;
                case "new_pokemon":
                    Bundle getBundle = null;
                    getBundle = intent.getExtras();
                    //TODO: make PokemonSimple Serializable/Parcelable
                    String encounterid = getBundle.getString("encounterid", null);
                    Long till = getBundle.getLong("timestampHidden", -0);
                    Double latitude = getBundle.getDouble("latitude", -0.0);
                    Double longitude = getBundle.getDouble("longitude", -0.0);
                    int pokemonid = getBundle.getInt("pokemonid", -1);
                    String name = getBundle.getString("name", "");
                    PokemonSimple p_simple = new PokemonSimple(till, encounterid, latitude, longitude, pokemonid, name);
                    if (p_simple.timestampHidden > System.currentTimeMillis()) {
                        pokemons.put(encounterid, p_simple);
                        addPokemonMarker(encounterid);
                    }
                    break;
                case "bubble":
                    Toast.makeText(this, intent.getStringExtra("bubble"), Toast.LENGTH_SHORT).show();
                    intent.getStringExtra("position");
                    toggleView(intent.getStringExtra("position"));
                    break;
                case "location":
                    LatLng new_loc = new LatLng(intent.getDoubleExtra("lat", sharedPref.getFloat("last_lat", 0.0f)), intent.getDoubleExtra("lng", sharedPref.getFloat("last_lng", 0.0f)));
                    setNewLocation(new_loc);
                    break;
                case "marker":
                    LatLng marker_loc = new LatLng(intent.getDoubleExtra("lat", 0.0f), intent.getDoubleExtra("lng", 0.0f));
                    addSearchCircle(marker_loc);
                    break;
                case "connection_error":
                    if (chatheadImg != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                            chatheadImg.animate().rotationYBy(360).setDuration(500).setInterpolator(new BounceInterpolator()).setUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                    if (valueAnimator.getAnimatedFraction() > 0.5) {
                                        chatheadImg.setImageResource(R.drawable.radar_error);
                                    }
                                }
                            }).start();
                        } else {
                            chatheadImg.animate().rotationYBy(360).setDuration(500).setInterpolator(new BounceInterpolator()).setListener(new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animator) {
                                }

                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    chatheadImg.setRotationY(0);
                                    chatheadImg.setImageResource(R.drawable.radar_error);
                                }

                                @Override
                                public void onAnimationCancel(Animator animator) {
                                    chatheadImg.setRotationY(0);
                                    chatheadImg.setImageResource(R.drawable.radar_error);
                                }

                                @Override
                                public void onAnimationRepeat(Animator animator) {
                                }
                            }).start();
                        }
                    }
                    break;
                case "connection_good":
                    Log.d(TAG, "Connection Good again");
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                        if (chatheadImg != null) {
                            chatheadImg.animate().rotationYBy(360).setDuration(500).setInterpolator(new BounceInterpolator()).setUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                    if (valueAnimator.getAnimatedFraction() > 0.5) {
                                        chatheadImg.setImageResource(R.drawable.radar_good);
                                    }
                                }
                            }).start();
                        } else {
                            chatheadImg.animate().rotationYBy(360).setDuration(500).setInterpolator(new BounceInterpolator()).setListener(new Animator.AnimatorListener() {
                                @Override
                                public void onAnimationStart(Animator animator) {
                                }

                                @Override
                                public void onAnimationEnd(Animator animator) {
                                    chatheadImg.setRotationY(0);
                                    chatheadImg.setImageResource(R.drawable.radar_good);
                                }

                                @Override
                                public void onAnimationCancel(Animator animator) {
                                    chatheadImg.setRotationY(0);
                                    chatheadImg.setImageResource(R.drawable.radar_good);
                                }

                                @Override
                                public void onAnimationRepeat(Animator animator) {
                                }
                            }).start();
                        }
                    }
            }
        }
        return START_STICKY;
    }

    private void addSearchCircle(LatLng marker_loc) {
        if (mMap != null) {
            if (mCircle != null) {
                mCircle.remove();
            }
            mCircle = mMap.addCircle(new CircleOptions().center(marker_loc).radius(100).strokeColor(Color.argb(32, 29, 132, 181)));
        }
    }


    private void addPokemonMarker(String encounterid) {
        PokemonSimple poke = pokemons.get(encounterid);
        if (poke != null) {
            String name = poke.name;
            if(mMap != null) {
                Marker m = mMap.addMarker(new MarkerOptions()
                        .position(new LatLng(poke.latitude, poke.longitude))
                        .title(name)
                        .icon(BitmapDescriptorFactory.fromResource(getResourceId("prefix_" + poke.pokemonid, "drawable"))));
                markers.put(encounterid, m);
            }

            //currently show and notify must both be on

        }
    }

    public PolygonOptions calculateBoundingBox(LatLng location) {

        //calculate boundingBox
        //topLeft
        double lat = location.latitude + (-1 * spanY * 75 / 111000f);
        double new_x = (-1 * spanX * 75 / 111000f) / Math.cos(location.longitude);
        double lng = location.longitude + new_x;
        LatLng topLeft = new LatLng(lat, lng);

        //topRight
        lat = location.latitude + (-1 * spanY * 75 / 111000f);
        new_x = (spanX * 75 / 111000f) / Math.cos(location.longitude);
        lng = location.longitude + new_x;
        LatLng topRight = new LatLng(lat, lng);

        //bottomLeft
        lat = location.latitude + spanY * 75 / 111000f;
        new_x = (-1 * spanX * 75 / 111000f) / Math.cos(location.longitude);
        lng = location.longitude + new_x;
        LatLng bottomLeft = new LatLng(lat, lng);

        //bottomRight
        lat = location.latitude + spanY * 75 / 111000f;
        new_x = (spanX * 75 / 111000f) / Math.cos(location.longitude);
        lng = location.longitude + new_x;
        LatLng bottomRight = new LatLng(lat, lng);

        return new PolygonOptions().add(topLeft).add(topRight).add(bottomRight).add(bottomLeft).add(topLeft).fillColor(Color.argb(8, 29, 132, 181)).strokeColor(Color.argb(16, 29, 132, 181));
    }

    private void setNewLocation(LatLng new_loc) {
        mCurrentLocation = new_loc;
        if (mMap != null) {
            //mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrentLocation, 15));
            if (mLocationMarker != null) {
                mLocationMarker.remove();
            }
            mLocationMarker = mMap.addMarker(new MarkerOptions().position(mCurrentLocation));
            updateBoundingCircle();
        }
    }

    private void updateBoundingRect() {
        if (mCurrentLocation != null && mMap != null) {
            PolygonOptions polygonOptions = calculateBoundingBox(mCurrentLocation);

            if (mPolygon != null) {
                mPolygon.remove();
            }
            mPolygon = mMap.addPolygon(polygonOptions);
        }
    }

    public void setupView() {
        if (windowManager == null) {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        }
        WindowManager.LayoutParams paramMap = new WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        paramMap.gravity = Gravity.TOP | Gravity.LEFT;

        v.setLayoutParams(paramMap);
        windowManager.addView(v, paramMap);
        handleStart();
    }

    public void toggleView(String position) {

        final WindowManager.LayoutParams paramMap = new WindowManager.LayoutParams(
                1,
                1,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        if (position.equals("top")) {
            paramMap.gravity = Gravity.TOP | Gravity.LEFT;
        } else {
            paramMap.gravity = Gravity.BOTTOM | Gravity.LEFT;
        }

        final DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);

        if (!mFullscreen) {
            v.animate().translationX(0).setDuration(500).setListener(new Animator.AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animator) {
                    v.setVisibility(View.VISIBLE);
                    setNewMapUpdateDelay(UPDATE_DELAY_FOREGROUND);

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
                    setNewMapUpdateDelay(UPDATE_DELAY_BACKGROUND);
                }

                @Override
                public void onAnimationEnd(Animator animator) {
                    mFullscreen = false;
                    paramMap.width = 1;
                    paramMap.height = 1;
                    windowManager.updateViewLayout(v, paramMap);
                    v.setVisibility(View.GONE);
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

    public void setNewMapUpdateDelay(int delay) {
        updateDelay = delay;
        updateHandler.removeCallbacks(myRunnable);
        updateHandler.postDelayed(myRunnable, updateDelay);
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (mCurrentLocation != null) {
            if (mLocationMarker != null) {
                mLocationMarker.remove();
            }
            mLocationMarker = mMap.addMarker(new MarkerOptions().position(mCurrentLocation));
            if (mPolygon != null) {
                mPolygon.remove();
            }
            //PolygonOptions polygonOptions = calculateBoundingBox(mCurrentLocation);
            //mPolygon = mMap.addPolygon(polygonOptions);
            //mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(mCurrentLocation, 15));
            updateBoundingCircle();

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

        EventBus.getDefault().post(new MapReadyEvent());
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mapView.onDestroy();
        windowManager.removeView(v);
        windowManager.removeView(chatheadView);
        updateHandler.removeCallbacks(myRunnable);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d("Intent", "Handle");

    }

    private void handleStart() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        removeView = (RelativeLayout) inflater.inflate(R.layout.remove, null);
        WindowManager.LayoutParams paramRemove = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        paramRemove.gravity = Gravity.TOP | Gravity.LEFT;

        removeView.setVisibility(View.GONE);
        removeImg = (ImageView) removeView.findViewById(R.id.remove_img);
        windowManager.addView(removeView, paramRemove);


        chatheadView = (RelativeLayout) inflater.inflate(R.layout.chathead, null);
        chatheadImg = (ImageView) chatheadView.findViewById(R.id.chathead_img);


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            windowManager.getDefaultDisplay().getSize(szWindow);
        } else {
            int w = windowManager.getDefaultDisplay().getWidth();
            int h = windowManager.getDefaultDisplay().getHeight();
            szWindow.set(w, h);
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = 100;
        windowManager.addView(chatheadView, params);

        chatheadView.setOnTouchListener(new View.OnTouchListener() {
            long time_start = 0, time_end = 0;
            boolean isLongclick = false, inBounded = false;
            int remove_img_width = 0, remove_img_height = 0;

            Handler handler_longClick = new Handler();
            Runnable runnable_longClick = new Runnable() {

                @Override
                public void run() {
                    isLongclick = true;
                    removeView.setVisibility(View.VISIBLE);
                    chathead_longclick();
                }
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) chatheadView.getLayoutParams();

                int x_cord = (int) event.getRawX();
                int y_cord = (int) event.getRawY();
                int x_cord_Destination, y_cord_Destination;

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        time_start = System.currentTimeMillis();
                        handler_longClick.postDelayed(runnable_longClick, 600);

                        remove_img_width = removeImg.getLayoutParams().width;
                        remove_img_height = removeImg.getLayoutParams().height;

                        x_init_cord = x_cord;
                        y_init_cord = y_cord;

                        x_init_margin = layoutParams.x;
                        y_init_margin = layoutParams.y;

                        if (txtView != null) {
                            txtView.setVisibility(View.GONE);
                        }
                        break;
                    case MotionEvent.ACTION_MOVE:
                        int x_diff_move = x_cord - x_init_cord;
                        int y_diff_move = y_cord - y_init_cord;

                        x_cord_Destination = x_init_margin + x_diff_move;
                        y_cord_Destination = y_init_margin + y_diff_move;

                        if (isLongclick) {
                            int x_bound_left = szWindow.x / 2 - (int) (remove_img_width * 1.5);
                            int x_bound_right = szWindow.x / 2 + (int) (remove_img_width * 1.5);
                            int y_bound_top = szWindow.y - (int) (remove_img_height * 1.5);

                            if ((x_cord >= x_bound_left && x_cord <= x_bound_right) && y_cord >= y_bound_top) {
                                inBounded = true;

                                int x_cord_remove = (int) ((szWindow.x - (remove_img_height * 1.5)) / 2);
                                int y_cord_remove = (int) (szWindow.y - ((remove_img_width * 1.5) + getStatusBarHeight()));

                                if (removeImg.getLayoutParams().height == remove_img_height) {
                                    removeImg.getLayoutParams().height = (int) (remove_img_height * 1.5);
                                    removeImg.getLayoutParams().width = (int) (remove_img_width * 1.5);

                                    WindowManager.LayoutParams param_remove = (WindowManager.LayoutParams) removeView.getLayoutParams();
                                    param_remove.x = x_cord_remove;
                                    param_remove.y = y_cord_remove;

                                    windowManager.updateViewLayout(removeView, param_remove);
                                }

                                layoutParams.x = x_cord_remove + (Math.abs(removeView.getWidth() - chatheadView.getWidth())) / 2;
                                layoutParams.y = y_cord_remove + (Math.abs(removeView.getHeight() - chatheadView.getHeight())) / 2;

                                windowManager.updateViewLayout(chatheadView, layoutParams);
                                break;
                            } else {
                                inBounded = false;
                                removeImg.getLayoutParams().height = remove_img_height;
                                removeImg.getLayoutParams().width = remove_img_width;

                                WindowManager.LayoutParams param_remove = (WindowManager.LayoutParams) removeView.getLayoutParams();
                                int x_cord_remove = (szWindow.x - removeView.getWidth()) / 2;
                                int y_cord_remove = szWindow.y - (removeView.getHeight() + getStatusBarHeight());

                                param_remove.x = x_cord_remove;
                                param_remove.y = y_cord_remove;

                                windowManager.updateViewLayout(removeView, param_remove);
                            }

                        }


                        layoutParams.x = x_cord_Destination;
                        layoutParams.y = y_cord_Destination;

                        windowManager.updateViewLayout(chatheadView, layoutParams);
                        break;
                    case MotionEvent.ACTION_UP:
                        isLongclick = false;
                        removeView.setVisibility(View.GONE);
                        removeImg.getLayoutParams().height = remove_img_height;
                        removeImg.getLayoutParams().width = remove_img_width;
                        handler_longClick.removeCallbacks(runnable_longClick);

                        if (inBounded) {
                            handleBubbleTrash();
                            //chatheadView.setVisibility(View.GONE);
                            //v.setVisibility(View.GONE);

                            inBounded = false;
                            break;
                        }


                        int x_diff = x_cord - x_init_cord;
                        int y_diff = y_cord - y_init_cord;

                        if (Math.abs(x_diff) < 5 && Math.abs(y_diff) < 5) {
                            time_end = System.currentTimeMillis();
                            if ((time_end - time_start) < 300) {
                                chathead_click();
                            }
                        }

                        y_cord_Destination = y_init_margin + y_diff;

                        int BarHeight = getStatusBarHeight();
                        if (y_cord_Destination < 0) {
                            y_cord_Destination = 0;
                        } else if (y_cord_Destination + (chatheadView.getHeight() + BarHeight) > szWindow.y) {
                            y_cord_Destination = szWindow.y - (chatheadView.getHeight() + BarHeight);
                        }
                        layoutParams.y = y_cord_Destination;

                        inBounded = false;
                        resetPosition(x_cord);

                        break;
                    default:
                        break;
                }
                return true;
            }
        });


        txtView = (LinearLayout) inflater.inflate(R.layout.txt, null);
        txt1 = (TextView) txtView.findViewById(R.id.txt1);
        txt_linearlayout = (LinearLayout) txtView.findViewById(R.id.txt_linearlayout);


        WindowManager.LayoutParams paramsTxt = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        paramsTxt.gravity = Gravity.TOP | Gravity.LEFT;

        txtView.setVisibility(View.GONE);
        windowManager.addView(txtView, paramsTxt);
    }

    private void handleBubbleTrash() {
        //stopService(new Intent(PokeService.this, FetchService.class));
        EventBus.getDefault().post(new MessageEvent("pokeball_trashed", ""));
        PokeService.this.onDestroy();
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (windowManager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
                windowManager.getDefaultDisplay().getSize(szWindow);
            } else {
                int w = windowManager.getDefaultDisplay().getWidth();
                int h = windowManager.getDefaultDisplay().getHeight();
                szWindow.set(w, h);
            }

            WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) chatheadView.getLayoutParams();

            if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {

                if (txtView != null) {
                    txtView.setVisibility(View.GONE);
                }

                if (layoutParams.y + (chatheadView.getHeight() + getStatusBarHeight()) > szWindow.y) {
                    layoutParams.y = szWindow.y - (chatheadView.getHeight() + getStatusBarHeight());
                    windowManager.updateViewLayout(chatheadView, layoutParams);
                }

                if (layoutParams.x != 0 && layoutParams.x < szWindow.x) {
                    resetPosition(szWindow.x);
                }

            } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT) {

                if (txtView != null) {
                    txtView.setVisibility(View.GONE);
                }

                if (layoutParams.x > szWindow.x) {
                    resetPosition(szWindow.x);
                }

            }
        }
    }

    private void resetPosition(int x_cord_now) {
        if (x_cord_now <= szWindow.x / 2) {
            isLeft = true;
            moveToLeft(x_cord_now);

        } else {
            isLeft = false;
            moveToRight(x_cord_now);

        }

    }

    private void moveToLeft(final int x_cord_now) {
        final int x = szWindow.x - x_cord_now;

        new CountDownTimer(500, 5) {
            WindowManager.LayoutParams mParams = (WindowManager.LayoutParams) chatheadView.getLayoutParams();

            public void onTick(long t) {
                long step = (500 - t) / 5;
                mParams.x = 0 - (int) (double) bounceValue(step, x);
                windowManager.updateViewLayout(chatheadView, mParams);
            }

            public void onFinish() {
                mParams.x = 0;
                windowManager.updateViewLayout(chatheadView, mParams);
            }
        }.start();
    }

    private void moveToRight(final int x_cord_now) {
        new CountDownTimer(500, 5) {
            WindowManager.LayoutParams mParams = (WindowManager.LayoutParams) chatheadView.getLayoutParams();

            public void onTick(long t) {
                long step = (500 - t) / 5;
                mParams.x = szWindow.x + (int) (double) bounceValue(step, x_cord_now) - chatheadView.getWidth();
                windowManager.updateViewLayout(chatheadView, mParams);
            }

            public void onFinish() {
                mParams.x = szWindow.x - chatheadView.getWidth();
                windowManager.updateViewLayout(chatheadView, mParams);
            }
        }.start();
    }

    private double bounceValue(long step, long scale) {
        double value = scale * Math.exp(-0.055 * step) * Math.cos(0.08 * step);
        return value;
    }

    private int getStatusBarHeight() {
        int statusBarHeight = (int) Math.ceil(25 * getApplicationContext().getResources().getDisplayMetrics().density);
        return statusBarHeight;
    }

    private void chathead_click() {
        Intent it = new Intent(this, PokeService.class);
        it.putExtra("type", "bubble");
        it.putExtra("bubble", "clicked");
        final DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);
        if (((WindowManager.LayoutParams) chatheadView.getLayoutParams()).y > (metrics.heightPixels / 2)) {
            it.putExtra("position", "bottom");
        } else {
            it.putExtra("position", "top");
        }

        startService(it);
    }

    private void chathead_longclick() {

        WindowManager.LayoutParams param_remove = (WindowManager.LayoutParams) removeView.getLayoutParams();
        int x_cord_remove = (szWindow.x - removeView.getWidth()) / 2;
        int y_cord_remove = szWindow.y - (removeView.getHeight() + getStatusBarHeight());

        param_remove.x = x_cord_remove;
        param_remove.y = y_cord_remove;

        windowManager.updateViewLayout(removeView, param_remove);
    }

}
