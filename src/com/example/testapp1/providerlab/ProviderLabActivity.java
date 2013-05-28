package com.example.testapp1.providerlab;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.example.testapp1.R;

/**
 * Created by mb on 24.05.13.
 */
public class ProviderLabActivity extends Activity {
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_provider_lab);
    }

    public void queryProvider(View view) {
        Uri uri;
        try {
            uri = Uri.parse(((TextView) findViewById(R.id.uri)).getText().toString());
        } catch (Exception e) {
            Toast.makeText(this, "Uri.parse: " + e.getClass() + ": " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        startActivity(new Intent(this, QueryResultActivity.class).setData(uri));
    }
}