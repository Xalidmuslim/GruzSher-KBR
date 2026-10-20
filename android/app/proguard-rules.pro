# Keep Android/MapLibre JNI surfaces stable under R8.
# The MapLibre SDK and osmdroid also ship library consumer keep rules.
-keep class org.maplibre.android.** { *; }
-keep class org.osmdroid.** { *; }

# Reflection and Kotlin data/annotation metadata used by SDKs.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# App model data is parsed explicitly with org.json rather than via reflected
# field names, so app code may safely be obfuscated and optimized by R8.
