# [Harmony — Multiprocess SharedPreferences](https://medium.com/@pablobaxter/harmony-sharedpreferences-4d0fb500907e?source=friends_link&sk=22b45fe99fe66a085dc8d455d0d90178)

[![CircleCI](https://circleci.com/gh/pablobaxter/Harmony/tree/main.svg?style=shield)](https://circleci.com/gh/pablobaxter/Harmony/tree/main)
[![GitHub](https://img.shields.io/github/license/pablobaxter/harmony)](https://github.com/pablobaxter/Harmony/blob/main/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.frybits.harmony/harmony?label=Harmony)](https://search.maven.org/artifact/com.frybits.harmony/harmony/1.1.5/aar)[![Maven Central](https://img.shields.io/maven-central/v/com.frybits.harmony/harmony-crypto?label=Harmony-Crypto)](https://search.maven.org/artifact/com.frybits.harmony/harmony-crypto/0.0.1/aar)
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
The latest release is available on [Maven Central](https://search.maven.org/artifact/com.frybits.harmony/harmony/1.1.5/aar).
### Gradle
```
implementation 'com.frybits.harmony:harmony:1.1.5'
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

Once you have this `SharedPreferences` object, it can be used just like any other `SharedPreferences`. The main difference with Harmony is that any changes made to `"PREF_NAME"` using `apply()` or `commit()` is reflected across all processes.

**NOTE: Changes in Harmony do not reflect in Android SharedPreferences and vice-versa!** 


## Performance
All tests were performed on a Samsung Galaxy S9 (SM-G960U) running Android 10.

// TODO Write about tests

## Special thanks

This section is to give a special thanks to inidividuals that helped with getting this project where it is today.
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
