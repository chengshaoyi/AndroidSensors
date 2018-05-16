package com.numericcal.androidsensors;

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "AS.Main";

    TextView statusText;
    TextureView camView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        statusText = (TextView) findViewById(R.id.statusText);
        camView = (TextureView) findViewById(R.id.camView);
    }

    @Override
    protected void onResume() {
        super.onResume();;

        Texture.getTextureSurface(camView)
                .subscribe(tr -> {
                    Log.wtf(TAG, "Thread: " + Thread.currentThread().getName());
                    statusText.setText("Surface is ready! Size: " + tr.width + " x " + tr.height);
                    Log.wtf(TAG, "Surface is ready!");
                });
    }

    @Override
    protected void onPause() {

        super.onPause();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
    }
}
