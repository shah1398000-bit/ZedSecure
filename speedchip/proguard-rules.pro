-dontobfuscate
-allowaccessmodification
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class dev.cluvex.zedsecure.** {
    *** Companion;
}
-keep @kotlinx.serialization.Serializable class dev.cluvex.zedsecure.** { *; }
-keep class com.yunus.speedchip.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn javax.naming.**
-dontwarn com.jcraft.jsch.**
-dontwarn com.google.zxing.**
-dontwarn **
