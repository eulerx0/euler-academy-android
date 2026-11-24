# Keep WebView JavaScript interfaces intact under R8 shrinking
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes *Annotation*
