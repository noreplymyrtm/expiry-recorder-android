package com.example.storescanner

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var etStoreCode: EditText
    private lateinit var etManualBarcode: EditText
    private lateinit var btnDatePick: Button
    private lateinit var etDateManual: EditText
    private lateinit var btnAddRow: Button
    private lateinit var btnSaveCsv: Button
    private lateinit var btnShareCsv: Button
    private lateinit var tvList: TextView
    private lateinit var tvStatus: TextView

    private val scans = mutableListOf<ScanRow>()
    private var currentPickedDate: String? = null

    data class ScanRow(val storeCode: String, val barcode: String, val date: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etStoreCode = findViewById(R.id.etStoreCode)
        barcodeView = findViewById(R.id.barcodeScanner)
        etManualBarcode = findViewById(R.id.etManualBarcode)
        btnDatePick = findViewById(R.id.btnDatePick)
        etDateManual = findViewById(R.id.etDateManual)
        btnAddRow = findViewById(R.id.btnAddRow)
        btnSaveCsv = findViewById(R.id.btnSaveCsv)
        btnShareCsv = findViewById(R.id.btnShareCsv)
        tvList = findViewById(R.id.tvList)
        tvStatus = findViewById(R.id.tvStatus)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1001)
        } else {
            startScanner()
        }

        btnDatePick.setOnClickListener {
            val c = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                val mm = (m + 1).toString().padStart(2, '0')
                val dd = d.toString().padStart(2, '0')
                currentPickedDate = "$y-$mm-$dd"
                etDateManual.setText(currentPickedDate)
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }

        btnAddRow.setOnClickListener {
            val storeCode = etStoreCode.text.toString().trim()
            if (storeCode.isEmpty()) {
                Toast.makeText(this, "Enter Store Code first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val barcode = etManualBarcode.text.toString().trim()
            if (barcode.isEmpty()) {
                Toast.makeText(this, "No manual barcode given. Waiting for camera scans.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val date = etDateManual.text.toString().ifEmpty { currentDateString() }
            addScanRow(storeCode, barcode, date)
            etManualBarcode.setText("")
        }

        btnSaveCsv.setOnClickListener { saveCsvToFile() }
        btnShareCsv.setOnClickListener { shareLatestCsv() }
    }

    private fun startScanner() {
        barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult?) {
                result ?: return
                val code = result.text ?: return
                runOnUiThread {
                    tvStatus.text = "Scanned: $code"
                    val storeCode = etStoreCode.text.toString().trim()
                    if (storeCode.isNotEmpty()) {
                        val date = etDateManual.text.toString().ifEmpty { currentDateString() }
                        addScanRow(storeCode, code, date)
                    } else {
                        etManualBarcode.setText(code)
                        Toast.makeText(this@MainActivity, "Barcode detected. Enter store code then Add Scan.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
        })
    }

    private fun addScanRow(store: String, barcode: String, date: String) {
        val r = ScanRow(store, barcode, date)
        scans.add(r)
        refreshList()
    }

    private fun refreshList() {
        val sb = StringBuilder()
        scans.forEachIndexed { idx, r ->
            sb.append("${idx+1}. ${r.storeCode}, ${r.barcode}, ${r.date}\n")
        }
        tvList.text = sb.toString()
        tvStatus.text = "Total rows: ${scans.size}"
    }

    private fun currentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    private fun saveCsvToFile() {
        if (scans.isEmpty()) {
            Toast.makeText(this, "No scans to save", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val csv = StringBuilder()
            csv.append("StoreCode,Barcode,Date\n")
            scans.forEach { csv.append("${it.storeCode},${it.barcode},${it.date}\n") }

            val dir = getExternalFilesDir(null)
            val fileName = "scans_${System.currentTimeMillis()}.csv"
            val outFile = File(dir, fileName)
            val fw = FileWriter(outFile)
            fw.write(csv.toString())
            fw.close()
            tvStatus.text = "Saved: ${outFile.absolutePath}"
            Toast.makeText(this, "CSV saved: ${outFile.name}", Toast.LENGTH_SHORT).show()

        } catch (ex: Exception) {
            Toast.makeText(this, "Error saving CSV: ${ex.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun shareLatestCsv() {
        val dir = getExternalFilesDir(null) ?: run {
            Toast.makeText(this, "Storage not available", Toast.LENGTH_SHORT).show()
            return
        }
        val files = dir.listFiles { f -> f.extension == "csv" } ?: arrayOf()
        if (files.isEmpty()) {
            Toast.makeText(this, "No CSV found. Save one first.", Toast.LENGTH_SHORT).show()
            return
        }
        val latest = files.maxByOrNull { it.lastModified() }!!
        val uri: Uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.fileprovider", latest)

        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "text/csv"
        intent.putExtra(Intent.EXTRA_STREAM, uri)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Share CSV"))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner()
            } else {
                Toast.makeText(this, "Camera permission required for scanning", Toast.LENGTH_LONG).show()
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    override fun onResume() {
        super.onResume()
        barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }
}