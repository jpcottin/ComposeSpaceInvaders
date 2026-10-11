# R8 keep rules.
#
# Intentionally empty: every dependency this app uses ships its own consumer
# rules (AndroidX, Compose, DataStore, Jetpack XR). The engine is plain Kotlin
# with no reflection. Verified by a minified release build that plays a
# round on an emulator (the Android CLI multi-run job in CI).
#
# Add app-specific rules here only if a future change introduces reflection,
# JNI, or another pattern R8 cannot see from the bytecode.
