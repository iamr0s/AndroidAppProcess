package com.rosan.app_process;

import android.os.IBinder;
import android.os.IInterface;
import android.os.Parcel;
import android.os.RemoteException;


import java.io.FileDescriptor;

class BinderWrapper implements IBinder {
    private final IProcessManager mManager;

    private final IBinder mBinder;

    BinderWrapper(IProcessManager manager, IBinder binder) {
        this.mManager = manager;
        this.mBinder = binder;
    }

    @Override
    public String getInterfaceDescriptor() throws RemoteException {
        return mBinder.getInterfaceDescriptor();
    }

    @Override
    public boolean pingBinder() {
        return mBinder.pingBinder();
    }

    @Override
    public boolean isBinderAlive() {
        return mBinder.isBinderAlive();
    }

    @Override
    public IInterface queryLocalInterface(String descriptor) {
        return null;
    }

    @Override
    public void dump(FileDescriptor fd, String[] args) throws RemoteException {
        mBinder.dump(fd, args);
    }

    @Override
    public void dumpAsync(FileDescriptor fd, String[] args) throws RemoteException {
        mBinder.dumpAsync(fd, args);
    }

    @Override
    public boolean transact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        return AppProcess.remoteTransact(mManager, mBinder, code, data, reply, flags);
    }

    @Override
    public void linkToDeath(DeathRecipient recipient, int flags) throws RemoteException {
        mBinder.linkToDeath(recipient, flags);
    }

    @Override
    public boolean unlinkToDeath(DeathRecipient recipient, int flags) {
        return mBinder.unlinkToDeath(recipient, flags);
    }
}
