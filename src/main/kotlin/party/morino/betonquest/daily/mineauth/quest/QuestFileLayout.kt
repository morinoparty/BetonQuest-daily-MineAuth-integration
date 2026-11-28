package party.morino.betonquest.daily.mineauth.quest

/**
 * NotEnoughQuest-QuestFiles (v2.1.x, BetonQuest 3.2.0) のパッケージ・オブジェクティブ・アクション名
 *
 * クエストファイル側で名前が変わったときは、このファイルだけを直せば追従できるようにまとめている
 * https://github.com/morinoparty/NotEnoughQuest-QuestFiles
 */
object QuestFileLayout {

    // BetonQuest 3.2.0 の ID 区切り文字（"<パッケージ>><名前>"）
    const val SEPARATOR = ">"

    // デイリークエスト全体を管理するパッケージ（Main / Reroll / QuestRepair などが置かれる）
    const val DAILY_QUEST_PACKAGE = "NotEnoughQuests-DailyQuest"

    // InternalManager / Status を持つパッケージ
    const val MANAGER_PACKAGE = "NotEnoughQuests-DailyQuest-Manager"

    // 難易度ごとのクエスト抽選アクションを持つパッケージ
    const val QUESTS_PACKAGE = "NotEnoughQuests-DailyQuest-Quests"

    // 登録済みクエストのパッケージパスを quest_package_<n> に保持する variable objective
    const val INTERNAL_MANAGER_OBJECTIVE = "$MANAGER_PACKAGE${SEPARATOR}InternalManager"

    // InternalManager 内のキーのプレフィックス（quest_package_0, quest_package_1, ...）
    const val QUEST_PACKAGE_KEY_PREFIX = "quest_package_"

    // 1 日に受けられるクエスト数（Config$maxDailyQuests。クエストファイル側で変更不可）
    const val MAX_DAILY_QUESTS = 3

    // 各クエストパッケージ内の objective 名
    const val QUEST_STATUS_OBJECTIVE = "QuestStatus"
    const val DISPLAY_STORAGE_OBJECTIVE = "DisplayStorage"
    const val QUEST_OBJECTIVE = "QuestObjective"

    // QuestObjective（counting objective）のいまの達成数を表すプロパティ
    const val ABSOLUTE_AMOUNT_PROPERTY = "absoluteamount"

    // QuestStatus (stage objective) のステージ名
    const val STAGE_QUEST_ACTIVE = "QUEST_ACTIVE"
    const val STAGE_OBJECTIVE_COMPLETED = "OBJECTIVE_COMPLETED"
    const val STAGE_QUEST_COMPLETED = "QUEST_COMPLETED"

    // その日のデイリークエストを受注済みかどうかを表すタグ（04:00 のリセットで外れる）
    const val MAIN_INITIALIZED_TAG = "$DAILY_QUEST_PACKAGE${SEPARATOR}main_initialized"

    // リロールの残り回数を持つポイント
    const val REROLL_POINT = "$DAILY_QUEST_PACKAGE${SEPARATOR}reroll"

    // 受注の流れ。ゲーム内の会話（daily_quest の 1_answer_*）と同じ順で実行する
    const val MAIN_DESTROY_ACTION = "Main\$destroy"
    const val MAIN_INITIALIZE_ACTION = "Main\$initialize"

    // 受注後に登録数が足りないかの判定と、その修復（会話の 2_repair と同じ）
    const val QUEST_REPAIR_CONDITION = "QuestRepair\$shouldRepair"
    const val QUEST_REPAIR_ACTION = "QuestRepair\$start"

    // リロール（ポイントの消費と、目標達成済みのクエストが無いかの判定はアクション側の条件で行われる）
    const val REROLL_ACTION = "Reroll\$start"

    // 難易度 -> QuestSelector の抽選アクション名
    val SELECTOR_ACTIONS: Map<QuestDifficulty, String> = mapOf(
        QuestDifficulty.EASY to "QuestSelector\$selectRandomEasyDailyQuests",
        QuestDifficulty.NORMAL to "QuestSelector\$selectRandomNormalDailyQuests",
        QuestDifficulty.HARD to "QuestSelector\$selectRandomHardDailyQuests"
    )

    /**
     * クエストパッケージ内の objective の完全な ID を組み立てる
     *
     * @param questPackage クエストのパッケージパス（例: "NotEnoughQuests-DailyQuest-Quests-Easy-foraging_woods"）
     * @param objective objective 名（例: "QuestStatus"）
     * @return "<パッケージ>><objective>" 形式の ID
     */
    fun objectiveId(questPackage: String, objective: String): String = "$questPackage$SEPARATOR$objective"
}
