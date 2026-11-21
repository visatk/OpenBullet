# Keep line numbers & source file names for crash diagnostics
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Native JNI bridge methods & class signatures
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.example.nativeengine.NativeScannerBridge { *; }

# Moshi codegen & network response models
-keep @com.squareup.moshi.JsonClass class * { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keep class com.example.network.*Response { *; }
-keep class com.example.network.*ResponseJsonAdapter { *; }

# Retrofit interfaces
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface com.example.network.OpenBulletApiService {
    <methods>;
}

# WorkManager CoroutineWorkers & custom WorkerFactory
-keep class com.example.worker.** { *; }

# Room database entities
-keep class com.example.data.local.*Entity { *; }

