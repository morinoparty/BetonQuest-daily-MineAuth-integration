package party.morino.betonquest.daily.mineauth.routes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.betonquest.betonquest.BetonQuest
import org.betonquest.betonquest.database.PlayerData
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import party.morino.betonquest.daily.mineauth.data.DailyQuestResponse
import party.morino.betonquest.daily.mineauth.utils.DailyQuestExtractor
import party.morino.betonquest.daily.mineauth.utils.coroutines.minecraft
import party.morino.mineauth.api.annotations.Authenticated
import party.morino.mineauth.api.annotations.GetMapping

/**
 * デイリークエスト情報を提供するハンドラー
 * /api/v1/plugins/{plugin-name}/ 配下にエンドポイントを提供する
 */
class DailyQuestHandler {

    /**
     * 認証済みプレイヤーのデイリークエストデータを取得する
     * GET /daily-quests/me
     *
     * @param player 認証済みプレイヤー（JWTから自動解決）
     * @return デイリークエストの進捗情報
     */
    @GetMapping("/daily-quests/me")
    suspend fun getMyDailyQuests(@Authenticated player: OfflinePlayer): DailyQuestResponse {
        // Bukkit APIはメインスレッドで実行する必要がある
        return withContext(Dispatchers.minecraft) {
            val betonQuest = BetonQuest.getInstance()

            // オブジェクティブデータを取得（オンライン/オフラインで取得元を切り替え）
            val objectives = buildObjectivesMap(betonQuest, player)

            // デイリークエスト情報をvariable objectiveデータから抽出
            val dailyQuests = try {
                DailyQuestExtractor.extract(objectives)
            } catch (e: Exception) {
                emptyList()
            }

            DailyQuestResponse(dailyQuests = dailyQuests)
        }
    }

    /**
     * オブジェクティブデータのマップを構築する
     * オンラインプレイヤーの場合はOnlineProfileを使ってObjectiveインスタンスから
     * リアルタイムデータを取得し、オフラインプレイヤーの場合はDBスナップショット（rawObjectives）を使用する
     *
     * 重要: ProfileProvider.getProfile(UUID)はProfileを返すが、
     * ProfileProvider.getProfile(Player)はOnlineProfileを返す。
     * getPlayerObjectivesやgetDataでライブデータを取得するには
     * OnlineProfileが必要なため、オンライン時はPlayer経由でプロファイルを取得する。
     *
     * @param betonQuest BetonQuestインスタンス
     * @param player 対象プレイヤー
     * @return オブジェクティブID -> シリアライズデータのマップ
     */
    private fun buildObjectivesMap(
        betonQuest: BetonQuest,
        player: OfflinePlayer
    ): Map<String, String> {
        val onlinePlayer = Bukkit.getPlayer(player.uniqueId)

        if (onlinePlayer != null) {
            // オンライン: OnlineProfileを取得してObjectiveからライブデータを取得
            return try {
                val onlineProfile = betonQuest.profileProvider.getProfile(onlinePlayer)
                val activeObjectives = betonQuest.questTypeApi.getPlayerObjectives(onlineProfile)
                activeObjectives.associate { objective ->
                    objective.label.orEmpty() to objective.getData(onlineProfile).orEmpty()
                }
            } catch (e: Exception) {
                // ライブデータ取得失敗時はrawObjectivesにフォールバック
                rawObjectivesFromPlayer(betonQuest, player)
            }
        }

        // オフライン: DBスナップショットを使用
        return rawObjectivesFromPlayer(betonQuest, player)
    }

    /**
     * PlayerDataのrawObjectivesをUUID経由で取得する
     * オフライン時またはライブデータ取得失敗時のフォールバック
     */
    private fun rawObjectivesFromPlayer(
        betonQuest: BetonQuest,
        player: OfflinePlayer
    ): Map<String, String> {
        return try {
            val profile = betonQuest.profileProvider.getProfile(player.uniqueId)
            val playerData = betonQuest.playerDataStorage.getOffline(profile)
            playerData.rawObjectives
                .mapKeys { it.key.orEmpty() }
                .mapValues { it.value?.toString().orEmpty() }
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
