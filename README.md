# TheNkiri v2.1 build hotfix

Fixes the exact GitHub Actions failures from Android run #8:

1. Removes obsolete `NkiriApp.kt`. `MainActivity` now launches `NkiriV2App`, so keeping
   the old Compose tree only made Gradle compile dead code that referenced removed
   ViewModel methods and did not handle the new HISTORY tab.
2. Rewrites the download snapshot `mapNotNull` block using an explicit `Pair`, avoiding
   the Kotlin lambda/`it` inference error.

No features are removed from the active v2 UI.
