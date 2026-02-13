package party.morino.betonquest.daily.mineauth.config

import org.bukkit.configuration.file.FileConfiguration
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * プラグイン設定を保持するデータクラス
 *
 * @param dailyResetTime デイリークエストのリセット時刻
 */
data class PluginConfig(
    val dailyResetTime: LocalTime
) {
    companion object {
        // config.ymlのキー
        private const val KEY_DAILY_RESET_TIME = "daily-reset-time"

        // デフォルトのリセット時刻
        private val DEFAULT_RESET_TIME: LocalTime = LocalTime.of(4, 0)

        private val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")

        /**
         * BukkitのFileConfigurationからPluginConfigを生成する
         */
        fun fromBukkitConfig(config: FileConfiguration): PluginConfig {
            val resetTimeStr = config.getString(KEY_DAILY_RESET_TIME) ?: return PluginConfig(DEFAULT_RESET_TIME)

            val resetTime = try {
                LocalTime.parse(resetTimeStr, TIME_FORMATTER)
            } catch (e: DateTimeParseException) {
                DEFAULT_RESET_TIME
            }

            return PluginConfig(dailyResetTime = resetTime)
        }
    }
}
