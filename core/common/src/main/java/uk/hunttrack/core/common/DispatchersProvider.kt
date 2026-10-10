package uk.hunttrack.core.common

object DispatchersProvider {
    val io = kotlinx.coroutines.Dispatchers.IO
    val main = kotlinx.coroutines.Dispatchers.Main
    val default = kotlinx.coroutines.Dispatchers.Default
}
