-dontobfuscate

-keep class org.readium.** { *; }
-keep interface org.readium.** { *; }
-dontwarn org.readium.**

-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, Exceptions

-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**
-dontwarn javax.annotation.**
-dontwarn org.slf4j.**
-dontwarn org.joda.time.**
-dontwarn kotlinx.serialization.**
