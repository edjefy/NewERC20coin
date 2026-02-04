# ProGuard rules for Claude Ray-Bans Assistant

# Keep Claude API client
-keep class com.raybans.claudeassistant.api.** { *; }

# Keep Gson models
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}

# Keep Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep service and receivers
-keep class com.raybans.claudeassistant.service.** { *; }
-keep class * extends android.content.BroadcastReceiver

# Keep data classes for serialization
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
