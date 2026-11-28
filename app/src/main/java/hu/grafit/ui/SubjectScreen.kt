package hu.grafit.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hu.grafit.data.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun SubjectScreen(vm: GrafitViewModel, subjectName: String, onSimulator: () -> Unit) {
    val subject = remember(vm.data, subjectName) { vm.data?.gradeSummaries()?.firstOrNull { it.name == subjectName } }
    val grades = subject?.grades.orEmpty()
    val hidden = vm.appearance.hideGrades
    val hu = Locale.forLanguageTag("hu-HU")
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Text(subjectName, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Text("Tantárgyi átlag: " + if (hidden) "••" else average(grades)?.let { "%.2f".format(hu, roundedGradeAverage(it)) } ?: "—",
                Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            key(vm.activeAccount, vm.demo, subjectName) { SubjectGradeGraph(grades, hidden) }
        }
        item {
            FilledTonalButton(onClick = onSimulator, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Icon(Icons.Outlined.Insights, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("Mi lenne, ha?")
            }
        }
        item { Text("Jegyeid", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium) }
        if (grades.isEmpty()) item { Text("Ebből a tantárgyból még nincs jegy.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(grades.sortedByDescending { it.date }) { GradeRow(it, vm.appearance) }
    }
}

@Composable
private fun SubjectGradeGraph(grades: List<Grade>, hidden: Boolean) {
    var revealed by remember(hidden) { mutableStateOf(!hidden) }
    val points = remember(grades) {
        grades.filter { it.number != null && it.number in 1..5 && it.valueType == "Osztalyzat" }.sortedWith(compareBy<Grade> { it.date }.thenBy { it.id })
    }
    GrafitCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Jegyek alakulása", style = MaterialTheme.typography.titleMedium)
            when {
                !revealed -> OutlinedButton(onClick = { revealed = true }, modifier = Modifier.fillMaxWidth()) { Text("Grafikon felfedése") }
                points.isEmpty() -> Text("A grafikon az első számszerű jegy után jelenik meg.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> {
                    val line = MaterialTheme.colorScheme.primary
                    val grid = MaterialTheme.colorScheme.outlineVariant
                    val surface = MaterialTheme.colorScheme.surfaceContainerLow
                    val accents = points.map { gradeAccent(it.number) ?: line }
                    Row(Modifier.fillMaxWidth().height(180.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.fillMaxHeight().width(16.dp), verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            (5 downTo 1).forEach { Text("$it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Canvas(Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp)
                            .semantics { contentDescription = "Jegyek időrendben. ${points.size} jegy; első: ${points.first().number}, legutóbbi: ${points.last().number}." }) {
                            val inset = 7.dp.toPx()
                            (0..4).forEach { step ->
                                val y = size.height * step / 4f
                                drawLine(grid.copy(alpha = .6f), Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                            }
                            fun position(index: Int): Offset {
                                val fraction = if (points.size == 1) .5f else index.toFloat() / (points.size - 1)
                                return Offset(inset + (size.width - 2 * inset) * fraction, size.height * (5 - points[index].number!!) / 4f)
                            }
                            if (points.size > 1) {
                                val area = Path().apply {
                                    moveTo(position(0).x, size.height)
                                    points.indices.forEach { index -> val p = position(index); lineTo(p.x, p.y) }
                                    lineTo(position(points.lastIndex).x, size.height)
                                    close()
                                }
                                drawPath(area, Brush.verticalGradient(listOf(line.copy(alpha = .18f), line.copy(alpha = .01f))))
                                (1..points.lastIndex).forEach { index -> drawLine(line, position(index - 1), position(index), 2.dp.toPx()) }
                            }
                            points.indices.forEach { index ->
                                drawCircle(surface, 6.dp.toPx(), position(index))
                                drawCircle(accents[index], 4.dp.toPx(), position(index))
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(graphDate(points.first().date), style = MaterialTheme.typography.labelSmall)
                        if (points.size > 1) Text(graphDate(points.last().date), style = MaterialTheme.typography.labelSmall)
                    }
                    Text("${points.size} számszerű jegy · időrendben", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun graphDate(date: String): String = runCatching {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MMM d.", Locale.forLanguageTag("hu-HU")))
}.getOrDefault(date)
