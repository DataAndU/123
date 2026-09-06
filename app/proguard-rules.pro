# Room
-keep class androidx.room.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# ML Kit on-device models
-keep class com.google.mlkit.** { *; }

# Keep entity/data classes (Room reflective access)
-keep class com.minijarvis.app.data.** { *; }

# MediaPipe LLM Inference (local, optional on-device model) + its protobuf-javalite
# dependency reference some compile-time-only annotation classes that aren't present
# at runtime — safe to silence per R8's own generated missing_rules.txt.
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.framework.image.**
-dontwarn com.google.protobuf.Internal$ProtoMethodMayReturnNull
-dontwarn com.google.protobuf.Internal$ProtoNonnullApi
-dontwarn com.google.protobuf.ProtoField
-dontwarn com.google.protobuf.ProtoPresenceBits
-dontwarn com.google.protobuf.ProtoPresenceCheckedField
