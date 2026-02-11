package party.morino.betonquest.daily.mineauth.utils.coroutines

import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

/**
 * Dispatchersにminecraftプロパティを追加する拡張プロパティ
 * MCCoroutineを使わず、プラグイン内で完結するディスパッチャーを提供する
 */
val Dispatchers.minecraft: CoroutineContext
    get() = DispatcherContainer.sync
