## Change Log

### Version 1.2.0 / 2022-09-15
- Adds new `OnHarmonySharedPreferenceChangedListener` that provides an explicit callback for `clear()` events, instead of just emitting `null` keys
- Exposes `withFileLock()` and `FileInputStream.sync()` utility functions for public use
- Updated Kotlin to 1.7.10
- Updated `compileSdk` to 33
- Updated Android Gradle plugin version to 7.2.2
- Moves all build logic to custom plugins in the `buildSrc` directory
- Moves tests specific for apps that target SDK 31 to a test module

### Harmony-Crypto Version 0.1.0 / 2022-09-15
- Fixes bug where `contains()` would be `false` for `null` keys
- Allows usage of new `OnHarmonySharedPreferenceChangedListener` from Harmony
- Updates `HarmonyKeysetManager` to more closely reflect the [`AndroidKeysetManager`](https://github.com/google/tink/blob/master/java_src/src/main/java/com/google/crypto/tink/integration/android/AndroidKeysetManager.java) from the [Google/Tink](https://github.com/google/tink) project
- Utilizes a custom `KeysetReader` and `KeysetWriter` that stores and reads keys using a file rather than the shared preferences
- Encrypted Harmony shared preferences can now handle notifying when `clear()` is called

### Version 1.1.11 / 2022-03-11
- Updated Kotlin to 1.6.10
- Updated `compileSdk` to 31
- Updated Android Gradle plugin version to 7.0.4
- Targets Java 11 now
- Fixes crashing issue caused by `WeakHashMap` throwing a `NoSuchElementException` [#41](https://github.com/pablobaxter/Harmony/issues/41)

### Version 1.1.10 / 2021-10-10
- Fixes crashing issue caused by the `FileObserver.startWatch()` function on LGE devices [#38](https://github.com/pablobaxter/Harmony/pull/38)
- Update Kotlin version to 1.5.31 and Coroutines to 1.5.2

### Version 1.1.9 / 2021-08-22
- Fixes crashing issue caused by bad transaction file [#36](https://github.com/pablobaxter/Harmony/pull/36)
- Update libraries

### Harmony-Crypto Version 0.0.2 / 2021-06-11
- Update to Harmony v1.1.8

### Version 1.1.8 / 2021-06-11
- Fixes OnSharedPreferenceChangeListener not emitting `null` issue [#14](https://github.com/pablobaxter/Harmony/issues/14)
- Removes targetSdk from library
- Adds support for `null` keys [#29](https://github.com/pablobaxter/Harmony/issues/29)
- Fixes issue with certain strings not being stored in Harmony [#31](https://github.com/pablobaxter/Harmony/issues/31)

### Version 1.1.7 / 2021-06-01
- Use `Os.fsync()` for Android versions that support it
- Fast follow improvement when reading transaction file

### Harmony-Crypto Version 0.0.1 / 2021-05-29
- Initial release!

### Version 1.1.6 / 2021-05-29
- Fixes OnSharedPreferenceChangeListener issue [#13](https://github.com/pablobaxter/Harmony/issues/13)
- Fixes OOM issue caused by bad transaction read. [#22](https://github.com/pablobaxter/Harmony/issues/22)
- Adds an API to inject a logger for capturing logs within Harmony. `Harmony.setLogger(harmonyLog: HarmonyLog)`
- Updates Kotlin library version to 1.5.10
- Improves performance when using multiple Harmony SharedPreferences
- Improved `commit()` time, and notification of `commit()` between processes

### Harmony Version 1.1.5 / 2021-02-27
- Removed dependency on Kotlin Coroutines. This is to reduce bringing in libraries that may not already exist into the project.
- Create a global thread to handle Harmony updates, instead of each Harmony object having their own thread.
- **Note**: The test times may appear better this release, but that is only because I was previously testing on a debug build of the demo app instead of a release build. In actuality, v1.1.5 performs just as well as v1.1.4 with the listed changes. Sorry if there is any confusion.

### Harmony Version 1.1.4 / 2021-02-10
- Added `FileDescriptor.sync()` for each transaction written (in response to [#15](https://github.com/pablobaxter/Harmony/issues/15))
- Minor restructure for reading JSON string from main file
- Updated Kotlin libraries and Android plugins
- Migrated to releasing directly to MavenCentral instead of Bintray

### Harmony Version 1.1.3 / 2021-01-02
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

### Harmony Version 1.1.2 / 2020-06-15
- Renamed several functions and variables

### Harmony Version 1.1.1 / 2020-06-15
- Fixes minor issue where phone restart could cause transactions to come in out of order

### Harmony Version 1.1.0 / 2020-06-13
- Fixes a bug where calling `apply()` in both processes at once would potentially cause removed data to be restored
- Improves in-memory replication time between processes when using `apply()`
- Creates a transaction file where changes get written to before being written to the main preferences file
  - Every time `apply()` or `commit()` is called, a new transaction is written to the transaction file
  - Each time a process restarts and gets an instance of a Harmony preference object, all transactions are flushed and written to the main file
  - Transactions are also flushed when transaction file grows beyond a certain size (128 KB currently, or about ~3k single key transactions)
  - All transactions contain a checksum value to validate transaction integrity

### Harmony Version 1.0.0 / 2020-05-23
- **FIRST MAJOR RELEASE!**
- Fixes a bug where `getAll()` only holds `long` numbers, instead of `int` and `float`
- Fixes a but where lock files could be deleted but not recreated, causing a crash
- Changes underlying data structure (BREAKING CHANGE)
- Updates Kotlin Coroutines library
- Updates min Android SDK to API 14
- Adds instrumented tests via Firebase Test Lab
- Added additional tests, especially around testing Harmony in multiprocess
- Change to casting logic from in-memory map, to match documentation of [SharedPreferences](https://developer.android.com/reference/android/content/SharedPreferences)

### Harmony Version 0.0.7 / 2020-05-20
- Slight improvement to `apply()` performance
- Adds code for performance testing of Harmony vs SharedPreferences
- Removes unused library from example app ([MMKV](https://github.com/Tencent/MMKV))

### Harmony Version 0.0.6 / 2020-05-15
- License change from MIT to Apache-2.0

### Harmony Version 0.0.5 / 2020-05-15
- Adds java doc (Dokka HTML) to this release
- Prep work to release on Maven Central

### Harmony Version 0.0.2 / 2020-05-15
- Removes `app_name` from the `strings.xml` file
- Restructures library to be under the package `com.frybits.harmony`
  instead of `com.frybits.harmonyprefs`
- Renames the `Harmony` class to `HarmonyImpl` and sets class to
  private.
- Import of `getHarmonySharedPreferences()` method is now cleaner

### Harmony Version 0.0.1 / 2020-05-15
- Initial release!
