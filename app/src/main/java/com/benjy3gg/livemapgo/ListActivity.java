package com.benjy3gg.livemapgo;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;

import java.util.ArrayList;
import java.util.List;

import POGOProtos.Enums.PokemonIdOuterClass;

public class ListActivity extends AppCompatActivity {
    ListView list;
    private List<String> web;
    private List<Integer> imageId;
    private SharedPreferences sharedPref;
    private Button btn;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        sharedPref = getSharedPreferences("credentials", Context.MODE_PRIVATE);

        web = new ArrayList<>();
        imageId = new ArrayList<>();

        for(int i=1; i <=151; i++) {
            web.add(PokemonIdOuterClass.PokemonId.valueOf(i).name());
            imageId.add(getResourseId("prefix_" + i, "drawable"));
        }

        boolean[] checksShow = new boolean[web.size()];
        boolean[] checksNotify = new boolean[web.size()];
        for(int i=0; i < checksShow.length; i++) {
            checksNotify[i] = sharedPref.getBoolean("notify_"+(i+1), false);
            checksShow[i] = sharedPref.getBoolean("show_"+(i+1), true);
        }


        final CustomList adapter = new
                CustomList(ListActivity.this, web, imageId, checksShow, checksNotify);
        list=(ListView)findViewById(R.id.list);
        list.setAdapter(adapter);

        btn = (Button)findViewById(R.id.saveButton);
        btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                boolean[] checksShow = adapter.showCheckBoxState;
                boolean[] checksNotify = adapter.notifyCheckBoxState;
                SharedPreferences.Editor editor = sharedPref.edit();
                for(int i=0; i < checksShow.length; i++) {
                    editor.putBoolean("notify_"+(i+1), checksNotify[i]);
                    editor.putBoolean("show_"+(i+1), checksShow[i]);
                }
                editor.commit();
            }
        });

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