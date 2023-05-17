package android.app;

import android.content.Context;
import android.content.IContentProvider;

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

    public final IContentProvider acquireProvider(Context c, String auth, int userId, boolean stable) {
        throw new RuntimeException("STUB");
    }

    public final boolean releaseProvider(IContentProvider provider, boolean stable) {
        throw new RuntimeException("STUB");
    }
}
