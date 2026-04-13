# VigileX ProGuard rules

# Keep ML Kit face detection classes
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Firebase classes
-keep class com.google.firebase.** { *; }

# Keep data model classes (used by Firestore serialization)
-keep class com.vigilex.core.model.** { *; }
-keep class com.vigilex.core.data.local.** { *; }

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# CameraX
-keep class androidx.camera.** { *; }

# WorkManager
-keep class androidx.work.** { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
