package com.rosan.app_process;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class ProcessManager extends IProcessManager.Stub {
    // transact sub service binder before destroy
    public static int TRANSACT_ON_DESTROY_CODE = 0x010000EE; // 16777454

    private final List<Process> mServiceProcesses = new ArrayList<>();

    private final List<IBinder> mServiceIBinders = new ArrayList<>();

    private IBinder mClientBinder;

    private final DeathRecipient mClientDeadRecipient = new DeathRecipient() {
        @Override
        public void binderDied() {
            synchronized (this) {
                exit(0);
            }
        }
    };

    @Keep
    public ProcessManager() {
        super();
    }

    @Override
    public void exit(int code) {
        synchronized (mServiceProcesses) {
            synchronized (mServiceIBinders) {
                for (Process process : mServiceProcesses) {
                    try {
                        process.destroyForcibly();
                    } catch (Throwable ignored) {
                    }
                }
                for (IBinder iBinder : mServiceIBinders) {
                    Parcel data = Parcel.obtain();
                    Parcel reply = Parcel.obtain();
                    try {
                        iBinder.transact(TRANSACT_ON_DESTROY_CODE, data, reply, Binder.FLAG_ONEWAY);
                    } catch (Throwable ignored) {
                    } finally {
                        data.recycle();
                        reply.recycle();
                    }
                }
                System.exit(code);
            }
        }
    }

    private boolean targetTransact(IBinder binder, int code, Parcel data, Parcel reply, int flags) throws
            RemoteException {
        try {
            return AppProcess.binderWithCleanCallingIdentity(() -> binder.transact(code, data, reply, flags));
        } catch (RemoteException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public IRemoteProcess remoteProcess(List<String> cmdList, Map<String, String> env, String directory) {
        ProcessBuilder builder = new ProcessBuilder().command(cmdList);
        if (directory != null) builder = builder.directory(new File(directory));
        if (env != null) builder.environment().putAll(env);
        try {
            Process process = builder.start();

            synchronized (mServiceProcesses) {
                mServiceProcesses.add(process);
            }

            return new RemoteProcessImpl(process);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public ParcelableBinder serviceBinder(ComponentName componentName) {
        return serviceBinder(null, null, componentName);
    }

    public ParcelableBinder serviceBinder(@Nullable Context context, @Nullable ClassLoader classLoader, ComponentName componentName) {
        try {
            IBinder iBinder;
            if (context != null && classLoader != null)
                iBinder = NewProcess.createBinder(context, classLoader, componentName.getClassName());
            else if (context != null)
                iBinder = NewProcess.createBinder(context, componentName);
            else iBinder = NewProcess.createBinder(componentName);

            synchronized (mServiceIBinders) {
                mServiceIBinders.add(iBinder);
            }

            return new ParcelableBinder(iBinder);
        } catch (PackageManager.NameNotFoundException | NoSuchFieldException |
                 InvocationTargetException | NoSuchMethodException | IllegalAccessException |
                 ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }


    @Override
    public void linkDeathTo(@Nullable ParcelableBinder pBinder) throws RemoteException {
        synchronized (this) {
            try {
                if (mClientBinder != null) mClientBinder.unlinkToDeath(mClientDeadRecipient, 0);
            } catch (Throwable ignored) {
            }
            if (pBinder == null || pBinder.getBinder() == null) return;
            IBinder iBinder = pBinder.getBinder();
            iBinder.linkToDeath(mClientDeadRecipient, 0);
            mClientBinder = iBinder;
        }
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws
            RemoteException {
        if (code != Binder.FIRST_CALL_TRANSACTION + 2)
            return super.onTransact(code, data, reply, flags);
        Parcel targetData = Parcel.obtain();
        try {
            data.enforceInterface(Objects.requireNonNull(this.asBinder().getInterfaceDescriptor()));
            IBinder binder = data.readStrongBinder();
            int targetCode = data.readInt();
            int targetFlags = data.readInt();
            targetData.appendFrom(data, data.dataPosition(), data.dataAvail());
            return targetTransact(binder, targetCode, targetData, reply, targetFlags);
        } finally {
            targetData.recycle();
        }
    }
}
