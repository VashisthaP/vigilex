# ══════════════════════════════════════════════════════════════════════════
# VigileX — R8 / ProGuard rules for release builds
# ══════════════════════════════════════════════════════════════════════════
#
# Context that makes minification safe here: FirestoreDataSource maps every
# document by hand (getString/getLong + toMap), so no model class is ever
# constructed reflectively. If anyone later switches to
# DocumentSnapshot.toObject<T>(), the model classes MUST stay kept below or
# fields will be stripped and silently deserialize as null.

# ── Keep line numbers for readable crash reports ─────────────────────────
# Without this, Play Console stack traces are unusable. Upload
# app/build/outputs/mapping/release/mapping.txt to deobfuscate.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Annotations and generics are needed by Firebase/Gson-style reflection and
# by Kotlin's own metadata.
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# ── Data models ──────────────────────────────────────────────────────────
# Kept defensively: cheap (a handful of classes) and prevents a silent,
# hard-to-debug breakage if reflective mapping is ever introduced.
-keep class com.vigilex.core.model.** { *; }

# ── Room ─────────────────────────────────────────────────────────────────
# Entities and DAOs are generated/reflected over at runtime.
-keep class com.vigilex.core.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# ── Firebase ─────────────────────────────────────────────────────────────
# Firebase ships consumer rules, but Firestore's serializer reflects over
# annotated members and Auth uses dynamic provider lookup.
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <methods>;
    @com.google.firebase.firestore.PropertyName <fields>;
}
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ── ML Kit face detection ────────────────────────────────────────────────
# Native bridges resolve classes by name; obfuscating them breaks detection
# at runtime with no compile-time warning.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.odml.** { *; }
-dontwarn com.google.mlkit.**

# ── CameraX ──────────────────────────────────────────────────────────────
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# ── Hilt / Dagger ────────────────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep,allowobfuscation @interface dagger.hilt.android.HiltAndroidApp
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }

# ── WorkManager ──────────────────────────────────────────────────────────
# Workers are instantiated by name from the WorkManager database, so a
# renamed class means an orphaned queued job.
-keep class androidx.work.** { *; }
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class com.vigilex.core.worker.** { *; }

# ── Android components resolved by name from the manifest ────────────────
# Services and receivers are referenced as strings in AndroidManifest.xml.
# R8 usually infers these, but the monitoring service is the core of the app
# and a silent failure here is not acceptable.
-keep class com.vigilex.feature.driver.service.** { *; }

# ── Google Maps & Places ─────────────────────────────────────────────────
-keep class com.google.android.libraries.places.** { *; }
-dontwarn com.google.android.libraries.places.**

# ── Kotlin coroutines ────────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**

# ── Kotlin runtime ───────────────────────────────────────────────────────
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**

# ── Compose ──────────────────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ── Strip verbose logging from release builds ────────────────────────────
# Keeps w/e so genuine problems still surface in logcat and Play vitals.
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
}
