package party.morino.betonquest.daily.mineauth.data

import kotlinx.serialization.Serializable
import party.morino.betonquest.daily.mineauth.quest.QuestDifficulty

/**
 * デイリークエスト情報のデータクラス
 * InternalManager、QuestStatus、DisplayStorage の各 objective から抽出される
 */
@Serializable
data class DailyQuestInfo(
    // 受注枠の番号（0 から MAX_DAILY_QUESTS - 1）
    val slot: Int,
    // クエストID（例: "crafting_arrow"）
    val questId: String,
    // 完全なパッケージパス（例: "NotEnoughQuests-DailyQuest-Quests-Easy-crafting_arrow"）
    val questPackage: String,
    // 難易度
    val difficulty: QuestDifficulty,
    // Hard のレア度（"common" / "rare" / "very_rare"）。Hard 以外は null
    val rarity: String?,
    // クエストタイトル（MiniMessage のタグを除いたもの）
    val title: String,
    // クエストの目標（説明文の最初の段落。タグを除いたもの）
    val objective: String,
    // クエストの説明（タグを除いた全文。報酬・進捗の段落を含む）
    val description: String,
    // DisplayStorage に書き込まれた MiniMessage 形式のタイトル
    val rawTitle: String,
    // DisplayStorage に書き込まれた MiniMessage 形式の説明文
    val rawDescription: String,
    // タスク完了フラグ（目標達成済みかどうか。QuestStatus が OBJECTIVE_COMPLETED 以上）
    val taskCompleted: Boolean,
    // クエスト完了フラグ（報酬受取済みかどうか。QuestStatus が QUEST_COMPLETED）
    val questCompleted: Boolean,
    // 進捗率（0-100）
    val progressPercentage: Int,
    // 達成に必要な数。取得できなければ null
    val totalAmount: Int?,
    // 現在の達成数（進捗率と必要数から求めた値）。取得できなければ null
    val currentAmount: Int?
)
