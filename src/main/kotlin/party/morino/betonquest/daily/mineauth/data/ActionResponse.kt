package party.morino.betonquest.daily.mineauth.data

import kotlinx.serialization.Serializable

/**
 * BetonQuestイベント実行結果のレスポンス
 * POST系エンドポイントの共通レスポンス形式
 */
@Serializable
data class ActionResponse(
    // イベントの実行結果（true: 成功、false: 条件未達などで実行されなかった）
    val success: Boolean,
    // 結果の説明メッセージ
    val message: String
)
