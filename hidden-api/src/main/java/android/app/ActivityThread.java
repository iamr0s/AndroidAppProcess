package android.app;

public class ActivityThread {
    public static ActivityThread currentActivityThread() {
        throw new RuntimeException("STUB");
    }

    public static ActivityThread systemMain() {
        throw new RuntimeException("STUB");
    }

    public ContextImpl getSystemContext() {
        throw new RuntimeException("STUB");
    }

    public Application getApplication() {
        throw new RuntimeException("STUB");
    }

    public final LoadedApk peekPackageInfo(String packageName, boolean includeCode) {
        throw new RuntimeException("STUB");
    }
}
