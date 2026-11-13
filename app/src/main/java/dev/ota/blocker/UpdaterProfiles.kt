package dev.ota.blocker

import android.content.pm.ApplicationInfo
import android.os.Build
import java.io.File
import java.lang.reflect.Method
import java.security.MessageDigest

internal data class OtaEndpoint(
    val action: OtaAction,
    val process: String,
    val owner: String,
    val name: String,
    val parameters: List<String>,
    val returnType: String = "void",
    val streamingPayloadIndex: Int? = null,
    val result: Any? = null,
    val applies: (List<Any?>) -> Boolean = { true }
) {
    fun shouldBlock(policy: OtaPolicy, args: List<Any?>): Boolean {
        val streaming = policy.blocks(OtaAction.DOWNLOAD) &&
            streamingPayloadIndex?.let { args[it] != null } == true
        return (policy.blocks(action) || streaming) && applies(args)
    }

    fun resolve(loader: ClassLoader): Method = Class.forName(owner, false, loader)
        .declaredMethods.single {
            it.name == name &&
                it.parameterTypes.map { type -> type.name } == parameters &&
                it.returnType.name == returnType &&
                !java.lang.reflect.Modifier.isAbstract(it.modifiers)
        }
}

internal data class UpdaterProfile(
    val packageName: String,
    val fingerprint: String,
    val apkDigests: Set<String>,
    val endpoints: List<OtaEndpoint>
) {
    fun matches(info: ApplicationInfo): Boolean {
        if (info.packageName != packageName || Build.FINGERPRINT != fingerprint ||
            info.flags and ApplicationInfo.FLAG_SYSTEM == 0
        ) return false
        val paths = listOf(info.sourceDir) + info.splitSourceDirs.orEmpty()
        if (paths.size != apkDigests.size) return false
        return paths.map { path ->
            val digest = MessageDigest.getInstance("SHA-256")
            File(path).inputStream().buffered().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val size = input.read(buffer)
                    if (size < 0) break
                    digest.update(buffer, 0, size)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        }.toSet() == apkDigests
    }
}

internal object UpdaterProfiles {
    const val UPDATER = "com.android.updater"
    private const val STRING = "java.lang.String"
    private const val CONTEXT = "android.content.Context"
    private const val INTENT = "android.content.Intent"
    private const val PENDING_INTENT = "android.app.PendingIntent"

    val verified = listOf(
        UpdaterProfile(
            UPDATER,
            "Xiaomi/xuanyuan/xuanyuan:17/CP2A.260605.016/OS4.0.3.0.XOACNXM:user/release-keys",
            setOf("7f4f3fc09d44a6d9140b0e986f6e1ed1cd054f662d8aba29a191b95e4bbf8f44"),
            listOf(
                OtaEndpoint(OtaAction.CHECK, UPDATER, "$UPDATER.A", "b", emptyList(),
                    returnType = "$UPDATER.UpdateInfo"),
                OtaEndpoint(OtaAction.DOWNLOAD, UPDATER, "$UPDATER.C", "i0", listOf(STRING, STRING)),
                OtaEndpoint(OtaAction.INSTALL, UPDATER, "$UPDATER.abupdate.UpdateEngineClient",
                    "updatePackage", listOf(STRING, "android.net.Uri", "boolean",
                        "$UPDATER.abupdate.Payload", "boolean"), returnType = "boolean", result = false,
                    streamingPayloadIndex = 3),
                OtaEndpoint(OtaAction.INSTALL, UPDATER, "$UPDATER.K\$b", "run", emptyList()),
                OtaEndpoint(OtaAction.REMINDER, UPDATER, "$UPDATER.receiver.LauncherDialogReceiver",
                    "onReceive", listOf(CONTEXT, INTENT), applies = { args ->
                        val intent = args[1] as? android.content.Intent
                        intent?.action == "$UPDATER.intent.ACTION_SHOW_DIALOG" &&
                            intent.getIntExtra("type", 0) in 1..2
                    }),
                OtaEndpoint(OtaAction.REMINDER, UPDATER, "q0.v", "o",
                    listOf(CONTEXT, "int"), applies = { args -> (args[1] as? Int ?: 0) in 1..2 }),
                OtaEndpoint(OtaAction.REMINDER, UPDATER, "q0.q", "q",
                    listOf(CONTEXT, STRING, "boolean", "$UPDATER.policy.Policy", "int")),
                OtaEndpoint(OtaAction.REMINDER, UPDATER, "q0.q", "n", listOf(CONTEXT)),
                OtaEndpoint(OtaAction.REMINDER, UPDATER, "q0.q", "w",
                    listOf(CONTEXT, PENDING_INTENT, "int", "int", "boolean", STRING, STRING, STRING,
                        "boolean", "boolean")),
                OtaEndpoint(OtaAction.REMINDER, UPDATER, "q0.q", "x",
                    listOf(CONTEXT, "int", STRING, STRING, "int", STRING, PENDING_INTENT,
                        PENDING_INTENT, "boolean", "boolean"),
                    applies = { args -> args[1] == 3 || args[1] == 4 }),
                OtaEndpoint(OtaAction.REMINDER, UPDATER, "q0.q", "y",
                    listOf(CONTEXT, INTENT, "int", STRING, STRING, "int", "boolean", "boolean"))
            )
        )
    )
}
