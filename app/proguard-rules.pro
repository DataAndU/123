# Room
-keep class androidx.room.** { *; }

# SQLCipher
-keep class net.sqlcipher.** { *; }
-keep class net.sqlcipher.database.** { *; }

# ML Kit on-device models
-keep class com.google.mlkit.** { *; }

# Keep entity/data classes (Room reflective access)
-keep class com.minijarvis.app.data.** { *; }
