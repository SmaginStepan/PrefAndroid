# kotlinx.serialization ships its own consumer rules; these keep our
# @Serializable model and protocol classes intact as belt and suspenders —
# a stripped serializer would silently break saved games (lastgame.json,
# pulka files) and the multiplayer protocol at runtime, which no unit test
# or debug build would ever catch.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.an0obIs.pref.**$$serializer { *; }
-keepclassmembers class com.an0obIs.pref.** {
    *** Companion;
}
-keepclasseswithmembers class com.an0obIs.pref.** {
    kotlinx.serialization.KSerializer serializer(...);
}
