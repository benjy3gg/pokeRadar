package com.benjy3gg.pokeradar;

import android.animation.IntEvaluator;
import android.animation.ValueAnimator;
import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.Toast;

import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.protobuf.InvalidProtocolBufferException;
import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.auth.PTCLogin;
import com.pokegoapi.exceptions.LoginFailedException;
import com.snappydb.DB;
import com.snappydb.DBFactory;
import com.snappydb.SnappydbException;

import net.rehacktive.waspdb.WaspDb;
import net.rehacktive.waspdb.WaspFactory;
import net.rehacktive.waspdb.WaspHash;
import net.rehacktive.waspdb.internals.collision.exceptions.KeyNotFoundException;

import java.util.ArrayList;
import java.util.Collection;
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

public class FetchService extends IntentService implements LocationListener {

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
    private DB snappydb;

    public FetchService() {
        super("FetchService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initializeDatabase();
        sharedPref = getSharedPreferences("credentials", Context.MODE_PRIVATE);
        mShouldFetch = true;
        spanX = 4;
        spanY = 3;
        stepX = 1;
        stepY = 1;
        initializeGps();
    }

    public void setLastKnownLocation() {
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putFloat("last_lat", (float)mCurrentLocation.latitude);
        editor.putFloat("last_lng", (float)mCurrentLocation.longitude);
        editor.apply();
    }

    @Override
    public void onDestroy() {
        myTimer.cancel();
        myTimer.purge();
        try {
            snappydb.close();
        } catch (SnappydbException e) {
            e.printStackTrace();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if(intent.getStringExtra("type") != null) {
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
                case "login":
                    String auth_s = intent.getStringExtra("auth");
                    if (auth_s != null) {
                        try {
                            auth = RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo.parseFrom(Base64.decode(auth_s, Base64.DEFAULT));
                        } catch (InvalidProtocolBufferException e) {
                            Toast.makeText(this, "Token may be invalid.", Toast.LENGTH_SHORT).show();
                        }
                    } else {
                        username = intent.getStringExtra("username");
                        password = intent.getStringExtra("password");
                    }
                    initializeGo();
                    break;
                case "mapReady":
                    startTimer();
                    break;
            }
        }
        return START_STICKY;
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

        try {
            snappydb = DBFactory.open(this); //create or open an existing databse using the default name

        } catch (SnappydbException e) {
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {

    }

    public void startTimer() {
        myTask = new MyTimerTask(mCurrentLocation);
        myTimer = new Timer();

        myTimer.schedule(myTask, 0, 45 * 1000);
    }

    @Override
    public void onLocationChanged(Location location) {
        mCurrentLocation = new LatLng(location.getLatitude(), location.getLongitude());
        setLastKnownLocation();
        //send Location to PokeService
        Intent it = new Intent(this, PokeService.class);
        it.putExtra("lat", mCurrentLocation.latitude);
        it.putExtra("lng", mCurrentLocation.longitude);
        it.putExtra("type", "location");
        startService(it);
        //TODO: notify PokeService
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {

    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }

    public void initializeGps() {

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        boolean enabled = locationManager
                .isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!enabled) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        try {
            locationManager.requestLocationUpdates("gps", 5000, 100, this);
            Location location = locationManager.getLastKnownLocation("gps");
            if (location != null) {
                onLocationChanged(location);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "SECURITY EXCEPTION: " + e.getMessage());
        }

        // Initialize the location fields

    }


    public void initializeGo() {
        client = new OkHttpClient();
        try {
            if (auth != null) {
                go = new PokemonGo(auth, client);
            } else {
                PTCLogin login = new PTCLogin(client);
                auth = login.login(username, password);
                go = new PokemonGo(auth, client);
            }
            go.getMap().setUseCache(false);
            SharedPreferences.Editor editor = sharedPref.edit();
            editor.putString("username", username);
            editor.putString("password", password);
            byte[] array = auth.toByteArray();
            String saveThis = Base64.encodeToString(array, Base64.DEFAULT);
            editor.putString("auth", saveThis);
            editor.apply();
            Intent it = new Intent(this, PokeService.class);
            it.putExtra("status", "initialized");
            it.putExtra("type", "status");
            startService(it);
        } catch (LoginFailedException e) {
            Toast.makeText(this, "Login Error, probably down!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Some other error! " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    class MyTimerTask extends TimerTask {
        MyTimerTask.MyAsyncTask atask;
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

                for (int y = -spanY; y <= spanY; y += stepY) {
                    for (int x = -spanX; x <= spanX; x += stepX) {
                        double lat = loc.latitude + y * 0.001;
                        double new_x = (x * 0.001) / Math.cos(loc.longitude);
                        double lng = loc.longitude + new_x;
                        search_loc = new LatLng(lat, lng);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException es) {
                            es.printStackTrace();
                        }

                        try {
                            go.setLocation(search_loc.latitude, search_loc.longitude, 0);
                        } catch (Exception e) {
                            Log.e(TAG, e.getMessage());
                        }

                        try {
                            Collection<WildPokemonOuterClass.WildPokemon> poke = go.getMap().getMapObjects().getWildPokemons();
                            //TODO: Add notification for CatchablePokemon?
                            for (WildPokemonOuterClass.WildPokemon p : poke) {
                                String encounterID = String.valueOf(p.getEncounterId());
                                synchronized (pokemons) {
                                    if (!pokemons.getAllKeys().contains(encounterID)) {
                                        long till = System.currentTimeMillis() + p.getTimeTillHiddenMs();
                                        WildPokemonExt p_ext = new WildPokemonExt(p, till);
                                        String name = PokemonIdOuterClass.PokemonId.valueOf(p.getPokemonData().getPokemonIdValue()).name();
                                        PokemonSimple p_simple = new PokemonSimple(till, String.valueOf(p.getEncounterId()), p.getLatitude(), p.getLongitude(), p.getPokemonData().getPokemonIdValue(), name);

                                        pokemons.put(encounterID, p_simple);

                                        Intent it = new Intent(FetchService.this, PokeService.class);
                                        Bundle pokedata = new Bundle();
                                        pokedata.putString("encounterid",  String.valueOf(p_simple.encounterId));
                                        pokedata.putLong("timestampHidden",  p_simple.timestampHidden);
                                        pokedata.putDouble("latitude",  p_simple.latitude);
                                        pokedata.putDouble ("longitude",  p_simple.longitude);
                                        pokedata.putInt("pokemonid",  p_simple.pokemonid);
                                        pokedata.putString("name",  p_simple.name);

                                        it.putExtras ( pokedata );
                                        it.putExtra ( "type", "new_pokemon" );
                                        startService(it);
                                    }
                                }
                            }
                        } catch (Exception ex) {

                            Log.d(TAG, "Error:" + ex);

                            try {
                                Thread.sleep(10000);
                            } catch (InterruptedException e1) {
                                e1.printStackTrace();
                            }


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
            atask = new MyTimerTask.MyAsyncTask();
            atask.execute(loc);
        }
    }

}
