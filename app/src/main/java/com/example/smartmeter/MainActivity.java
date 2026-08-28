package com.example.smartmeter;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

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
                Fragment fragment = null;
                if (item.getItemId() == R.id.nav_overview) {
                    fragment = new OverviewFragment();
                } else if (item.getItemId() == R.id.nav_meter) {
                    // 继续使用原有的 SmartPhotoActivity 独立页面
                    Intent intent = new Intent(MainActivity.this, SmartPhotoActivity.class);
                    startActivity(intent);
                    return true;
                } else if (item.getItemId() == R.id.nav_chat) {
                    fragment = new ChatFragment(); // 保持原有 Fragment
                }
                if (fragment != null) {
                    getSupportFragmentManager()
                        .beginTransaction()
                        .replace(R.id.fragment_container, fragment)
                        .commit();
                    return true;
                }
                return false;
            }
        });

        // 默认选中总览
        bottomNavigationView.setSelectedItemId(R.id.nav_overview);
    }
}
