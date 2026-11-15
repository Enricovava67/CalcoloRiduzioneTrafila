package it.vavassori.trafila

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Blu = Color(0xFF1E4FA0)
val BluChiaro = Color(0xFFE8EEFA)
val Verde = Color(0xFF1E8C46)
val Ambra = Color(0xFF9A6B00)
val AmbraChiaro = Color(0xFFFFF4D6)
val Rosso = Color(0xFFC62828)

enum class Schermata { HOME, INPUT_SECCO, INPUT_BAGNO, RISULTATI, GRAFICO, SALVATE, IMPOSTAZIONI }

class MainActivity : ComponentActivity() {
    private var importata by mutableStateOf<Sequenza?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importata = Storage.importaDaIntent(this, intent)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Blu, onPrimary = Color.White, secondary = Verde,
                    background = Color(0xFFF7F8FB), surface = Color.White
                )
            ) { App(importata) { importata = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        importata = Storage.importaDaIntent(this, intent)
    }
}

/** Stato di una sequenza in lavorazione. */
class Lavoro(
    val tipo: String,                    // "secco" | "bagno"
    val dVergella: Double,
    val dFinito: Double,
    val velocita: Double,
    val rmVergella: Double?,
    val ridFissa: Double?,               // solo bagno
    val aggiustaPrimo: Boolean,          // solo bagno
    val passiTeorici: Double?            // solo bagno
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(importata: Sequenza?, onImportGestita: () -> Unit) {
    val ctx = LocalContext.current
    var schermata by remember { mutableStateOf(Schermata.HOME) }

    // ---- input secco ----
    var dVergella by remember { mutableStateOf("5,50") }
    var dFinito by remember { mutableStateOf("2,00") }
    var nPassi by remember { mutableStateOf("7") }
    var velocita by remember { mutableStateOf("10,0") }
    var rmVergella by remember { mutableStateOf("") }
    // ---- input bagno ----
    var bIn by remember { mutableStateOf("2,00") }
    var bOut by remember { mutableStateOf("0,80") }
    var bRid by remember { mutableStateOf("20,0") }
    var bVel by remember { mutableStateOf("15,0") }
    var bRm by remember { mutableStateOf("") }
    var bAggiustaPrimo by remember { mutableStateOf(false) }

    var manuale by remember { mutableStateOf(false) }
    var errore by remember { mutableStateOf<String?>(null) }

    // ---- risultati ----
    var lavoro by remember { mutableStateOf<Lavoro?>(null) }
    var riduzioni by remember { mutableStateOf<List<String>>(emptyList()) }
    var nomeCorrente by remember { mutableStateOf("") }

    var coeff by remember { mutableStateOf(Storage.coeffIncrudimento(ctx)) }
    var salvate by remember { mutableStateOf(Storage.carica(ctx)) }

    fun caricaSequenza(s: Sequenza) {
        lavoro = Lavoro(s.tipo, s.dVergella, s.dFinito, s.velocitaFinale, s.rmVergella, s.ridFissa, s.aggiustaPrimo, null)
        if (s.tipo == "bagno") {
            bIn = s.dVergella.fmt(2); bOut = s.dFinito.fmt(2); bVel = s.velocitaFinale.fmt(1)
            bRm = s.rmVergella?.fmt(0) ?: ""; bRid = s.ridFissa?.fmt(1) ?: bRid; bAggiustaPrimo = s.aggiustaPrimo
        } else {
            dVergella = s.dVergella.fmt(2); dFinito = s.dFinito.fmt(2); nPassi = s.riduzioni.size.toString()
            velocita = s.velocitaFinale.fmt(1); rmVergella = s.rmVergella?.fmt(0) ?: ""
        }
        manuale = s.manuale
        riduzioni = s.riduzioni.map { it.fmt(2) }
        nomeCorrente = s.nome
        schermata = Schermata.RISULTATI
    }

    LaunchedEffect(importata) {
        if (importata != null) {
            salvate = Storage.salva(ctx, importata)
            caricaSequenza(importata)
            Toast.makeText(ctx, "Sequenza \"${importata.nome}\" importata e salvata", Toast.LENGTH_LONG).show()
            onImportGestita()
        }
    }

    fun sequenzaCorrente(nome: String): Sequenza? {
        val l = lavoro ?: return null
        return Sequenza(
            nome = nome, dVergella = l.dVergella, dFinito = l.dFinito, velocitaFinale = l.velocita,
            rmVergella = l.rmVergella, manuale = manuale, riduzioni = riduzioni.map { it.toDoubleIt() ?: 0.0 },
            tipo = l.tipo, ridFissa = l.ridFissa, aggiustaPrimo = l.aggiustaPrimo
        )
    }

    fun calcolaSecco() {
        val dv = dVergella.toDoubleIt(); val df = dFinito.toDoubleIt()
        val n = nPassi.trim().toIntOrNull(); val v = velocita.toDoubleIt()
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
        lavoro = Lavoro("secco", dv!!, df!!, v!!, rmVergella.toDoubleIt(), null, false, null)
        riduzioni = riduzioniAutomatiche(dv, df, n!!).map { it.fmt(2) }
        nomeCorrente = ""
        schermata = Schermata.RISULTATI
    }

    fun calcolaBagno() {
        val di = bIn.toDoubleIt(); val dout = bOut.toDoubleIt()
        val r = bRid.toDoubleIt(); val v = bVel.toDoubleIt()
        errore = when {
            di == null || di <= 0 -> "Diametro entrata non valido"
            dout == null || dout <= 0 -> "Diametro uscita non valido"
            dout >= di -> "Il diametro di uscita deve essere minore di quello di entrata"
            r == null || r <= 0 || r >= 60 -> "Riduzione per passo non valida (0–60 %)"
            v == null || v <= 0 -> "Velocità finale non valida"
            bRm.isNotBlank() && bRm.toDoubleIt() == null -> "Resistenza non valida"
            else -> null
        }
        if (errore != null) return
        val rb = riduzioniBagno(di!!, dout!!, r!!, if (bAggiustaPrimo) Aggiustamento.PRIMO else Aggiustamento.TUTTI)
        if (rb.riduzioni.size > 60) { errore = "Troppi passi (${rb.riduzioni.size}): aumenta la riduzione"; return }
        lavoro = Lavoro("bagno", di, dout, v!!, bRm.toDoubleIt(), r, bAggiustaPrimo, rb.passiTeorici)
        riduzioni = rb.riduzioni.map { it.fmt(2) }
        manuale = false
        nomeCorrente = ""
        schermata = Schermata.RISULTATI
    }

    fun indietro() {
        schermata = when (schermata) {
            Schermata.GRAFICO -> Schermata.RISULTATI
            Schermata.RISULTATI -> if (lavoro?.tipo == "bagno") Schermata.INPUT_BAGNO else Schermata.INPUT_SECCO
            else -> Schermata.HOME
        }
    }
    BackHandler(enabled = schermata != Schermata.HOME) { indietro() }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (schermata) {
                            Schermata.HOME -> "Calcolo Riduzione Trafila"
                            Schermata.INPUT_SECCO -> "Trafilatura a secco"
                            Schermata.INPUT_BAGNO -> "Trafilatura a bagno"
                            Schermata.RISULTATI -> if (nomeCorrente.isBlank()) "Risultati" else nomeCorrente
                            Schermata.GRAFICO -> "Grafico riduzioni"
                            Schermata.SALVATE -> "Sequenze salvate"
                            Schermata.IMPOSTAZIONI -> "Impostazioni"
                        }, maxLines = 1
                    )
                },
                navigationIcon = {
                    if (schermata != Schermata.HOME) IconButton(onClick = { indietro() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    when (schermata) {
                        Schermata.HOME -> {
                            IconButton(onClick = { salvate = Storage.carica(ctx); schermata = Schermata.SALVATE }) {
                                Icon(Icons.Default.FolderOpen, contentDescription = "Salvate")
                            }
                            IconButton(onClick = { schermata = Schermata.IMPOSTAZIONI }) {
                                Icon(Icons.Default.Settings, contentDescription = "Impostazioni")
                            }
                        }
                        Schermata.RISULTATI -> {
                            IconButton(onClick = { schermata = Schermata.GRAFICO }) {
                                Icon(Icons.Default.BarChart, contentDescription = "Grafico")
                            }
                            IconButton(onClick = { schermata = Schermata.HOME }) {
                                Icon(Icons.Default.Home, contentDescription = "Home")
                            }
                        }
                        else -> {}
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Blu, titleContentColor = Color.White,
                    navigationIconContentColor = Color.White, actionIconContentColor = Color.White
                )
            )
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (schermata) {
                Schermata.HOME -> SchermataHome(
                    onSecco = { errore = null; schermata = Schermata.INPUT_SECCO },
                    onBagno = { errore = null; schermata = Schermata.INPUT_BAGNO }
                )
                Schermata.INPUT_SECCO -> SchermataInputSecco(
                    dVergella, { dVergella = it }, dFinito, { dFinito = it }, nPassi, { nPassi = it },
                    velocita, { velocita = it }, rmVergella, { rmVergella = it },
                    manuale, { manuale = it }, errore, ::calcolaSecco
                )
                Schermata.INPUT_BAGNO -> SchermataInputBagno(
                    bIn, { bIn = it }, bOut, { bOut = it }, bRid, { bRid = it }, bVel, { bVel = it },
                    bRm, { bRm = it }, bAggiustaPrimo, { bAggiustaPrimo = it }, errore, ::calcolaBagno
                )
                Schermata.RISULTATI -> lavoro?.let { l ->
                    SchermataRisultati(
                        lavoro = l, coeff = coeff,
                        manuale = manuale, onManualeChange = { manuale = it },
                        riduzioni = riduzioni, onRiduzioniChange = { riduzioni = it },
                        onReset = {
                            riduzioni = if (l.tipo == "bagno" && l.ridFissa != null)
                                riduzioniBagno(l.dVergella, l.dFinito, l.ridFissa, if (l.aggiustaPrimo) Aggiustamento.PRIMO else Aggiustamento.TUTTI).riduzioni.map { it.fmt(2) }
                            else riduzioniAutomatiche(l.dVergella, l.dFinito, riduzioni.size).map { it.fmt(2) }
                        },
                        nomeCorrente = nomeCorrente,
                        onSalva = { nome ->
                            sequenzaCorrente(nome)?.let { salvate = Storage.salva(ctx, it) }
                            nomeCorrente = nome
                            Toast.makeText(ctx, "Salvata \"$nome\"", Toast.LENGTH_SHORT).show()
                        },
                        onCondividi = { nome -> sequenzaCorrente(nome)?.let { Storage.condividi(ctx, it) } }
                    )
                }
                Schermata.GRAFICO -> lavoro?.let { l ->
                    SchermataGrafico(l, riduzioni.map { it.toDoubleIt() ?: 0.0 })
                }
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

// ------------------------------------------------------------------ HOME
@Composable
fun SchermataHome(onSecco: () -> Unit, onBagno: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Che tipo di trafilatura?", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
        Spacer(Modifier.height(28.dp))
        SceltaCard("TRAFILATURA A SECCO", "Riduzione decrescente passo per passo.\nInput: vergella, finito, numero di passi.", Blu, onSecco)
        Spacer(Modifier.height(18.dp))
        SceltaCard("TRAFILATURA A BAGNO", "Riduzione fissa uguale su tutti i passi.\nInput: entrata, uscita, % per passo.", Verde, onBagno)
    }
}

@Composable
fun SceltaCard(titolo: String, sotto: String, colore: Color, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = colore),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(22.dp)) {
            Text(titolo, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(sotto, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
        }
    }
}

// ------------------------------------------------------------------ INPUT SECCO
@Composable
fun SchermataInputSecco(
    dVergella: String, onDV: (String) -> Unit, dFinito: String, onDF: (String) -> Unit,
    nPassi: String, onN: (String) -> Unit, velocita: String, onV: (String) -> Unit,
    rm: String, onRm: (String) -> Unit, manuale: Boolean, onManuale: (Boolean) -> Unit,
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
        SelettoreDue("Automatico", "Manuale", manuale, onManuale)
        if (errore != null) Text(errore, color = Rosso, fontWeight = FontWeight.Bold)
        BottoneCalcola(onCalcola)
        Text(
            "Automatico: riduzioni decrescenti dal primo all'ultimo passo, arrivando esattamente al finito.\n" +
            "Manuale: parte dalla curva automatica; puoi modificare la riduzione % oppure direttamente il diametro della filiera di ogni passo.",
            color = Color.Gray, fontSize = 13.sp
        )
    }
}

// ------------------------------------------------------------------ INPUT BAGNO
@Composable
fun SchermataInputBagno(
    dIn: String, onIn: (String) -> Unit, dOut: String, onOut: (String) -> Unit,
    rid: String, onRid: (String) -> Unit, vel: String, onVel: (String) -> Unit,
    rm: String, onRm: (String) -> Unit, aggiustaPrimo: Boolean, onAgg: (Boolean) -> Unit,
    errore: String?, onCalcola: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CampoNumero("Diametro entrata (mm)", dIn, onIn)
        CampoNumero("Diametro uscita (mm)", dOut, onOut)
        CampoNumero("Riduzione per passo (%)", rid, onRid)
        CampoNumero("Velocità finale (m/s)", vel, onVel)
        CampoNumero("Resistenza entrata Rm (N/mm²) – facoltativa", rm, onRm, intero = true)
        Text("Per chiudere esatti al diametro di uscita:", color = Color.Gray, fontSize = 14.sp)
        SelettoreDue("Ritocca tutti i passi", "Solo il primo passo", aggiustaPrimo, onAgg)
        if (errore != null) Text(errore, color = Rosso, fontWeight = FontWeight.Bold)
        BottoneCalcola(onCalcola)
        Text(
            "L'app calcola il numero di passi necessario con la riduzione indicata (arrotondato per eccesso).\n" +
            "Ritocca tutti i passi: la % viene leggermente ridotta su tutti i passi.\n" +
            "Solo il primo passo: tutti i passi restano alla % nominale, il primo assorbe la differenza.",
            color = Color.Gray, fontSize = 13.sp
        )
    }
}

@Composable
fun BottoneCalcola(onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = Modifier.fillMaxWidth().height(60.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Verde), shape = RoundedCornerShape(16.dp)
    ) { Text("CALCOLA", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun CampoNumero(label: String, valore: String, onChange: (String) -> Unit, intero: Boolean = false) {
    OutlinedTextField(
        value = valore, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = if (intero) KeyboardType.Number else KeyboardType.Decimal)
    )
}

@Composable
fun SelettoreDue(labelA: String, labelB: String, secondo: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFFDCDEE6), RoundedCornerShape(30.dp)).padding(4.dp)) {
        listOf(false to labelA, true to labelB).forEach { (m, label) ->
            val sel = secondo == m
            Box(
                Modifier.weight(1f).height(44.dp)
                    .background(if (sel) Blu else Color.Transparent, RoundedCornerShape(26.dp))
                    .clickable { onChange(m) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (sel) Color.White else Color.DarkGray, fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal, textAlign = TextAlign.Center)
            }
        }
    }
}

// ------------------------------------------------------------------ RISULTATI
@Composable
fun SchermataRisultati(
    lavoro: Lavoro, coeff: Double,
    manuale: Boolean, onManualeChange: (Boolean) -> Unit,
    riduzioni: List<String>, onRiduzioniChange: (List<String>) -> Unit,
    onReset: () -> Unit,
    nomeCorrente: String, onSalva: (String) -> Unit, onCondividi: (String) -> Unit
) {
    val tastiera = LocalSoftwareKeyboardController.current
    val ridNum = riduzioni.map { it.toDoubleIt() ?: 0.0 }
    val ris = remember(lavoro, coeff, ridNum) {
        calcola(lavoro.dVergella, lavoro.dFinito, ridNum, lavoro.velocita, lavoro.rmVergella, coeff)
    }

    // testi delle caselle diametro (in manuale): inizializzati dai diametri calcolati
    var diaTesti by remember(riduzioni.size, manuale) { mutableStateOf(ris.passi.map { it.dOut.fmt(3) }) }
    // riga in modifica: -1 nessuna, altrimenti indice; serve per non sovrascrivere la casella mentre si scrive
    var rigaInModificaDia by remember { mutableStateOf(-1) }
    LaunchedEffect(ris) {
        diaTesti = ris.passi.mapIndexed { i, p -> if (i == rigaInModificaDia) diaTesti.getOrElse(i) { p.dOut.fmt(3) } else p.dOut.fmt(3) }
    }

    var chiediNome by remember { mutableStateOf<String?>(null) }
    var nome by remember { mutableStateOf(nomeCorrente) }
    LaunchedEffect(nomeCorrente) { nome = nomeCorrente }

    val scostOk = kotlin.math.abs(ris.scostamentoMm) < 0.005
    val fuoriLimite = ris.passi.count { it.fuoriLimite }
    val mostraRm = lavoro.rmVergella != null

    Column(Modifier.fillMaxSize()) {
        // ---- riepilogo compatto in alto ----
        Column(Modifier.fillMaxWidth().background(BluChiaro).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                "${lavoro.dVergella.fmt(2)} → ${lavoro.dFinito.fmt(2)} mm · ${ris.passi.size} passi · " +
                (if (lavoro.tipo == "bagno") "bagno" else "secco"),
                fontWeight = FontWeight.Bold, fontSize = 16.sp
            )
            Text(
                "Rid. totale ${ris.riduzioneTotalePct.fmt(1)} %  ·  ${ris.produttivitaKgH.fmt(0)} kg/h @ ${lavoro.velocita.fmt(1)} m/s" +
                (if (ris.resistenzaFinale != null) "  ·  Rm ${ris.resistenzaFinale.fmt(0)}" else ""),
                fontSize = 13.sp
            )
            if (lavoro.tipo == "bagno" && lavoro.ridFissa != null)
                Text(
                    "Rid. nominale ${lavoro.ridFissa.fmt(1)} %" +
                    (if (lavoro.passiTeorici != null) " · passi teorici ${lavoro.passiTeorici.fmt(2)}" else "") +
                    (if (lavoro.aggiustaPrimo) " · aggiustato il 1° passo" else " · ritoccati tutti i passi"),
                    fontSize = 13.sp, color = Color.DarkGray
                )
        }

        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { SelettoreDue("Automatico", "Manuale", manuale, onManualeChange) }
            if (manuale) {
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = { rigaInModificaDia = -1; onReset() }) { Text("Reset") }
            }
        }

        // ---- intestazione ----
        Row(Modifier.fillMaxWidth().background(Color(0xFFE1E4EE)).padding(vertical = 8.dp, horizontal = 6.dp)) {
            Cella("N°", 0.5f, bold = true)
            Cella("Ø in", 0.9f, bold = true)
            Cella("Filiera", 1.3f, bold = true)
            Cella("Rid. %", 1.3f, bold = true)
            Cella("m/s", 0.9f, bold = true)
            if (mostraRm) Cella("Rm", 0.8f, bold = true)
        }

        // ---- tabella ----
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 8.dp)) {
            items(ris.passi.size) { i ->
                val p = ris.passi[i]
                val bg = when {
                    p.fuoriLimite -> AmbraChiaro
                    i % 2 == 0 -> Color.White
                    else -> Color(0xFFF6F7FA)
                }
                Row(
                    Modifier.fillMaxWidth().background(bg).padding(horizontal = 6.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Cella(p.n.toString(), 0.5f)
                    Cella(p.dIn.fmt(2), 0.9f)
                    Box(Modifier.weight(1.3f).padding(horizontal = 3.dp)) {
                        if (manuale) {
                            CasellaEdit(
                                valore = diaTesti.getOrElse(i) { p.dOut.fmt(3) },
                                colore = Verde,
                                onFocus = { rigaInModificaDia = i },
                                onChange = { nuovo ->
                                    diaTesti = diaTesti.toMutableList().also { if (i < it.size) it[i] = nuovo }
                                    val d = nuovo.toDoubleIt()
                                    if (d != null && d > 0 && d < p.dIn) {
                                        val r = riduzionePct(p.dIn, d)
                                        onRiduzioniChange(riduzioni.toMutableList().also { it[i] = r.fmt(2) })
                                    }
                                },
                                onDone = { rigaInModificaDia = -1; tastiera?.hide() }
                            )
                        } else Testo(p.dOut.fmt(3), bold = true)
                    }
                    Box(Modifier.weight(1.3f).padding(horizontal = 3.dp)) {
                        if (manuale) {
                            CasellaEdit(
                                valore = riduzioni[i],
                                colore = Blu,
                                onFocus = { rigaInModificaDia = -1 },
                                onChange = { nuovo -> onRiduzioniChange(riduzioni.toMutableList().also { it[i] = nuovo }) },
                                onDone = { tastiera?.hide() }
                            )
                        } else Testo(p.ridPct.fmt(1), bold = p.fuoriLimite, colore = if (p.fuoriLimite) Ambra else Color.Unspecified)
                    }
                    Cella(p.velocita.fmt(2), 0.9f)
                    if (mostraRm) Cella(p.resistenza?.fmt(0) ?: "", 0.8f)
                }
                HorizontalDivider(color = Color(0xFFE6E6EA))
            }
        }

        // ---- barra di stato in basso: sempre visibile, anche con la tastiera aperta ----
        Column(Modifier.fillMaxWidth().background(if (scostOk) Color(0xFFE6F4EA) else Color(0xFFFDE8E8)).padding(horizontal = 14.dp, vertical = 8.dp)) {
            Text(
                "Ultimo Ø ${ris.diametroFinaleOttenuto.fmt(3)} mm · scostamento ${if (ris.scostamentoMm >= 0) "+" else ""}${ris.scostamentoMm.fmt(3)} mm ${if (scostOk) "✓" else "✗"}",
                color = if (scostOk) Verde else Rosso, fontWeight = FontWeight.Bold, fontSize = 14.sp
            )
            if (fuoriLimite > 0)
                Text("⚠ $fuoriLimite passo/i fuori dai limiti ${RID_MIN.fmt(0)}–${RID_MAX.fmt(0)} %", color = Ambra, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { chiediNome = "salva" }, modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Blu)) {
                Icon(Icons.Default.Save, null); Spacer(Modifier.width(6.dp)); Text("Salva")
            }
            Button(onClick = { chiediNome = "condividi" }, modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Verde)) {
                Icon(Icons.Default.Share, null); Spacer(Modifier.width(6.dp)); Text("Condividi")
            }
        }
    }

    if (chiediNome != null) {
        AlertDialog(
            onDismissRequest = { chiediNome = null },
            title = { Text(if (chiediNome == "salva") "Salva sequenza" else "Condividi sequenza") },
            text = { OutlinedTextField(value = nome, onValueChange = { nome = it }, label = { Text("Nome") }, singleLine = true) },
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

/** Casella di modifica compatta per la tabella (più bassa di OutlinedTextField). */
@Composable
fun CasellaEdit(valore: String, colore: Color, onFocus: () -> Unit, onChange: (String) -> Unit, onDone: () -> Unit) {
    var focus by remember { mutableStateOf(false) }
    BasicTextField(
        value = valore,
        onValueChange = onChange,
        singleLine = true,
        textStyle = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = colore, textAlign = TextAlign.Center),
        cursorBrush = SolidColor(colore),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(Color.White, RoundedCornerShape(8.dp))
            .border(if (focus) 2.dp else 1.dp, colore, RoundedCornerShape(8.dp))
            .onFocusChanged { st -> focus = st.isFocused; if (st.isFocused) onFocus() },
        decorationBox = { inner -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() } }
    )
}

@Composable
fun BoxScope.Testo(testo: String, bold: Boolean = false, colore: Color = Color.Unspecified) {
    Text(testo, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 15.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, color = colore)
}

@Composable
fun RowScope.Cella(testo: String, peso: Float, bold: Boolean = false) {
    Text(testo, modifier = Modifier.weight(peso), textAlign = TextAlign.Center, fontSize = 15.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
}

// ------------------------------------------------------------------ GRAFICO
@Composable
fun SchermataGrafico(lavoro: Lavoro, riduzioni: List<Double>) {
    val ris = remember(lavoro, riduzioni) { calcola(lavoro.dVergella, lavoro.dFinito, riduzioni, lavoro.velocita, lavoro.rmVergella) }
    val misura = rememberTextMeasurer()
    val n = riduzioni.size

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Riduzione % per passo", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Linee tratteggiate: limiti ${RID_MIN.fmt(0)} % e ${RID_MAX.fmt(0)} %. Barre gialle: passi fuori limite.", color = Color.Gray, fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))

        val maxY = kotlin.math.max(40.0, (riduzioni.maxOrNull() ?: 0.0) + 5.0)
        Canvas(Modifier.fillMaxWidth().height(300.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)) {
            val left = 44.dp.toPx(); val bottom = size.height - 28.dp.toPx(); val top = 10.dp.toPx()
            val right = size.width - 8.dp.toPx()
            val h = bottom - top; val w = right - left
            fun y(v: Double) = (bottom - h * (v / maxY)).toFloat()

            // assi e griglia
            drawLine(Color.Gray, Offset(left, top), Offset(left, bottom), 2f)
            drawLine(Color.Gray, Offset(left, bottom), Offset(right, bottom), 2f)
            var g = 0.0
            while (g <= maxY) {
                drawLine(Color(0xFFEEEEEE), Offset(left, y(g)), Offset(right, y(g)), 1f)
                drawText(misura, g.fmt(0), Offset(4f, y(g) - 7.sp.toPx()), TextStyle(fontSize = 11.sp, color = Color.Gray))
                g += 10.0
            }
            // limiti
            val tratt = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))
            drawLine(Rosso, Offset(left, y(RID_MIN)), Offset(right, y(RID_MIN)), 3f, pathEffect = tratt)
            drawLine(Rosso, Offset(left, y(RID_MAX)), Offset(right, y(RID_MAX)), 3f, pathEffect = tratt)

            // barre
            if (n > 0) {
                val slot = w / n
                val bw = slot * 0.62f
                riduzioni.forEachIndexed { i, r ->
                    val x = left + slot * i + (slot - bw) / 2
                    val fuori = r < RID_MIN || r > RID_MAX
                    drawRect(if (fuori) Color(0xFFF2B33D) else Blu, Offset(x, y(r)), Size(bw, bottom - y(r)))
                    val lab = r.fmt(1)
                    val tl = misura.measure(lab, TextStyle(fontSize = 11.sp))
                    drawText(misura, lab, Offset(x + bw / 2 - tl.size.width / 2, y(r) - tl.size.height - 2f), TextStyle(fontSize = 11.sp, color = Color.DarkGray, fontWeight = FontWeight.Bold))
                    val nl = (i + 1).toString()
                    val tn = misura.measure(nl, TextStyle(fontSize = 12.sp))
                    drawText(misura, nl, Offset(x + bw / 2 - tn.size.width / 2, bottom + 4f), TextStyle(fontSize = 12.sp, color = Color.DarkGray))
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Text("Diametro filiera per passo (mm)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        val dMax = lavoro.dVergella; val dMin = lavoro.dFinito
        Canvas(Modifier.fillMaxWidth().height(220.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(8.dp)) {
            val left = 48.dp.toPx(); val bottom = size.height - 28.dp.toPx(); val top = 12.dp.toPx()
            val right = size.width - 12.dp.toPx()
            val h = bottom - top; val w = right - left
            val span = (dMax - dMin).coerceAtLeast(0.01)
            fun y(v: Double) = (bottom - h * ((v - dMin) / span)).toFloat()
            drawLine(Color.Gray, Offset(left, top), Offset(left, bottom), 2f)
            drawLine(Color.Gray, Offset(left, bottom), Offset(right, bottom), 2f)
            drawText(misura, dMax.fmt(2), Offset(2f, top - 6.sp.toPx()), TextStyle(fontSize = 11.sp, color = Color.Gray))
            drawText(misura, dMin.fmt(2), Offset(2f, bottom - 7.sp.toPx()), TextStyle(fontSize = 11.sp, color = Color.Gray))
            val punti = listOf(0 to dMax) + ris.passi.map { it.n to it.dOut }
            val step = if (punti.size > 1) w / (punti.size - 1) else w
            var prev: Offset? = null
            punti.forEachIndexed { i, (nn, d) ->
                val pt = Offset(left + step * i, y(d))
                prev?.let { drawLine(Verde, it, pt, 4f) }
                drawCircle(Verde, 7f, pt)
                val tn = misura.measure(nn.toString(), TextStyle(fontSize = 12.sp))
                drawText(misura, nn.toString(), Offset(pt.x - tn.size.width / 2, bottom + 4f), TextStyle(fontSize = 12.sp, color = Color.DarkGray))
                prev = pt
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("Una curva regolare, senza gradini, indica una sequenza ben bilanciata.", color = Color.Gray, fontSize = 13.sp)
    }
}

// ------------------------------------------------------------------ SALVATE
@Composable
fun SchermataSalvate(
    lista: List<Sequenza>, onApri: (Sequenza) -> Unit, onElimina: (Sequenza) -> Unit,
    onCondividi: (Sequenza) -> Unit, onImportaTesto: (String) -> Unit
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
                                Text(
                                    "${if (s.tipo == "bagno") "Bagno" else "Secco"} · ${s.dVergella.fmt(2)} → ${s.dFinito.fmt(2)} mm · ${s.riduzioni.size} passi · ${s.velocitaFinale.fmt(1)} m/s" +
                                    (if (s.manuale) " · manuale" else ""),
                                    color = Color.Gray, fontSize = 13.sp
                                )
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
            "La resistenza in uscita è stimata con la formula empirica:\n\n    Rm = Rm vergella + C × ln(A₀ / A)\n\n" +
            "dove A₀ è la sezione in entrata e A quella del filo dopo il passo. " +
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
        Text(
            "Densità acciaio: ${DENSITA_ACCIAIO.fmt(2)} kg/dm³\n" +
            "Secco – riduzione primo passo (curva automatica): ~${RID_PRIMO_PASSO.fmt(0)} %\n" +
            "Secco – riduzione ultimo passo (curva automatica): ~${RID_ULTIMO_PASSO.fmt(0)} %\n" +
            "Avviso fuori limite: sotto ${RID_MIN.fmt(0)} % o sopra ${RID_MAX.fmt(0)} %",
            fontSize = 14.sp, color = Color.DarkGray
        )
        Text("Calcolo Riduzione Trafila – v1.1", color = Color.Gray, fontSize = 12.sp)
    }
}
