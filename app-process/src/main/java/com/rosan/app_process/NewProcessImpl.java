package com.rosan.app_process;

import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.Keep;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class NewProcessImpl extends INewProcess.Stub {
    @Keep
    public NewProcessImpl() {
        super();
    }

    @Override
    public void exit(int code) {
        System.exit(code);
    }

    private boolean targetTransact(IBinder binder, int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        long id = clearCallingIdentity();
        boolean result = binder.transact(code, data, reply, flags);
        restoreCallingIdentity(id);
        return result;
    }

    @Override
    public IRemoteProcess remoteProcess(List<String> cmdList, Map<String, String> env, String directory) {
        ProcessBuilder builder = new ProcessBuilder().command(cmdList);
        if (directory != null) builder = builder.directory(new File(directory));
        if (env != null) builder.environment().putAll(env);
        File file = null;
        try {
            return new RemoteProcessImpl(builder.start());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code != Binder.FIRST_CALL_TRANSACTION + 2)
            return super.onTransact(code, data, reply, flags);
        Parcel targetData = Parcel.obtain();
        try {
            data.enforceInterface(this.asBinder().getInterfaceDescriptor());
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
