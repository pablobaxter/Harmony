## Change Log

### Harmony-Crypto Version 0.0.1 / 2021-05-24
- Initial release!

### Version 1.1.6 / 2021-05-24
- Fixes OnSharedPreferenceChangeListener issue [#13](https://github.com/pablobaxter/Harmony/issues/13)
- Fixes OOM issue caused by bad transaction read. [#22](https://github.com/pablobaxter/Harmony/issues/22)
- Adds an API to inject a logger for capturing logs within Harmony. `Harmony.setLogger(harmonyLog: HarmonyLog)`
- Updates Kotlin library version to 1.5.10

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