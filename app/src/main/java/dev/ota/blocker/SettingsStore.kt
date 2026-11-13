package dev.ota.blocker

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class BlockerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(SettingsStore)
    }
}

internal object SettingsStore : XposedServiceHelper.OnServiceListener {
    private val connection = MutableStateFlow<XposedService?>(null)
    val service = connection.asStateFlow()

    override fun onServiceBind(service: XposedService) {
        Diagnostics.record("收到 LSPosed 服务连接")
        connection.value = service
    }

    override fun onServiceDied(service: XposedService) {
        if (connection.compareAndSet(service, null)) Diagnostics.record("LSPosed 服务已断开")
    }

    fun read(service: XposedService): OtaPolicy = OtaPolicy(
        service.getRemotePreferences(OtaPolicy.GROUP).getInt(OtaPolicy.KEY, 0)
    )

    fun write(service: XposedService, policy: OtaPolicy): Boolean {
        if (connection.value !== service) return false
        return service.getRemotePreferences(OtaPolicy.GROUP).edit()
            .putInt(OtaPolicy.KEY, policy.bits).commit()
    }
}
