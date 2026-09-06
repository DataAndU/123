# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.prosperity.game.**$$serializer { *; }
-keepclassmembers class com.prosperity.game.** { *** Companion; }
-keepclasseswithmembers class com.prosperity.game.** { kotlinx.serialization.KSerializer serializer(...); }
