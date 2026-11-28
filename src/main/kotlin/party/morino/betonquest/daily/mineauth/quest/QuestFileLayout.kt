package party.morino.betonquest.daily.mineauth.quest

/**
 * NotEnoughQuest-QuestFiles (v2.1.x, BetonQuest 3.3.0) のパッケージ・オブジェクティブ・アクション名
 *
 * クエストファイル側で名前が変わったときは、このファイルだけを直せば追従できるようにまとめている
 * https://github.com/morinoparty/NotEnoughQuest-QuestFiles
 */
object QuestFileLayout {

    // BetonQuest 3.2.0 以降の ID 区切り文字（"<パッケージ>><名前>"）
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

    /**
     * クエストパッケージ内の objective の完全な ID を組み立てる
     *
     * @param questPackage クエストのパッケージパス（例: "NotEnoughQuests-DailyQuest-Quests-Easy-foraging_woods"）
     * @param objective objective 名（例: "QuestStatus"）
     * @return "<パッケージ>><objective>" 形式の ID
     */
    fun objectiveId(questPackage: String, objective: String): String = "$questPackage$SEPARATOR$objective"
}
