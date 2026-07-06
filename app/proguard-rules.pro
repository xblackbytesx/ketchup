# App code is shrunk/obfuscated like everything else. The only reflective
# surface is kotlinx.serialization, used for the type-safe navigation routes —
# keep its generated serializers.
-keepattributes *Annotation*, InnerClasses

-keepclassmembers class com.example.ketchup.navigation.** {
    *** Companion;
}
-keepclasseswithmembers class com.example.ketchup.navigation.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.example.ketchup.navigation.**$$serializer { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn coil3.**
-dontwarn com.google.errorprone.annotations.**
