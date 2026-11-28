package party.morino.betonquest.daily.mineauth.quest

/**
 * クエストのパッケージパスを分解した結果
 *
 * パッケージパスの形式:
 * - Easy / Normal: "NotEnoughQuests-DailyQuest-Quests-<Difficulty>-<questId>"
 * - Hard: "NotEnoughQuests-DailyQuest-Quests-Hard-<rarity>-<questId>"（rarity: common / rare / very_rare）
 *
 * @property difficulty 難易度
 * @property rarity Hard のレア度。Hard 以外は null
 * @property questId クエスト ID（例: "foraging_woods"）
 */
data class QuestPackagePath(
    val difficulty: QuestDifficulty,
    val rarity: String?,
    val questId: String
) {
    companion object {
        // クエストを置くパッケージのプレフィックス
        private const val QUESTS_PREFIX = "${QuestFileLayout.QUESTS_PACKAGE}-"

        // Hard のレア度別サブパッケージ名
        private val HARD_RARITIES = listOf("common", "rare", "very_rare")

        /**
         * パッケージパスを難易度・レア度・クエスト ID に分解する
         * 想定外の形式の場合は難易度 UNKNOWN、クエスト ID にパスの末尾を入れて返す
         *
         * @param questPackage クエストのパッケージパス
         * @return 分解結果
         */
        fun parse(questPackage: String): QuestPackagePath {
            // 想定外の形式でも ID が空にならないよう、最後の "-" 以降を既定値にする
            val fallback = QuestPackagePath(QuestDifficulty.UNKNOWN, null, questPackage.substringAfterLast('-'))
            if (!questPackage.startsWith(QUESTS_PREFIX)) return fallback

            // "<Difficulty>-<残り>" に分ける
            val rest = questPackage.removePrefix(QUESTS_PREFIX)
            val segment = rest.substringBefore('-')
            val difficulty = QuestDifficulty.entries.firstOrNull {
                it != QuestDifficulty.UNKNOWN && it.packageSegment == segment
            } ?: return fallback
            val afterDifficulty = rest.substringAfter('-', missingDelimiterValue = "")
            if (afterDifficulty.isEmpty()) return fallback

            // Hard だけはレア度のサブパッケージを挟む。very_rare は "_" を含むため前方一致で判定する
            if (difficulty == QuestDifficulty.HARD) {
                val rarity = HARD_RARITIES
                    .sortedByDescending { it.length }
                    .firstOrNull { afterDifficulty.startsWith("$it-") }
                if (rarity != null) {
                    return QuestPackagePath(difficulty, rarity, afterDifficulty.removePrefix("$rarity-"))
                }
            }
            return QuestPackagePath(difficulty, null, afterDifficulty)
        }
    }
}
