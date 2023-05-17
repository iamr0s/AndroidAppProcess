package android.app;

import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.IInterface;
import android.os.RemoteException;

import androidx.annotation.RequiresApi;

public interface IActivityManager extends IInterface {
    @RequiresApi(Build.VERSION_CODES.Q)
    ContentProviderHolder getContentProviderExternal(String name, int userId, IBinder token, String tag)
            throws RemoteException;

    ContentProviderHolder getContentProviderExternal(String name, int userId, IBinder token)
            throws RemoteException;

    abstract class Stub extends Binder implements IActivityManager {
        public static IActivityManager asInterface(IBinder binder) {
            throw new RuntimeException("Stub");
        }
    }
}
