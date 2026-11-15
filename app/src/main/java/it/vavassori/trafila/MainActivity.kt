package it.vavassori.trafila

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Blu = Color(0xFF1E4FA0)
val Verde = Color(0xFF1E8C46)
val Giallo = Color(0xFFFFC107)
val Rosso = Color(0xFFC62828)

enum class Schermata { INPUT, RISULTATI, SALVATE, IMPOSTAZIONI }

class MainActivity : ComponentActivity() {
    private var importata by mutableStateOf<Sequenza?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importata = Storage.importaDaIntent(this, intent)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Blu,
                    onPrimary = Color.White,
                    secondary = Verde,
                    background = Color(0xFFF7F8FB),
                    surface = Color.White
                )
            ) {
                App(importata) { importata = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importata = Storage.importaDaIntent(this, intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(importata: Sequenza?, onImportGestita: () -> Unit) {
    val ctx = LocalContext.current
    var schermata by remember { mutableStateOf(Schermata.INPUT) }

    // ---- stato di input ----
    var dVergella by remember { mutableStateOf("5,50") }
    var dFinito by remember { mutableStateOf("2,00") }
    var nPassi by remember { mutableStateOf("7") }
    var velocita by remember { mutableStateOf("10,0") }
    var rmVergella by remember { mutableStateOf("") }
    var manuale by remember { mutableStateOf(false) }
    var errore by remember { mutableStateOf<String?>(null) }

    // ---- stato dei risultati ----
    var riduzioni by remember { mutableStateOf<List<String>>(emptyList()) }
    var nomeCorrente by remember { mutableStateOf("") }

    var coeff by remember { mutableStateOf(Storage.coeffIncrudimento(ctx)) }
    var salvate by remember { mutableStateOf(Storage.carica(ctx)) }

    fun caricaSequenza(s: Sequenza) {
        dVergella = s.dVergella.fmt(2)
        dFinito = s.dFinito.fmt(2)
        nPassi = s.riduzioni.size.toString()
        velocita = s.velocitaFinale.fmt(1)
        rmVergella = s.rmVergella?.fmt(0) ?: ""
        manuale = s.manuale
        riduzioni = s.riduzioni.map { it.fmt(1) }
        nomeCorrente = s.nome
        schermata = Schermata.RISULTATI
    }

    // gestione di un file .trafila aperto dall'esterno
    LaunchedEffect(importata) {
        if (importata != null) {
            salvate = Storage.salva(ctx, importata)
            caricaSequenza(importata)
            Toast.makeText(ctx, "Sequenza \"${importata.nome}\" importata e salvata", Toast.LENGTH_LONG).show()
            onImportGestita()
        }
    }

    fun avviaCalcolo() {
        val dv = dVergella.toDoubleIt()
        val df = dFinito.toDoubleIt()
        val n = nPassi.trim().toIntOrNull()
        val v = velocita.toDoubleIt()
        errore = when {
            dv == null || dv <= 0 -> "Diametro vergella non valido"
            df == null || df <= 0 -> "Diametro finito non valido"
            df >= dv -> "Il diametro finito deve essere minore della vergella"
            n == null || n < 1 || n > 30 -> "Numero di passi non valido (1–30)"
            v == null || v <= 0 -> "Velocità finale non valida"
            rmVergella.isNotBlank() && rmVergella.toDoubleIt() == null -> "Resistenza vergella non valida"
            else -> null
        }
        if (errore != null) return
        riduzioni = riduzioniAutomatiche(dv!!, df!!, n!!).map { it.fmt(1) }
        nomeCorrente = ""
        schermata = Schermata.RISULTATI
    }

    BackHandler(enabled = schermata != Schermata.INPUT) { schermata = Schermata.INPUT }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (schermata) {
                            Schermata.INPUT -> "Calcolo Riduzione Trafila"
                            Schermata.RISULTATI -> if (nomeCorrente.isBlank()) "Risultati" else nomeCorrente
                            Schermata.SALVATE -> "Sequenze salvate"
                            Schermata.IMPOSTAZIONI -> "Impostazioni"
                        }
                    )
                },
                navigationIcon = {
                    if (schermata != Schermata.INPUT) {
                        IconButton(onClick = { schermata = Schermata.INPUT }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                        }
                    }
                },
                actions = {
                    if (schermata == Schermata.INPUT) {
                        IconButton(onClick = { salvate = Storage.carica(ctx); schermata = Schermata.SALVATE }) {
                            Icon(Icons.Default.FolderOpen, contentDescription = "Salvate")
                        }
                        IconButton(onClick = { schermata = Schermata.IMPOSTAZIONI }) {
                            Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Blu, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White)
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (schermata) {
                Schermata.INPUT -> SchermataInput(
                    dVergella, { dVergella = it }, dFinito, { dFinito = it },
                    nPassi, { nPassi = it }, velocita, { velocita = it },
                    rmVergella, { rmVergella = it }, manuale, { manuale = it },
                    errore, ::avviaCalcolo
                )
                Schermata.RISULTATI -> SchermataRisultati(
                    dVergella = dVergella.toDoubleIt() ?: 0.0,
                    dFinito = dFinito.toDoubleIt() ?: 0.0,
                    velocita = velocita.toDoubleIt() ?: 0.0,
                    rmVergella = rmVergella.toDoubleIt(),
                    coeff = coeff,
                    manuale = manuale,
                    onManualeChange = { manuale = it },
                    riduzioni = riduzioni,
                    onRiduzioniChange = { riduzioni = it },
                    onRicalcolaAuto = {
                        val dv = dVergella.toDoubleIt(); val df = dFinito.toDoubleIt()
                        if (dv != null && df != null) riduzioni = riduzioniAutomatiche(dv, df, riduzioni.size).map { it.fmt(1) }
                    },
                    nomeCorrente = nomeCorrente,
                    onSalva = { nome ->
                        val seq = Sequenza(
                            nome = nome,
                            dVergella = dVergella.toDoubleIt() ?: 0.0,
                            dFinito = dFinito.toDoubleIt() ?: 0.0,
                            velocitaFinale = velocita.toDoubleIt() ?: 0.0,
                            rmVergella = rmVergella.toDoubleIt(),
                            manuale = manuale,
                            riduzioni = riduzioni.map { it.toDoubleIt() ?: 0.0 }
                        )
                        salvate = Storage.salva(ctx, seq)
                        nomeCorrente = nome
                        Toast.makeText(ctx, "Salvata \"$nome\"", Toast.LENGTH_SHORT).show()
                    },
                    onCondividi = { nome ->
                        val seq = Sequenza(
                            nome = nome,
                            dVergella = dVergella.toDoubleIt() ?: 0.0,
                            dFinito = dFinito.toDoubleIt() ?: 0.0,
                            velocitaFinale = velocita.toDoubleIt() ?: 0.0,
                            rmVergella = rmVergella.toDoubleIt(),
                            manuale = manuale,
                            riduzioni = riduzioni.map { it.toDoubleIt() ?: 0.0 }
                        )
                        Storage.condividi(ctx, seq)
                    }
                )
                Schermata.SALVATE -> SchermataSalvate(
                    lista = salvate,
                    onApri = { caricaSequenza(it) },
                    onElimina = { salvate = Storage.elimina(ctx, it.nome) },
                    onCondividi = { Storage.condividi(ctx, it) },
                    onImportaTesto = { testo ->
                        val s = Storage.importaDaTesto(testo)
                        if (s == null) Toast.makeText(ctx, "Testo non valido", Toast.LENGTH_SHORT).show()
                        else { salvate = Storage.salva(ctx, s); Toast.makeText(ctx, "Importata \"${s.nome}\"", Toast.LENGTH_SHORT).show() }
                    }
                )
                Schermata.IMPOSTAZIONI -> SchermataImpostazioni(coeff) { coeff = it; Storage.salvaCoeff(ctx, it) }
            }
        }
    }
}

// ------------------------------------------------------------------ INPUT
@Composable
fun SchermataInput(
    dVergella: String, onDV: (String) -> Unit,
    dFinito: String, onDF: (String) -> Unit,
    nPassi: String, onN: (String) -> Unit,
    velocita: String, onV: (String) -> Unit,
    rm: String, onRm: (String) -> Unit,
    manuale: Boolean, onManuale: (Boolean) -> Unit,
    errore: String?, onCalcola: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CampoNumero("Diametro vergella (mm)", dVergella, onDV)
        CampoNumero("Diametro finito (mm)", dFinito, onDF)
        CampoNumero("Numero di passi", nPassi, onN, intero = true)
        CampoNumero("Velocità finale (m/s)", velocita, onV)
        CampoNumero("Resistenza vergella Rm (N/mm²) – facoltativa", rm, onRm, intero = true)

        Text("Modalità di calcolo", color = Color.Gray, fontSize = 14.sp)
        SelettoreModalita(manuale, onManuale)

        if (errore != null) {
            Text(errore, color = Rosso, fontWeight = FontWeight.Bold)
        }

        Button(
            onClick = onCalcola,
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Verde),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("CALCOLA", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            "Automatico: l'app propone riduzioni decrescenti dal primo all'ultimo passo e arriva esattamente al diametro finito.\n" +
            "Manuale: parte dalla curva automatica e puoi modificare la riduzione di ogni passo; l'app ricalcola i diametri e indica lo scostamento dal finito.",
            color = Color.Gray, fontSize = 13.sp
        )
    }
}

@Composable
fun CampoNumero(label: String, valore: String, onChange: (String) -> Unit, intero: Boolean = false) {
    OutlinedTextField(
        value = valore,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (intero) KeyboardType.Number else KeyboardType.Decimal)
    )
}

@Composable
fun SelettoreModalita(manuale: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFFDCDEE6), RoundedCornerShape(30.dp)).padding(4.dp)
    ) {
        listOf(false to "Automatico", true to "Manuale").forEach { (m, label) ->
            val sel = manuale == m
            Box(
                Modifier.weight(1f).height(44.dp)
                    .background(if (sel) Blu else Color.Transparent, RoundedCornerShape(26.dp)),
                contentAlignment = Alignment.Center
            ) {
                TextButton(onClick = { onChange(m) }, modifier = Modifier.fillMaxSize()) {
                    Text(label, color = if (sel) Color.White else Color.DarkGray,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

// ------------------------------------------------------------------ RISULTATI
@Composable
fun SchermataRisultati(
    dVergella: Double, dFinito: Double, velocita: Double, rmVergella: Double?, coeff: Double,
    manuale: Boolean, onManualeChange: (Boolean) -> Unit,
    riduzioni: List<String>, onRiduzioniChange: (List<String>) -> Unit,
    onRicalcolaAuto: () -> Unit,
    nomeCorrente: String,
    onSalva: (String) -> Unit,
    onCondividi: (String) -> Unit
) {
    val ridNum = riduzioni.map { it.toDoubleIt() ?: 0.0 }
    val ris = remember(dVergella, dFinito, velocita, rmVergella, coeff, ridNum) {
        calcola(dVergella, dFinito, ridNum, velocita, rmVergella, coeff)
    }
    var chiediNome by remember { mutableStateOf<String?>(null) } // "salva" / "condividi"
    var nome by remember { mutableStateOf(nomeCorrente) }
    LaunchedEffect(nomeCorrente) { nome = nomeCorrente }

    val scostOk = kotlin.math.abs(ris.scostamentoMm) < 0.005
    val fuoriLimite = ris.passi.count { it.fuoriLimite }

    Column(Modifier.fillMaxSize()) {
        // riepilogo
        Card(
            Modifier.fillMaxWidth().padding(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F4FC))
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${dVergella.fmt(2)} mm  →  ${dFinito.fmt(2)} mm   in ${ris.passi.size} passi",
                    fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("Riduzione totale: ${ris.riduzioneTotalePct.fmt(1)} %")
                Text("Produttività: ${ris.produttivitaKgH.fmt(0)} kg/h  @ ${velocita.fmt(1)} m/s")
                if (ris.resistenzaFinale != null)
                    Text("Rm stimata finito: ${ris.resistenzaFinale.fmt(0)} N/mm²  (vergella ${rmVergella?.fmt(0)})")
                Text(
                    "Ultimo Ø: ${ris.diametroFinaleOttenuto.fmt(3)} mm  –  scostamento ${if (ris.scostamentoMm >= 0) "+" else ""}${ris.scostamentoMm.fmt(3)} mm ${if (scostOk) "✓" else "✗"}",
                    color = if (scostOk) Verde else Rosso, fontWeight = FontWeight.Bold
                )
                if (fuoriLimite > 0)
                    Text("⚠ $fuoriLimite passo/i fuori dai limiti ${RID_MIN.fmt(0)}–${RID_MAX.fmt(0)} %",
                        color = Color(0xFF9A6B00), fontWeight = FontWeight.Bold)
            }
        }

        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SelettoreModalita(manuale, onManualeChange) }
            if (manuale) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onRicalcolaAuto) { Text("Reset") }
            }
        }

        // intestazione tabella
        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp).background(Color(0xFFE1E4EE)).padding(vertical = 8.dp, horizontal = 8.dp)
        ) {
            Cella("Passo", 0.7f, bold = true)
            Cella("Ø in", 1f, bold = true)
            Cella("Filiera", 1f, bold = true)
            Cella("Rid. %", 1.2f, bold = true)
            Cella("m/s", 0.9f, bold = true)
            if (rmVergella != null) Cella("Rm", 0.9f, bold = true)
        }

        LazyColumn(Modifier.weight(1f)) {
            items(ris.passi.size) { i ->
                val p = ris.passi[i]
                val bg = when {
                    p.fuoriLimite -> Color(0xFFFFF4D6)
                    i % 2 == 0 -> Color.White
                    else -> Color(0xFFF6F7FA)
                }
                Row(
                    Modifier.fillMaxWidth().background(bg).padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cella(p.n.toString(), 0.7f)
                    Cella(p.dIn.fmt(2), 1f)
                    Cella(p.dOut.fmt(2), 1f, bold = true)
                    Box(Modifier.weight(1.2f).padding(horizontal = 2.dp)) {
                        if (manuale) {
                            OutlinedTextField(
                                value = riduzioni[i],
                                onValueChange = { nuovo ->
                                    onRiduzioniChange(riduzioni.toMutableList().also { it[i] = nuovo })
                                },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, color = Blu, fontWeight = FontWeight.Bold),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth().height(52.dp)
                            )
                        } else {
                            Text(p.ridPct.fmt(1), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                                color = if (p.fuoriLimite) Color(0xFF9A6B00) else Color.Unspecified,
                                fontWeight = if (p.fuoriLimite) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                    Cella(p.velocita.fmt(2), 0.9f)
                    if (rmVergella != null) Cella(p.resistenza?.fmt(0) ?: "", 0.9f)
                }
                HorizontalDivider(color = Color(0xFFE6E6EA))
            }
        }

        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { chiediNome = "salva" }, modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blu)) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Salva")
            }
            Button(onClick = { chiediNome = "condividi" }, modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Verde)) {
                Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Condividi")
            }
        }
    }

    if (chiediNome != null) {
        AlertDialog(
            onDismissRequest = { chiediNome = null },
            title = { Text(if (chiediNome == "salva") "Salva sequenza" else "Condividi sequenza") },
            text = {
                OutlinedTextField(value = nome, onValueChange = { nome = it }, label = { Text("Nome") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    if (nome.isNotBlank()) {
                        if (chiediNome == "salva") onSalva(nome.trim()) else onCondividi(nome.trim())
                        chiediNome = null
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { chiediNome = null }) { Text("Annulla") } }
        )
    }
}

@Composable
fun RowScope.Cella(testo: String, peso: Float, bold: Boolean = false) {
    Text(
        testo, modifier = Modifier.weight(peso), textAlign = TextAlign.Center,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, fontSize = 15.sp
    )
}

// ------------------------------------------------------------------ SALVATE
@Composable
fun SchermataSalvate(
    lista: List<Sequenza>,
    onApri: (Sequenza) -> Unit,
    onElimina: (Sequenza) -> Unit,
    onCondividi: (Sequenza) -> Unit,
    onImportaTesto: (String) -> Unit
) {
    var daEliminare by remember { mutableStateOf<Sequenza?>(null) }
    var importa by remember { mutableStateOf(false) }
    var testoImport by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        if (lista.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("Nessuna sequenza salvata.\nCalcola una sequenza e premi \"Salva\".", textAlign = TextAlign.Center, color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(lista, key = { it.nome }) { s ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s.nome, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text("${s.dVergella.fmt(2)} → ${s.dFinito.fmt(2)} mm · ${s.riduzioni.size} passi · ${s.velocitaFinale.fmt(1)} m/s" +
                                        (if (s.manuale) " · manuale" else " · auto"), color = Color.Gray, fontSize = 13.sp)
                            }
                            IconButton(onClick = { onCondividi(s) }) { Icon(Icons.Default.Share, "Condividi", tint = Verde) }
                            IconButton(onClick = { daEliminare = s }) { Icon(Icons.Default.Delete, "Elimina", tint = Rosso) }
                            Button(onClick = { onApri(s) }, colors = ButtonDefaults.buttonColors(containerColor = Blu)) { Text("Apri") }
                        }
                    }
                }
            }
        }
        OutlinedButton(onClick = { importa = true }, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Icon(Icons.Default.ContentPaste, null); Spacer(Modifier.width(6.dp)); Text("Importa da testo incollato")
        }
    }

    if (daEliminare != null) {
        AlertDialog(
            onDismissRequest = { daEliminare = null },
            title = { Text("Eliminare \"${daEliminare!!.nome}\"?") },
            confirmButton = { TextButton(onClick = { onElimina(daEliminare!!); daEliminare = null }) { Text("Elimina", color = Rosso) } },
            dismissButton = { TextButton(onClick = { daEliminare = null }) { Text("Annulla") } }
        )
    }
    if (importa) {
        AlertDialog(
            onDismissRequest = { importa = false },
            title = { Text("Importa sequenza") },
            text = {
                Column {
                    Text("Incolla qui il contenuto di un file .trafila ricevuto.", fontSize = 13.sp, color = Color.Gray)
                    OutlinedTextField(value = testoImport, onValueChange = { testoImport = it }, modifier = Modifier.fillMaxWidth().height(160.dp))
                }
            },
            confirmButton = { TextButton(onClick = { onImportaTesto(testoImport); testoImport = ""; importa = false }) { Text("Importa") } },
            dismissButton = { TextButton(onClick = { importa = false }) { Text("Annulla") } }
        )
    }
}

// ------------------------------------------------------------------ IMPOSTAZIONI
@Composable
fun SchermataImpostazioni(coeff: Double, onCoeff: (Double) -> Unit) {
    var testo by remember { mutableStateOf(coeff.fmt(0)) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Coefficiente di incrudimento", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "La resistenza in uscita è stimata con la formula empirica:\n\n" +
            "    Rm = Rm vergella + C × ln(A₀ / A)\n\n" +
            "dove A₀ è la sezione della vergella e A quella del filo dopo il passo. " +
            "C dipende dal tipo di acciaio (indicativamente 250–350 per basso carbonio, 400–600 per alto carbonio). " +
            "Taralo con 2–3 prove di trazione reali: inserisci la Rm della vergella, confronta la Rm stimata del finito con quella misurata e correggi C finché coincidono.",
            fontSize = 14.sp, color = Color.DarkGray
        )
        OutlinedTextField(
            value = testo, onValueChange = { testo = it }, label = { Text("C (N/mm² per unità di deformazione)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
        )
        Button(onClick = { testo.toDoubleIt()?.let(onCoeff) }, colors = ButtonDefaults.buttonColors(containerColor = Blu)) { Text("Salva coefficiente") }

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        Text("Parametri fissi", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Densità acciaio: ${DENSITA_ACCIAIO.fmt(2)} kg/dm³\n" +
             "Riduzione primo passo (curva automatica): ~${RID_PRIMO_PASSO.fmt(0)} %\n" +
             "Riduzione ultimo passo (curva automatica): ~${RID_ULTIMO_PASSO.fmt(0)} %\n" +
             "Avviso fuori limite: sotto ${RID_MIN.fmt(0)} % o sopra ${RID_MAX.fmt(0)} %",
            fontSize = 14.sp, color = Color.DarkGray)
        Text("Calcolo Riduzione Trafila – v1.0", color = Color.Gray, fontSize = 12.sp)
    }
}
