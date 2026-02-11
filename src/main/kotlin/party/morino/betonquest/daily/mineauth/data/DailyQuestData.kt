package party.morino.betonquest.daily.mineauth.data

import kotlinx.serialization.Serializable

/**
 * デイリークエストレスポンス
 * GET /daily-quests/me で返却される
 */
@Serializable
data class DailyQuestResponse(
    // デイリークエスト情報一覧
    val dailyQuests: List<DailyQuestInfo>
)

/**
 * デイリークエスト情報のデータクラス
 * InternalManager、State、DisplayStorageの各variable objectiveから抽出される
 */
@Serializable
data class DailyQuestInfo(
    // クエストID（例: "crafting_arrow"）
    val questId: String,
    // 完全なパッケージ名（例: "NotEnoughQuests-DailyQuest-Quests-Easy-crafting_arrow"）
    val questPackage: String,
    // 難易度: "easy", "normal", "hard", "event"
    val difficulty: String,
    // クエストタイトル（DisplayStorageから取得）
    val title: String,
    // クエストの説明（DisplayStorageから取得）
    val description: String,
    // タスク完了フラグ（目標達成済みかどうか）
    val taskCompleted: Boolean,
    // クエスト完了フラグ（報酬受取済みかどうか）
    val questCompleted: Boolean,
    // 進捗率（0-100）
    val progressPercentage: Int
)
