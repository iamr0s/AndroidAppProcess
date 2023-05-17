package com.rosan.app_process;

import android.content.ComponentName;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AppProcess implements Closeable {
    private INewProcess mNewProcess = null;

    private final Map<String, INewProcess> mChildProcess = new HashMap<>();

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

    public boolean init(String packageName) {
        if (initialized()) return true;
        mNewProcess = NewProcessReceiver.start(this, new ComponentName(packageName, NewProcessImpl.class.getName()));
        IBinder binder = mNewProcess.asBinder();
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
        return mNewProcess != null && mNewProcess.asBinder().isBinderAlive();
    }

    @Override
    public void close() throws IOException {
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
        boolean result = false;
        IBinder processBinder = requireNewProcess().asBinder();
        Parcel processData = Parcel.obtain();
        try {
            processData.writeInterfaceToken(processBinder.getInterfaceDescriptor());
            processData.writeStrongBinder(binder);
            processData.writeInt(code);
            processData.writeInt(flags);
            processData.appendFrom(data, 0, data.dataSize());
            result = processBinder.transact(IBinder.FIRST_CALL_TRANSACTION + 2, processData, reply, 0);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        } finally {
            processData.recycle();
        }
        return result;
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

    public INewProcess startProcess(@NonNull ComponentName componentName) {
        String token = componentName.flattenToString();
        INewProcess newProcess = mChildProcess.get(token);
        if (newProcess != null) return newProcess;
        newProcess = NewProcessReceiver.start(this, componentName);
        IBinder binder = newProcess.asBinder();
        try {
            binder.linkToDeath(() -> {
                INewProcess curNewProcess = mChildProcess.get(token);
                if (curNewProcess == null || curNewProcess.asBinder() != binder) return;
                mChildProcess.remove(token);
            }, 0);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return newProcess;
    }

    public void stopProcess(@NonNull ComponentName componentName) {
        String token = componentName.flattenToString();
        INewProcess newProcess = mChildProcess.get(token);
        if (newProcess == null || !newProcess.asBinder().isBinderAlive()) return;
        try {
            newProcess.exit(0);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
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

    public static class Root extends Default {
        @NonNull
        @Override
        protected Process newProcess(@NonNull ProcessParams params) throws IOException {
            ProcessParams newParams = new ProcessParams(params);
            List<String> newCmdList = new ArrayList<>();
            newCmdList.add("su");
            newCmdList.add("-c");
            newCmdList.addAll(newParams.getCmdList());
            newParams.setCmdList(newCmdList);
            return super.newProcess(newParams);
        }
    }

    public static class RootSystem extends Default {
        @NonNull
        @Override
        protected Process newProcess(@NonNull ProcessParams params) throws IOException {
            ProcessParams newParams = new ProcessParams(params);
            List<String> newCmdList = new ArrayList<>();
            newCmdList.add("su");
            newCmdList.add("1000");
            newCmdList.add("-c");
            newCmdList.addAll(newParams.getCmdList());
            newParams.setCmdList(newCmdList);
            return super.newProcess(newParams);
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

        public void setCmdList(@NonNull List<String> mCmdList) {
            this.mCmdList = mCmdList;
        }

        public void setEnv(@Nullable Map<String, String> mEnv) {
            this.mEnv = mEnv;
        }

        public void setDirectory(@Nullable String mDirectory) {
            this.mDirectory = mDirectory;
        }
    }
}