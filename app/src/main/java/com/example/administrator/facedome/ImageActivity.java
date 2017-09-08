package com.example.administrator.facedome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;

import com.example.administrator.facedemo.R;
import com.squareup.picasso.Picasso;

import java.io.File;

public class ImageActivity extends Activity {

ImageView imageView;
    public static Intent buildIntent(Context context, String filePath) {
        Intent intent = new Intent(context, ImageActivity.class);
        intent.putExtra("image", filePath);
        return intent;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_image);
        imageView= (ImageView) findViewById(R.id.iv);
        String filePath = getIntent().getStringExtra("image");
        if (filePath != null) {
            Picasso.with(this).load(new File(filePath)).into(imageView);
        }
    }
}
