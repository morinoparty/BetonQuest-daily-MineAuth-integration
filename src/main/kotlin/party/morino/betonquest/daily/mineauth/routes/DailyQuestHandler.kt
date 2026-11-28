package party.morino.betonquest.daily.mineauth.routes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.betonquest.betonquest.BetonQuest
import org.betonquest.betonquest.api.BetonQuestApi
import org.betonquest.betonquest.api.profile.Profile
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import party.morino.betonquest.daily.mineauth.config.PluginConfig
import party.morino.betonquest.daily.mineauth.data.ActionResponse
import party.morino.betonquest.daily.mineauth.data.DailyQuestResponse
import party.morino.betonquest.daily.mineauth.data.QuestSelectRequest
import party.morino.betonquest.daily.mineauth.quest.QuestDifficulty
import party.morino.betonquest.daily.mineauth.quest.QuestFileLayout
import party.morino.betonquest.daily.mineauth.utils.BetonQuestScripts
import party.morino.betonquest.daily.mineauth.utils.DailyQuestExtractor
import party.morino.betonquest.daily.mineauth.utils.coroutines.minecraft
import party.morino.mineauth.api.annotations.Authenticated
import party.morino.mineauth.api.annotations.Body
import party.morino.mineauth.api.annotations.Caller
import party.morino.mineauth.api.annotations.Get
import party.morino.mineauth.api.annotations.Post
import party.morino.mineauth.api.auth.Principal
import party.morino.mineauth.api.http.HttpError
import party.morino.mineauth.api.http.HttpStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * デイリークエスト情報を提供するハンドラー
 * /api/v1/plugins/dailyquest/ 配下にエンドポイントを提供する
 *
 * @param config プラグイン設定
 * @param api BetonQuest の API
 */
class DailyQuestHandler(
    private val config: PluginConfig,
    private val api: BetonQuestApi
) {

    // クエストファイルのアクション・コンディションの実行
    private val scripts = BetonQuestScripts(api)

    /**
     * 認証済みプレイヤーのデイリークエストデータを取得する
     * GET /daily-quests/me
     *
     * オフラインプレイヤーでリセット時刻を跨いでいる場合、
     * DBのデータは前日のものであるため未受注として返す
     *
     * @param caller 認証済みプレイヤー
     * @return デイリークエストの進捗情報
     */
    @Get("/daily-quests/me")
    @Authenticated
    suspend fun getMyDailyQuests(@Caller caller: Principal.User): DailyQuestResponse {
        // Bukkit API と BetonQuest のデータはメインスレッドで読む
        return withContext(Dispatchers.minecraft) {
            val player = caller.offlinePlayer
            val onlinePlayer = Bukkit.getPlayer(caller.uuid)
            val profile = profileOf(player, onlinePlayer)
            val playerData = BetonQuest.getInstance().playerDataStorage.getOffline(profile)
            val rerollCharges = playerData.points().get(QuestFileLayout.REROLL_POINT).orElse(0)

            // 受注タグが無い、またはオフライン中にリセット時刻を跨いだ場合は、その日のクエストは無い
            val ordered = playerData.tags().has(QuestFileLayout.MAIN_INITIALIZED_TAG) &&
                (onlinePlayer != null || !isQuestDataStale(player))
            if (!ordered) {
                return@withContext DailyQuestResponse(
                    ordered = false,
                    online = onlinePlayer != null,
                    rerollCharges = rerollCharges,
                    dailyQuests = emptyList()
                )
            }

            // オブジェクティブデータを取得（オンライン/オフラインで取得元を切り替え）
            val objectives = buildObjectivesMap(profile, onlinePlayer != null)
            val liveAmounts = if (onlinePlayer != null) liveQuestAmounts(profile) else emptyMap()
            DailyQuestResponse(
                ordered = true,
                online = onlinePlayer != null,
                rerollCharges = rerollCharges,
                dailyQuests = DailyQuestExtractor.extract(objectives, liveAmounts)
            )
        }
    }

    /**
     * デイリークエストのリロールを実行する
     * POST /daily-quests/me/reroll
     *
     * クエストファイルの Reroll$start を実行し、現在のクエストを破棄して未受注に戻す。
     * リロールポイントの不足や、目標達成済みのクエストがある場合はアクション側の条件で実行されない
     *
     * @param caller 認証済みプレイヤー
     * @return アクション実行結果
     */
    @Post("/daily-quests/me/reroll")
    @Authenticated
    suspend fun rerollDailyQuests(@Caller caller: Principal.User): ActionResponse {
        return withContext(Dispatchers.minecraft) {
            // アクションの実行にはオンラインプレイヤーが必要（BetonQuest の OnlineProfile）
            val profile = api.profiles().getProfile(requireOnlinePlayer(caller))
            runScript("reroll") {
                val success = scripts.runAction(profile, QuestFileLayout.DAILY_QUEST_PACKAGE, QuestFileLayout.REROLL_ACTION)
                if (success) {
                    ActionResponse(success = true, message = "Daily quests rerolled successfully")
                } else {
                    ActionResponse(success = false, message = "Reroll conditions not met")
                }
            }
        }
    }

    /**
     * 指定された難易度でその日のデイリークエストを受注する
     * POST /daily-quests/me/select
     *
     * ゲーム内の会話と同じく、未受注のときだけ Main$destroy → Main$initialize → QuestSelector の順に実行する。
     * 受注済みのときに実行できると、達成済みのクエストを消して報酬を何度も受け取れてしまうため 409 を返す
     *
     * @param caller 認証済みプレイヤー
     * @param request クエスト選択リクエスト（難易度を指定）
     * @return アクション実行結果
     */
    @Post("/daily-quests/me/select")
    @Authenticated
    suspend fun selectDailyQuest(
        @Caller caller: Principal.User,
        @Body request: QuestSelectRequest
    ): ActionResponse {
        // 難易度の入力バリデーション
        val difficulty = QuestDifficulty.fromRequest(request.difficulty)
            ?: throw HttpError(
                HttpStatus.BAD_REQUEST,
                "Invalid difficulty: '${request.difficulty}'. Must be one of: easy, normal, hard"
            )
        val selectorAction = QuestFileLayout.SELECTOR_ACTIONS.getValue(difficulty)

        return withContext(Dispatchers.minecraft) {
            val profile = api.profiles().getProfile(requireOnlinePlayer(caller))
            val playerData = BetonQuest.getInstance().playerDataStorage.get(profile)

            // 受注済みなら何もしない（会話でも未受注のときしか選択肢が出ない）
            if (playerData.tags().has(QuestFileLayout.MAIN_INITIALIZED_TAG)) {
                throw HttpError(HttpStatus.CONFLICT, "Daily quests are already ordered today", "already_ordered")
            }

            runScript("select quest") {
                // 会話の 1_answer_* と同じ順で実行する
                scripts.runAction(profile, QuestFileLayout.DAILY_QUEST_PACKAGE, QuestFileLayout.MAIN_DESTROY_ACTION)
                scripts.runAction(profile, QuestFileLayout.DAILY_QUEST_PACKAGE, QuestFileLayout.MAIN_INITIALIZE_ACTION)
                scripts.runAction(profile, QuestFileLayout.QUESTS_PACKAGE, selectorAction)

                // 登録数が足りなければ会話の 2_repair と同じく修復し、受け直してもらう
                val shouldRepair = scripts.testCondition(
                    profile,
                    QuestFileLayout.DAILY_QUEST_PACKAGE,
                    QuestFileLayout.QUEST_REPAIR_CONDITION
                )
                if (shouldRepair) {
                    scripts.runAction(profile, QuestFileLayout.DAILY_QUEST_PACKAGE, QuestFileLayout.QUEST_REPAIR_ACTION)
                    ActionResponse(success = false, message = "Quest registration failed. Please order again")
                } else {
                    ActionResponse(success = true, message = "Quest selected with difficulty: ${request.difficulty}")
                }
            }
        }
    }

    /**
     * クエストファイルのアクション実行時の例外を 500 に変換する
     * HttpError はそのまま投げ直す
     */
    private inline fun runScript(label: String, block: () -> ActionResponse): ActionResponse = try {
        block()
    } catch (e: HttpError) {
        throw e
    } catch (e: Exception) {
        throw HttpError(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to $label: ${e.message}")
    }

    /**
     * プレイヤーがオンラインであることを確認する
     * BetonQuest のアクション実行には OnlineProfile が必要なため、
     * オフラインプレイヤーの場合は HTTP エラーを返す
     *
     * @param caller 認証済みプレイヤー
     * @return オンラインの Player インスタンス
     * @throws HttpError プレイヤーがオフラインの場合
     */
    private fun requireOnlinePlayer(caller: Principal.User): Player {
        return Bukkit.getPlayer(caller.uuid)
            ?: throw HttpError(HttpStatus.BAD_REQUEST, "Player must be online to perform this action", "player_offline")
    }

    /**
     * オンラインなら OnlineProfile、オフラインなら Profile を返す
     * ProfileProvider.getProfile(Player) だけが OnlineProfile を返すため、オンライン時は Player 経由で取得する
     */
    private fun profileOf(player: OfflinePlayer, onlinePlayer: Player?): Profile =
        if (onlinePlayer != null) api.profiles().getProfile(onlinePlayer) else api.profiles().getProfile(player)

    /**
     * オフラインプレイヤーのクエストデータが古いかどうかを判定する
     *
     * プレイヤーの最終ログイン時刻が直近のデイリーリセット時刻より前の場合、
     * DBに保存されたクエストデータはリセット前のものであるためstaleと判定する
     *
     * @param player 対象のオフラインプレイヤー
     * @return データが古い場合true
     */
    private fun isQuestDataStale(player: OfflinePlayer): Boolean {
        val lastLogin = player.lastLogin
        // 一度もログインしていない場合はデータ無し
        if (lastLogin == 0L) return true

        val serverZone = ZoneId.systemDefault()
        val lastLoginInstant = Instant.ofEpochMilli(lastLogin)
        val lastResetInstant = calculateLastResetTime(config.dailyResetTime, serverZone)

        // 最終ログインがリセット時刻より前ならデータは古い
        return lastLoginInstant.isBefore(lastResetInstant)
    }

    /**
     * 直近のデイリーリセット時刻を算出する
     *
     * 例: リセット時刻が04:00の場合
     * - 現在06:00 → 今日の04:00
     * - 現在02:00 → 昨日の04:00
     *
     * @param resetTime リセット時刻
     * @param zone サーバーのタイムゾーン
     * @return 直近のリセット時刻のInstant
     */
    private fun calculateLastResetTime(resetTime: LocalTime, zone: ZoneId): Instant {
        val now = Instant.now()
        val today = LocalDate.now(zone)
        val todayReset = today.atTime(resetTime).atZone(zone).toInstant()

        // 現在時刻がリセット時刻を過ぎていれば今日のリセット、そうでなければ昨日のリセット
        return if (!now.isBefore(todayReset)) {
            todayReset
        } else {
            today.minusDays(1).atTime(resetTime).atZone(zone).toInstant()
        }
    }

    /**
     * オブジェクティブデータのマップを構築する
     *
     * オンラインプレイヤーの objective は開始時に DB のスナップショット（rawObjectives）から
     * Objective インスタンスへ移されるため、Objective のデータからライブの値を読む。
     * オフラインプレイヤーは DB のスナップショットを使う
     *
     * @param profile 対象プロファイル（オンライン時は OnlineProfile）
     * @param online プレイヤーがオンラインか
     * @return オブジェクティブの完全な ID -> シリアライズデータのマップ
     */
    private fun buildObjectivesMap(profile: Profile, online: Boolean): Map<String, String> {
        if (online) {
            return try {
                api.objectives().manager().getForProfile(profile).associate { objective ->
                    objective.objectiveID.full to objective.service.data[profile].orEmpty()
                }
            } catch (_: Exception) {
                // ライブデータ取得失敗時はrawObjectivesにフォールバック
                rawObjectives(profile)
            }
        }
        return rawObjectives(profile)
    }

    /**
     * オンラインプレイヤーの各クエストの QuestObjective から、いまの達成数を読む
     * 読めなかった objective は含めない（DisplayStorage の値にフォールバックする）
     *
     * @param profile 対象の OnlineProfile
     * @return QuestObjective の完全な ID -> いまの達成数
     */
    private fun liveQuestAmounts(profile: Profile): Map<String, Int> {
        val suffix = "${QuestFileLayout.SEPARATOR}${QuestFileLayout.QUEST_OBJECTIVE}"
        return try {
            api.objectives().manager().getForProfile(profile)
                .filter { it.objectiveID.full.endsWith(suffix) }
                .mapNotNull { objective ->
                    val amount = try {
                        objective.service.properties
                            .getProperty(QuestFileLayout.ABSOLUTE_AMOUNT_PROPERTY, profile)
                            ?.toIntOrNull()
                    } catch (_: Exception) {
                        null
                    }
                    amount?.let { objective.objectiveID.full to it }
                }
                .toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    /**
     * PlayerDataのrawObjectivesを取得する
     * オフライン時またはライブデータ取得失敗時のフォールバック
     */
    private fun rawObjectives(profile: Profile): Map<String, String> {
        return try {
            BetonQuest.getInstance().playerDataStorage.getOffline(profile).rawObjectives.toMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
