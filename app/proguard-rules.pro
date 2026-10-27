# Optimized build. Class names are kept (no obfuscation) so crash reports stay readable
# and nothing that relies on names can break; unused code is still removed.
-dontobfuscate

# Readium (the book engine) talks to the page through JavaScript and uses reflection,
# and ships no rules of its own: keep it whole.
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
