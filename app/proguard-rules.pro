# Loaded by name from META-INF/xposed/java_init.list, outside the application graph.
-keep,allowoptimization class com.xtjm.fusionstatusbar.FusionModule {
    public <init>();
    public void onSystemServerStarting(io.github.libxposed.api.XposedModuleInterface$SystemServerStartingParam);
    public void onPackageLoaded(io.github.libxposed.api.XposedModuleInterface$PackageLoadedParam);
    public void onPackageReady(io.github.libxposed.api.XposedModuleInterface$PackageReadyParam);
}

# Compile-only API annotations are not part of the installed module.
-dontwarn io.github.libxposed.annotation.**
