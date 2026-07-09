package com.csbaby.kefu.infrastructure.ota

import com.csbaby.kefu.data.model.OtaUpdate

/**
 * OTA APK 下载路径决策器 — 纯函数,从 OtaUpdate 推断出本地下载路径布局。
 *
 * 设计约束(由 [file_paths.xml] 决定):
 * - 子目录必须是 App 私有外部目录 `getExternalFilesDir(SUBDIR)` 下的 ota_updates
 * - 与 file_paths.xml 的 `<external-files-path name="external_app_files" path="."/>` 对齐
 * - 命名规范: `kefu_v{versionName}_{versionCode}.apk`
 *
 * 为何从 OtaManager 中抽出:
 * 1. Context/Environment 不可在 JVM 单元测试中注入, 抽出后纯逻辑可单测
 * 2. 路径策略可替换(默认私有目录, 未来可改为 cache 或公开目录)
 * 3. 故障根因(原 OtaManager 写公开 Download/, 与 FileProvider 越界)可被测试捕获
 *
 * resolve 只产出决策(子目录名 + 文件名),不绑定具体 baseDir;
 * OtaManager 拿到 [PathDecision] 后负责把 baseDir 注入:
 * `File(context.getExternalFilesDir(decision.subdir), decision.fileName)`。
 */
object OtaDownloadPath {

    /** 下载子目录名,需与调用方传入 getExternalFilesDir() 的参数一致。 */
    const val SUBDIR: String = "ota_updates"

    /**
     * 路径决策结果:子目录 + 文件名。
     */
    data class PathDecision(
        val subdir: String,
        val fileName: String
    )

    /**
     * 解析出 APK 下载路径的子目录与文件名决策。
     *
     * @throws IllegalArgumentException versionName 为空或 versionCode <= 0
     */
    fun resolve(update: OtaUpdate): PathDecision {
        require(update.versionName.isNotBlank()) {
            "OTA versionName 不能为空, 当前 update=$update"
        }
        require(update.versionCode > 0) {
            "OTA versionCode 必须 > 0, 当前=${update.versionCode}"
        }
        // 替换可能引起文件系统问题的字符, 保留 versionName 中的字母数字点横线
        val safeName = update.versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val fileName = "kefu_v${safeName}_${update.versionCode}.apk"
        return PathDecision(subdir = SUBDIR, fileName = fileName)
    }
}
