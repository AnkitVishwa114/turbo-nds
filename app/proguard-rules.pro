-keepattributes SourceFile,LineNumberTable
# LibretroDroid's native code calls back into these classes by name.
-keep class com.swordfish.libretrodroid.** { *; }
-keepclassmembers class * {
    @androidx.lifecycle.OnLifecycleEvent <methods>;
}

# Android's default ViewModel factory invokes this constructor through reflection.
-keep,allowoptimization,allowobfuscation class com.appkraft.turbonds.LibraryViewModel {
    public <init>(android.app.Application);
}

# review-ktx references a compile-time-only Play services annotation.
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
