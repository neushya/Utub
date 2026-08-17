# NewPipeExtractor + Rhino(JS 엔진) — R8 난독화 시 런타임 오류 방지 (docs/04 R-03)
-keep class org.schabi.newpipe.extractor.** { *; }
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.** { *; }
-dontwarn org.mozilla.javascript.**
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn javax.annotation.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
