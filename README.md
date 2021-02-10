# Harmony Preferences
[![CircleCI](https://circleci.com/gh/pablobaxter/Harmony/tree/main.svg?style=shield)](https://circleci.com/gh/pablobaxter/Harmony/tree/main)
![GitHub](https://img.shields.io/github/license/pablobaxter/harmony)
![Bintray](https://img.shields.io/bintray/v/soaboz/Harmony/com.frybits.harmony?style=shield) [![API](https://img.shields.io/badge/API-17%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=17)

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
### Gradle
```
implementation 'com.frybits.harmony:harmony:1.1.3'
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

Once you have this `SharedPreferences` object, it can be used just like any other `SharedPreferences`. The main difference with Harmony is that any changes made to `"PREF_NAME"` using `apply()` or `commit()` is reflected across all processes.

**NOTE: Changes in Harmony do not reflect in Android SharedPreferences and vice-versa!** 

## Performance
All tests were performed on a Samsung Galaxy S9 (SM-G960U) running Android 10.

### Commit (Single Entry) Test
Test setup:
- Harmony preferences are cleared before the start of the test
- Each test creates a single `Editor` object
- Each time an entry is set on the `Editor`, `commit()` was called immediately
- Each test inserted 1k `long` values
- The time measured is the duration it took to complete all 1k inserts
- This test was performed 10 times

The source code for this test can be found in [`HarmonyPrefsCommitActivity`](./app/src/main/java/com/frybits/harmony/app/test/singleentry/commit/HarmonyPrefsCommitActivity.kt)

![Commit Single Entry Test](./graphics/commit_test.png)

Inter-Process replication test setup:
- A service called `HarmonyPrefsReceiveService` is listening on another processes using the `OnSharedPreferenceChangeListener`
- On every key change, the current time is taken on the service process, and compared against the received time from the activity process
- This test was performed 10 times, with the results based off of all 10k entries
- Time for `OnSharedPreferenceChangeListener` to be called in other process (Harmony only):
  - **Min time:** `5 ms`
  - **Max time:** `423 ms`
  - **Average time:** `38.0076 ms`

**Summary:** This result is expected. Harmony will perform a commit that is slower than the vanilla SharedPreferences due to file locking occurring, but will quickly emit the changes to any process that is listening.

### Apply (Single Entry) Test
Test setup:
- Harmony preferences are cleared before the start of the test
- Each test creates a single `Editor` object
- Each time an entry is set on the `Editor`, `apply()` was called immediately
- Each test inserted 1k `long` values
- The time measured is the duration it took to complete all 1k inserts
- This test was performed 10 times

The source code for this test can be found in [`HarmonyPrefsApplyActivity`](./app/src/main/java/com/frybits/harmony/app/test/singleentry/apply/HarmonyPrefsApplyActivity.kt)

![Apply Single Entry Test](./graphics/apply_test.png)

Inter-Process replication test setup:
- A service called `HarmonyPrefsReceiveService` is listening on another processes using the `OnSharedPreferenceChangeListener`
- On every key change, the current time is taken on the service process, and compared against the received time from the activity process
- Each test calls `apply()` 1k times and awaits to read the data on the other process
- This test was performed 10 times, with the results based off of all 10k entries
- Time for `OnSharedPreferenceChangeListener` to be called in other process (Harmony only):
  - **Min time:** `10 ms`
  - **Max time:** `317 ms`
  - **Average time:** `115.6742 ms`
- **NOTE:** Quickly calling `apply()` can lead to longer replication times. You should always batch changes into the `SharedPreferenced.Editor` object before calling `apply()` for the best performance.

**Summary:** With the recent changes (`v1.1.3`), Harmony `apply()` is as fast as the vanilla `SharedPreferences` and the replication performance across processes has been greatly improved. Previously, this replication would take up to 3 seconds when calling `apply()` upwards of 1k times, but now will take ~350 ms at maximum.

## Change Log
### Version 1.1.4 / 2021-02-10
- Added `fsync()` for each transaction written (in response to #15)
- Minor restructure for reading JSON string from main file

### Version 1.1.3 / 2021-01-02
- **MIN SDK Raised to API 17**
- Adds batching to transactions, making inter-process data replication much faster
- Updates several core Kotlin and Coroutines libraries
- Fixes potential bug where an `IOException` could be thrown by a function, but isn't declared as throws when compiled to JVM bytecode.
- Slight improvements with memory usage
- Fixes a file descriptor crash
- Additional unit tests for `apply()` and `commit()` functions
- Fixed crasg bug when storing a large string (64K limit with `DataOutputStream.writeUTF()`)
- **Known issues:**
  - There is a bug where changes don't always emit on `OnSharedPreferenceChangeListener` across processes (https://github.com/pablobaxter/Harmony/issues/13)
  - When targeting API 30, `OnSharedPreferenceChangeListener` emits an event when `Editor.clear()` is called for `SharedPreferences`. Harmony does not currently honor this, as modifying this affects the above bug (https://github.com/pablobaxter/Harmony/issues/14)
  - Harmony `apply()` fails occasionally (https://github.com/pablobaxter/Harmony/issues/15)

### Version 1.1.2 / 2020-06-15
- Renamed several functions and variables

### Version 1.1.1 / 2020-06-15
- Fixes minor issue where phone restart could cause transactions to come in out of order

### Version 1.1.0 / 2020-06-13
- Fixes a bug where calling `apply()` in both processes at once would potentially cause removed data to be restored
- Improves in-memory replication time between processes when using `apply()`
- Creates a transaction file where changes get written to before being written to the main preferences file
  - Every time `apply()` or `commit()` is called, a new transaction is written to the transaction file
  - Each time a process restarts and gets an instance of a Harmony preference object, all transactions are flushed and written to the main file
  - Transactions are also flushed when transaction file grows beyond a certain size (128 KB currently, or about ~3k single key transactions)
  - All transactions contain a checksum value to validate transaction integrity

### Version 1.0.0 / 2020-05-23
- **FIRST MAJOR RELEASE!**
- Fixes a bug where `getAll()` only holds `long` numbers, instead of `int` and `float`
- Fixes a but where lock files could be deleted but not recreated, causing a crash
- Changes underlying data structure (BREAKING CHANGE)
- Updates Kotlin Coroutines library
- Updates min Android SDK to API 14
- Adds instrumented tests via Firebase Test Lab
- Added additional tests, especially around testing Harmony in multiprocess
- Change to casting logic from in-memory map, to match documentation of [SharedPreferences](https://developer.android.com/reference/android/content/SharedPreferences)

### Version 0.0.7 / 2020-05-20
- Slight improvement to `apply()` performance
- Adds code for performance testing of Harmony vs SharedPreferences
- Removes unused library from example app ([MMKV](https://github.com/Tencent/MMKV))

### Version 0.0.6 / 2020-05-15
- License change from MIT to Apache-2.0

### Version 0.0.5 / 2020-05-15
- Adds java doc (Dokka HTML) to this release
- Prep work to release on Maven Central

### Version 0.0.2 / 2020-05-15
- Removes `app_name` from the `strings.xml` file
- Restructures library to be under the package `com.frybits.harmony`
  instead of `com.frybits.harmonyprefs`
- Renames the `Harmony` class to `HarmonyImpl` and sets class to
  private.
- Import of `getHarmonySharedPreferences()` method is now cleaner

### Version 0.0.1 / 2020-05-15
- Initial release!

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
