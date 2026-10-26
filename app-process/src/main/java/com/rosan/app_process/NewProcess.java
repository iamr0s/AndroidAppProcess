package com.rosan.app_process;

import android.annotation.SuppressLint;
import android.app.ActivityThread;
import android.app.LoadedApk;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Process;
import android.util.ArraySet;
import android.util.Log;


import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class NewProcess {
    private static final String TAG = "NewProcess";

    private static ActivityThread mActivityThread = null;

    public static void main(String[] args) throws Throwable {
        try {
            innerMain(args);
        } catch (Throwable e) {
            e.printStackTrace();
            Log.e(TAG, "main", e);
            throw e;
        }
    }

    private static void innerMain(String[] args) throws Throwable {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("");
        }
        preloadSystemAssets();
        Options options = new Options().addOption(Option.builder().longOpt("package").hasArg().required().type(String.class).build()).addOption(Option.builder().longOpt("token").hasArg().required().type(String.class).build()).addOption(Option.builder().longOpt("component").hasArg().required().type(String.class).build());
        CommandLine cmdLine = new DefaultParser().parse(options, args);
        String packageName = cmdLine.getOptionValue("package");
        String token = cmdLine.getOptionValue("token");
        String component = cmdLine.getOptionValue("component");
        ComponentName componentName = ComponentName.unflattenFromString(component);

        Bundle bundle = new Bundle();
        IBinder binder = createBinder(componentName);
        bundle.putBinder(NewProcessReceiver.EXTRA_NEW_PROCESS, binder);
        bundle.putString(NewProcessReceiver.EXTRA_TOKEN, token);
        Intent intent = new Intent(NewProcessReceiver.ACTION_SEND_NEW_PROCESS)
                .setPackage(packageName)
                .putExtras(bundle);
        getSystemContext().sendBroadcast(intent);
        Looper.loop();
    }

    public static IBinder createBinder(ComponentName componentName) throws PackageManager.NameNotFoundException, NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException, ClassNotFoundException {
        return createBinder(getUIDContext(), componentName);
    }

    public static IBinder createBinder(Context context, ComponentName componentName) throws PackageManager.NameNotFoundException, ClassNotFoundException {
        Context packageContext = getSystemContext().createPackageContext(componentName.getPackageName(), Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
        ClassLoader classLoader = packageContext.getClassLoader();
        return createBinder(context, classLoader, componentName.getClassName());
    }

    public static IBinder createBinder(Context context, ClassLoader classLoader, String className) throws ClassNotFoundException {
        Class<?> clazz = classLoader.loadClass(className);
        Constructor<?> constructor = null;
        try {
            constructor = clazz.getDeclaredConstructor(Context.class);
        } catch (NoSuchMethodException ignored) {
        }
        Object result;
        try {
            result = constructor != null ? constructor.newInstance(context) : clazz.newInstance();
        } catch (IllegalAccessException | InstantiationException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
        return ((IInterface) result).asBinder();
    }

    public static List<String> getPackagesForUid(Context context, int uid) {
        String[] packageNames = context.getPackageManager().getPackagesForUid(uid);
        if (packageNames == null) return Collections.emptyList();
        return Arrays.asList(packageNames);
    }

    private static void preloadSystemAssets() {
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.P) return;
        try {
            Class<?> apkAssetsClass = Class.forName("android.content.res.ApkAssets");
            Object apkAssets;
            try {
                apkAssets = apkAssetsClass.getMethod("loadFromPath", String.class, boolean.class)
                        .invoke(null, "/system/framework/framework-res.apk", true);
            } catch (NoSuchMethodException e) {
                apkAssets = apkAssetsClass.getMethod("loadFromPath", String.class, int.class)
                        .invoke(null, "/system/framework/framework-res.apk", 1);
            }

            Object apkAssetsArray = Array.newInstance(apkAssetsClass, 1);
            Array.set(apkAssetsArray, 0, apkAssets);

            Field systemApkAssets = AssetManager.class.getDeclaredField("sSystemApkAssets");
            systemApkAssets.setAccessible(true);
            systemApkAssets.set(null, apkAssetsArray);

            ArraySet<Object> apkAssetsSet = new ArraySet<>();
            apkAssetsSet.add(apkAssets);
            Field systemApkAssetsSet = AssetManager.class.getDeclaredField("sSystemApkAssetsSet");
            systemApkAssetsSet.setAccessible(true);
            systemApkAssetsSet.set(null, apkAssetsSet);

            Constructor<AssetManager> constructor = AssetManager.class.getDeclaredConstructor(boolean.class);
            constructor.setAccessible(true);
            AssetManager assets = constructor.newInstance(true);

            Method setApkAssets = AssetManager.class.getDeclaredMethod("setApkAssets",
                    apkAssetsArray.getClass(), boolean.class);
            setApkAssets.setAccessible(true);
            setApkAssets.invoke(assets, apkAssetsArray, false);

            Field sSystem = AssetManager.class.getDeclaredField("sSystem");
            sSystem.setAccessible(true);
            sSystem.set(null, assets);
            Log.i(TAG, "preloadSystemAssets ok");
        } catch (Throwable t) {
            Log.e(TAG, "preloadSystemAssets failed", t);
        }
    }

    private static ActivityThread getActivityThread() {
        if (mActivityThread != null) return mActivityThread;
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        if (Looper.myLooper() == null) Looper.prepare();

        try {
            Constructor<ActivityThread> constructor = ActivityThread.class.getDeclaredConstructor();
            constructor.setAccessible(true);
            ActivityThread thread = constructor.newInstance();

            Method attach = ActivityThread.class.getDeclaredMethod("attach", boolean.class, long.class);
            attach.setAccessible(true);
            attach.invoke(thread, true, 0L);

            mActivityThread = thread;
        } catch (Throwable ignored) {
            mActivityThread = ActivityThread.systemMain();
        }

        Objects.requireNonNull(mActivityThread);
        return mActivityThread;
    }

    private static Context getSystemContext() {
        return getActivityThread().getSystemContext();
    }

    public static Context getUIDContext() throws PackageManager.NameNotFoundException, NoSuchFieldException, InvocationTargetException, NoSuchMethodException, IllegalAccessException {
        Context context = getSystemContext();

        int uid = Process.myUid();
        List<String> packageNames = getPackagesForUid(context, uid);
        if (packageNames.isEmpty()) return context;
        if (packageNames.contains(context.getPackageName()) && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || packageNames.contains(context.getOpPackageName())))
            return context;

        return createAppContext(context, packageNames.get(0));
    }

    @SuppressLint("PrivateApi")
    private static Context createAppContext(Context context, String packageName) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException, PackageManager.NameNotFoundException, NoSuchFieldException {
        Context impl = context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
        while (impl instanceof ContextWrapper) {
            impl = ((ContextWrapper) impl).getBaseContext();
        }

        Method method = impl.getClass().getDeclaredMethod("createAppContext", ActivityThread.class, LoadedApk.class);
        method.setAccessible(true);
        return (Context) method.invoke(null, getActivityThread(), getActivityThread().peekPackageInfo(packageName, true));
    }
}