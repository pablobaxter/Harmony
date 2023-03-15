# [Harmony — Multiprocess SharedPreferences](https://medium.com/@pablobaxter/harmony-sharedpreferences-4d0fb500907e?source=friends_link&sk=22b45fe99fe66a085dc8d455d0d90178)

[![CircleCI](https://circleci.com/gh/pablobaxter/Harmony/tree/main.svg?style=shield)](https://circleci.com/gh/pablobaxter/Harmony/tree/main)
[![GitHub](https://img.shields.io/github/license/pablobaxter/harmony)](https://github.com/pablobaxter/Harmony/blob/main/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.frybits.harmony/harmony?label=Harmony)](https://search.maven.org/artifact/com.frybits.harmony/harmony/1.2.3/aar) [![Harmony API](https://img.shields.io/badge/API-17%2B-brightgreen.svg?style=flat&label=Harmony%20API)](https://android-arsenal.com/api?level=17) [![Maven Central](https://img.shields.io/maven-central/v/com.frybits.harmony/harmony-crypto?label=Harmony-Crypto)](https://search.maven.org/artifact/com.frybits.harmony/harmony-crypto/0.1.2/aar) [![Crypto API](https://img.shields.io/badge/API-23%2B-purple.svg?style=flat&label=Crypto%20API)](https://android-arsenal.com/api?level=23)

Working on multiprocess Android apps is a complex undertaking. One of the biggest challenges is managing shared data between the multiple processes. Most solutions rely on one process to be available for another to read the data, which can be quite slow and could potentially lead to ANRs.

Harmony is a thread-safe, process-safe, full [`SharedPreferences`](https://developer.android.com/reference/android/content/SharedPreferences) implementation. It can be used in place of [`SharedPreferences`](https://developer.android.com/reference/android/content/SharedPreferences) everywhere.

## Features
- Built to support multiprocess apps
- Each process can open a Harmony `SharedPreference` object, without requiring another process to start
- Full [`SharedPreferences`](https://developer.android.com/reference/android/content/SharedPreferences) implementation
- [`OnSharedPreferenceChangeListener`](https://developer.android.com/reference/android/content/SharedPreferences.OnSharedPreferenceChangeListener) emits changes made by other processes
- Uses no native code (NDK) or any IPC classes such as [`ContentProvider`](https://developer.android.com/reference/android/content/ContentProvider), [`Service`](https://developer.android.com/reference/android/app/Service), [`BroadcastReceiver`](https://developer.android.com/reference/android/content/BroadcastReceiver), or [AIDL](https://developer.android.com/guide/components/aidl)
- Built-in failed-write recovery similar to the default [`SharedPreferences`](https://developer.android.com/reference/android/content/SharedPreferences)
- Supports Android API 17+ (Crypto Android API 23+)

## Download
The latest release is available on [Maven Central](https://search.maven.org/artifact/com.frybits.harmony/harmony/1.2.3/aar).
### Gradle
```
implementation 'com.frybits.harmony:harmony:1.2.3'
// implementation 'com.frybits.harmony:harmony-crypto:0.1.2' // For Encrypted SharedPreferences
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

|Library                                             |Read (avg)|Write (avg) |IPC (avg) <sup>1</sup> |
|----------------------------------------------------|----------|------------|-----------------------|
|SharedPreferences                                   |0.0006 ms |0.066 ms    |N/A                    |
|Harmony                                             |0.0008 ms |0.024 ms    |102.018 ms             |
|[MMKV](https://github.com/Tencent/MMKV) <sup>2</sup>|0.009 ms  |0.051 ms    |93.628 ms <sup>3</sup> |
|[Tray](https://github.com/GCX-HCI/tray) <sup>2</sup>|2.895 ms  |8.225 ms    |1.928 s                |


#### Synchronous Tests

|Library                                             |Read (avg)|Write (avg) |IPC (avg) <sup>1</sup> |
|----------------------------------------------------|----------|------------|-----------------------|
|SharedPreferences                                   |0.001 ms  |9.214 ms    |N/A                    |
|Harmony                                             |0.003 ms  |4.626 ms    |13.579 ms              |
|[MMKV](https://github.com/Tencent/MMKV) <sup>2</sup>|0.010 ms  |0.061 ms    |86.100 ms <sup>3</sup> |
|[Tray](https://github.com/GCX-HCI/tray) <sup>2</sup>|2.805 ms  |8.168 ms    |1.773 s                |

<sup>1</sup> IPC is the time it took for the item to be available in a secondary process. SharedPreferences doesn't support IPC.

<sup>2</sup> These libraries don't support asynchronous writes. All tests were synchronous writes by default.

<sup>3</sup> MMKV doesn't support a change listener, so a while-loop in a separate thread was used to determine how soon the data was available in the separate process. See [`MMKVRemoteTestRunnerService.kt`](app/src/main/java/com/frybits/harmony/app/test/MMKVRemoteTestRunnerService.kt) for implementation details.

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
