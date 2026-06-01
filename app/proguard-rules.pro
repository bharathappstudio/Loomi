# WebRTC ProGuard rules
-keep class org.webrtc.** { *; }
-keep interface org.webrtc.** { *; }
-dontwarn org.webrtc.**

# Firebase/Google Auth
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Coil
-keep class coil.** { *; }
