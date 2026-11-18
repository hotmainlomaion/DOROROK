# ML Kit Subject Segmentation (beta) — 리플렉션으로 로딩되는 내부 클래스 보호
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_subject_segmentation.** { *; }
-dontwarn com.google.mlkit.**

# Compose 런타임은 자체 규칙을 포함한다.
-dontwarn androidx.compose.**
