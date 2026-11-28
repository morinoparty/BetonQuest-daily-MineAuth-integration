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
}
