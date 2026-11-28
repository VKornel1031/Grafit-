package hu.grafit.ui

import android.text.Html
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import hu.grafit.data.*
import java.time.format.DateTimeFormatter

@Composable
private fun DetailText(text: String, empty: String = "Nincs megadott szöveg.") {
    val plain = remember(text) { Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT).toString().trim() }
    SelectionContainer { Text(plain.ifBlank { empty }, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun TaskDetails(task: SchoolTask) {
    Text(task.kind, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    if (task.date.isNotBlank()) Text("${if (task.kind == "Házi feladat") "Határidő" else "Dátum"}: ${task.date}", style = MaterialTheme.typography.bodySmall)
    if (task.assignedDate.isNotBlank()) Text("Feladva: ${task.assignedDate}", style = MaterialTheme.typography.bodySmall)
    if (task.teacher.isNotBlank()) Text("${if (task.kind in setOf("Faliújság", "Feljegyzés")) "Készítette" else "Tanár"}: ${task.teacher}", style = MaterialTheme.typography.bodySmall)
    DetailText(task.text)
}

@Composable
internal fun MessageDetailDialog(task: SchoolTask, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(task.subject.ifBlank { task.kind }) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TaskDetails(task)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Bezárás") } })
}

@Composable
internal fun LessonDetailDialog(lesson: Lesson, tasks: List<SchoolTask>, onDismiss: () -> Unit) {
    val assignments = remember(lesson, tasks) { assignmentsForLesson(lesson, tasks) }
    val time = DateTimeFormatter.ofPattern("HH:mm")
    AlertDialog(onDismissRequest = onDismiss, title = { Text(lesson.subject) }, text = {
        Column(Modifier.fillMaxWidth().heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${lesson.start.toLocalDate()} · ${lesson.start.format(time)}–${lesson.end.format(time)}", style = MaterialTheme.typography.labelLarge)
            if (lesson.room.isNotBlank()) {
                val context = androidx.compose.ui.platform.LocalContext.current
                TextButton(onClick = {
                    context.startActivity(android.content.Intent(context, hu.grafit.MainActivity::class.java)
                        .putExtra("tool", "rooms").putExtra("room", lesson.room)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP))
                }) { Text("Terem: ${lesson.room} · keresés az alaprajzon") }
            }
            if (lesson.teacher.isNotBlank()) Text("Tanár: ${lesson.teacher}")
            if (lesson.state.isNotBlank()) Text(if (lesson.state == "Elmaradt") "Elmaradt tanóra" else lesson.state,
                color = if (lesson.state == "Elmaradt") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tananyag", style = MaterialTheme.typography.titleSmall)
            DetailText(lesson.topic, "Nincs megadott tananyag.")
            HorizontalDivider()
            Text("Az órához rendelt házi feladat", style = MaterialTheme.typography.titleSmall)
            when {
                assignments.linkedHomework.isNotEmpty() -> assignments.linkedHomework.forEach { TaskDetails(it) }
                assignments.missingLinkedHomework -> Text("A KRÉTA feladatot jelöl ehhez az órához, de annak részletei még nem érhetők el a mentett adatokban. Próbálj kézi frissítést.")
                else -> Text("Nincs letöltött, közvetlenül ehhez az órához rendelt házi feladat.")
            }
            if (assignments.dueThatDay.isNotEmpty()) {
                HorizontalDivider()
                Text("Ebből a tantárgyból erre a napra esedékes", style = MaterialTheme.typography.titleSmall)
                assignments.dueThatDay.forEach {
                    TaskDetails(it)
                    HorizontalDivider()
                }
            }
            Text("A részletek az utoljára letöltött adatokon alapulnak.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Bezárás") } })
}
