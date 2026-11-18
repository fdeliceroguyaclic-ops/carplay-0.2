package kotlinx.coroutines.flow
import kotlinx.coroutines.CoroutineScope
interface Flow<out T>
interface StateFlow<out T> : Flow<T> { val value: T }
interface MutableStateFlow<T> : StateFlow<T> { override var value: T }
fun <T> MutableStateFlow(value: T): MutableStateFlow<T> = TODO()
fun <T1, T2, T3, R> combine(a: Flow<T1>, b: Flow<T2>, c: Flow<T3>, transform: suspend (T1, T2, T3) -> R): Flow<R> = TODO()
fun <T> Flow<T>.stateIn(scope: CoroutineScope, started: SharingStarted, initialValue: T): StateFlow<T> = TODO()
interface SharingStarted { companion object { val Eagerly: SharingStarted get() = TODO() } }
