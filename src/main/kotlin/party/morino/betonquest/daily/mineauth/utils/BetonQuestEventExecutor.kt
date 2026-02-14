package party.morino.betonquest.daily.mineauth.utils

import org.betonquest.betonquest.BetonQuest
import org.betonquest.betonquest.api.quest.QuestException
import org.betonquest.betonquest.api.profile.Profile
import org.betonquest.betonquest.id.EventID

/**
 * BetonQuestのイベントを実行するユーティリティ
 * QuestTypeAPIを使用してプロファイルに対してイベントを発火する
 *
 * 重要: このクラスのメソッドはBukkitメインスレッドで呼び出す必要がある
 */
object BetonQuestEventExecutor {

    /**
     * 指定されたパッケージ内のイベントをプロファイルに対して実行する
     *
     * @param profile 対象プロファイル（OnlineProfileを推奨）
     * @param packageName BetonQuestパッケージ名（例: "NotEnoughQuests-DailyQuest"）
     * @param eventName イベント名（例: "Reroll#start"）
     * @return イベント実行結果（true: 成功、false: 条件未達で実行されなかった）
     * @throws QuestException パッケージやイベントが見つからない場合
     */
    fun fireEvent(
        profile: Profile,
        packageName: String,
        eventName: String
    ): Boolean {
        val betonQuest = BetonQuest.getInstance()

        // パッケージを取得
        val questPackage = betonQuest.packages[packageName]
            ?: throw QuestException("Quest package not found: $packageName")

        // EventIDを構築してイベントを実行
        val eventId = EventID(questPackage, eventName)
        return betonQuest.questTypeAPI.event(profile, eventId)
    }
}
