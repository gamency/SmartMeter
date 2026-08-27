package com.example.smartmeter;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNavigationView = findViewById(R.id.bottom_navigation);
        bottomNavigationView.setOnNavigationItemSelectedListener(new BottomNavigationView.OnNavigationItemSelectedListener() {
            @Override
            public boolean onNavigationItemSelected(@NonNull MenuItem item) {
                if (item.getItemId() == R.id.nav_overview) {
                    getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragment_container, new OverviewFragment())
                            .commit();
                    return true;
                } else if (item.getItemId() == R.id.nav_meter) {
                    // 跳转到 SmartPhotoActivity 而不是 Fragment
                    Intent intent = new Intent(MainActivity.this, SmartPhotoActivity.class);
                    startActivity(intent);
                    return true;
                }
                return false;
            }
        });

        // 默认显示用电总览
        bottomNavigationView.setSelectedItemId(R.id.nav_overview);
    }
}