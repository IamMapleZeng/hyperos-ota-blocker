package dev.ota.blocker

import android.content.SharedPreferences
import android.util.Log
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.error.XposedFrameworkError
import java.util.concurrent.atomic.AtomicBoolean

internal class OtaHookManager(
    private val module: OtaModule,
    private val preferences: SharedPreferences
) {
    fun install(endpoints: List<OtaEndpoint>, loader: ClassLoader) {
        endpoints.groupBy { it.action }.forEach { (action, group) ->
            protect("Hook 安装失败 · $action", Unit) {
                val methods = group.map { it to it.resolve(loader) }
                // Publish only after every entry point for the action in this process is hooked.
                val ready = AtomicBoolean()
                try {
                    methods.forEach { (endpoint, method) ->
                        val logged = AtomicBoolean()
                        module.hook(method).setExceptionMode(ExceptionMode.PROTECTIVE).intercept { chain ->
                            val block = ready.get() && protect("配置读取失败", false) {
                                val policy = OtaPolicy(preferences.getInt(OtaPolicy.KEY, 0))
                                endpoint.shouldBlock(policy, chain.args)
                            }
                            if (block) {
                                if (logged.compareAndSet(false, true)) {
                                    protect("LSPosed 日志写入失败", Unit) {
                                        module.log(Log.INFO, Diagnostics.TAG, "已拦截 ${method.declaringClass.name}.${method.name}")
                                    }
                                }
                                endpoint.result
                            } else {
                                chain.proceed()
                            }
                        }
                    }
                } catch (error: XposedFrameworkError) {
                    Diagnostics.record("Hook 安装失败 · $action · ${error.javaClass.simpleName}")
                    return@protect
                }
                ready.set(true)
                protect("LSPosed 日志写入失败", Unit) {
                    module.log(Log.INFO, Diagnostics.TAG, "已注册 $action · ${methods.size} 个入口")
                }
            }
        }
    }
}
