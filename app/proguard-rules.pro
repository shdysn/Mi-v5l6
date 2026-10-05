# Keep Jetpack Compose & UI classes
-keepclassmembers class * {
    @androidx.compose.runtime.Composable *;
}

# Keep app classes and models
-keep class com.mi.explorer.** { *; }

# Keep broadcast receivers & widget
-keep class com.mi.explorer.utils.PackageInstallerStatusReceiver { *; }
-keep class com.mi.explorer.widget.MiStorageWidgetProvider { *; }

# Keep annotations & signature attributes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# Suppress harmless warnings during optimization
-dontwarn **
