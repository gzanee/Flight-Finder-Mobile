# kotlinx.serialization: keep the serializers of the engine's models (saved searches).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class it.gzanee.flightfinder.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class it.gzanee.flightfinder.**$$serializer { *; }
# Ktor / OkHttp optional dependencies.
-dontwarn org.slf4j.**
-dontwarn java.lang.management.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
