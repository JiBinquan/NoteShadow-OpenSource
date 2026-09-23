-keepattributes *Annotation*
# sherpa-onnx JNI looks up configuration fields and result constructors by their exact names.
-keep class com.k2fsa.sherpa.onnx.** { *; }
