package party.morino.betonquest.daily.mineauth.routes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.betonquest.betonquest.BetonQuest
import org.betonquest.betonquest.api.BetonQuestApi
import org.betonquest.betonquest.api.profile.Profile
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
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

/**
 * デイリークエスト情報を提供するハンドラー
 * /api/v1/plugins/dailyquest/ 配下にエンドポイントを提供する
 *
 * 受注・リロールはプレイヤーが資源サーバーにいなくても実行できる。
 * BetonQuest 3.3.0 のアクションはオフラインの Profile でも動き、タグ・ポイント・objective の変更は DB に保存される
 *
 * @param api BetonQuest の API
 */
class DailyQuestHandler(
    private val api: BetonQuestApi
) {

    // クエストファイルのアクション・コンディションの実行
    private val scripts = BetonQuestScripts(api)

    /**
     * 認証済みプレイヤーのデイリークエストデータを取得する
     * GET /daily-quests/me
     *
     * 04:00 のリセット（ScheduleReset$start の tag delete）はオフラインのプレイヤーのタグも DB からまとめて消すため、
     * 受注タグがあればその日に受注したものとして扱う
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
            // タグとポイントは公開 API から読む（内部の PlayerDataStorage はバージョンごとにシグネチャが変わるため）
            val playerData = api.persistence().of(profile)
            val rerollCharges = playerData.points().get(QuestFileLayout.REROLL_POINT).orElse(0)

            // 受注タグが無ければ、その日のクエストは無い
            val ordered = playerData.tags().has(QuestFileLayout.MAIN_INITIALIZED_TAG)
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
            // サーバーにいなくても、オフラインの Profile でアクションを実行する
            val profile = profileOf(caller)
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
            val profile = profileOf(caller)
            val playerData = api.persistence().of(profile)

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
     * オンラインなら OnlineProfile、オフラインなら Profile を返す
     * ProfileProvider.getProfile(Player) だけが OnlineProfile を返すため、オンライン時は Player 経由で取得する
     */
    private fun profileOf(player: OfflinePlayer, onlinePlayer: Player?): Profile =
        if (onlinePlayer != null) api.profiles().getProfile(onlinePlayer) else api.profiles().getProfile(player)

    /**
     * 認証済みプレイヤーの Profile を返す（オンラインなら OnlineProfile）
     */
    private fun profileOf(caller: Principal.User): Profile =
        profileOf(caller.offlinePlayer, Bukkit.getPlayer(caller.uuid))



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
     *
     * objective の保存データは公開 API に無いため内部 API を使う。
     * BetonQuest の更新でシグネチャが変わっても API 全体を落とさないよう、LinkageError も空として扱う
     */
    private fun rawObjectives(profile: Profile): Map<String, String> {
        return try {
            BetonQuest.getInstance().playerDataStorage.get(profile).rawObjectives.toMap()
        } catch (_: Exception) {
            emptyMap()
        } catch (_: LinkageError) {
            emptyMap()
        }
    }
}
