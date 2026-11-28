package party.morino.betonquest.daily.mineauth.data

import kotlinx.serialization.Serializable

/**
 * デイリークエストレスポンス
 * GET /daily-quests/me で返却される
 */
@Serializable
data class DailyQuestResponse(
    // その日のデイリークエストを受注済みか（false の間は dailyQuests を表示しない）
    val ordered: Boolean,
    // プレイヤーが資源サーバーにオンラインか（受注・リロールはオンライン中のみ実行できる）
    val online: Boolean,
    // リロールの残り回数
    val rerollCharges: Int,
    // デイリークエスト情報一覧（未受注の場合は空）
    val dailyQuests: List<DailyQuestInfo>
)
