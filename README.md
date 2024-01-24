# OpenCVSampleAndroid

## How to install OpenCV

app/build.gradle: 

ref: https://github.com/opencv/opencv/pull/24575

```
dependencies {
    // ...
    implementation 'org.opencv:opencv:4.9.0'
}
```

## How to install OpenCV (less than 4.9.0)

- Download opencv-x.x.x-android-sdk.zip from https://github.com/opencv/opencv/releases.
- unzip
- https://github.com/opencv/opencv/blob/e64857c5611d5898b7b30640a775331488a5ebef/modules/java/android_sdk/build.gradle.in#L6
  - > // Add module into Android Studio application project:
    > ...
    > //   Import module: Menu -> "File" -> "New" -> "Module" -> "Import Gradle project":
    > //   Source directory: select this "sdk" directory
    > //   Module name: ":opencv"
  - > // Add dependency into application module:
    > // - or add "project(':opencv')" dependency into app/build.gradle:
    > //
    > //   dependencies {
    > //       implementation fileTree(dir: 'libs', include: ['*.jar'])
    > //       ...
    > //       implementation project(':opencv')
    > //   }
  - > // Load OpenCV native library before using:
    > //
    > // - avoid using of "OpenCVLoader.initAsync()" approach - it is deprecated
    > //   It may load library with different version (from OpenCV Android Manager, which is installed separatelly on device)
    > //
    > // - use "System.loadLibrary("opencv_java4")" or "OpenCVLoader.initDebug()"
    > //   TODO: Add accurate API to load OpenCV native library
