package com.rosan.app_process;

import android.annotation.SuppressLint;
import android.app.ActivityThread;
import android.app.ContextImpl;
import android.app.LoadedApk;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Process;

import androidx.annotation.Keep;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class NewProcess {
    @Keep
    public static void main(String[] args) throws Throwable {
        try {
            innerMain(args);
        } catch (Throwable e) {
            e.printStackTrace();
            throw e;
        }
    }

    private static void innerMain(String[] args) throws ParseException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("");
        }
        Options options = new Options().addOption(Option.builder().longOpt("package").hasArg().required().type(String.class).build()).addOption(Option.builder().longOpt("token").hasArg().required().type(String.class).build()).addOption(Option.builder().longOpt("component").hasArg().required().type(String.class).build());
        CommandLine cmdLine = new DefaultParser().parse(options, args);
        String packageName = cmdLine.getOptionValue("package");
        String token = cmdLine.getOptionValue("token");
        String component = cmdLine.getOptionValue("component");
        ComponentName componentName = ComponentName.unflattenFromString(component);

        if (Looper.getMainLooper() == null) {
            Looper.prepareMainLooper();
        }

        ActivityThread activityThread = ActivityThread.systemMain();
        Context context = activityThread.getSystemContext();
        Context uidContext = createUIDContext(context, activityThread);

        Bundle bundle = new Bundle();
        IBinder binder = createBinder(uidContext, componentName);
        bundle.putBinder(NewProcessReceiver.EXTRA_NEW_PROCESS, binder);
        bundle.putString(NewProcessReceiver.EXTRA_TOKEN, token);
        Intent intent = new Intent(NewProcessReceiver.ACTION_SEND_NEW_PROCESS)
                .setPackage(packageName)
                .putExtras(bundle);
        uidContext.sendBroadcast(intent);
        Looper.loop();
    }

    public static IBinder createBinder(Context context, ComponentName componentName) {
        Context packageContext = context;
        if (!Objects.equals(context.getPackageName(), componentName.getPackageName())) {
            try {
                packageContext = context.createPackageContext(componentName.getPackageName(), Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
            } catch (PackageManager.NameNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        Class<?> clazz;
        try {
            clazz = packageContext.getClassLoader().loadClass(componentName.getClassName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
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

    public static Context createUIDContext(Context context, ActivityThread activityThread) {
        int uid = Process.myUid();
        if (uid < 100000) return context;
        List<String> packageNames = getPackagesForUid(context, uid);
        if (packageNames.isEmpty()) return context;
        if (packageNames.contains(context.getPackageName()) && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || packageNames.contains(context.getOpPackageName())))
            return context;
        String packageName = packageNames.get(0);
        ContextImpl packageContext = createPackageContext(context, packageName);
        if (packageContext == null) return context;
        LoadedApk loadedApk = getLoadedApk(packageContext);
        if (loadedApk == null) return context;
        return createAppContext(packageContext, activityThread, loadedApk);
    }

    public static ContextImpl createPackageContext(Context context, String packageName) {
        ContextImpl packageContext = null;
        try {
            packageContext = (ContextImpl) context.createPackageContext(packageName, Context.CONTEXT_IGNORE_SECURITY | Context.CONTEXT_INCLUDE_CODE);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return packageContext;
    }

    @SuppressLint("PrivateApi")
    public static LoadedApk getLoadedApk(ContextImpl context) {
        try {
            Field field = context.getClass().getDeclaredField("mPackageInfo");
            field.setAccessible(true);
            return (LoadedApk) field.get(context);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }

    @SuppressLint("PrivateApi")
    public static ContextImpl createAppContext(ContextImpl context, ActivityThread activityThread, LoadedApk loadedApk) {
        try {
            Method method = context.getClass().getDeclaredMethod("createAppContext", ActivityThread.class, LoadedApk.class);
            method.setAccessible(true);
            return (ContextImpl) method.invoke(context, activityThread, loadedApk);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            e.printStackTrace();
            return null;
        }
    }
}