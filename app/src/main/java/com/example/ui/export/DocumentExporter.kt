package com.example.ui.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.data.FinancialMonth
import com.example.data.TransactionEntry
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object DocumentExporter {

    /**
     * Share text/csv format representing Excel spreadsheet
     */
    suspend fun exportToExcel(
        context: Context,
        month: FinancialMonth,
        transactions: List<TransactionEntry>,
        currencySymbol: String
    ) = withContext(Dispatchers.IO) {
        try {
            val fileName = "SmartMoney_Spreadsheet_${month.id}.csv"
            val cacheFile = File(context.cacheDir, fileName)
            val writer = FileOutputStream(cacheFile).bufferedWriter()

            // CSV Header
            writer.write("Smart Money Manager - ${month.displayLabel} (Reference Spreadsheet)\n")
            writer.write("Closing Balance Sheet Status,,,,,,\n")
            writer.write("\n")

            // Summary Info
            val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
            val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val netSavings = totalIncome - totalExpense

            writer.write("Summary Calculations,,,,,,\n")
            writer.write("Total Income,${currencySymbol} ${String.format("%.2f", totalIncome)},,,,,\n")
            writer.write("Total Expense,${currencySymbol} ${String.format("%.2f", totalExpense)},,,,,\n")
            writer.write("Net Savings,${currencySymbol} ${String.format("%.2f", netSavings)},,,,,\n")
            writer.write("\n")

            // Spreadsheet columns (Grid Layout structure)
            writer.write("Date,Day of Month,Type,Category,Notes,Amount ($currencySymbol),Daily Balance\n")

            // Entries
            val dayGroups = transactions.groupBy { it.day }
            var runningBalance = 0.0

            // Fill row by row sorted by date day
            for (day in 1..31) {
                val dayTxs = dayGroups[day] ?: emptyList()
                val dayIncome = dayTxs.filter { it.type == "INCOME" }.sumOf { it.amount }
                val dayExpense = dayTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
                val dayBalance = dayIncome - dayExpense
                runningBalance += dayBalance

                if (dayTxs.isEmpty()) {
                    // Just show calculated empty row for formatting
                    writer.write("${month.id}-$day,Day $day,EMPTY,-,-,0.00,${String.format("%.2f", runningBalance)}\n")
                } else {
                    for (index in dayTxs.indices) {
                        val tx = dayTxs[index]
                        val formattedAmount = String.format("%.2f", tx.amount)
                        val notesSafe = tx.notes.replace("\"", "\"\"")
                        val runBalCol = if (index == dayTxs.size - 1) String.format("%.2f", runningBalance) else ""
                        writer.write("${month.id}-$day,Day $day,${tx.type},\"${tx.category}\",\"$notesSafe\",$formattedAmount,$runBalCol\n")
                    }
                }
            }

            writer.flush()
            writer.close()

            // Save a direct local copy in the device public Downloads folder
            saveToDownloads(context, cacheFile, "text/csv", fileName)

            withContext(Dispatchers.Main) {
                shareFile(context, cacheFile, "text/csv", "Share Financial Spreadsheet")
            }

        } catch (e: Throwable) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Spreadsheet export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Export to styled PDF report via Native PdfDocument
     */
    suspend fun exportToPdf(
        context: Context,
        month: FinancialMonth,
        transactions: List<TransactionEntry>,
        currencySymbol: String
    ) = withContext(Dispatchers.IO) {
        val pdfDocument = PdfDocument()
        try {
            val pageWidth = 595 // A4 standard size: 595 x 842 points
            val pageHeight = 842
            var pageNumber = 1

            // Filter for pages
            val sortedTxs = transactions.sortedWith(compareBy({ it.day }, { it.id }))
            val totalIncome = sortedTxs.filter { it.type == "INCOME" }.sumOf { it.amount }
            val totalExpense = sortedTxs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
            val netSavings = totalIncome - totalExpense

            val paint = Paint()

            // A helper to start a page
            var currentPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            var currentPage = pdfDocument.startPage(currentPageInfo)
            var canvas: Canvas = currentPage.canvas

            // Page margin limits
            var yPos = 40f

            // DRAW HEADER SECTION IN GREEN/BLUE SLATE
            fun drawDocHeader() {
                // Header Background
                paint.color = Color.parseColor("#1B5E20") // Rich Slate Green
                canvas.drawRect(30f, 30f, pageWidth - 30f, 95f, paint)

                // Title Text
                paint.color = Color.WHITE
                paint.textSize = 18f
                paint.isFakeBoldText = true
                paint.isAntiAlias = true
                canvas.drawText("SMART MONEY MANAGER STATEMENT", 45f, 54f, paint)

                // Subtitle month
                paint.textSize = 11f
                paint.isFakeBoldText = false
                canvas.drawText("Statement Period: ${month.displayLabel}", 45f, 72f, paint)

                val generatedOn = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                paint.textSize = 9f
                canvas.drawText("Generated on: $generatedOn", 45f, 86f, paint)

                // Draw Summary Box
                paint.color = Color.parseColor("#F1F8E9") // Very Light Green Background
                canvas.drawRect(30f, 105f, pageWidth - 30f, 175f, paint)

                paint.color = Color.parseColor("#33691E") // Dark green border
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = 1f
                canvas.drawRect(30f, 105f, pageWidth - 30f, 175f, paint)

                paint.style = Paint.Style.FILL
                paint.textSize = 11f
                paint.color = Color.BLACK
                paint.isFakeBoldText = true
                canvas.drawText("Financial Overview", 45f, 124f, paint)

                paint.textSize = 10f
                paint.isFakeBoldText = false
                canvas.drawText("Total Income:  $currencySymbol ${String.format("%.2f", totalIncome)}", 45f, 142f, paint)
                canvas.drawText("Total Expenses: $currencySymbol ${String.format("%.2f", totalExpense)}", 45f, 158f, paint)

                // Balance with color code
                if (netSavings >= 0) {
                    paint.color = Color.parseColor("#2E7D32") // Green
                    paint.isFakeBoldText = true
                    canvas.drawText("Net Savings:     $currencySymbol ${String.format("%.2f", netSavings)}", 280f, 142f, paint)
                } else {
                    paint.color = Color.RED
                    paint.isFakeBoldText = true
                    canvas.drawText("Net Deficit:     $currencySymbol ${String.format("%.2f", netSavings)}", 280f, 142f, paint)
                }

                // Add indicator for budget limit highlights
                paint.color = Color.parseColor("#555555")
                paint.isFakeBoldText = false
                canvas.drawText("Monthly Budget: $currencySymbol ${String.format("%.2f", month.budgetLimit)}", 280f, 158f, paint)

                // List Table Headers
                yPos = 205f

                // Header Row Background
                paint.color = Color.parseColor("#E0E0E0")
                canvas.drawRect(30f, yPos - 15f, pageWidth - 30f, yPos + 8f, paint)

                // Column names
                paint.color = Color.BLACK
                paint.isFakeBoldText = true
                paint.textSize = 10f
                canvas.drawText("Day", 35f, yPos, paint)
                canvas.drawText("Type", 75f, yPos, paint)
                canvas.drawText("Category", 130f, yPos, paint)
                canvas.drawText("Notes", 220f, yPos, paint)
                canvas.drawText("Amount", pageWidth - 110f, yPos, paint)

                paint.isFakeBoldText = false
                yPos += 20f
            }

            drawDocHeader()

            // Draw each transaction row
            for (idx in sortedTxs.indices) {
                val tx = sortedTxs[idx]

                // Page break detection
                if (yPos > pageHeight - 60f) {
                    // Finish page
                    pdfDocument.finishPage(currentPage)

                    // Create new page
                    pageNumber++
                    currentPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    currentPage = pdfDocument.startPage(currentPageInfo)
                    canvas = currentPage.canvas

                    // Top header for next sheets
                    paint.color = Color.parseColor("#1B5E20")
                    canvas.drawRect(30f, 30f, pageWidth - 30f, 60f, paint)
                    paint.color = Color.WHITE
                    paint.textSize = 12f
                    paint.isFakeBoldText = true
                    canvas.drawText("SMART MONEY MANAGER STATEMENT - Continued (Page $pageNumber)", 45f, 48f, paint)

                    // Draw Table Headers
                    yPos = 90f
                    paint.color = Color.parseColor("#E0E0E0")
                    canvas.drawRect(30f, yPos - 15f, pageWidth - 30f, yPos + 8f, paint)
                    paint.color = Color.BLACK
                    paint.textSize = 10f
                    canvas.drawText("Day", 35f, yPos, paint)
                    canvas.drawText("Type", 75f, yPos, paint)
                    canvas.drawText("Category", 130f, yPos, paint)
                    canvas.drawText("Notes", 220f, yPos, paint)
                    canvas.drawText("Amount", pageWidth - 110f, yPos, paint)

                    paint.isFakeBoldText = false
                    yPos += 20f
                }

                // Row zebra striping
                if (idx % 2 == 0) {
                    paint.color = Color.parseColor("#FAFAFA")
                    canvas.drawRect(30f, yPos - 13f, pageWidth - 30f, yPos + 5f, paint)
                }

                // Draw values
                paint.color = Color.BLACK
                paint.textSize = 9.5f
                canvas.drawText("Day ${tx.day}", 35f, yPos, paint)

                // Type with color
                if (tx.type == "INCOME") {
                    paint.color = Color.parseColor("#2E7D32")
                    canvas.drawText("INCOME", 75f, yPos, paint)
                } else {
                    paint.color = Color.parseColor("#C62828")
                    canvas.drawText("EXPENSE", 75f, yPos, paint)
                }

                paint.color = Color.BLACK
                canvas.drawText(tx.category, 130f, yPos, paint)

                // Handle notes text truncation
                val notePaint = Paint(paint)
                val notesText = if (tx.notes.length > 32) tx.notes.substring(0, 29) + "..." else tx.notes
                canvas.drawText(notesText, 220f, yPos, notePaint)

                // Right aligned amount
                val amountStr = "$currencySymbol ${String.format("%.2f", tx.amount)}"
                paint.color = if (tx.type == "INCOME") Color.parseColor("#2E7D32") else Color.parseColor("#C62828")
                canvas.drawText(amountStr, pageWidth - 110f, yPos, paint)

                paint.color = Color.BLACK
                yPos += 18f
            }

            // Draw footer page numbers on final page
            paint.color = Color.GRAY
            paint.textSize = 8f
            canvas.drawText("Page $pageNumber", pageWidth / 2f - 15f, pageHeight - 30f, paint)

            pdfDocument.finishPage(currentPage)

            // Save file and open share sheet
            val fileName = "SmartMoney_Statement_${month.id}.pdf"
            val cacheFile = File(context.cacheDir, fileName)
            FileOutputStream(cacheFile).use { stream ->
                pdfDocument.writeTo(stream)
            }

            // Save a direct local copy in the device public Downloads folder
            saveToDownloads(context, cacheFile, "application/pdf", fileName)

            withContext(Dispatchers.Main) {
                shareFile(context, cacheFile, "application/pdf", "Share PDF Statement")
            }

        } catch (e: Throwable) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "PDF Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } finally {
            try {
                pdfDocument.close()
            } catch (ignored: Throwable) {}
        }
    }

    /**
     * Modern Scoped Storage (MediaStore) saving block for AndroidQ+ (API 29+),
     * and fallback standard Environment downloads directory write for legacy devices.
     */
    suspend fun saveToDownloads(context: Context, file: File, mimeType: String, finalName: String) = withContext(Dispatchers.IO) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                // Android 10+ (using Scoped Storage / MediaStore API)
                val resolver = context.contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, finalName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/SmartMoney")
                }
                // Avoid direct reference to android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI
                // to prevent ClassNotFoundException / NoClassDefFoundError on older legacy platforms.
                val uri = resolver.insert(Uri.parse("content://media/external/downloads"), contentValues)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { outputStream ->
                        java.io.FileInputStream(file).use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Statement saved to Downloads/SmartMoney/$finalName", Toast.LENGTH_LONG).show()
                    }
                } else {
                    throw java.io.IOException("Failed to create MediaStore Downloads row entry")
                }
            } else {
                // Android 9 and below (Legacy File Write System - requires WRITE_EXTERNAL_STORAGE permission)
                val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                val destFolder = File(downloadsDir, "SmartMoney")
                if (!destFolder.exists()) {
                    destFolder.mkdirs()
                }
                val destFile = File(destFolder, finalName)
                java.io.FileInputStream(file).use { inputStream ->
                    java.io.FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Statement saved to ${destFile.absolutePath}", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Failed saving persistent local file copy: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val authority = "${context.packageName}.fileprovider"
        try {
            val fileUri: Uri = FileProvider.getUriForFile(context, authority, file)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, chooserTitle).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "No sharing app found on this device", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
