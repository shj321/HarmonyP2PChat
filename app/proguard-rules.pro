# 默认 ProGuard 规则
-keep class com.p2pchat.app.model.** { *; }
-keep class com.p2pchat.app.p2p.** { *; }
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
-dontwarn androidx.room.**
