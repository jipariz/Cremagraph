# R8 is on for release. Everything here is a reflective or service-loaded path R8 cannot see.

# kotlinx.serialization. Without these the generated serializers are stripped and every
# settings.json and sidecar fails to parse — silently, at runtime, only in a release build.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class app.onlyshots.**$$serializer { *; }
-keepclassmembers class app.onlyshots.** { *** Companion; }
-keepclasseswithmembers class app.onlyshots.** { kotlinx.serialization.KSerializer serializer(...); }
# The sealed StopRule's subclasses are named in the JSON and found by reflection.
-keep class app.onlyshots.core.model.StopRule { *; }
-keep class app.onlyshots.core.model.StopRule$* { *; }

# Ktor finds its engine through META-INF/services at first use.
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
-keep class * implements io.ktor.client.HttpClientEngineContainer { *; }
-dontwarn io.ktor.**
-dontwarn org.slf4j.**

# Kable talks to the Android BLE stack through callbacks R8 only sees from the framework side.
-keep class com.juul.kable.** { *; }
-dontwarn com.juul.kable.**

