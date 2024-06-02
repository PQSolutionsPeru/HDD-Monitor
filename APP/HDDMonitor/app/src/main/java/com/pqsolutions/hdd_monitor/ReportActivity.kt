package com.pqsolutions.hdd_monitor

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.firestore.FirebaseFirestore
import com.pqsolutions.hdd_monitor.databinding.ActivityReportBinding

class ReportActivity : AppCompatActivity() {
    private lateinit var binding: ActivityReportBinding
    private lateinit var firestore: FirebaseFirestore
    private lateinit var reportAdapter: ReportAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportBinding.inflate(layoutInflater)
        setContentView(binding.root)

        firestore = FirebaseFirestore.getInstance()

        // Set up the report type spinner
        ArrayAdapter.createFromResource(
            this,
            R.array.report_types,
            android.R.layout.simple_spinner_item
        ).also { adapter ->
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding.spinnerReportType.adapter = adapter
        }

        reportAdapter = ReportAdapter(emptyList())
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = reportAdapter

        binding.buttonGenerateReport.setOnClickListener {
            binding.spinnerReportType.selectedItem.toString()
            val startDate = binding.editTextStartDate.text.toString()
            val endDate = binding.editTextEndDate.text.toString()

            if (startDate.isNotEmpty() && endDate.isNotEmpty()) {
                generateReport(startDate, endDate)
            } else {
                Toast.makeText(this, "Please enter start and end dates", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun generateReport(startDate: String, endDate: String) {
        firestore.collection("hdd-monitor/accounts/clients/client_1/panels/panel_1/panel_events_log")
            .whereGreaterThanOrEqualTo("date_time", startDate)
            .whereLessThanOrEqualTo("date_time", endDate)
            .get()
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val documents = task.result?.documents?.mapNotNull { it.toObject(Report::class.java) }
                    reportAdapter.updateReports(documents ?: emptyList())
                } else {
                    Toast.makeText(this, "Failed to generate report", Toast.LENGTH_SHORT).show()
                }
            }
    }
}
