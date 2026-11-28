package party.morino.betonquest.daily.mineauth.routes

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.betonquest.betonquest.BetonQuest
import org.betonquest.betonquest.api.BetonQuestApi
import org.betonquest.betonquest.api.profile.Profile
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.entity.Player
import party.morino.betonquest.daily.mineauth.data.DailyQuestResponse
import party.morino.betonquest.daily.mineauth.quest.QuestFileLayout
import party.morino.betonquest.daily.mineauth.utils.DailyQuestExtractor
import party.morino.betonquest.daily.mineauth.utils.coroutines.minecraft
import party.morino.mineauth.api.annotations.Authenticated
import party.morino.mineauth.api.annotations.Caller
import party.morino.mineauth.api.annotations.Get
import party.morino.mineauth.api.auth.Principal

/**
 * デイリークエスト情報を提供するハンドラー
 * /api/v1/plugins/dailyquest/ 配下にエンドポイントを提供する
 *
 * 閲覧専用。受注・リロールはゲーム内の受付だけで行う
 * （Web からも実行できると、ゲーム内と同時に実行してリロールを重ねて使えてしまうため）
 *
 * @param api BetonQuest の API
 */
class DailyQuestHandler(
    private val api: BetonQuestApi
) {

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
     * オンラインなら OnlineProfile、オフラインなら Profile を返す
     * ProfileProvider.getProfile(Player) だけが OnlineProfile を返すため、オンライン時は Player 経由で取得する
     */
    private fun profileOf(player: OfflinePlayer, onlinePlayer: Player?): Profile =
        if (onlinePlayer != null) api.profiles().getProfile(onlinePlayer) else api.profiles().getProfile(player)

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
