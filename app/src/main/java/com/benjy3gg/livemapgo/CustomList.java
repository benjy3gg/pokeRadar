package com.benjy3gg.livemapgo;

import android.app.Activity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class CustomList extends ArrayAdapter<String>{

    private final Activity context;
    private final List<String> web;
    private final List<Integer> imageId;
    public boolean [] notifyCheckBoxState;
    public boolean [] showCheckBoxState;
    public CustomList(Activity context,
                      List<String> web, List<Integer> imageId, boolean[] checksShow, boolean[] checksNotify) {
        super(context, R.layout.list_single, web);
        this.context = context;
        this.web = web;
        this.imageId = imageId;

        this.notifyCheckBoxState = checksNotify;
        this.showCheckBoxState = checksShow;

    }
    @Override
    public View getView(int position, View view, ViewGroup parent) {

        LayoutInflater inflater = context.getLayoutInflater();
        View rowView= inflater.inflate(R.layout.list_single, null, true);
        TextView txtTitle = (TextView) rowView.findViewById(R.id.text);

        ImageView imageView = (ImageView) rowView.findViewById(R.id.img);

        CheckBox showBox = (CheckBox) rowView.findViewById(R.id.checkBoxShow);
        ViewTag showTag = new ViewTag(position, "show_");
        showBox.setTag(showTag);
        showBox.setOnCheckedChangeListener(checkListener);
        showBox.setChecked(showCheckBoxState[position]);

        CheckBox notifyBox = (CheckBox) rowView.findViewById(R.id.checkBoxNotify);
        ViewTag notifyTag = new ViewTag(position, "notify_");
        notifyBox.setTag(notifyTag);
        notifyBox.setOnCheckedChangeListener(checkListener);
        notifyBox.setChecked(notifyCheckBoxState[position]);

        txtTitle.setText(web.get(position));

        imageView.setImageResource(imageId.get(position));
        return rowView;
    }

    CheckBox.OnCheckedChangeListener checkListener = new CheckBox.OnCheckedChangeListener()
    {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
            ViewTag tag = (ViewTag)compoundButton.getTag();
            if(tag.type.equals("notify_")) {
                notifyCheckBoxState[tag.position] = b;
            }else {
                showCheckBoxState[tag.position] = b;
            }
        }
    };
}