-keep class com.javis.launcher.** { *; }
-keep class androidx.room.** { *; }
-keepattributes *Annotation*

-keepattributes Signature
-keepattributes *Annotation*
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

-keep class com.airbnb.lottie.** { *; }
