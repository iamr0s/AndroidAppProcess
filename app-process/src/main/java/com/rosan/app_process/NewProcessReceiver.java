package com.rosan.app_process;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Exchanger;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

abstract class NewProcessReceiver extends BroadcastReceiver {
    public static IBinder start(Context context, AppProcess appProcess, ComponentName componentName) {
        final String token = UUID.randomUUID().toString();

        final Exchanger<IBinder> exchanger = new Exchanger<>();

        HandlerThread worker = new HandlerThread("IPCWorker");
        worker.start();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (token.equals(intent.getStringExtra(NewProcessReceiver.EXTRA_TOKEN))) {
                    IBinder binder = intent.getExtras().getBinder(NewProcessReceiver.EXTRA_NEW_PROCESS);
                    try {
                        exchanger.exchange(binder);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        };

        try {
            IntentFilter filter = new IntentFilter(ACTION_SEND_NEW_PROCESS);
            Handler handler = new Handler(worker.getLooper());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, null, handler, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter, null, handler);
            }

            appProcess.start(context.getPackageCodePath(), NewProcess.class, new String[]{
                    "--package=" + context.getPackageName(),
                    "--token=" + token,
                    "--component=" + componentName.flattenToString()
            });

            return exchanger.exchange(null, 15, TimeUnit.SECONDS);

        } catch (TimeoutException | InterruptedException | IOException e) {
            return null;
        } finally {
            try {
                context.unregisterReceiver(receiver);
            } catch (Exception ignored) {
            }
            worker.quitSafely();
        }
    }

    public static String ACTION_SEND_NEW_PROCESS = "com.rosan.app_process.send.new_process";

    public static String EXTRA_NEW_PROCESS = "new_process";

    public static String EXTRA_TOKEN = "token";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Bundle extras = intent.getExtras();
        if (extras == null) return;
        if (!Objects.equals(action, ACTION_SEND_NEW_PROCESS)) return;
        String token = extras.getString(EXTRA_TOKEN);
        if (token == null) return;
        IBinder binder = extras.getBinder(EXTRA_NEW_PROCESS);
        if (binder == null) return;
        onReceive(new NewProcessResult(token, binder));
    }

    abstract void onReceive(NewProcessResult result);

    static class NewProcessResult {
        private final @NonNull String mToken;

        private final @NonNull IBinder mBinder;

        NewProcessResult(@NonNull String token, @NonNull IBinder binder) {
            mToken = token;
            mBinder = binder;
        }

        @NonNull
        public String getToken() {
            return mToken;
        }

        @NonNull
        public IBinder getBinder() {
            return mBinder;
        }
    }
}