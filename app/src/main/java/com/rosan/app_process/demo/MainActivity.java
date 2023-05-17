package com.rosan.app_process.demo;

import android.content.ComponentName;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.rosan.app_process.AppProcess;
import com.rosan.app_process.NewProcessImpl;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppProcess appProcess = new AppProcess.RootSystem();
        new Thread(() -> {
            Log.e("r0s", "app_process init: " + appProcess.init(this.getPackageName()));
            Log.e("r0s", "app_process new: " + appProcess.startProcess(new ComponentName(this, NewProcessImpl.class)));
        }).start();
    }
}