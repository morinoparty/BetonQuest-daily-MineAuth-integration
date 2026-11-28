package party.morino.betonquest.daily.mineauth

import org.betonquest.betonquest.api.BetonQuestApiService
import org.bukkit.plugin.java.JavaPlugin
import party.morino.betonquest.daily.mineauth.config.PluginConfig
import party.morino.betonquest.daily.mineauth.routes.DailyQuestHandler
import party.morino.mineauth.api.EndpointRegistrationException
import party.morino.mineauth.api.MineAuthApi

/**
 * BetonQuestデイリークエストのMineAuth連携プラグイン
 * MineAuthのHTTP API経由でデイリークエストの進捗情報を提供する
 */
class DailyQuestMineAuthPlugin : JavaPlugin() {

    private companion object {
        // MineAuth の URL 名前空間。プラグイン名は 32 文字を超えるため短い名前を明示する
        const val MINEAUTH_NAMESPACE = "dailyquest"
    }

    override fun onEnable() {
        logger.info("BetonQuest DailyQuest MineAuth integration enabling...")

        // config.ymlの読み込み
        saveDefaultConfig()
        val pluginConfig = PluginConfig.fromBukkitConfig(config)
        logger.info("Daily reset time: ${pluginConfig.dailyResetTime}")

        // BetonQuest 3.2.0 以降は ServicesManager 経由で API を取得する
        val betonQuestApi = BetonQuestApiService.get().orElse(null)?.api(this)
        if (betonQuestApi == null) {
            logger.severe("BetonQuest API service is not available")
            server.pluginManager.disablePlugin(this)
            return
        }

        // MineAuth はロード済みだがサービス登録前という狭い窓のみ null になる
        val mineAuthApi = MineAuthApi.get(server)
        if (mineAuthApi == null) {
            logger.severe("MineAuth service is not registered")
            server.pluginManager.disablePlugin(this)
            return
        }

        try {
            val registration = mineAuthApi.register(this, MINEAUTH_NAMESPACE, DailyQuestHandler(pluginConfig, betonQuestApi))
            logger.info("Mounted ${registration.endpoints.size} endpoints under ${registration.basePath}")
        } catch (e: EndpointRegistrationException) {
            // 登録は all-or-nothing のため、検証エラーの詳細をログに出す
            logger.severe("Failed to register MineAuth endpoints: ${e.message}")
            server.pluginManager.disablePlugin(this)
            return
        }

        logger.info("BetonQuest DailyQuest MineAuth integration enabled")
    }

    override fun onDisable() {
        logger.info("BetonQuest DailyQuest MineAuth integration disabled")
    }
}
