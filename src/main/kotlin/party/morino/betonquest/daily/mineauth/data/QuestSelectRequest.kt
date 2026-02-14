package party.morino.betonquest.daily.mineauth.data

import kotlinx.serialization.Serializable

/**
 * クエスト選択リクエストのデータクラス
 * POST /daily-quests/me/select のリクエストボディ
 */
@Serializable
data class QuestSelectRequest(
    // クエスト難易度: "easy", "normal", "hard"
    val difficulty: String
)
