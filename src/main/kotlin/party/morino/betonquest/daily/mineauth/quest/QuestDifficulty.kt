package party.morino.betonquest.daily.mineauth.quest

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * デイリークエストの難易度
 * クエストのパッケージパスの "-Quests-<Difficulty>-" から判定する
 *
 * @property packageSegment パッケージパス上の難易度の名前
 */
@Serializable
enum class QuestDifficulty(val packageSegment: String) {
    @SerialName("easy")
    EASY("Easy"),

    @SerialName("normal")
    NORMAL("Normal"),

    @SerialName("hard")
    HARD("Hard"),

    // QuestFiles 側ではまだコメントアウトされているが、パッケージが追加されたときに読めるよう残す
    @SerialName("event")
    EVENT("Event"),

    // パッケージパスから判定できなかった場合
    @SerialName("unknown")
    UNKNOWN("");

    companion object {
        /**
         * リクエストの文字列（"easy" など）から受注できる難易度を求める
         *
         * @param value 小文字・大文字を問わない難易度名
         * @return 受注に使える難易度。該当しなければ null
         */
        fun fromRequest(value: String): QuestDifficulty? =
            QuestFileLayout.SELECTOR_ACTIONS.keys.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}
