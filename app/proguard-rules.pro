# AndroidViewModelFactory discovers this constructor reflectively.
# Keep only the required constructor; application code remains optimisable.
-keepclassmembers,allowoptimization class hu.grafit.ui.GrafitViewModel {
    public <init>(android.app.Application);
}
