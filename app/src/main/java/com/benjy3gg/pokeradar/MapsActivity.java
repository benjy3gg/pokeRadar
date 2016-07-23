package com.benjy3gg.pokeradar;

import android.Manifest;
import android.animation.Animator;
import android.animation.IntEvaluator;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import android.view.LayoutInflater;
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
import com.txusballesteros.bubbles.BubbleLayout;
import com.txusballesteros.bubbles.BubblesManager;

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

public class MapsActivity extends AppCompatActivity {

    private int OVERLAY_PERMISSION_REQ_CODE = 1234;
    private static final int GPS_PERMISSION_REQ_CODE = 1235;

    private EditText editUsername;
    private EditText editPassword;
    private EditText editApikey;
    private SharedPreferences sharedPref;

    public List<Boolean> shouldNotify = new ArrayList<>();
    public List<Boolean> shouldShow = new ArrayList<>();
    private Button btn;

    private RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo auth_l;
    private Button btnToken;
    private DiscreteSlider sliderWidth;
    private DiscreteSlider sliderHeight;
    private BubblesManager mBubblesManager;
    private BubbleLayout bubbleView;
    private BroadcastReceiver serviceReceiver;

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

        serviceReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                Toast.makeText(getApplicationContext(), "received message in service..!", Toast.LENGTH_SHORT).show();
                Log.d("Service", "Sending broadcast to activity");
                hideLoginButtons();
            }
        };

        allowOverlay();
        requestGpsPermission();
        setupLayout();
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
    }

    public void hideLoginButtons() {

    }

    public void setupLayout() {

        sharedPref = getSharedPreferences("credentials", Context.MODE_PRIVATE);
        btnToken = (Button) findViewById(R.id.loginToken);
        final String loadedAuth = sharedPref.getString("auth", null);
        if(loadedAuth != null) {
            try {
                auth_l = RequestEnvelopeOuterClass.RequestEnvelope.AuthInfo.parseFrom(Base64.decode(loadedAuth, Base64.DEFAULT));

                if(auth_l != null) {
                    btnToken.setEnabled(true);
                    btnToken.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                        Intent it = new Intent(MapsActivity.this, FetchService.class);
                        it.putExtra("type", "login");
                        it.putExtra("auth", loadedAuth);
                        startService(it);
                        }
                    });
                }else {
                    btnToken.setEnabled(false);
                }
            } catch (InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }

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
                    Intent it = new Intent(MapsActivity.this, FetchService.class);
                    it.putExtra("type", "login");
                    it.putExtra("username", username_e);
                    it.putExtra("password", password_e);
                    startService(it);
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


                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        GPS_PERMISSION_REQ_CODE);
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        switch (requestCode) {
            case GPS_PERMISSION_REQ_CODE: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "No GPS Permission granted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "No GPS Permission granted", Toast.LENGTH_SHORT).show();
                }
                Toast.makeText(this, "GPS Permission granted", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }


    public void allowOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE);
            }else {

            }
        }
    }

}
