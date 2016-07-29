package com.benjy3gg.livemapgo;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.auth.GoogleUserCredentialProvider;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;
import com.pokegoapi.util.SystemTimeImpl;
import com.pokegoapi.util.Time;

import okhttp3.OkHttpClient;

public class GoogleLoginTask extends AsyncTask<Void, Void, PokemonGo> {

    private static String TAG = "GOOGLELOGINTASK";
    private String refreshToken;
    private final String username;
    private final String password;
    OkHttpClient client;
    SharedPreferences sharedPrefs;
    AuthenticationListener listener;
    String loginUrl;
    String reason;

    public GoogleLoginTask(OkHttpClient client, String refreshToken, String username, String password, SharedPreferences sharedPrefs, AuthenticationListener listener) {
        this.client = client;
        this.refreshToken = refreshToken;
        this.username = username;
        this.password = password;
        this.sharedPrefs = sharedPrefs;
        this.listener = listener;
    }

    @Override
    protected PokemonGo doInBackground(Void... params) {
        Log.d(TAG, "Started logintask");

        PokemonGo go = null;

        try {
            Time time = new SystemTimeImpl();
            refreshToken = sharedPrefs.getString("googleToken", null);
            if(username == null && password == null && refreshToken == null) {
                String authReq = sharedPrefs.getString("authReq", null);
                if(authReq != null) {
                    GoogleUserCredentialProvider provider = new GoogleUserCredentialProvider(client);
                    provider.login(authReq);
                    refreshToken = provider.getRefreshToken();
                    sharedPrefs.edit().putString("googleToken", refreshToken).commit();
                }
            }
            if (refreshToken != null) {
                GoogleUserCredentialProvider credToken = new GoogleUserCredentialProvider(client, refreshToken, new SystemTimeImpl());
                go = new PokemonGo(credToken, client, time);
                SharedPreferences.Editor editor = sharedPrefs.edit();
                editor.putString("googleToken", refreshToken);
                editor.apply();
            } else {
                //TODO: RETURN ERROR NO AUTHENTICATION METHOD PROVIDED
            }
        } catch (LoginFailedException e) {
            reason = "LoginFailedException";
        } catch (RemoteServerException e) {
            reason = "RemoteServerException";
        }
        return go;
    }

    @Override
    protected void onPostExecute(PokemonGo go) {
        if(go != null) {
            listener.onGoAvailable(go);
        }else {
            //we need to show the user a webview
            listener.onGoNeedsAuthentification();
        }

        //TODO: Error handling
    }

    @Override
    protected void onPreExecute() {
        //TODO: Error handling
    }

    @Override
    protected void onProgressUpdate(Void... values) {
        //TODO: Error handling
    }
}