-dontwarn io.github.libxposed.annotation.**

# 关掉 R8 的优化阶段（shrink / obfuscate 保留）。
#
# Android 17 的 dex verifier 会逐个方法做类型检查。R8 的 class merging 在 Compose 的
# Flow 代码上会把 kotlinx.coroutines.flow.internal.AbortFlowException（它是 Throwable）
# 合并进一个普通类，`catch (AbortFlowException)` 的 handler 类型于是不再是 Throwable，
# 验证器直接把整个类判死：
#   VerifyError: Verifier rejected class e4 (AndroidComposeView_androidKt):
#   [0x31] unexpected non-throwable class Reference: a
# 触发点是 AbstractComposeView.onAttachedToWindow -> getWindowRecomposer 里第一次 new
# AndroidComposeView，所以表现是「宿主一显示浮层就闪退」（小红书 8.x 必现）。
# 关掉优化阶段最稳：这类非法 catch 只可能由优化产生，shrink/obfuscate 不影响字节码合法性。
-dontoptimize

# 混淆后的类名必须落在我们独占的包里，绝不能留在根包（默认行为就是根包）。
#
# 模块的 dex 是被注入到宿主进程、由宿主的 ClassLoader 链路加载的，而 Java 的委派是
# 「父优先」——解析 `La;` 时先去宿主的 PathClassLoader 找，命中就用宿主的类。
# 小红书本身是重度混淆的，根包下必然有 `a` / `a0` / `k` / `l` 这些单字母类，于是：
#   我们 dex 里的 La;（AbortFlowException，是 Throwable）
#   → 被解析成宿主的 La;（普通类）
#   → `catch (La;)` 的 handler 类型不再可赋值给 Throwable
#   → ART 17 验证器判定整个方法非法，宿主启动即闪退：
#      VerifyError: Verifier rejected class up0 (FlowKt__ReduceKt):
#      [0x35] unexpected non-throwable class Reference: a
# 同一个坑还会以 ClassCastException / NoSuchMethodError / 静默走错分支的形式出现。
# 把混淆类集中到 duo18.plan.o 之后，宿主不可能存在同名类，冲突彻底消失。
-repackageclasses 'duo18.plan.o'
-allowaccessmodification

-adaptresourcefilecontents META-INF/xposed/java_init.list
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    public <init>();
}

# Runtime collaborators are invoked reflectively by the target apps' callbacks.
-keep class com.aritxonly.myhypermodifier.** { *; }
-keep class com.aritxonly.deadliner.** { *; }
