# These libraries call Java methods from native code or use JNI lookups that
# depend on stable class and member names. Keep the JNI surfaces intact in
# minified release builds.
-keep class ai.onnxruntime.** { *; }
-keep class org.opencv.** { *; }
-keep class com.google.ai.edge.litert.** { *; }
-keep class org.pytorch.executorch.** { *; }
# ExecuTorch's JNI bridge reflects into FBJNI exception and hybrid classes.
# The ExecuTorch AAR keeps its own API but does not keep FBJNI's Java surface.
-keep class com.facebook.jni.** { *; }
# sherpa-onnx's Android AAR does not ship consumer rules. Its Kotlin wrappers
# are called from JNI, so keep their names and members in minified releases.
-keep class com.k2fsa.sherpa.onnx.** { *; }
