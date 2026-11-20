package hu.grafit.ui

import hu.grafit.data.LocalProfile
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class OperationStateTest {
    @Test fun nameAndPhotoCanBothSaveWhileSyncIsSuspended() = runBlocking {
        val sync = OperationState()
        val edits = OperationState()
        val response = CompletableDeferred<Unit>()
        var persisted = LocalProfile("Régi név")
        sync.launch(this, "Hálózati hiba") { response.await() }
        try {
            yield()
            assertTrue(sync.busy)
            edits.launch(this, "Mentési hiba") { persisted = persisted.copy(displayName = "Új név") }
            yield()
            assertEquals("Új név", persisted.displayName)
            assertFalse(edits.busy)
            assertTrue(sync.busy)
            edits.launch(this, "Mentési hiba") { persisted = persisted.copy(photo = "local-photo") }
            yield()
            assertEquals(LocalProfile("Új név", "local-photo"), persisted)
            assertFalse(edits.busy)
            assertTrue(sync.busy)
        } finally {
            response.complete(Unit)
        }
        yield()
        assertFalse(sync.busy)
    }

    @Test fun overlappingLocalSavesAreSerialisedWithoutOverwritingEachOther() = runBlocking {
        val edits = OperationState()
        val save = CompletableDeferred<Unit>()
        var writes = 0
        edits.launch(this, "Mentési hiba") { save.await(); writes++ }
        try {
            yield()
            edits.launch(this, "Mentési hiba") { writes += 100 }
            assertTrue(edits.busy)
            assertEquals(0, writes)
        } finally {
            save.complete(Unit)
        }
        yield()
        assertEquals(1, writes)
        assertFalse(edits.busy)
    }

    @Test fun failedLocalSaveDoesNotChangeSyncStateAndCanBeRetried() = runBlocking {
        val sync = OperationState()
        val edits = OperationState()
        sync.error = "Korábbi szinkronhiba"
        edits.launch(this, "Mentési hiba") { throw IllegalStateException("IO") }
        yield()
        assertEquals("Mentési hiba", edits.error)
        assertFalse(edits.busy)
        assertEquals("Korábbi szinkronhiba", sync.error)
        edits.launch(this, "Mentési hiba") { }
        yield()
        assertNull(edits.error)
    }

    @Test fun cancellationReleasesTheSaveStateWithoutReportingAnError() = runBlocking {
        val edits = OperationState()
        val owner = Job()
        edits.launch(CoroutineScope(coroutineContext + owner), "Mentési hiba") { CompletableDeferred<Unit>().await() }
        yield()
        assertTrue(edits.busy)
        owner.cancelAndJoin()
        assertFalse(edits.busy)
        assertNull(edits.error)
    }
}
