package party.morino.betonquest.daily.mineauth

import org.bukkit.plugin.java.JavaPlugin
import party.morino.betonquest.daily.mineauth.routes.DailyQuestHandler
import party.morino.mineauth.api.MineAuthAPI

/**
 * BetonQuestデイリークエストのMineAuth連携プラグイン
 * MineAuthのHTTP API経由でデイリークエストの進捗情報を提供する
 */
class DailyQuestMineAuthPlugin : JavaPlugin() {

    private lateinit var mineAuthAPI: MineAuthAPI

    override fun onEnable() {
        logger.info("BetonQuest DailyQuest MineAuth integration enabling...")

        // MineAuthAPIの取得
        val mineAuthPlugin = server.pluginManager.getPlugin("MineAuth")
        val api = mineAuthPlugin as? MineAuthAPI
        if (api == null) {
            logger.severe("MineAuth plugin not found or is not a valid MineAuthAPI instance")
            server.pluginManager.disablePlugin(this)
            return
        }
        mineAuthAPI = api

        // BetonQuestプラグインの存在確認
        if (!verifyBetonQuest()) {
            logger.severe("BetonQuest plugin not found")
            server.pluginManager.disablePlugin(this)
            return
        }

        // MineAuthにデイリークエストハンドラーを登録
        setupMineAuth()

        logger.info("BetonQuest DailyQuest MineAuth integration enabled")
    }

    override fun onDisable() {
        logger.info("BetonQuest DailyQuest MineAuth integration disabled")
    }

    /**
     * BetonQuestプラグインの検証
     */
    private fun verifyBetonQuest(): Boolean {
        val bqPlugin = server.pluginManager.getPlugin("BetonQuest")
        if (bqPlugin == null || !bqPlugin.isEnabled) {
            logger.warning("BetonQuest plugin not found or not enabled")
            return false
        }
        logger.info("BetonQuest plugin found: ${bqPlugin.pluginMeta.version}")
        return true
    }

    /**
     * MineAuthにデイリークエストハンドラーを登録する
     */
    private fun setupMineAuth() {
        mineAuthAPI.createHandler(this)
            .register(DailyQuestHandler())
    }
}
