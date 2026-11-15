package it.vavassori.trafila

import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.sqrt

/** Densità acciaio in kg/dm³ (fissa). */
const val DENSITA_ACCIAIO = 7.85

/** Limiti di riduzione per passo (percentuale di sezione). */
const val RID_MIN = 18.0
const val RID_MAX = 36.0

/** Riduzione iniziale e finale di riferimento per la curva automatica. */
const val RID_PRIMO_PASSO = 34.0
const val RID_ULTIMO_PASSO = 19.0

/** Coefficiente di incrudimento di default (N/mm² per unità di deformazione vera). Da tarare. */
const val COEFF_INCRUDIMENTO_DEFAULT = 300.0

fun areaMm2(d: Double): Double = PI * d * d / 4.0

/** Riduzione % di sezione tra due diametri. */
fun riduzionePct(dIn: Double, dOut: Double): Double = (1.0 - (dOut * dOut) / (dIn * dIn)) * 100.0

/** Diametro in uscita dato il diametro in entrata e la riduzione %. */
fun diametroUscita(dIn: Double, ridPct: Double): Double = dIn * sqrt(1.0 - ridPct / 100.0)

data class Passo(
    val n: Int,
    val dIn: Double,
    val dOut: Double,
    val ridPct: Double,
    val velocita: Double,      // m/s
    val resistenza: Double?    // N/mm² stimata in uscita dal passo (null se non calcolata)
) {
    val fuoriLimite: Boolean get() = ridPct < RID_MIN || ridPct > RID_MAX
}

data class Risultato(
    val passi: List<Passo>,
    val riduzioneTotalePct: Double,
    val produttivitaKgH: Double,
    val diametroFinaleOttenuto: Double,
    val scostamentoMm: Double,
    val resistenzaFinale: Double?
)

/**
 * Curva automatica: riduzioni decrescenti linearmente dal primo all'ultimo passo,
 * poi scalate (bisezione) in modo che il diametro finale coincida esattamente con quello richiesto.
 */
fun riduzioniAutomatiche(dVergella: Double, dFinito: Double, nPassi: Int): List<Double> {
    require(nPassi >= 1)
    if (nPassi == 1) return listOf(riduzionePct(dVergella, dFinito))
    val forma = (0 until nPassi).map { i ->
        RID_PRIMO_PASSO + (RID_ULTIMO_PASSO - RID_PRIMO_PASSO) * i / (nPassi - 1)
    }
    fun finale(scala: Double): Double {
        var d = dVergella
        for (r in forma) d = diametroUscita(d, (r * scala).coerceIn(0.0, 99.0))
        return d
    }
    var lo = 0.0
    var hi = 2.5
    repeat(80) {
        val m = (lo + hi) / 2
        if (finale(m) > dFinito) lo = m else hi = m
    }
    val scala = (lo + hi) / 2
    return forma.map { it * scala }
}

/**
 * Calcola la sequenza completa data la lista di riduzioni (in %), una per passo.
 * @param rmVergella resistenza a trazione della vergella in N/mm² (opzionale)
 * @param coeffIncrudimento coefficiente C nella formula Rm = Rm0 + C * ln(A0/A)
 */
fun calcola(
    dVergella: Double,
    dFinito: Double,
    riduzioni: List<Double>,
    velocitaFinale: Double,
    rmVergella: Double? = null,
    coeffIncrudimento: Double = COEFF_INCRUDIMENTO_DEFAULT
): Risultato {
    // prima passata: diametri
    val diametri = ArrayList<Double>()
    var d = dVergella
    for (r in riduzioni) {
        val dOut = diametroUscita(d, r.coerceIn(0.0, 99.0))
        diametri.add(dOut)
        d = dOut
    }
    val dFinaleOttenuto = diametri.lastOrNull() ?: dVergella
    val a0 = areaMm2(dVergella)

    // seconda passata: velocità (conservazione del volume) e resistenza
    val passi = ArrayList<Passo>()
    var dIn = dVergella
    riduzioni.forEachIndexed { i, r ->
        val dOut = diametri[i]
        // la velocità su ogni passo è riferita al diametro finale reale ottenuto
        val v = velocitaFinale * (dFinaleOttenuto * dFinaleOttenuto) / (dOut * dOut)
        val rm = rmVergella?.let { it + coeffIncrudimento * ln(a0 / areaMm2(dOut)) }
        passi.add(Passo(i + 1, dIn, dOut, r, v, rm))
        dIn = dOut
    }

    val ridTot = riduzionePct(dVergella, dFinaleOttenuto)
    // kg/h = area[mm²] * v[m/s] * 3600 * densità[kg/dm³] / 1000
    val kgH = areaMm2(dFinaleOttenuto) * velocitaFinale * 3600.0 * DENSITA_ACCIAIO / 1000.0

    return Risultato(
        passi = passi,
        riduzioneTotalePct = ridTot,
        produttivitaKgH = kgH,
        diametroFinaleOttenuto = dFinaleOttenuto,
        scostamentoMm = dFinaleOttenuto - dFinito,
        resistenzaFinale = passi.lastOrNull()?.resistenza
    )
}

// ------------------------------------------------------------------ TRAFILATURA A BAGNO

/** Come far tornare il diametro finito quando la riduzione fissa non chiude esatta. */
enum class Aggiustamento { TUTTI, PRIMO }

data class RisultatoBagno(val riduzioni: List<Double>, val passiTeorici: Double)

/**
 * Trafilatura a bagno: riduzione % uguale su tutti i passi.
 * Calcola il numero di passi necessario (arrotondato per eccesso) e poi:
 *  - TUTTI: ritocca leggermente la percentuale su tutti i passi per chiudere esatti al finito
 *  - PRIMO: lascia la percentuale nominale su tutti i passi tranne il primo, che assorbe la differenza
 */
fun riduzioniBagno(dIn: Double, dOut: Double, ridPct: Double, agg: Aggiustamento): RisultatoBagno {
    val lnTot = ln(areaMm2(dIn) / areaMm2(dOut))          // deformazione totale
    val perPasso = -ln(1.0 - ridPct / 100.0)              // deformazione per passo
    val nTeorico = lnTot / perPasso
    val n = kotlin.math.ceil(nTeorico - 1e-9).toInt().coerceAtLeast(1)
    if (n == 1) return RisultatoBagno(listOf(riduzionePct(dIn, dOut)), nTeorico)
    val rid = when (agg) {
        Aggiustamento.TUTTI -> {
            val r = (1.0 - kotlin.math.exp(-lnTot / n)) * 100.0
            List(n) { r }
        }
        Aggiustamento.PRIMO -> {
            // dOut = d1 * sqrt(1-r)^(n-1)  =>  d1 = dOut / (1-r)^((n-1)/2)
            val d1 = dOut / Math.pow(1.0 - ridPct / 100.0, (n - 1) / 2.0)
            listOf(riduzionePct(dIn, d1)) + List(n - 1) { ridPct }
        }
    }
    return RisultatoBagno(rid, nTeorico)
}

/** Converte una stringa con virgola o punto in Double. */
fun String.toDoubleIt(): Double? = this.trim().replace(',', '.').toDoubleOrNull()

/** Formatta un numero con virgola decimale. */
fun Double.fmt(dec: Int = 2): String = String.format(java.util.Locale.ITALY, "%.${dec}f", this)
