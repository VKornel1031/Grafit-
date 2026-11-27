package hu.grafit.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import hu.grafit.data.*
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class DetailDialogsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Test fun messageShowsFullTextAuthorAndCanBeDismissed() {
        val visible = mutableStateOf(true)
        val task = SchoolTask("Iskolai hír", "Faliújság", "<p>Az üzenet teljes tartalma.</p>", "2026-11-27", teacher = "Titkárság")
        compose.setContent { MaterialTheme { if (visible.value) MessageDetailDialog(task) { visible.value = false } } }
        compose.onNodeWithText("Az üzenet teljes tartalma.").assertIsDisplayed()
        compose.onNodeWithText("Készítette: Titkárság").assertIsDisplayed()
        compose.onNodeWithText("Bezárás").performClick()
        compose.runOnIdle { assertFalse(visible.value) }
    }
    @Test fun lessonDetailsDisplayItsLinkedAssignment() {
        val lesson = Lesson("lesson", "Matematika", LocalDateTime.parse("2026-11-27T08:00:00"),
            LocalDateTime.parse("2026-11-27T08:45:00"), "101", "Tanár", "Egyenletek", "Megtartott", homeworkId = "hw")
        val task = SchoolTask("Matematika", "Házi feladat", "Tankönyv 42. oldal.", "2026-12-01", id = "hw")
        compose.setContent { MaterialTheme { LessonDetailDialog(lesson, listOf(task)) { } } }
        compose.onNodeWithText("Tankönyv 42. oldal.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Határidő: 2026-12-01").performScrollTo().assertIsDisplayed()
    }
}
