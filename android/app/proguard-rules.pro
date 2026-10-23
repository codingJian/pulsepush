# PulsePush ProGuard / R8 rules
# Retrofit / Gson: keep DTOs (field names are the JSON contract)
-keep class com.shing.pulsepush.data.remote.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
# Hilt / Firebase are covered by their consumer rules.
