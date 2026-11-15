package com.alisttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alisttracker.data.Movie
import java.util.Locale

/** One thing bought at the concession stand on a visit. */
data class ConcItem(val what: String, val price: Double, val pay: String)

val CONC_PAY_OPTIONS = listOf("Rewards", "Cash", "Credit Card", "Voucher", "Gift Card", "Other")

private const val REC = '\u001e'
private const val FLD = '\u001f'

fun encodeConc(items: List<ConcItem>): String =
    items.filter { it.price > 0 }.joinToString(REC.toString()) { "${it.what.replace(REC, ' ').replace(FLD, ' ')}$FLD${it.price}$FLD${it.pay.replace(REC, ' ').replace(FLD, ' ')}" }

/** The purchases of a visit. Visits saved before itemising have just a total, which shows as a single item. */
fun Movie.concItems(): List<ConcItem> {
    val parsed = concessionItems.split(REC).mapNotNull { rec ->
        val f = rec.split(FLD)
        val price = f.getOrNull(1)?.toDoubleOrNull() ?: return@mapNotNull null
        if (price <= 0) null else ConcItem(f[0], price, f.getOrElse(2) { "" })
    }
    if (parsed.isNotEmpty()) return parsed
    return if (concessions > 0) listOf(ConcItem("", concessions, concessionsPay)) else emptyList()
}

/** Pay label kept on the visit for the sheet: the one method used, or "Mixed". */
fun payLabel(items: List<ConcItem>): String =
    items.map { it.pay }.filter { it.isNotBlank() }.distinct().let { if (it.size > 1) "Mixed" else it.firstOrNull() ?: "" }

private fun priceText(v: Double) = String.format(Locale.US, "%.2f", v)

/** Editable row in the movie form. */
class ConcRow(what: String = "", price: String = "", pay: String = "") {
    var what by mutableStateOf(what)
    var price by mutableStateOf(price)
    var pay by mutableStateOf(pay)
    fun toItem() = ConcItem(what.trim(), price.replace("$", "").replace(",", "").trim().toDoubleOrNull()?.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0, pay)
}

fun concRowsFor(m: Movie?): List<ConcRow> =
    m?.concItems()?.map { ConcRow(it.what, priceText(it.price), it.pay) } ?: emptyList()

/** "Bought something?" Yes/No, then a row per purchase: what, price, how it was paid. */
@Composable
fun ConcessionsEditor(bought: Boolean, onBought: (Boolean) -> Unit, rows: MutableList<ConcRow>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Bought concessions?", color = Muted, fontSize = 14.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(true to "Yes", false to "No").forEach { (v, label) ->
                val on = bought == v
                Text(
                    label, color = if (on) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Orange else Surface2)
                        .clickable { onBought(v); if (v && rows.isEmpty()) rows.add(ConcRow()) }.padding(horizontal = 22.dp, vertical = 8.dp)
                )
            }
        }
        if (bought) {
            rows.forEachIndexed { i, r ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Surface2).padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(r.what, { r.what = it }, label = { Text("What did you buy?") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(r.price, { r.price = it }, label = { Text("Price") }, singleLine = true, modifier = Modifier.width(96.dp))
                    }
                    Choice("Paid with", r.pay, CONC_PAY_OPTIONS) { r.pay = it }
                    if (rows.size > 1) Text("Remove", color = Red, fontSize = 12.sp, modifier = Modifier.clickable { rows.removeAt(i) }.padding(vertical = 2.dp))
                }
            }
            TextButton(onClick = { rows.add(ConcRow(pay = rows.lastOrNull()?.pay ?: "")) }) { Text("+ Add another item") }
        }
    }
}

/** Stats: how often you bought, how much, and how it was paid — kept apart from tickets and membership. */
@Composable
fun ConcessionsSection(movies: List<Movie>) {
    val bought = remember(movies) { movies.filter { it.concItems().isNotEmpty() } }
    if (bought.isEmpty()) return
    val years = remember(bought) { bought.mapNotNull { it.date.take(4).toIntOrNull() }.distinct().sortedDescending() }
    var year by rememberSaveable { mutableIntStateOf(0) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    val sel = if (year == 0 || year in years) year else 0
    val inYear = remember(bought, sel) { if (sel == 0) bought else bought.filter { it.date.startsWith("$sel") } }
    val items = remember(inYear) { inYear.flatMap { v -> v.concItems().map { v to it } } }
    val total = items.sumOf { it.second.price }
    val visitsAll = remember(movies, sel) { movies.count { sel == 0 || it.date.startsWith("$sel") } }
    val byPay = remember(items) {
        items.groupBy { it.second.pay.ifBlank { "Not set" } }.map { (k, v) -> Triple(k, v.size, v.sumOf { it.second.price }) }.sortedByDescending { it.third }
    }
    val topItems = remember(items) {
        items.filter { it.second.what.isNotBlank() }.groupBy { it.second.what.trim().lowercase() }
            .map { (_, v) -> Triple(v.first().second.what.trim(), v.size, v.sumOf { it.second.price }) }.sortedByDescending { it.second }.take(5)
    }
    val list = remember(inYear) { inYear.sortedWith(compareByDescending<Movie> { it.date }.thenByDescending { it.showtime }) }
    val shown = if (showAll) list else list.take(6)

    Section("Concessions", Orange) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            (listOf(0) + years).forEach { y ->
                val on = y == sel
                Text(
                    if (y == 0) "All time" else "$y", color = if (on) Color.Black else Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) Orange else Surface2)
                        .clickable { year = y; showAll = false }.padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Times bought", "${inYear.size}", Orange, Modifier.weight(1f))
            Tile("Total spent", money(total), Gold, Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile("Avg per purchase", money(total / inYear.size), Teal, Modifier.weight(1f))
            Tile("Items bought", "${items.size}", Blue, Modifier.weight(1f))
        }
        Text("Bought something on ${inYear.size} of $visitsAll visits", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(10.dp))
        Text("Paid with", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        val max = byPay.maxOfOrNull { it.third }?.coerceAtLeast(0.01) ?: 1.0
        byPay.forEach { (k, n, sum) ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row {
                    Text("$k · $n×", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(money(sum), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(Surface2)) {
                    Box(Modifier.fillMaxWidth((sum / max).toFloat().coerceIn(0f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(Orange))
                }
            }
        }
        if (topItems.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("Most bought", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            topItems.forEach { (name, n, sum) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text("$name · $n×", color = Color.White, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    Text(money(sum), color = Muted, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("Purchases", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        shown.forEach { m ->
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Row {
                    Text(prettyDate(m.date) + " · " + m.title, color = Gold, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    Text(money(m.concItems().sumOf { it.price }), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                m.concItems().forEach { it ->
                    Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                        Text(it.what.ifBlank { "Concessions" }, color = Color(0xFFD7D8E6), fontSize = 12.sp, modifier = Modifier.weight(1f), maxLines = 1)
                        Text(money(it.price) + if (it.pay.isNotBlank()) " · ${it.pay}" else "", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        }
        if (list.size > 6) Text(if (showAll) "Show fewer" else "Show all ${list.size} purchases", color = Blue, fontSize = 13.sp,
            modifier = Modifier.clickable { showAll = !showAll }.padding(top = 6.dp))
    }
}
