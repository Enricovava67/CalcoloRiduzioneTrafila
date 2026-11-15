package it.vavassori.trafila

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Un calcolo salvato con nome. */
data class Sequenza(
    val nome: String,
    val dVergella: Double,
    val dFinito: Double,
    val velocitaFinale: Double,
    val rmVergella: Double?,
    val manuale: Boolean,
    val riduzioni: List<Double>,
    val tipo: String = "secco",          // "secco" oppure "bagno"
    val ridFissa: Double? = null,        // solo bagno: riduzione nominale per passo
    val aggiustaPrimo: Boolean = false,  // solo bagno: true = aggiusta solo il primo passo
    val data: Long = System.currentTimeMillis()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("nome", nome)
        put("dVergella", dVergella)
        put("dFinito", dFinito)
        put("velocitaFinale", velocitaFinale)
        put("rmVergella", rmVergella ?: JSONObject.NULL)
        put("manuale", manuale)
        put("riduzioni", JSONArray(riduzioni))
        put("tipo", tipo)
        put("ridFissa", ridFissa ?: JSONObject.NULL)
        put("aggiustaPrimo", aggiustaPrimo)
        put("data", data)
    }

    companion object {
        fun fromJson(o: JSONObject): Sequenza {
            val arr = o.getJSONArray("riduzioni")
            val rid = (0 until arr.length()).map { arr.getDouble(it) }
            return Sequenza(
                nome = o.getString("nome"),
                dVergella = o.getDouble("dVergella"),
                dFinito = o.getDouble("dFinito"),
                velocitaFinale = o.getDouble("velocitaFinale"),
                rmVergella = if (o.isNull("rmVergella")) null else o.getDouble("rmVergella"),
                manuale = o.optBoolean("manuale", true),
                riduzioni = rid,
                tipo = o.optString("tipo", "secco"),
                ridFissa = if (o.isNull("ridFissa")) null else o.optDouble("ridFissa"),
                aggiustaPrimo = o.optBoolean("aggiustaPrimo", false),
                data = o.optLong("data", System.currentTimeMillis())
            )
        }
    }
}

object Storage {
    private const val PREFS = "trafila_prefs"
    private const val KEY_LISTA = "sequenze"
    private const val KEY_COEFF = "coeff_incrudimento"

    fun carica(ctx: Context): List<Sequenza> {
        val s = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LISTA, "[]") ?: "[]"
        return try {
            val arr = JSONArray(s)
            (0 until arr.length()).map { Sequenza.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun salvaTutte(ctx: Context, lista: List<Sequenza>) {
        val arr = JSONArray()
        lista.forEach { arr.put(it.toJson()) }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LISTA, arr.toString()).apply()
    }

    /** Salva o sostituisce (stesso nome). */
    fun salva(ctx: Context, seq: Sequenza): List<Sequenza> {
        val lista = carica(ctx).filter { it.nome != seq.nome } + seq
        salvaTutte(ctx, lista.sortedBy { it.nome.lowercase() })
        return carica(ctx)
    }

    fun elimina(ctx: Context, nome: String): List<Sequenza> {
        salvaTutte(ctx, carica(ctx).filter { it.nome != nome })
        return carica(ctx)
    }

    fun coeffIncrudimento(ctx: Context): Double =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_COEFF, COEFF_INCRUDIMENTO_DEFAULT.toFloat()).toDouble()

    fun salvaCoeff(ctx: Context, c: Double) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putFloat(KEY_COEFF, c.toFloat()).apply()
    }

    /** Condivide una sequenza come file .trafila (JSON) tramite il menu di condivisione Android. */
    fun condividi(ctx: Context, seq: Sequenza) {
        val dir = File(ctx.cacheDir, "export").apply { mkdirs() }
        val nomeFile = seq.nome.replace(Regex("[^A-Za-z0-9_\\-]"), "_") + ".trafila"
        val f = File(dir, nomeFile)
        f.writeText(seq.toJson().toString(2))
        val uri: Uri = FileProvider.getUriForFile(ctx, "it.vavassori.trafila.fileprovider", f)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sequenza trafila: ${seq.nome}")
            putExtra(Intent.EXTRA_TEXT, "Sequenza di trafilatura \"${seq.nome}\" – apri con Calcolo Riduzione Trafila")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(intent, "Condividi sequenza"))
    }

    /** Legge una sequenza da un intent di apertura file (ACTION_VIEW / ACTION_SEND). */
    fun importaDaIntent(ctx: Context, intent: Intent?): Sequenza? {
        if (intent == null) return null
        @Suppress("DEPRECATION")
        val uri: Uri = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data
            Intent.ACTION_SEND -> intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            else -> null
        } ?: return null
        return try {
            val testo = ctx.contentResolver.openInputStream(uri)!!.bufferedReader().readText()
            importaDaTesto(testo)
        } catch (e: Exception) {
            null
        }
    }

    fun importaDaTesto(testo: String): Sequenza? = try {
        Sequenza.fromJson(JSONObject(testo))
    } catch (e: Exception) {
        null
    }
}
