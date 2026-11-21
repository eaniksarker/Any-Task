# Add project specific ProGuard rules here.
# Preserve Firestore @Keep models and no-arg constructors in release builds
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class com.example.data.remote.FirestoreTaskList { *; }
-keep class com.example.data.remote.FirestoreTask { *; }
-keep class com.example.domain.model.** { *; }
