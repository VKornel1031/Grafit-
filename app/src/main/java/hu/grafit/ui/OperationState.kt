package hu.grafit.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import hu.grafit.data.ApiFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Serialises related operations without locking independent work, such as sync and local edits. */
internal class OperationState {
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)

    fun launch(scope: CoroutineScope, failureMessage: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = if (e is ApiFailure || e is IllegalArgumentException) e.message else failureMessage
            } finally {
                busy = false
            }
        }
    }
}
