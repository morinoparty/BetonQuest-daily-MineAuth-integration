package party.morino.betonquest.daily.mineauth.utils

import party.morino.betonquest.daily.mineauth.data.DailyQuestInfo
import party.morino.betonquest.daily.mineauth.quest.QuestFileLayout
import party.morino.betonquest.daily.mineauth.quest.QuestPackagePath
import kotlin.math.roundToInt

/**
 * プレイヤーの objective データからデイリークエスト情報を抽出するクラス
 *
 * InternalManager の variable objective に登録されたクエストパッケージを読み取り、
 * 各クエストの QuestStatus（stage objective）と DisplayStorage（variable objective）を解析して
 * DailyQuestInfo のリストを生成する
 */
object DailyQuestExtractor {

    // ステージの並び。インデックスの大小で「以上」を判定する
    private val STAGE_ORDER = listOf(
        QuestFileLayout.STAGE_QUEST_ACTIVE,
        QuestFileLayout.STAGE_OBJECTIVE_COMPLETED,
        QuestFileLayout.STAGE_QUEST_COMPLETED
    )

    /**
     * objective データからデイリークエスト情報を抽出する
     *
     * @param objectives objective の完全な ID（"<パッケージ>><名前>"）-> シリアライズ済みデータ
     * @param liveAmounts QuestObjective の完全な ID -> いまの達成数（オンライン時のみ。オフラインは空）
     * @return デイリークエスト情報のリスト（クエスト未登録の場合は空リスト）
     */
    fun extract(
        objectives: Map<String, String>,
        liveAmounts: Map<String, Int> = emptyMap()
    ): List<DailyQuestInfo> {
        // InternalManager が無ければ、まだ一度も受注していない
        val managerData = objectives[QuestFileLayout.INTERNAL_MANAGER_OBJECTIVE]
            ?.let { VariableDataParser.parse(it) }
            ?: return emptyList()

        // quest_package_0, _1, _2 から登録済みのクエストパッケージを取得する（登録解除後は空文字が残る）
        return (0 until QuestFileLayout.MAX_DAILY_QUESTS).mapNotNull { slot ->
            managerData["${QuestFileLayout.QUEST_PACKAGE_KEY_PREFIX}$slot"]
                ?.takeIf { it.isNotBlank() }
                ?.let { buildDailyQuestInfo(slot, it, objectives, liveAmounts) }
        }
    }

    /**
     * 個別のクエストパッケージから DailyQuestInfo を組み立てる
     */
    private fun buildDailyQuestInfo(
        slot: Int,
        questPackage: String,
        objectives: Map<String, String>,
        liveAmounts: Map<String, Int>
    ): DailyQuestInfo {
        // QuestStatus は stage objective で、データはステージ名そのもの
        val stage = objectives[QuestFileLayout.objectiveId(questPackage, QuestFileLayout.QUEST_STATUS_OBJECTIVE)]
        val stageIndex = STAGE_ORDER.indexOf(stage)

        // DisplayStorage はタイトル・説明・進捗を持つ variable objective
        val displayData = objectives[QuestFileLayout.objectiveId(questPackage, QuestFileLayout.DISPLAY_STORAGE_OBJECTIVE)]
            ?.let { VariableDataParser.parse(it) }
            ?: emptyMap()

        val totalAmount = displayData["quest_total_amount"]?.toDoubleOrNull()?.roundToInt()
        val taskCompleted = stageIndex >= STAGE_ORDER.indexOf(QuestFileLayout.STAGE_OBJECTIVE_COMPLETED)

        // DisplayStorage はメニューを開いたときなどにしか書き換わらないため、
        // オンライン中は QuestObjective のいまの達成数から進捗を求める
        val liveAmount = liveAmounts[QuestFileLayout.objectiveId(questPackage, QuestFileLayout.QUEST_OBJECTIVE)]
        val progress = when {
            // 目標を達成すると QuestObjective は消えるため、ステージを優先する
            taskCompleted -> 1.0
            liveAmount != null && totalAmount != null && totalAmount > 0 -> liveAmount.toDouble() / totalAmount
            // progress_percentage は 0.000〜1.000 で保存されている
            else -> displayData["progress_percentage"]?.toDoubleOrNull() ?: 0.0
        }.coerceIn(0.0, 1.0)

        val rawTitle = displayData["quest_title"].orEmpty()
        val rawDescription = displayData["quest_description"].orEmpty()
        val description = MiniMessageText.toPlain(rawDescription)
        val path = QuestPackagePath.parse(questPackage)

        return DailyQuestInfo(
            slot = slot,
            questId = path.questId,
            questPackage = questPackage,
            difficulty = path.difficulty,
            rarity = path.rarity,
            title = MiniMessageText.toPlain(rawTitle).trim(),
            objective = MiniMessageText.firstParagraph(description),
            description = description.trim(),
            rawTitle = rawTitle,
            rawDescription = rawDescription,
            taskCompleted = taskCompleted,
            questCompleted = stageIndex >= STAGE_ORDER.indexOf(QuestFileLayout.STAGE_QUEST_COMPLETED),
            progressPercentage = (progress * 100).roundToInt(),
            totalAmount = totalAmount,
            // ライブの値があればそのまま使う。DisplayStorage の進捗率は小数第 3 位で切り捨てられているため、
            // 必要数を掛けて丸めれば元の達成数に戻る
            currentAmount = totalAmount?.let { total -> liveAmount?.coerceIn(0, total) ?: (progress * total).roundToInt() }
        )
    }
}
