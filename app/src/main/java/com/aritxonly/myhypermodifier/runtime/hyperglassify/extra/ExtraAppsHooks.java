package com.aritxonly.myhypermodifier;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Lifecycle bridge for hosts without a class-name adapter. The Activity class is looked up by name
 * first; a framework {@link Activity} fallback keeps working when the host renames or delegates its
 * launcher Activity.
 */
final class ExtraAppsHooks {
    private static final String TAG = "MyHyperModifier";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private ExtraAppsHooks() {}

    static void install(XposedModule module, ClassLoader classLoader, String packageName) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        ExtraCrashLog.install(packageName);
        ExtraNavigationSpec spec = ExtraNavigationTargets.INSTANCE.specFor(packageName);
        ExtraNavigationDelegate delegate =
                ExtraNavigationTargets.INSTANCE.delegateFor(packageName);
        if (spec == null || delegate == null) {
            INSTALLED.set(false);
            return;
        }
        try {
            installFrameworkLifecycle(module, spec, delegate);
        } catch (Throwable error) {
            INSTALLED.set(false);
            module.log(Log.ERROR, TAG,
                    "Could not install " + spec.getLogName() + " navigation hooks", error);
            HookDiagnostics.failure(packageName, spec.getLogName() + "悬浮底栏", error);
        }
    }

    private static void installFrameworkLifecycle(
            XposedModule module,
            ExtraNavigationSpec spec,
            ExtraNavigationDelegate delegate
    ) throws NoSuchMethodException {
        Method onResume = findMethod(Activity.class, "onResume");
        Method onPause = findMethod(Activity.class, "onPause");
        Method onDestroy = findMethod(Activity.class, "onDestroy");
        Method onWindowFocusChanged = findMethod(
                Activity.class, "onWindowFocusChanged", boolean.class);
        Method dispatchTouchEvent = findMethod(
                Activity.class, "dispatchTouchEvent", MotionEvent.class);
        String stem = spec.getPackageName() + "-extra";

        module.hook(onResume)
                .setId(stem + "-resume")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = targetActivity(chain.getThisObject(), spec);
                    if (activity != null) {
                        activate(activity, delegate);
                        activity.getWindow().getDecorView().postDelayed(
                                () -> activate(activity, delegate), 150L);
                    }
                    return result;
                });

        module.hook(onPause)
                .setId(stem + "-pause")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = targetActivity(chain.getThisObject(), spec);
                    if (activity != null) {
                        try {
                            if (delegate.hasHost(activity)) delegate.setForeground(activity, false);
                        } catch (Throwable error) {
                            ExtraCrashLog.note("pause", error);
                        }
                    }
                    return chain.proceed();
                });

        module.hook(onDestroy)
                .setId(stem + "-destroy")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Activity activity = targetActivity(chain.getThisObject(), spec);
                    if (activity != null) {
                        try {
                            if (delegate.hasHost(activity)) delegate.dispose(activity);
                        } catch (Throwable error) {
                            ExtraCrashLog.note("destroy", error);
                        }
                    }
                    return chain.proceed();
                });

        module.hook(onWindowFocusChanged)
                .setId(stem + "-focus")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = targetActivity(chain.getThisObject(), spec);
                    if (activity != null) {
                        activate(activity, delegate);
                        try {
                            if (delegate.hasHost(activity)) delegate.refresh(activity);
                        } catch (Throwable error) {
                            ExtraCrashLog.note("focus", error);
                        }
                    }
                    return result;
                });

        module.hook(dispatchTouchEvent)
                .setId(stem + "-touch")
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    Activity activity = targetActivity(chain.getThisObject(), spec);
                    Object event = chain.getArg(0);
                    if (activity != null) {
                        // Also catches PackageReady arriving after the first resume/focus.
                        activate(activity, delegate);
                        if (event instanceof MotionEvent) {
                            try {
                                if (delegate.hasHost(activity)) {
                                    delegate.onTouchEvent(activity, (MotionEvent) event);
                                }
                            } catch (Throwable error) {
                                ExtraCrashLog.note("touch", error);
                            }
                        }
                    }
                    return result;
                });
    }

    /**
     * 主线程消息队列里的 attach（postDelayed / decorView.post）不受 hook 的 PROTECTIVE 模式保护，
     * 一旦抛异常就是宿主进程崩溃。这里整段兜住并把栈写进 {@link ExtraCrashLog}。
     */
    private static void activate(Activity activity, ExtraNavigationDelegate delegate) {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        try {
            delegate.attach(activity);
            delegate.setForeground(activity, true);
        } catch (Throwable error) {
            ExtraCrashLog.note("attach", error);
        }
    }

    private static Activity targetActivity(Object target, ExtraNavigationSpec spec) {
        if (!(target instanceof Activity)) return null;
        Activity activity = (Activity) target;
        return spec.getPackageName().equals(activity.getPackageName()) ? activity : null;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes)
            throws NoSuchMethodException {
        Class<?> current = type;
        while (current != null) {
            try {
                Method method = current.getDeclaredMethod(name, parameterTypes);
                method.setAccessible(true);
                return method;
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }
}
