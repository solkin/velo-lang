# ProGuard/R8 rules for the Velo Android app.
#
# The Velo VM links a loaded .vbc against host "native" classes purely by
# reflection: NativeRegistry maps a Velo name to a JVM class, and
# NativeDescriptor scans that class's *public methods/constructors by name* and
# reads their *generic signatures* to recover Velo types (List<T> element types,
# (T) -> Unit callback params, etc). R8 would rename those methods and strip the
# generic signatures, breaking the link at load time. So we keep every native
# implementation class with all members, and preserve the attributes the
# descriptor reflection depends on.

# Generic type info (ParameterizedType / function-type params) the native
# descriptor reads, plus annotation/inner-class metadata used by reflection.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod,Exceptions

# All native host implementations registered in VeloTerminalSession are reflected
# by method name — keep the classes and every member intact.
-keep class org.velo.android.engine.** { *; }

# VeloFunction (guest callbacks passed to natives) is matched by class identity
# in the native bridge — must survive minification under its real name.
-keep,allowoptimization interface core.VeloFunction { *; }

# Kotlin function types are what declared `(T) -> Unit` native params reflect as;
# the descriptor inspects them to derive callback signatures.
-keep class kotlin.jvm.functions.** { *; }

# viewBinding generated classes are instantiated reflectively by name.
-keep class org.velo.android.databinding.** { *; }
