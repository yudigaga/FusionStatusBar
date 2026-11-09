# The framework loads this class by the name in META-INF/xposed/java_init.list.
-keep,allowoptimization class com.xtjm.fusionstatusbar.FusionModule {
    public <init>();
    public void onSystemServerStarting(io.github.libxposed.api.XposedModuleInterface$SystemServerStartingParam);
    public void onPackageLoaded(io.github.libxposed.api.XposedModuleInterface$PackageLoadedParam);
    public void onPackageReady(io.github.libxposed.api.XposedModuleInterface$PackageReadyParam);
}

# libxposed annotations are compile-time metadata, provided separately by the API toolchain.
-dontwarn io.github.libxposed.annotation.**

# Preserve useful stack traces alongside the archived R8 mappings.
-keepattributes SourceFile,LineNumberTable
