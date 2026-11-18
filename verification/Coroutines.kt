// Déclarations de signatures uniquement (kotlinx.coroutines / AndroidX non téléchargeables ici) — jamais exécutées.
package kotlinx.coroutines
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
interface Job { val isActive: Boolean; fun cancel() }
interface CoroutineScope { val coroutineContext: CoroutineContext }
object Dispatchers { val IO: CoroutineContext = EmptyCoroutineContext; val Main: CoroutineContext = EmptyCoroutineContext }
fun CoroutineScope.launch(context: CoroutineContext = EmptyCoroutineContext, block: suspend CoroutineScope.() -> Unit): Job = TODO()
suspend fun delay(timeMillis: Long) {}
val CoroutineScope.isActive: Boolean get() = true
suspend fun <T> withContext(context: CoroutineContext, block: suspend CoroutineScope.() -> T): T = TODO()
