package com.benjy3gg.pokeradar;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.NotificationCompat;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.pokegoapi.api.PokemonGo;

import net.rehacktive.waspdb.WaspDb;
import net.rehacktive.waspdb.WaspFactory;
import net.rehacktive.waspdb.WaspHash;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;
import java.util.TimeZone;
import java.util.Timer;
import java.util.TimerTask;

import POGOProtos.Enums.PokemonIdOuterClass;
import POGOProtos.Map.Pokemon.WildPokemonOuterClass;
import POGOProtos.Networking.Envelopes.RequestEnvelopeOuterClass;
import fr.quentinklein.slt.LocationTracker;
import fr.quentinklein.slt.LocationUtils;
import fr.quentinklein.slt.TrackerSettings;
import okhttp3.OkHttpClient;

public class FetchService extends IntentService {

    private OkHttpClient client;
    private RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo auth;
    private PokemonGo go;
    private LatLng mCurrentLocation;
    private boolean mShouldFetch;
    private LocationManager locationManager;
    private static String TAG = "FETCHSERVICE";
    private int spanX;
    private int spanY;
    private int stepY;
    private int stepX;
    private WaspHash pokemons;
    private MyTimerTask myTask;
    private Timer myTimer;
    private String username;
    private String password;
    private SharedPreferences sharedPref;
    private boolean mMapReady = false;
    private boolean mFirstStart = true;
    private Handler updateHandler;
    private int updateDelay;
    private Runnable myRunnable;

    Calendar cal = Calendar.getInstance();
    TimeZone tz = cal.getTimeZone();

    private double lat_gap_meters = 150.0;
    private double lng_gap_meters = 86.6;
    private double meters_per_degree = 111111;
    private double lat_gap_degrees = (lat_gap_meters) / meters_per_degree;
    private int mNumSteps;
    private boolean mShouldBreak;
    public static String ACTION_HIDE_LOGIN = "hide";
    public static String ACTION_DISABLE_LOGIN = "disable";
    public static String ACTION_ENABLE_LOGIN = "enable";
    public static String ACTION_SHOW_LOGIN = "show";

    public static String ACTION_SHOW_PROGRESS = "progressShow";
    public static String ACTION_HIDE_PROGRESS = "progressHide";


    public boolean mSendConnectionGoodAgain = false;
    private NotificationManager mNotificationManager;
    private int NOTIFICATION_ID = 1234;
    private int NOTIFICATION_ID_AUTH = 1235;
    public List<PokemonSimple> pokemonsInNotification = new ArrayList<>();
    private static final String NOTIFICATION_DELETED_ACTION = "NOTIFICATION_DELETED";
    private static final String NOTIFICATION_CODE_CLIPBOARD = "NOTIFICATION_CLIPBOARD";
    private FirebaseAnalytics mFirebaseAnalytics;

    public static volatile boolean shouldContinue = true;


    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(NOTIFICATION_DELETED_ACTION)) {
                pokemonsInNotification.clear();
                unregisterReceiver(this);
            } else if (intent.getAction().equals(NOTIFICATION_CODE_CLIPBOARD)) {
                ClipboardManager clipboard = (ClipboardManager)
                        getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("usercode", mUserCode);
                clipboard.setPrimaryClip(clip);
                Toast.makeText(FetchService.this, "Copied usercode to clipboard", Toast.LENGTH_SHORT).show();
            }
        }
    };
    private String mAuthType;
    private String mUserCode;
    private LocationTracker tracker;


    public FetchService() {
        super("FetchService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);
        initializeDatabase();
        sharedPref = getSharedPreferences("credentials", Context.MODE_PRIVATE);
        mNotificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mShouldFetch = true;
        mNumSteps = 3;

        EventBus.getDefault().register(this);

        initializeGps();
    }

    @Subscribe
    public void handleSomethingElse(GoFoundEvent event) {
        go = event.go;
        tryStart();
    }

    @Subscribe
    public void handleSomethingElse(MapReadyEvent event) {
        mMapReady = true;
    }



    public void setLastKnownLocation() {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putFloat("last_lat", (float) mCurrentLocation.latitude);
        editor.putFloat("last_lng", (float) mCurrentLocation.longitude);
        editor.apply();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(receiver);
        }catch (IllegalArgumentException e) {
            Log.d(TAG, "no prob");
        }
        if (myTimer != null) {
            myTimer.cancel();

            myTimer.purge();
        }
        if (myTask != null) {
            myTask.cancel();
        }
        if (updateHandler != null) {
            updateHandler.removeCallbacks(myRunnable);
        }
        //stopSelf();

    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getStringExtra("type") != null) {
            switch (intent.getStringExtra("type")) {
                case "status":
                    Toast.makeText(this, intent.getStringExtra("status"), Toast.LENGTH_SHORT).show();
                    break;
                case "new_pokemon":
                    Toast.makeText(this, intent.getStringExtra("encounterid"), Toast.LENGTH_SHORT).show();
                    break;
                case "bubble":
                    Toast.makeText(this, intent.getStringExtra("bubble"), Toast.LENGTH_SHORT).show();
                    break;
                case "mNumSteps":
                    mNumSteps = intent.getIntExtra("mNumSteps", mNumSteps);
                    mShouldBreak = true;
                    break;
                case "spanY":
                    spanY = intent.getIntExtra("spanY", spanY);
                    break;
                case "login":
                    String authType = intent.getStringExtra("authType");
                    if (authType.equals("google")) {
                        username = intent.getStringExtra("username");
                        password = intent.getStringExtra("password");
                        //initializeGo(authType);
                    } else {
                        username = intent.getStringExtra("username");
                        password = intent.getStringExtra("password");
                        //initializeGo(authType);
                    }

                    break;
                case "mapReady":
                    Log.d(TAG, "Map is ready");
                    mMapReady = true;
                    tryStart();
                    break;
                case "destroy":
                    this.onDestroy();
                    break;
            }
        }
        return START_NOT_STICKY;
    }

    public void tryStart() {
        updateHandler = new Handler();
        updateDelay = 1000; //milliseconds

        myRunnable = new Runnable() {
            public void run() {
                Log.d(TAG, "Try starting fetch loop");
                if(mCurrentLocation != null) {
                    Intent it = new Intent(FetchService.this, PokeService.class);
                    it.putExtra("lat", mCurrentLocation.latitude);
                    it.putExtra("lng", mCurrentLocation.longitude);
                    it.putExtra("type", "location");
                    startService(it);
                }

                if (mMapReady && mCurrentLocation != null && go != null) {
                    Log.d(TAG, "Fetch loop done");
                    updateHandler.removeCallbacks(myRunnable);
                    startTimer();
                } else {
                    updateHandler.postDelayed(this, updateDelay);
                }

            }
        };

        updateHandler.postDelayed(myRunnable, updateDelay);
    }

    public void initializeDatabase() {
        String path = getFilesDir().getPath();
        String databaseName = "myDb";
        String password = "passw0rd";

        WaspDb db = WaspFactory.openOrCreateDatabase(path, databaseName, password);
        db.removeHash("pokemons");
        pokemons = db.openOrCreateHash("pokemons");
        pokemons.flush();
        pokemons.flush();
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }

    public void startTimer() {
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.SEARCH, null);
        myTask = new MyTimerTask(mCurrentLocation);
        myTimer = new Timer();

        myTimer.schedule(myTask, 0, 10 * 1000);
    }


    public void initializeGps() {

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "You didn't grant any GPS permission!", Toast.LENGTH_SHORT).show();
        } else {
            TrackerSettings settings =
                    new TrackerSettings()
                            .setUseGPS(LocationUtils.isGpsProviderEnabled(this))
                            .setUseNetwork(LocationUtils.isNetworkProviderEnabled(this))
                            .setUsePassive(LocationUtils.isPassiveProviderEnabled(this))
                            .setTimeBetweenUpdates(10 * 1000)
                            .setMetersBetweenUpdates(100);
            tracker = new LocationTracker(this, settings) {
                @Override
                public void onLocationFound(@NonNull Location location) {
                    FetchService.this.onLocationChanged(location);
                }

                @Override
                public void onTimeout() {
                    Toast.makeText(FetchService.this, "Location tracking timed out!", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onProviderDisabled(@NonNull String provider) {
                    super.onProviderDisabled(provider);
                }

                @Override
                public void onProviderEnabled(@NonNull String provider) {
                    super.onProviderEnabled(provider);
                }
            };
            tracker.startListening();
            tracker.quickFix();
        }

    }

    public void onLocationChanged(Location location) {
        //TODO: add check for distance
        Log.d(TAG, "Location set");
        mCurrentLocation = new LatLng(location.getLatitude(), location.getLongitude());
        if(mCurrentLocation.longitude != 0.0 && mCurrentLocation.latitude != 0.0) {
            setLastKnownLocation();
        }
        //send Location to PokeService
        Intent it = new Intent(this, PokeService.class);
        it.putExtra("lat", mCurrentLocation.latitude);
        it.putExtra("lng", mCurrentLocation.longitude);
        it.putExtra("type", "location");
        startService(it);
    }

    public void sendBroadcastIntent(String action) {
        sendBroadcast(new Intent(action));
    }

    public double calculate_lng_degrees(double lat) {
        return (lng_gap_meters) / (meters_per_degree * Math.cos(Math.toRadians(lat)));
    }

    public List<LatLng> generate_location_steps() {
        List<LatLng> results = new ArrayList<>();
        int ring = 1;
        double lat_location = mCurrentLocation.latitude;
        double lng_location = mCurrentLocation.longitude;

        results.add(new LatLng(lat_location, lng_location));

        while (ring < mNumSteps) {
            lat_location += lat_gap_degrees;
            lng_location -= calculate_lng_degrees(lat_location);
            for(int direction=0; direction < 6; direction++) {
                for(int r = 0; r < ring; r++) {
                    if (direction == 0) {
                        lng_location += calculate_lng_degrees(lat_location) * 2;
                    }

                    if (direction == 1) {
                        lat_location -= lat_gap_degrees;
                        lng_location += calculate_lng_degrees(lat_location);
                    }

                    if (direction == 2) {
                        lat_location -= lat_gap_degrees;
                        lng_location -= calculate_lng_degrees(lat_location);
                    }

                    if (direction == 3) {
                        lng_location -= calculate_lng_degrees(lat_location) * 2;
                    }

                    if (direction == 4) {
                        lat_location += lat_gap_degrees;
                        lng_location -= calculate_lng_degrees(lat_location);
                    }

                    if (direction == 5) {
                        lat_location += lat_gap_degrees;
                        lng_location += calculate_lng_degrees(lat_location);
                    }

                    results.add(new LatLng(lat_location, lng_location));

                }
            }
            ring += 1;
        }

        return results;

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

            }

            @Override
            protected List<WildPokemonOuterClass.WildPokemon> doInBackground(LatLng... params) {
                LatLng search_loc;

                List<LatLng> locations = generate_location_steps();
                int total_steps = (3 * (mNumSteps * mNumSteps)) - (3 * mNumSteps) + 1;
                for (int i=0; i < total_steps; i++) {
                    if(mShouldBreak) {
                        mShouldBreak = false;
                        //myTimer.schedule(myTask, 0, 10 * 1000);
                        break;
                    }
                    search_loc = locations.get(i);
                    Intent itm = new Intent(FetchService.this, PokeService.class);
                    itm.putExtra("type", "marker");
                    itm.putExtra("lat", search_loc.latitude);
                    itm.putExtra("lng", search_loc.longitude);
                    startService(itm);
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException es) {
                        es.printStackTrace();
                    }

                    try {
                        go.setLocation(search_loc.latitude, search_loc.longitude, 0);
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                        if(!mSendConnectionGoodAgain) {
                            Intent it = new Intent(FetchService.this, PokeService.class);
                            it.putExtra("type", "connection_error");
                            mSendConnectionGoodAgain = true;
                            //Log.d(TAG, "Connection Error");
                            startService(it);
                        }
                    }

                    try {
                        Collection<WildPokemonOuterClass.WildPokemon> poke = go.getMap().getMapObjects().getWildPokemons();
                        //Log.d(TAG, "Connection Restored " + mSendConnectionGoodAgain);
                        if(mSendConnectionGoodAgain) {
                            Intent itt = new Intent(FetchService.this, PokeService.class);
                            itt.putExtra("type", "connection_good");
                            startService(itt);
                            mSendConnectionGoodAgain = false;
                            //Log.d(TAG, "Connection Good Again " + mSendConnectionGoodAgain);
                        }

                        for (WildPokemonOuterClass.WildPokemon p : poke) {
                            String encounterID = String.valueOf(p.getEncounterId());

                            if (!pokemons.getAllKeys().contains(encounterID)) {
                                long till = System.currentTimeMillis() + p.getTimeTillHiddenMs();
                                String name = PokemonIdOuterClass.PokemonId.valueOf(p.getPokemonData().getPokemonIdValue()).name();
                                PokemonSimple p_simple = new PokemonSimple(till, String.valueOf(p.getEncounterId()), p.getLatitude(), p.getLongitude(), p.getPokemonData().getPokemonIdValue(), name);

                                pokemons.put(encounterID, p_simple);
                                if(sharedPref.getBoolean("show_"+p_simple.pokemonid, true)) {

                                    Intent it = new Intent(FetchService.this, PokeService.class);
                                    Bundle pokedata = new Bundle();
                                    pokedata.putString("encounterid", String.valueOf(p_simple.encounterId));
                                    pokedata.putLong("timestampHidden", p_simple.timestampHidden);
                                    pokedata.putDouble("latitude", p_simple.latitude);
                                    pokedata.putDouble("longitude", p_simple.longitude);
                                    pokedata.putInt("pokemonid", p_simple.pokemonid);
                                    pokedata.putString("name", p_simple.name);

                                    it.putExtras(pokedata);
                                    it.putExtra("type", "new_pokemon");
                                    startService(it);
                                }
                                if(sharedPref.getBoolean("notify_"+p_simple.pokemonid, false)) {
                                    if(p_simple.timestampHidden > System.currentTimeMillis()) {
                                        pokemonsInNotification.add(p_simple);
                                        postNotification(p_simple);
                                    }
                                }
                            }

                        }
                    } catch (Exception ex) {

                        Log.d(TAG, "Error:" + ex);
                        if(!mSendConnectionGoodAgain) {
                            Intent it = new Intent(FetchService.this, PokeService.class);
                            it.putExtra("type", "connection_error");
                            mSendConnectionGoodAgain = true;
                            Log.d(TAG, "Connection Error");
                            startService(it);
                        }

                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }


                    }
                }

                return null;
            }


            @Override
            protected void onProgressUpdate(WildPokemonOuterClass.WildPokemon... values) {

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

    public String getDirectionName(double d) {
        String directions[] = {"N", "NE", "E", "SE", "S", "SW", "W", "NW", "N"};
        return directions[ (int)Math.round((  ((double)d % 360) / 45)) ];
    }


    public void postNotification(PokemonSimple pp) {

        NotificationCompat.InboxStyle inboxStyle = new NotificationCompat.InboxStyle();
        for(PokemonSimple p : pokemonsInNotification) {

            Location locationA = new Location("Player");
            locationA.setLatitude(mCurrentLocation.latitude);
            locationA.setLongitude(mCurrentLocation.longitude);
            Location locationB = new Location("Pokemon");

            locationB.setLatitude(p.latitude);
            locationB.setLongitude(p.longitude);

            float distance = locationA.distanceTo(locationB);
            float bearing = locationA.bearingTo(locationB);
            String direction = getDirectionName(bearing);

            CharSequence relTime = DateUtils.getRelativeTimeSpanString(
            p.timestampHidden,
            System.currentTimeMillis(),
                    DateUtils.FORMAT_ABBREV_RELATIVE);
            inboxStyle.addLine(p.name + " flees " + relTime + ", " + Math.round(distance) + " meters in " + direction);
        }



        Intent deleteIntent = new Intent(NOTIFICATION_DELETED_ACTION);
        PendingIntent pendintIntent = PendingIntent.getBroadcast(this, 0, deleteIntent, 0);
        registerReceiver(receiver, new IntentFilter(NOTIFICATION_DELETED_ACTION));
        long[] pattern = {600, 300, 400};
        if(!sharedPref.getBoolean("vibrate" ,false)) {
            pattern[0] = 0L;
            pattern[1] = 0L;
            pattern[2] = 0L;
        }

        Notification n  = new NotificationCompat.Builder(this)
                .setContentTitle("New Pokemon")
                .setContentText(pp.name)
                .setSmallIcon(getResourceId("icon", "mipmap"))
                .setAutoCancel(true)
                .setStyle(inboxStyle)
                .setDeleteIntent(pendintIntent)
                .setContentIntent(pendintIntent)
                .setFullScreenIntent(pendintIntent, false)
                .setVibrate(pattern)
                .build();

        mNotificationManager.notify(NOTIFICATION_ID, n);
    }

    public int getResourceId(String pVariableName, String pType) {
        try {
            return getResources().getIdentifier(pVariableName, pType, getPackageName());
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }

}
