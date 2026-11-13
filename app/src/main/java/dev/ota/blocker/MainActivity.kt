package dev.ota.blocker

import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class UpdaterStatus(val version: String = "未确认", val actions: Set<OtaAction> = emptySet())

private data class FrameworkStatus(
    val version: String,
    val target: String,
    val policy: OtaPolicy?
)

class MainActivity : ComponentActivity() {
    private var resumeCount by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        resumeCount++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                SettingsScreen()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun updaterStatus(): UpdaterStatus {
        val profile = UpdaterProfiles.verified.firstOrNull { it.fingerprint == Build.FINGERPRINT }
        return protect("更新器查询失败", UpdaterStatus()) {
            val pkg = packageManager.getPackageInfo(UpdaterProfiles.UPDATER, PackageManager.GET_META_DATA)
            val info = pkg.applicationInfo
            val actions = if (info != null && profile != null && profile.matches(info)) {
                profile.endpoints.map { it.action }.toSet()
            } else emptySet()
            UpdaterStatus("${pkg.packageName} ${pkg.versionName} (${pkg.longVersionCode})", actions)
        }
    }

    @Composable
    private fun SettingsScreen() {
        val service by SettingsStore.service.collectAsState()
        var updater by remember { mutableStateOf(UpdaterStatus()) }
        var policy by remember(service) { mutableStateOf<OtaPolicy?>(null) }
        var framework by remember(service) { mutableStateOf("未连接") }
        var target by remember(service) { mutableStateOf("模块服务未连接") }
        var message by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var logText by remember { mutableStateOf<String?>(null) }
        val scope = rememberCoroutineScope()

        LaunchedEffect(resumeCount) { updater = withContext(Dispatchers.IO) { updaterStatus() } }
        LaunchedEffect(service, resumeCount) {
            val current = service ?: return@LaunchedEffect
            val status = withContext(Dispatchers.IO) {
                protect("连接信息读取失败", null) {
                    val api = current.apiVersion
                    val name = "${current.frameworkName} ${current.frameworkVersion} (${current.frameworkVersionCode}) · API $api"
                    if (api < 102) {
                        FrameworkStatus(name, "需要 API 102", null)
                    } else {
                        val packageName = UpdaterProfiles.UPDATER
                        val targetState = protect("目标进程状态查询失败", "进程状态不可用") {
                            val running = current.runningTargets.firstOrNull { it.processName == packageName }
                            when {
                                packageName !in current.scope -> "更新器未加入模块作用域"
                                running != null -> "已注入更新器 · ${running.state}"
                                else -> "已配置作用域，等待更新器启动"
                            }
                        }
                        FrameworkStatus(name, targetState, SettingsStore.read(current))
                    }
                }
            }
            framework = status?.version ?: "连接读取失败"
            target = status?.target ?: "状态不可用"
            policy = status?.policy
        }

        fun save(next: OtaPolicy, restoring: Boolean = false) {
            val current = service ?: return
            if (busy) return
            busy = true
            scope.launch {
                try {
                    val saved = withContext(Dispatchers.IO) {
                        protect("设置保存失败", false) { SettingsStore.write(current, next) }
                    }
                    if (saved) {
                        if (service === current) policy = next
                        Diagnostics.record(if (restoring) "全部开关已关闭" else "屏蔽配置已保存")
                        message = if (restoring) {
                            "已关闭所有拦截。若页面仍显示旧状态，请重新启动更新应用。"
                        } else "设置已保存"
                    } else {
                        message = "保存失败。可在 LSPosed 禁用模块，然后重新启动更新应用。"
                    }
                } finally {
                    busy = false
                }
            }
        }

        val otaStatus = when {
            updater.actions.isEmpty() -> "未适配此构建"
            policy == null -> "配置不可读"
            policy?.enabled != true -> "已关闭"
            updater.actions.none { policy?.blocks(it) == true } -> "总开关已打开，未选择拦截功能"
            else -> "已配置，运行效果以更新器日志为准"
        }
        val build = "Android ${Build.VERSION.RELEASE} · ${Build.DISPLAY}"
        val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri != null) {
                val report = """
                    HyperOS OTA Blocker
                    $build
                    Fingerprint: ${Build.FINGERPRINT}
                    LSPosed: $framework
                    Target: $target
                    Updater: ${updater.version}
                    OTA: $otaStatus

                    ${Diagnostics.snapshot()}
                """.trimIndent()
                scope.launch {
                    val exported = withContext(Dispatchers.IO) {
                        protect("导出诊断失败", false) {
                            val stream = contentResolver.openOutputStream(uri, "wt") ?: return@protect false
                            stream.bufferedWriter().use { it.write(report) }
                            true
                        }
                    }
                    message = if (exported) "已导出诊断信息" else "导出失败"
                }
            }
        }

        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("HyperOS OTA Blocker", style = MaterialTheme.typography.headlineMedium)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Status("当前系统版本", build)
                        Status("LSPosed 服务", framework)
                        Status("模块运行状态", target)
                        Status("当前更新应用版本", updater.version)
                        Status("OTA 屏蔽状态", otaStatus)
                    }
                }
                val canEdit = policy != null && !busy
                Toggle("启用系统更新屏蔽", policy?.enabled == true,
                    canEdit && (updater.actions.isNotEmpty() || policy?.enabled == true)) {
                    policy?.let { existing -> save(existing.set(OtaPolicy.MASTER, it)) }
                }
                OtaAction.entries.forEach { action ->
                    Toggle(action.label, policy?.selected(action) == true, canEdit && action in updater.actions) {
                        policy?.let { existing -> save(existing.set(action.bit, it)) }
                    }
                }
                OutlinedButton(onClick = { save(OtaPolicy(), restoring = true) }, enabled = canEdit) {
                    Text("恢复系统更新 / 默认设置")
                }
                OutlinedButton(onClick = { logText = Diagnostics.snapshot() }) { Text("查看调试日志") }
                OutlinedButton(onClick = { export.launch("hyperos-ota-diagnostics.txt") }) {
                    Text("导出诊断信息")
                }
                if (message.isNotBlank()) Text(message)
            }
        }
        logText?.let { logs ->
            AlertDialog(
                onDismissRequest = { logText = null },
                title = { Text("调试日志") },
                text = {
                    Text("设置进程日志\n\n$logs\n\n更新器进程日志请在 LSPosed 管理器查看。",
                        Modifier.verticalScroll(rememberScrollState()))
                },
                confirmButton = { TextButton(onClick = { logText = null }) { Text("关闭") } }
            )
        }
    }
}

@Composable
private fun Status(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Toggle(label: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange, enabled = enabled)
    }
}
