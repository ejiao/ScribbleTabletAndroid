# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /Users/.../sdk/tools/proguard/proguard-android.txt

# Keep Ktor classes
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }

# Keep kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep data classes
-keep class com.commonknowledge.scribbletablet.data.model.** { *; }
