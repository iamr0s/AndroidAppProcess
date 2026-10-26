package com.rosan.app_process;

import android.app.ActivityThread;
import android.content.ComponentName;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;

public abstract class AppProcess implements Closeable {
    protected Context mContext = null;

    protected volatile IProcessManager mManager = null;

    protected final Map<String, IBinder> mChildProcess = new ConcurrentHashMap<>();

    public static ProcessParams generateProcessParams(String classPath, String entryClassName, List<String> args) {
        List<String> cmdList = new ArrayList<>();
        cmdList.add("/system/bin/app_process");
        cmdList.add("-Djava.class.path=" + classPath);
        cmdList.add("/system/bin");
        cmdList.add(entryClassName);
        cmdList.addAll(args);
        return new ProcessParams(cmdList, null, null);
    }

    public static <T> T binderWithCleanCallingIdentity(Callable<T> action) throws Exception {
        final long callingIdentity = Binder.clearCallingIdentity();
        try {
            return action.call();
        } finally {
            Binder.restoreCallingIdentity(callingIdentity);
        }
    }

    public static IBinder binderWrapper(IProcessManager manager, IBinder binder) {
        return new BinderWrapper(manager, binder);
    }

    public static boolean remoteTransact(IProcessManager manager, IBinder binder, int code, Parcel data, Parcel reply, int flags) {
        IBinder managerBinder = manager.asBinder();
        Parcel processData = Parcel.obtain();
        try {
            String descriptor = managerBinder.getInterfaceDescriptor();
            processData.writeInterfaceToken(descriptor != null ? descriptor : "");
            processData.writeStrongBinder(binder);
            processData.writeInt(code);
            processData.writeInt(flags);
            processData.appendFrom(data, 0, data.dataSize());
            return managerBinder.transact(IBinder.FIRST_CALL_TRANSACTION + 2, processData, reply, 0);
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        } finally {
            processData.recycle();
        }
    }

    public static void linkDeathToThis(IProcessManager serviceManager) {
        IClientManager clientManager = new ClientManager();
        linkDeathTo(serviceManager, clientManager.asBinder());
    }

    public static void linkDeathTo(IProcessManager serviceManager, IBinder binder) {
        try {
            ParcelableBinder pBinder = binder == null ? null : new ParcelableBinder(binder);
            serviceManager.linkDeathTo(pBinder);
        } catch (RemoteException e) {
            throw new RuntimeException((e));
        }
    }

    public Process start(String classPath, String entryClassName, List<String> args) throws IOException {
        return startProcess(generateProcessParams(classPath, entryClassName, args));
    }

    public Process start(String classPath, String entryClassName, String[] args) throws IOException {
        return start(classPath, entryClassName, Arrays.asList(args));
    }

    public <T> Process start(String classPath, Class<T> entryClass, List<String> args) throws IOException {
        return start(classPath, entryClass.getName(), args);
    }

    public <T> Process start(String classPath, Class<T> entryClass, String[] args) throws IOException {
        return start(classPath, entryClass, Arrays.asList(args));
    }

    public boolean init() {
        return init(ActivityThread.currentActivityThread().getApplication());
    }

    public synchronized boolean init(Context context) {
        if (initialized()) return true;
        mContext = context;

        IProcessManager manager = newManager();
        if (manager == null) return false;
        mManager = manager;

        IBinder managerBinder = manager.asBinder();
        try {
            managerBinder.linkToDeath(() -> {
                IProcessManager current = mManager;
                if (current != null && current.asBinder() == managerBinder) mManager = null;
            }, 0);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        return initialized();
    }

    /*
     * 启动一个新的Process，并返回一个Android的IBinder，方便进行远程进程管理
     * */
    protected IProcessManager newManager() {
        IBinder binder = isolatedServiceBinder(new ComponentName(mContext.getPackageName(), ProcessManager.class.getName()));
        if (binder == null) return null;

        IProcessManager serviceManager = IProcessManager.Stub.asInterface(binder);
        linkDeathToThis(serviceManager);
        return serviceManager;
    }

    public boolean initialized() {
        return mContext != null && mManager != null && mManager.asBinder().isBinderAlive();
    }

    @Override
    public void close() {
        mContext = null;
        IProcessManager manager = mManager;
        if (manager == null || !manager.asBinder().isBinderAlive()) return;
        try {
            manager.exit(0);
        } catch (RuntimeException rethrown) {
            throw rethrown;
        } catch (Exception ignored) {
        }
        // 无需在此处设置为null，因为已经在Binder::linkToDeath中实现了此操作，当ProcessManager::exit时，会调用到此方法
//        mManager = null;
    }

    /*
     * 根据传来的进程参数启动一个Process
     * */
    protected Process newProcess(ProcessParams params) throws IOException {
        List<String> cmdList = params.getCmdList();
        Map<String, String> env = params.getEnv();
        String directory = params.getDirectory();
        ProcessBuilder builder = new ProcessBuilder().command(cmdList);
        if (directory != null) builder = builder.directory(new File(directory));
        if (env != null) builder.environment().putAll(env);
        return builder.start();
    }

    private Process startProcess(ProcessParams params) throws IOException {
        if (!initialized()) return newProcess(params);
        return remoteProcess(params.getCmdList(), params.getEnv(), params.getDirectory());
    }

    private IProcessManager requireManager() {
        if (!initialized()) throw new IllegalStateException("please call init() first.");
        return mManager;
    }

    public boolean remoteTransact(IBinder binder, int code, Parcel data, Parcel reply, int flags) {
        return remoteTransact(requireManager(), binder, code, data, reply, flags);
    }

    public IBinder binderWrapper(IBinder binder) {
        return binderWrapper(requireManager(), binder);
    }

    public Process remoteProcess(List<String> cmdList, Map<String, String> env, String directory) {
        try {
            return new RemoteProcess(requireManager().remoteProcess(cmdList, env, directory));
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public IBinder serviceBinder(ComponentName componentName) {
        try {
            return requireManager().serviceBinder(componentName).getBinder();
        } catch (RemoteException e) {
            throw new RuntimeException(e);
        }
    }

    public void linkDeathToThis() {
        IClientManager clientManager = new ClientManager();
        linkDeathTo(clientManager.asBinder());
    }

    public void linkDeathTo(IBinder binder) {
        linkDeathTo(requireManager(), binder);
    }

    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    private Object buildLock(String token) {
        synchronized (locks) {
            Object lock = locks.get(token);
            if (lock == null) {
                lock = new Object();
                locks.put(token, lock);
            }
            return lock;
        }
    }

    public IBinder isolatedServiceBinder(ComponentName componentName, boolean useCache) {
        if (!useCache) {
            String token = componentName.flattenToString();
            synchronized (buildLock(token)) {
                mChildProcess.remove(token);
            }
        }
        return isolatedServiceBinder(componentName);
    }

    public IBinder isolatedServiceBinder(ComponentName componentName) {
        String token = componentName.flattenToString();
        synchronized (buildLock(token)) {
            IBinder existsBinder = mChildProcess.get(token);
            if (existsBinder != null && existsBinder.isBinderAlive()) return existsBinder;
            mChildProcess.remove(token);

            IBinder started = isolatedServiceBinderUnchecked(componentName);
            if (started == null) started = isolatedServiceBinderUnchecked(componentName);
            if (started == null) return null;
            final IBinder binder = started;
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

    private IBinder isolatedServiceBinderUnchecked(ComponentName componentName) {
        return NewProcessReceiver.start(mContext, this, componentName);
    }

    public static class Default extends AppProcess {
    }

    /*
     * 不在新进程启动，而是直接在当前进程中进行
     * */
    public static class None extends AppProcess {
        @Override
        protected IProcessManager newManager() {
            return new ProcessManager();
        }

        @Override
        public void close() {
            mManager = null;
        }
    }

    public abstract static class Terminal extends Default {
        protected abstract List<String> newTerminal();

        @Override
        protected Process newProcess(ProcessParams params) throws IOException {
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

        @Override
        protected List<String> newTerminal() {
            List<String> terminal = new ArrayList<>();
            terminal.add("su");
            return terminal;
        }
    }

    public static class RootSystem extends Terminal {
        @Override
        protected List<String> newTerminal() {
            List<String> terminal = new ArrayList<>();
            terminal.add("su");
            terminal.add("1000");
            return terminal;
        }
    }

    public static class ProcessParams {
        private List<String> mCmdList;

        private Map<String, String> mEnv;

        private String mDirectory;

        public ProcessParams(List<String> cmdList, Map<String, String> env, String directory) {
            this.mCmdList = cmdList;
            this.mEnv = env;
            this.mDirectory = directory;
        }

        public ProcessParams(ProcessParams params) {
            this.mCmdList = new ArrayList<>(params.getCmdList());
            Map<String, String> env = params.getEnv();
            this.mEnv = env != null ? new HashMap<>(env) : null;
            this.mDirectory = params.getDirectory();
        }

        public List<String> getCmdList() {
            return mCmdList;
        }

        public Map<String, String> getEnv() {
            return mEnv;
        }

        public String getDirectory() {
            return mDirectory;
        }

        public ProcessParams setCmdList(List<String> mCmdList) {
            this.mCmdList = mCmdList;
            return this;
        }

        public ProcessParams setEnv(Map<String, String> mEnv) {
            this.mEnv = mEnv;
            return this;
        }

        public ProcessParams setDirectory(String mDirectory) {
            this.mDirectory = mDirectory;
            return this;
        }
    }
}