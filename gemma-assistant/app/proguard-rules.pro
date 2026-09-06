# Room
-keep class androidx.room.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# Keep entity/data classes (Room reflective access)
-keep class com.gemmaassistant.app.data.** { *; }

# MediaPipe LLM Inference + its protobuf-javalite dependency reference some
# compile-time-only annotation classes that aren't present at runtime, and
# unused multimodal-image classes this app doesn't touch — safe to silence
# per R8's own generated missing_rules.txt (same fix as Mini JARVIS needed).
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.framework.image.**
-dontwarn com.google.protobuf.Internal$ProtoMethodMayReturnNull
-dontwarn com.google.protobuf.Internal$ProtoNonnullApi
-dontwarn com.google.protobuf.ProtoField
-dontwarn com.google.protobuf.ProtoPresenceBits
-dontwarn com.google.protobuf.ProtoPresenceCheckedField
-dontwarn com.google.auto.value.AutoValue
-dontwarn com.google.auto.value.AutoValue$Builder
