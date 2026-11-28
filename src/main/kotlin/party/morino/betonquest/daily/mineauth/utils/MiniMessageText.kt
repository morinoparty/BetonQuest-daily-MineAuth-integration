package party.morino.betonquest.daily.mineauth.utils

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer

/**
 * DisplayStorage に書き込まれた MiniMessage 形式の文字列を、Web で扱いやすいプレーンテキストに変換する
 */
object MiniMessageText {

    private val miniMessage = MiniMessage.miniMessage()
    private val plainText = PlainTextComponentSerializer.plainText()

    /**
     * MiniMessage のタグを取り除いたテキストを返す
     * 解釈できない文字列はそのまま返す（表示が崩れても取得自体は失敗させない）
     *
     * @param text MiniMessage 形式の文字列
     * @return タグを除いたテキスト
     */
    fun toPlain(text: String): String = try {
        plainText.serialize(miniMessage.deserialize(text))
    } catch (_: Exception) {
        text
    }

    /**
     * クエスト説明文の最初の段落（目標の文）だけを取り出す
     * 説明文は「目標」「報酬:」「進捗:」の段落を空行で区切った形式になっている
     *
     * @param plainDescription タグを除いた説明文
     * @return 最初の段落。空なら空文字
     */
    fun firstParagraph(plainDescription: String): String =
        plainDescription
            .split(Regex("\\n\\s*\\n"))
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() }
            .orEmpty()
}
