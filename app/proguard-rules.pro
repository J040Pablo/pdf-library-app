# Proguard rules for R8 optimization

# Keep data models
-keep class com.example.library.model.** { *; }

# PDFBox Android rules (com.tom_roush)
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-dontwarn com.tom_roush.fontbox.**
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Junrar rules
-keep class com.github.junrar.** { *; }
-dontwarn com.github.junrar.**

# Retain essential attributes for stack traces and annotations
-keepattributes Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable, *Annotation*
