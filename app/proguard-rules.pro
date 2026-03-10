# Apache POI
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.openxmlformats.**
-dontwarn com.microsoft.**

# iText
-keep class com.itextpdf.** { *; }
-dontwarn com.itextpdf.**

# AndroidPdfViewer
-keep class com.github.barteksc.** { *; }

# Keep Kotlin metadata
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes SourceFile,LineNumberTable
