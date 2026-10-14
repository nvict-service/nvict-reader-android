# Pdfium: native code roept Kotlin/Java-klassen aan via JNI
-keep class io.legere.pdfiumandroid.** { *; }
-keepclassmembers class * implements io.legere.pdfiumandroid.api.PdfWriteCallback { *; }
-dontwarn io.legere.pdfiumandroid.**

# PdfBox-Android: optionele JPEG2000-ondersteuning is niet meegenomen
-dontwarn com.gemalto.jp2.**
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.tom_roush.**
