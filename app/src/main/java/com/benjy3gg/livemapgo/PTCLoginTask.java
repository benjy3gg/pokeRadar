package com.benjy3gg.livemapgo;

import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import com.pokegoapi.api.PokemonGo;
import com.pokegoapi.auth.CredentialProvider;
import com.pokegoapi.auth.PtcCredentialProvider;
import com.pokegoapi.exceptions.LoginFailedException;
import com.pokegoapi.exceptions.RemoteServerException;

import okhttp3.OkHttpClient;

public class PTCLoginTask extends AsyncTask<Void, Void, PokemonGo> {

    public static String TAG = "PTCLOGINTASK";

    OkHttpClient client;
    String username;
    String password;
    SharedPreferences sharedPrefs;
    AuthenticationListener listener;
    String reason;

    public PTCLoginTask(OkHttpClient client, String username, String password, SharedPreferences sharedPrefs, AuthenticationListener listener) {
        this.client = client;
        this.username = username;
        this.password = password;
        this.sharedPrefs = sharedPrefs;
        this.listener = listener;
    }

    @Override
    protected PokemonGo doInBackground(Void... params) {
        Log.d(TAG, "Started logintask");
        PokemonGo go = null;

        CredentialProvider auth = null;
        try {
            go = new PokemonGo(new PtcCredentialProvider(client,username,password),client);
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
            SharedPreferences.Editor editor = sharedPrefs.edit();
            editor.putString("username", username);
            editor.putString("password", password);
            editor.apply();
            listener.onGoAvailable(go);
        }else {
            listener.onGoFailed(reason);
        }
    }

    @Override
    protected void onPreExecute() {
    }

    @Override
    protected void onProgressUpdate(Void... values) {
    }
}