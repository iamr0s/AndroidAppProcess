package com.rosan.app_process.demo;

import android.app.Activity;
import android.content.pm.IPackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.widget.TextView;
import android.widget.Toast;

import com.rosan.app_process.AppProcess;

import java.util.Arrays;

public class MainActivity extends Activity {

    private void makeText(String msg) {
        runOnUiThread(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AppProcess process = new AppProcess.Default();
        process.init(this);

        IBinder manager = ServiceManager.getService("package");
        IBinder binderWrapper;
        try {
            binderWrapper = process.binderWrapper(manager);
        } catch (IllegalStateException ignored) {
            makeText("AppProcess: please call init() first.");
            finishAndRemoveTask();
            return;
        }
        IPackageManager managerWrapper = IPackageManager.Stub.asInterface(binderWrapper);

        String[] libraries = new String[]{"NULL"};
        try {
            if (managerWrapper != null) {
                libraries = managerWrapper.getSystemSharedLibraryNames();
            }
        } catch (RemoteException ignored) {
            makeText("RemoteException occurred.");
        }

        TextView text = findViewById(R.id.text);
        text.setText(Arrays.toString(libraries));

        process.close();
    }
}
