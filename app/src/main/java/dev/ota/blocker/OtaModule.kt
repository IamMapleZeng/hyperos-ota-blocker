package dev.ota.blocker

import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam

class OtaModule : XposedModule() {
    private var process: String? = null

    override fun onModuleLoaded(param: ModuleLoadedParam) {
        process = param.processName
    }

    override fun onPackageReady(param: PackageReadyParam) {
        if (!param.isFirstPackage) return
        protect("更新器适配失败", Unit) {
            val profile = UpdaterProfiles.verified.singleOrNull { it.matches(param.applicationInfo) }
                ?: return@protect
            val endpoints = profile.endpoints.filter { it.process == process }
            if (endpoints.isEmpty()) return@protect
            val preferences = getRemotePreferences(OtaPolicy.GROUP)
            OtaHookManager(this, preferences).install(endpoints, param.classLoader)
        }
    }
}
