package party.morino.betonquest.daily.mineauth.routes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.betonquest.betonquest.BetonQuest
import org.betonquest.betonquest.api.profile.Profile
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

            // UUIDからProfileを取得
            val profile = betonQuest.profileProvider.getProfile(player.uniqueId)

            // PlayerDataを取得（フォールバック用）
            val playerData = betonQuest.playerDataStorage.getOffline(profile)

            // オブジェクティブデータを取得（オンライン/オフラインで取得元を切り替え）
            val objectives = buildObjectivesMap(betonQuest, profile, player, playerData)

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
     * オンラインプレイヤーの場合はObjectiveインスタンスからリアルタイムデータを取得し、
     * オフラインプレイヤーの場合はDBスナップショット（rawObjectives）を使用する
     *
     * rawObjectivesはログイン時にDBからロードされた初期データであり、
     * プレイ中のObjective進捗変更はObjectiveインスタンスの
     * ObjectiveDataに保持されるため、ライブデータの取得が必要
     *
     * @param betonQuest BetonQuestインスタンス
     * @param profile プレイヤーのProfile
     * @param player 対象プレイヤー
     * @param playerData フォールバック用のPlayerData
     * @return オブジェクティブID -> シリアライズデータのマップ
     */
    private fun buildObjectivesMap(
        betonQuest: BetonQuest,
        profile: Profile,
        player: OfflinePlayer,
        playerData: PlayerData
    ): Map<String, String> {
        return try {
            val isOnline = Bukkit.getPlayer(player.uniqueId) != null
            if (isOnline) {
                // オンライン: Objectiveインスタンスから最新データを取得
                val activeObjectives = betonQuest.questTypeApi.getPlayerObjectives(profile)
                activeObjectives.associate { objective ->
                    objective.label.orEmpty() to objective.getData(profile).orEmpty()
                }
            } else {
                // オフライン: DBスナップショットを使用
                rawObjectivesToMap(playerData)
            }
        } catch (e: Exception) {
            // フォールバック: rawObjectivesを使用
            try {
                rawObjectivesToMap(playerData)
            } catch (_: Exception) {
                emptyMap()
            }
        }
    }

    /**
     * PlayerDataのrawObjectivesをKotlin型安全なMapに変換する
     */
    private fun rawObjectivesToMap(playerData: PlayerData): Map<String, String> {
        return playerData.rawObjectives
            .mapKeys { it.key.orEmpty() }
            .mapValues { it.value?.toString().orEmpty() }
    }
}
