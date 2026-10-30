## 2025-05-22 - Caching Secondary Launcher Activity Fallback Queries
**Learning:** Checking whether non-primary packages provide `CATEGORY_HOME` launcher activities via `packageManager.queryIntentActivities` on every `TYPE_WINDOW_STATE_CHANGED` Accessibility event invokes synchronous Binder IPC for every foreground window transition. Caching secondary package launcher status in a `ConcurrentHashMap` avoids repeated IPC calls for installed packages during normal window transitions.
**Action:** When performing fallback framework activity resolution checks for arbitrary package names during high-frequency window/accessibility events, cache package-level query results in memory and invalidate alongside primary launcher package cache resets.

## 2025-05-20 - Eager Argument Evaluation with Cached Framework Calls
**Learning:** Passing `resolveInfo.loadIcon(packageManager)` directly into a caching function evaluates the expensive `loadIcon` Binder IPC call eagerly before entering the function, bypassing the internal cache check. Checking cache existence at the caller site prevents unnecessary `PackageManager` IPC during repeated list queries.
**Action:** When invoking functions with caching mechanisms, ensure expensive framework queries are evaluated lazily or checked against the cache prior to evaluation.

## 2025-05-18 - Caching Home Launcher Package Resolution
**Learning:** Querying `packageManager.resolveActivity` on every `AccessibilityEvent` introduces Binder IPC overhead (~1-5ms) on every foreground activity/window state change. Caching the resolved home launcher package in memory reduces checking latency from Binder IPC scale to sub-microsecond in-memory comparisons (<1µs).
**Action:** When working in high-frequency event callbacks (such as `AccessibilityService` handlers or broadcast receivers), always cache framework metadata that changes infrequently (like default launcher package) instead of issuing synchronous `PackageManager` queries.
