# [Harmony — Multiprocess SharedPreferences](https://medium.com/@pablobaxter/harmony-sharedpreferences-4d0fb500907e?source=friends_link&sk=22b45fe99fe66a085dc8d455d0d90178)

[![CircleCI](https://circleci.com/gh/pablobaxter/Harmony/tree/main.svg?style=shield)](https://circleci.com/gh/pablobaxter/Harmony/tree/main)
[![GitHub](https://img.shields.io/github/license/pablobaxter/harmony)](https://github.com/pablobaxter/Harmony/blob/main/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.frybits.harmony/harmony?label=Harmony)](https://search.maven.org/artifact/com.frybits.harmony/harmony/1.1.6/aar)[![Maven Central](https://img.shields.io/maven-central/v/com.frybits.harmony/harmony-crypto?label=Harmony-Crypto)](https://search.maven.org/artifact/com.frybits.harmony/harmony-crypto/0.0.1/aar)
[![API](https://img.shields.io/badge/API-17%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=17)

Working on multiprocess Android apps is a complex undertaking. One of the biggest challenges is managing shared data between the multiple processes. Most solutions rely on one process to be available for another to read the data, which can be quite slow and could potentially lead to ANRs.

Harmony is a thread-safe, process-safe, full [`SharedPreferences`](https://developer.android.com/reference/android/content/SharedPreferences) implementation. It can be used in place of [`SharedPreferences`](https://developer.android.com/reference/android/content/SharedPreferences) everywhere.

## Features
- Built to support multiprocess apps
- Each process can open a Harmony `SharedPreference` object, without requiring another process to start
- Full [`SharedPreferences`](https://developer.android.com/reference/android/content/SharedPreferences) implementation
- [`OnSharedPreferenceChangeListener`](https://developer.android.com/reference/android/content/SharedPreferences.OnSharedPreferenceChangeListener) emits changes made by other processes
- Uses no native code (NDK) or any IPC classes such as [`ContentProvider`](https://developer.android.com/reference/android/content/ContentProvider), [`Service`](https://developer.android.com/reference/android/app/Service), [`BroadcastReceiver`](https://developer.android.com/reference/android/content/BroadcastReceiver), or [AIDL](https://developer.android.com/guide/components/aidl)
- Built-in failed-write recovery similar to the default [`SharedPreferences`](https://developer.android.com/reference/android/content/SharedPreferences)
- Supports Android API 17+

## Download
The latest release is available on [Maven Central](https://search.maven.org/artifact/com.frybits.harmony/harmony/1.1.6/aar).
### Gradle
```
implementation 'com.frybits.harmony:harmony:1.1.6'
// implementation 'com.frybits.harmony:harmony-crypto:0.0.1' // For Encrypted SharedPreferences
```

## Usage
### Creating Harmony SharedPreferences
#### Kotlin
```kotlin
// Getting Harmony SharedPreferences
val prefs: SharedPreferences = context.getHarmonySharedPreferences("PREF_NAME")
```

#### Java
```java
// Getting Harmony SharedPreferences
SharedPreferences prefs = Harmony.getSharedPreferences(context, "PREF_NAME")
```

OR

### Creating Encrypted Harmony SharedPreferences (Requires `harmony-crypto` library)
#### Kotlin
```kotlin
// Getting Encrypted Harmony SharedPreferences
val prefs: SharedPreferences = context.getEncryptedHarmonySharedPreferences(
            "PREF_NAME",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
```

#### Java
```java
// Getting Encrypted Harmony SharedPreferences
SharedPreferences prefs = EncryptedHarmony.getSharedPreferences(
            context, 
            "PREF_NAME",
            MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
          )
```

Once you have this `SharedPreferences` object, it can be used just like any other `SharedPreferences`. The main difference with Harmony is that any change made to `"PREF_NAME"` using `apply()` or `commit()` is reflected across all processes.

**NOTE: Changes in Harmony do not reflect in Android SharedPreferences and vice-versa!** 

## Performance

The following are comparison performance tests of some popular multiprocess preference libraries. Each test measures the time it takes to insert 1000 items individually into the preference library (Write), the time it takes to read each 1000 items individually (Read), and how long it took for each item to be available in an alternate process (IPC). Each test was run 10 times. All values in the table below are the average time for a single item to be inserted, read, and available in the alternate process.

Tests were broken into two separate categories:
- Asynchronous writing (if applicable)
- Synchronous writing

Logic for tests can been seen in the [`TestRunner.kt`](app/src/main/java/com/frybits/harmony/app/test/TestRunner.kt) file.

**Notes** 
- All tests were performed on a Samsung Galaxy S9 (SM-G960U) running Android 10
- Times are for single item operation.

#### Asynchronous Tests

|Library                                             |Read (avg)|Write (avg)          |IPC (avg)             |
|----------------------------------------------------|----------|---------------------|----------------------|
|SharedPreferences                                   |0.001 ms  |0.062 ms             |N/A <sup>1</sup>      |
|Harmony                                             |0.0007 ms |0.035 ms             |75.591 ms             |
|[MMKV](https://github.com/Tencent/MMKV) <sup>2</sup>|0.007 ms  |0.049 ms             |93.916 ms <sup>3</sup>|
|[Tray](https://github.com/GCX-HCI/tray) <sup>2</sup>|2.389 ms  |8.697 ms             |1.795 s               |


#### Synchronous Tests

|Library                                             |Read (avg)|Write (avg)           |IPC (avg)              |
|----------------------------------------------------|----------|----------------------|-----------------------|
|SharedPreferences                                   |0.002 ms  |9.058 ms              |N/A <sup>1</sup>       |
|Harmony                                             |0.002 ms  |22.866 ms <sup>4</sup>|29.224 ms              |
|[MMKV](https://github.com/Tencent/MMKV) <sup>2</sup>|0.010 ms  |0.045 ms              |109.548 ms <sup>3</sup>|
|[Tray](https://github.com/GCX-HCI/tray) <sup>2</sup>|2.411 ms  |8.306 ms              |1.626 s                |

<sup>1</sup> SharedPreferences doesn't support IPC, so this was not tested.

<sup>2</sup> These libraries don't support asynchronous writes. All tests were synchronous writes by default.

<sup>3</sup> MMKV doesn't support a change listener, so a while-loop in a separate thread was used to determine how soon the data was available in the separate process. See [`MMKVRemoteTestRunnerService.kt`](app/src/main/java/com/frybits/harmony/app/test/MMKVRemoteTestRunnerService.kt) for implementation details.

<sup>4</sup> Harmony performs file locking and file syncing operations on each call to `commit()`, which greatly increases the write time, but decreses the time it takes data to be available in the alternate processes. However, using `apply()` is still recommended.

## Special thanks

This section is to give a special thanks to individuals that helped with getting this project where it is today.
- JD - For the batching idea, reviewing the code, and all around bouncing of ideas to improve this project. 
- [@orrinLife360](https://github.com/orrinLife360) - For helping review some of the more critical improvements.
- [@imminent](https://github.com/imminent) - For all the Kotlin insight and helping review many of the changes on this project.
- [@bipin360](https://github.com/bipin360) - For pushing me on this project when I was unsure about it.

Finally, a very special thank you to [@life360](https://github.com/life360) for integrating this project and providing incredibly valuable feedback that I was able to use to improve Harmony.

## License
```
   Copyright 2020 Pablo Baxter

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
```
