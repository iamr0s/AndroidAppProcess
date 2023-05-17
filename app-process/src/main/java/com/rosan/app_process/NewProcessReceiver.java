package com.rosan.app_process;

import android.app.ActivityThread;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.IBinder;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

abstract class NewProcessReceiver extends BroadcastReceiver {
    public static @NonNull INewProcess start(AppProcess appProcess, ComponentName componentName) {
        Context context = ActivityThread.currentActivityThread().getApplication();
        String token = UUID.randomUUID().toString();
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SEND_NEW_PROCESS);
        LinkedBlockingQueue<AtomicReference<NewProcessResult>> queue = new LinkedBlockingQueue<>();
        BroadcastReceiver receiver = new NewProcessReceiver() {
            @Override
            void onReceive(NewProcessResult result) {
                if (!result.getToken().equals(token)) return;
                queue.offer(new AtomicReference<>(result));
            }
        };
        context.registerReceiver(receiver, filter);
        ExecutorService executorService = Executors.newCachedThreadPool();
        Future<AtomicReference<NewProcessResult>> future = executorService.submit(queue::take);
        try {
            AtomicReference<Process> process = new AtomicReference<>(null);
            executorService.execute(() -> {
                try {
                    process.set(appProcess.start(context.getPackageCodePath(), NewProcess.class, new String[]{
                            String.format("--package=%s", context.getPackageName()),
                            String.format("--token=%s", token),
                            String.format("--component=%s", componentName.flattenToString())
                    }));
                    process.get().waitFor();
                } catch (InterruptedException | IOException ignored) {
                } finally {
                    if (process.get() != null) process.get().destroy();
                }
                queue.offer(new AtomicReference<>(null));
            });
            NewProcessResult result = future.get(15, TimeUnit.SECONDS).get();
            return result != null ? result.getNewProcess() : null;
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            e.printStackTrace();
            return null;
        } finally {
            context.unregisterReceiver(receiver);
            executorService.shutdown();
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
        INewProcess newProcess = INewProcess.Stub.asInterface(binder);
        onReceive(new NewProcessResult(token, newProcess));
    }

    abstract void onReceive(NewProcessResult result);

    static class NewProcessResult {
        private final @NonNull String mToken;

        private final @NonNull INewProcess mNewProcess;

        NewProcessResult(@NonNull String token, @NonNull INewProcess newProcess) {
            mToken = token;
            mNewProcess = newProcess;
        }

        @NonNull
        public String getToken() {
            return mToken;
        }

        @NonNull
        public INewProcess getNewProcess() {
            return mNewProcess;
        }
    }
}