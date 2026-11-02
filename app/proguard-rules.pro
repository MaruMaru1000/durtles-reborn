# R8 rules for the release build. The libraries (Room, WorkManager, OkHttp, kotlinx.serialization,
# Compose) ship their own consumer rules, so only app-specific needs go here.

# Keep line numbers in crash reports.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Optional TLS providers OkHttp can use when present.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**

# Kuromoji loads its IPADIC .bin files with Class.getResourceAsStream relative to its own
# package (com/atilika/kuromoji/ipadic). R8 renaming and repackaging these classes breaks
# that lookup in release builds, so keep the names and packages intact.
-keep class com.atilika.kuromoji.** { *; }
