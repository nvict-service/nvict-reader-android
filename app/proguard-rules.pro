# Pdfium: native code roept Kotlin/Java-klassen aan via JNI
-keep class io.legere.pdfiumandroid.** { *; }
-keepclassmembers class * implements io.legere.pdfiumandroid.api.PdfWriteCallback { *; }
-dontwarn io.legere.pdfiumandroid.**
