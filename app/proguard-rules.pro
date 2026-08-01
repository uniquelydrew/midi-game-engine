# The app uses Kotlin data models and Android framework callbacks without reflection.
# Keep application/core names readable in Play Console crash reports.
-keepnames class com.example.midigameengine.** { *; }
-keepnames class core.** { *; }
