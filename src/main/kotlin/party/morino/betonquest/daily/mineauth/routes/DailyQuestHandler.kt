package party.morino.betonquest.daily.mineauth.routes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.betonquest.betonquest.BetonQuest
import org.betonquest.betonquest.database.PlayerData
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import party.morino.betonquest.daily.mineauth.data.ActionResponse
import party.morino.betonquest.daily.mineauth.data.DailyQuestResponse
import party.morino.betonquest.daily.mineauth.data.QuestSelectRequest
import party.morino.betonquest.daily.mineauth.utils.BetonQuestEventExecutor
import party.morino.betonquest.daily.mineauth.utils.DailyQuestExtractor
import party.morino.betonquest.daily.mineauth.utils.coroutines.minecraft
import party.morino.mineauth.api.annotations.Authenticated
import party.morino.mineauth.api.annotations.GetMapping
import party.morino.mineauth.api.annotations.PostMapping
import party.morino.mineauth.api.annotations.RequestBody
import party.morino.mineauth.api.http.HttpError
import party.morino.mineauth.api.http.HttpStatus

/**
 * デイリークエスト情報を提供するハンドラー
 * /api/v1/plugins/{plugin-name}/ 配下にエンドポイントを提供する
 */
class DailyQuestHandler {

    private companion object {
        // リロールイベントのパッケージ名とイベント名
        const val REROLL_PACKAGE = "NotEnoughQuests-DailyQuest"
        const val REROLL_EVENT = "Reroll#start"

        // クエスト選択イベントのパッケージ名
        const val QUEST_REGISTRY_PACKAGE = "NotEnoughQuests-DailyQuest-Quests"

        // 難易度 -> BetonQuestイベント名のマッピング
        val DIFFICULTY_EVENT_MAP = mapOf(
            "easy" to "QuestRegistry#selectRandomEasyDailyQuest",
            "normal" to "QuestRegistry#selectRandomNormalDailyQuest",
            "hard" to "QuestRegistry#selectRandomHardDailyQuest"
        )
    }

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
     * デイリークエストのリロールを実行する
     * POST /daily-quests/me/reroll
     *
     * BetonQuestの Reroll#start イベントを発火し、
     * 現在のクエストを破棄して新しいランダムクエストを選択する
     * リロールポイントが不足している場合はイベント側の条件で実行がスキップされる
     *
     * @param player 認証済みプレイヤー（JWTから自動解決）
     * @return アクション実行結果
     */
    @PostMapping("/daily-quests/me/reroll")
    suspend fun rerollDailyQuests(@Authenticated player: OfflinePlayer): ActionResponse {
        // イベント実行にはオンラインプレイヤーが必要（BetonQuestのOnlineProfile）
        val onlinePlayer = requireOnlinePlayer(player)

        return withContext(Dispatchers.minecraft) {
            val betonQuest = BetonQuest.getInstance()
            val onlineProfile = betonQuest.profileProvider.getProfile(onlinePlayer)

            try {
                // Reroll#startイベントを実行（条件チェックはBetonQuest側で行われる）
                val success = BetonQuestEventExecutor.fireEvent(
                    profile = onlineProfile,
                    packageName = REROLL_PACKAGE,
                    eventName = REROLL_EVENT
                )
                if (success) {
                    ActionResponse(success = true, message = "Daily quests rerolled successfully")
                } else {
                    ActionResponse(success = false, message = "Reroll conditions not met")
                }
            } catch (e: Exception) {
                throw HttpError(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to execute reroll: ${e.message}", emptyMap())
            }
        }
    }

    /**
     * 指定された難易度のデイリークエストをランダム選択する
     * POST /daily-quests/me/select
     *
     * BetonQuestのQuestRegistryイベントを発火し、
     * 指定された難易度プールからランダムにクエストを登録する
     *
     * @param player 認証済みプレイヤー（JWTから自動解決）
     * @param request クエスト選択リクエスト（難易度を指定）
     * @return アクション実行結果
     */
    @PostMapping("/daily-quests/me/select")
    suspend fun selectDailyQuest(
        @Authenticated player: OfflinePlayer,
        @RequestBody request: QuestSelectRequest
    ): ActionResponse {
        // 難易度の入力バリデーション
        val eventName = DIFFICULTY_EVENT_MAP[request.difficulty.lowercase()]
            ?: throw HttpError(
                HttpStatus.BAD_REQUEST,
                "Invalid difficulty: '${request.difficulty}'. Must be one of: ${DIFFICULTY_EVENT_MAP.keys.joinToString()}",
                emptyMap()
            )

        // イベント実行にはオンラインプレイヤーが必要
        val onlinePlayer = requireOnlinePlayer(player)

        return withContext(Dispatchers.minecraft) {
            val betonQuest = BetonQuest.getInstance()
            val onlineProfile = betonQuest.profileProvider.getProfile(onlinePlayer)

            try {
                // 対応する難易度のクエスト選択イベントを実行
                val success = BetonQuestEventExecutor.fireEvent(
                    profile = onlineProfile,
                    packageName = QUEST_REGISTRY_PACKAGE,
                    eventName = eventName
                )
                if (success) {
                    ActionResponse(success = true, message = "Quest selected with difficulty: ${request.difficulty}")
                } else {
                    ActionResponse(success = false, message = "Quest selection conditions not met")
                }
            } catch (e: Exception) {
                throw HttpError(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to select quest: ${e.message}", emptyMap())
            }
        }
    }

    /**
     * プレイヤーがオンラインであることを確認する
     * BetonQuestのイベント実行にはOnlineProfileが必要なため、
     * オフラインプレイヤーの場合はHTTPエラーを返す
     *
     * @param player 認証済みプレイヤー
     * @return オンラインのPlayerインスタンス
     * @throws HttpError プレイヤーがオフラインの場合
     */
    private fun requireOnlinePlayer(player: OfflinePlayer): Player {
        return Bukkit.getPlayer(player.uniqueId)
            ?: throw HttpError(HttpStatus.BAD_REQUEST, "Player must be online to perform this action", emptyMap())
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
