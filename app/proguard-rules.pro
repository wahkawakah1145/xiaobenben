# Room entities and DAOs
-keep class com.example.data.*Entity { *; }
-keep class com.example.data.*Entity$* { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Android services and broadcast receivers registered in manifest
-keep class com.example.service.AutoBookkeepingNotificationService { *; }
-keep class com.example.service.AutoBookkeepingAccessibilityService { *; }
-keep class com.example.service.BootCompletedReceiver { *; }
-keep class com.example.service.BackupReminderReceiver { *; }

# Coroutines & OkHttp warnings
-dontwarn kotlinx.coroutines.debug.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Strip verbose/debug/info logging in Release builds to reduce DEX size
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
