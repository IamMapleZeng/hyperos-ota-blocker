package dev.ota.blocker

enum class OtaAction(val bit: Int, val label: String) {
    CHECK(2, "禁止检测系统更新"),
    DOWNLOAD(4, "禁止自动下载更新"),
    INSTALL(8, "禁止自动安装更新"),
    REMINDER(16, "隐藏系统更新提醒")
}

@JvmInline
value class OtaPolicy(val bits: Int = 0) {
    val enabled get() = bits and MASTER != 0

    fun selected(action: OtaAction): Boolean = bits and action.bit != 0
    fun blocks(action: OtaAction): Boolean = enabled && selected(action)
    fun set(bit: Int, checked: Boolean): OtaPolicy =
        OtaPolicy(if (checked) bits or bit else bits and bit.inv())

    companion object {
        const val MASTER = 1
        const val GROUP = "ota_policy"
        const val KEY = "flags_v1"
    }
}
