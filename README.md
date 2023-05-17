# Android AppProcess

Encapsulation of Android app_process, you can use it just like Android Service.

![Maven Central](https://img.shields.io/maven-central/v/io.github.iamr0s/AndroidAppProcess)

### How to use it

### Import library

```gradle
implementation 'io.github.iamr0s:AndroidAppProcess:<version>'
```

### 1. New Process

- Default

```java
AppProcess process = new AppProcess.Default();
process.init(context.getPackageName());
```

- Root

```java
AppProcess process = new AppProcess.Root();
process.init(context.getPackageName());
```

### 2. Use it.

- Remote Binder Transact

```java
AppProcess process = new AppProcess.Root();
process.init(context.getPackageName());
IPackageManager manager = android.os.ServiceManager.getService("package");
IBinder binderWrapper = process.binderWrapper(manager.asBinder());
IPackageManager managerWrapper = IPackageManager.Stub.asInterface(binderWrapper);

managerWrapper.uninstall(...) // will call it in root.
```

- More

> See the demo.

### 3. Close

> You must close the AppProcess after use it.

```java
process.close()
```