package com.rosan.app_process;

import android.app.ActivityThread;
import android.content.ComponentName;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

public abstract class AppProcess implements Closeable {
    private Context mContext = null;

    private INewProcess mNewProcess = null;

    private final Map<String, IBinder> mChildProcess = new HashMap<>();

    public static ProcessParams generateProcessParams(@NonNull String classPath, @NonNull String entryClassName, @NonNull List<String> args) {
        Map<String, String> env = new HashMap<>();
        env.put("CLASSPATH", classPath);
        List<String> cmdList = new ArrayList<>();
        cmdList.add("/system/bin/app_process");
        cmdList.add("/system/bin");
        cmdList.add(entryClassName);
        cmdList.addAll(args);
        return new ProcessParams(cmdList, env, null);
    }

    public static <T> T binderWithCleanCallingIdentity(Callable<T> action) throws Exception {
        final long callingIdentity = Binder.clearCallingIdentity();
        try {
            return action.call();
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    public @NonNull Process start(@NonNull String classPath, @NonNull String entryClassName, @NonNull List<String> args) throws IOException {
        return startProcess(generateProcessParams(classPath, entryClassName, args));
    }

    public @NonNull Process start(@NonNull String classPath, @NonNull String entryClassName, @NonNull String[] args) throws IOException {
        return start(classPath, entryClassName, Arrays.asList(args));
    }

    public <T> @NonNull Process start(@NonNull String classPath, @NonNull Class<T> entryClass, @NonNull List<String> args) throws IOException {
        return start(classPath, entryClass.getName(), args);
    }

    public <T> @NonNull Process start(@NonNull String classPath, @NonNull Class<T> entryClass, @NonNull String[] args) throws IOException {
        return start(classPath, entryClass, Arrays.asList(args));
    }

    public boolean init() {
        return init(ActivityThread.currentActivityThread().getApplication());
    }

    public synchronized boolean init(@NonNull Context context) {
        if (initialized()) return true;
        mContext = context;
        IBinder binder = startProcess(new ComponentName(mContext.getPackageName(), NewProcessImpl.class.getName()));
        if (binder == null) return false;
        mNewProcess = INewProcess.Stub.asInterface(binder);
        try {
            binder.linkToDeath(() -> {
                if (mNewProcess == null || mNewProcess.asBinder() != binder) return;
                mNewProcess = null;
            }, 0);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return initialized();
    }

    public boolean initialized() {
        return mContext != null && mNewProcess != null && mNewProcess.asBinder().isBinderAlive();
    }

    @Override
    public void close() {
        mContext = null;
        if (mNewProcess == null || !mNewProcess.asBinder().pingBinder()) return;
        try {
            mNewProcess.exit(0);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    protected abstract @NonNull Process newProcess(@NonNull ProcessParams params) throws IOException;

    private @NonNull Process startProcess(@NonNull ProcessParams params) throws IOException {
        if (!initialized()) return newProcess(params);
        return remoteProcess(params.getCmdList(), params.getEnv(), params.getDirectory());
    }

    private @NonNull INewProcess requireNewProcess() {
        if (!initialized()) throw new IllegalStateException("please call init() first.");
        return mNewProcess;
    }

    public boolean remoteTransact(IBinder binder, int code, Parcel data, Parcel reply, int flags) {
        IBinder processBinder = requireNewProcess().asBinder();
        Parcel processData = Parcel.obtain();
        try {
            processData.writeInterfaceToken(processBinder.getInterfaceDescriptor());
            processData.writeStrongBinder(binder);
            processData.writeInt(code);
            processData.writeInt(flags);
            processData.appendFrom(data, 0, data.dataSize());
            return processBinder.transact(IBinder.FIRST_CALL_TRANSACTION + 2, processData, reply, 0);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        } finally {
            processData.recycle();
        }
    }

    public IBinder binderWrapper(IBinder binder) {
        return new BinderWrapper(this, binder);
    }

    public Process remoteProcess(@NonNull List<String> cmdList, @Nullable Map<String, String> env, @Nullable String directory) {
        try {
            return new RemoteProcess(requireNewProcess().remoteProcess(cmdList, env, directory));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    private final Map<String, Object> locks = new HashMap<>();

    private synchronized Object buildLock(String token) {
        Object lock = locks.get(token);
        if (lock == null) lock = new Object();
        locks.put(token, lock);
        return lock;
    }

    public IBinder startProcess(@NonNull ComponentName componentName, boolean useCache) {
        if (!useCache) startProcessUnchecked(componentName);
        return startProcess(componentName);
    }

    public IBinder startProcess(@NonNull ComponentName componentName) {
        String token = componentName.flattenToString();
        synchronized (buildLock(token)) {
            IBinder existsBinder = mChildProcess.get(token);
            if (existsBinder != null) return existsBinder;
            final IBinder binder = startProcessUnchecked(componentName);
            if (binder == null) return null;
            mChildProcess.put(token, binder);
            try {
                binder.linkToDeath(() -> {
                    IBinder curBinder = mChildProcess.get(token);
                    if (curBinder == null || curBinder != binder) return;
                    mChildProcess.remove(token);
                }, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            return binder;
        }
    }

    private IBinder startProcessUnchecked(@NonNull ComponentName componentName) {
        return NewProcessReceiver.start(mContext, this, componentName);
    }

    public static class Default extends AppProcess {
        @NonNull
        @Override
        protected Process newProcess(@NonNull ProcessParams params) throws IOException {
            List<String> cmdList = params.getCmdList();
            Map<String, String> env = params.getEnv();
            String directory = params.getDirectory();
            ProcessBuilder builder = new ProcessBuilder().command(cmdList);
            if (directory != null) builder = builder.directory(new File(directory));
            if (env != null) builder.environment().putAll(env);
            return builder.start();
        }
    }

    public abstract static class Terminal extends Default {
        protected abstract @NonNull List<String> newTerminal();

        @NonNull
        @Override
        protected Process newProcess(@NonNull ProcessParams params) throws IOException {
            ProcessParams newParams = new ProcessParams(params).setCmdList(newTerminal());
            Process process = super.newProcess(newParams);
            PrintWriter printWriter = new PrintWriter(process.getOutputStream(), true);
            int count = 0;
            StringBuilder buffer = new StringBuilder();
            for (String element : params.getCmdList()) {
                if (++count > 1) buffer.append(" ");
                buffer.append(element);
            }
            printWriter.println(buffer);
            printWriter.println("exit $?");
            return process;
        }
    }

    public static class Root extends Terminal {

        @NonNull
        @Override
        protected List<String> newTerminal() {
            List<String> terminal = new ArrayList<>();
            terminal.add("su");
            return terminal;
        }
    }

    public static class RootSystem extends Terminal {
        @NonNull
        @Override
        protected List<String> newTerminal() {
            List<String> terminal = new ArrayList<>();
            terminal.add("su");
            terminal.add("1000");
            return terminal;
        }
    }

    public static class ProcessParams {
        private @NonNull List<String> mCmdList;

        private @Nullable Map<String, String> mEnv;

        private @Nullable String mDirectory;

        public ProcessParams(@NonNull List<String> cmdList, @Nullable Map<String, String> env, @Nullable String directory) {
            this.mCmdList = cmdList;
            this.mEnv = env;
            this.mDirectory = directory;
        }

        public ProcessParams(@NonNull ProcessParams params) {
            this.mCmdList = new ArrayList<>(params.getCmdList());
            Map<String, String> env = params.getEnv();
            this.mEnv = env != null ? new HashMap<>(env) : null;
            this.mDirectory = params.getDirectory();
        }

        @NonNull
        public List<String> getCmdList() {
            return mCmdList;
        }

        @Nullable
        public Map<String, String> getEnv() {
            return mEnv;
        }

        @Nullable
        public String getDirectory() {
            return mDirectory;
        }

        public ProcessParams setCmdList(@NonNull List<String> mCmdList) {
            this.mCmdList = mCmdList;
            return this;
        }

        public ProcessParams setEnv(@Nullable Map<String, String> mEnv) {
            this.mEnv = mEnv;
            return this;
        }

        public ProcessParams setDirectory(@Nullable String mDirectory) {
            this.mDirectory = mDirectory;
            return this;
        }
    }
}