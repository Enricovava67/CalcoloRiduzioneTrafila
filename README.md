# Calcolo Riduzione Trafila

App Android per il calcolo della sequenza di filiere in trafilatura.

**Input:** diametro vergella, diametro finito, numero di passi, velocità finale (m/s), resistenza Rm vergella (facoltativa).

**Output:** per ogni passo diametro ingresso, diametro filiera, riduzione % di sezione, velocità del filo, Rm stimata;
riduzione totale, produttività in kg/h (densità acciaio 7,85), scostamento dal diametro finito.

**Modalità automatica:** riduzioni decrescenti dal primo (~34 %) all'ultimo passo (~19 %), scalate per arrivare esattamente al finito.
**Modalità manuale:** la colonna "Rid. %" è modificabile passo per passo; i diametri si ricalcolano al volo; avviso giallo se un passo è sotto 18 % o sopra 36 %.

Le sequenze si salvano con un nome sul telefono e si condividono come file `.trafila` (WhatsApp, mail…); aprendo il file su un altro telefono con l'app installata la sequenza viene importata.

---

## Come ottenere l'APK con GitHub (senza installare nulla sul PC)

1. Vai su https://github.com e accedi.
2. In alto a destra clicca **+** → **New repository**.
   - Repository name: `CalcoloRiduzioneTrafila`
   - Lascia **Public** oppure scegli **Private** (funziona in entrambi i casi).
   - NON spuntare "Add a README".
   - Clicca **Create repository**.
3. Nella pagina del nuovo repository clicca sul link **uploading an existing file**.
4. Apri la cartella `CalcoloRiduzioneTrafila` estratta dallo zip, seleziona **tutto il contenuto** (compresa la cartella nascosta `.github`) e trascinalo nella pagina di GitHub.
   - Se Windows non mostra la cartella `.github`: in Esplora file → Visualizza → spunta "Elementi nascosti".
5. In basso clicca **Commit changes**.
6. Clicca sulla scheda **Actions** in alto. Vedrai il workflow "Compila APK" in esecuzione (pallino giallo). Attendi 5–8 minuti finché diventa verde ✓.
   - Se GitHub chiede di abilitare le Actions, clicca il pulsante verde "I understand my workflows, go ahead and enable them" e poi rilancia da **Actions → Compila APK → Run workflow**.
7. Clicca sull'esecuzione verde, scorri in basso alla sezione **Artifacts** e scarica **CalcoloRiduzioneTrafila-APK** (è uno zip che contiene `CalcoloRiduzioneTrafila.apk`).
8. Manda l'APK al telefono (WhatsApp a te stesso, mail, cavo USB). Sul telefono toccalo e consenti l'installazione da origini sconosciute quando richiesto.

Ogni volta che modificherai un file del progetto su GitHub, l'APK verrà ricompilato automaticamente.

## Struttura del progetto

```
app/src/main/java/it/vavassori/trafila/
  Calc.kt          ← formule di calcolo (riduzioni, velocità, kg/h, incrudimento)
  Storage.kt       ← salvataggio, condivisione e importazione delle sequenze
  MainActivity.kt  ← tutte le schermate (Jetpack Compose)
.github/workflows/build.yml  ← compilazione automatica dell'APK
```

## Formule

- Riduzione di sezione: `r = 1 − (d_out / d_in)²`
- Diametro filiera: `d_out = d_in · √(1 − r)`
- Velocità al passo i (conservazione del volume): `v_i = v_finale · (d_finale / d_i)²`
- Produttività: `kg/h = (π d²/4)[mm²] · v[m/s] · 3600 · 7,85 / 1000`
- Rm stimata: `Rm = Rm_vergella + C · ln(A₀ / A)` con C tarabile nelle Impostazioni
