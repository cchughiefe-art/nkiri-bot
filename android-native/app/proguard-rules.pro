# Keep JSON model names simple for org.json based parsing.
-keepattributes *Annotation*

# OkHttp / Kotlin metadata.
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
