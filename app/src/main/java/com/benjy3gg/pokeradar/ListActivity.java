package com.benjy3gg.pokeradar;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.Toast;
import android.app.Activity;

import java.util.ArrayList;
import java.util.List;

import POGOProtos.Enums.PokemonIdOuterClass;

public class ListActivity extends Activity {
    ListView list;
    private List<String> web;
    private List<Integer> imageId;
    private SharedPreferences sharedPref;


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


        CustomList adapter = new
                CustomList(ListActivity.this, web, imageId);
        list=(ListView)findViewById(R.id.list);
        list.setAdapter(adapter);
        list.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                Toast.makeText(ListActivity.this, "You Clicked at " +web.get(position), Toast.LENGTH_SHORT).show();
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean("notify_"+(position+1), ((CheckBox)list.findViewById(R.id.checkBoxNotify)).isChecked());
                editor.putBoolean("show_"+(position+1), ((CheckBox)list.findViewById(R.id.checkBoxShow)).isChecked());
                editor.apply();
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