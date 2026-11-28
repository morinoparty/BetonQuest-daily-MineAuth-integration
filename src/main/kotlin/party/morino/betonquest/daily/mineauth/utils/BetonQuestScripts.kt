package party.morino.betonquest.daily.mineauth.utils

import org.betonquest.betonquest.api.BetonQuestApi
import org.betonquest.betonquest.api.QuestException
import org.betonquest.betonquest.api.identifier.ActionIdentifier
import org.betonquest.betonquest.api.identifier.ConditionIdentifier
import org.betonquest.betonquest.api.identifier.Identifier
import org.betonquest.betonquest.api.profile.Profile

/**
 * クエストファイルに定義されたアクション・コンディションを実行するユーティリティ
 *
 * 重要: このクラスのメソッドは Bukkit メインスレッドで呼び出す必要がある
 *
 * @param api BetonQuest の API
 */
class BetonQuestScripts(private val api: BetonQuestApi) {

    /**
     * 指定されたパッケージ内のアクションをプロファイルに対して実行する
     *
     * @param profile 対象プロファイル（OnlineProfile を推奨）
     * @param packageName BetonQuest パッケージパス（例: "NotEnoughQuests-DailyQuest"）
     * @param actionName アクション名（例: "Reroll$start"）
     * @return 実行結果（false: アクション側の条件を満たさず実行されなかった）
     * @throws QuestException パッケージやアクションが見つからない場合
     */
    fun runAction(profile: Profile, packageName: String, actionName: String): Boolean {
        val id = parse(ActionIdentifier::class.java, packageName, actionName)
        return api.actions().manager().run(profile, id)
    }

    /**
     * 指定されたパッケージ内のコンディションをプロファイルに対して判定する
     *
     * @param profile 対象プロファイル
     * @param packageName BetonQuest パッケージパス
     * @param conditionName コンディション名（例: "QuestRepair$shouldRepair"）
     * @return 判定結果
     * @throws QuestException パッケージやコンディションが見つからない場合
     */
    fun testCondition(profile: Profile, packageName: String, conditionName: String): Boolean {
        val id = parse(ConditionIdentifier::class.java, packageName, conditionName)
        return api.conditions().manager().test(profile, id)
    }

    /**
     * パッケージと名前から BetonQuest の ID を組み立てる
     */
    private fun <I : Identifier> parse(type: Class<I>, packageName: String, name: String): I {
        val questPackage = api.packages().getPackages()[packageName]
            ?: throw QuestException("Quest package not found: $packageName")
        return api.identifiers().getFactory(type).parseIdentifier(questPackage, name)
    }
}
