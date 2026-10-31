# PuzzleBox release keep rules.
# Only reflection-based surfaces need rules; Compose/Room/Koin ship consumer rules.

# --- Retrofit: dynamic Proxy over PuzzleFeedApi requires interface methods kept ---
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*
-keep interface com.ivandsky.puzzlebox.data.network.PuzzleFeedApi { *; }

# --- kotlinx.serialization: serializer() is looked up reflectively per @Serializable class ---
-keepclasseswithmembers class com.ivandsky.puzzlebox.** {
    public static ** Companion;
}
-keepclasseswithmembers class com.ivandsky.puzzlebox.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.ivandsky.puzzlebox.**$$serializer { *; }

# DataStore preferences: no reflection. Compose: none needed.
