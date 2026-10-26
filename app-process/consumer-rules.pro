# Classes loaded by name from the app_process child and by the AIDL surface.
-keep class com.rosan.app_process.ProcessManager {
    public <init>();
}
-keep class com.rosan.app_process.NewProcess {
    public static void main(java.lang.String[]);
}
-keep class com.rosan.app_process.NewProcessReceiver { *; }
-keep class com.rosan.app_process.IProcessManager { *; }
-keep class com.rosan.app_process.IProcessManager$* { *; }
