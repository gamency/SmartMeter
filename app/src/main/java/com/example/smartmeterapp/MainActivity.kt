package com.example.smartmeterapp

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    // ====== 请修改为你的笔记本 IP 地址 ======
    private val BASE_URL = "http://192.168.10.12:5000"

    private lateinit var cardCrossValue: TextView
    private lateinit var cardCostValue: TextView
    private lateinit var cardDiffValue: TextView
    private lateinit var cardDiffLabel: TextView
    private lateinit var loadingText: TextView
    private lateinit var errorText: TextView
    private lateinit var refreshBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cardCrossValue = findViewById(R.id.card_cross_value)
        cardCostValue = findViewById(R.id.card_cost_value)
        cardDiffValue = findViewById(R.id.card_diff_value)
        cardDiffLabel = findViewById(R.id.card_diff_label)
        loadingText = findViewById(R.id.loading_text)
        errorText = findViewById(R.id.error_text)
        refreshBtn = findViewById(R.id.refresh_btn)

        refreshBtn.setOnClickListener {
            fetchDashboardData()
        }

        fetchDashboardData()
    }

    private fun fetchDashboardData() {
        loadingText.visibility = View.VISIBLE
        errorText.visibility = View.GONE
        refreshBtn.isEnabled = false

        Thread {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(5, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder()
                    .url("$BASE_URL/api/dashboard")
                    .get()
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val jsonData = response.body?.string()
                    val json = JSONObject(jsonData)

                    val totalCross = json.optDouble("total_cross", 0.0)
                    val totalCost = json.optDouble("total_cost", 0.0)
                    val diff = json.optDouble("diff", 0.0)

                    runOnUiThread {
                        cardCrossValue.text = String.format("%.2f", totalCross)
                        cardCostValue.text = String.format("%.2f", totalCost)
                        cardDiffValue.text = String.format("%.2f", diff)

                        if (diff > 0) {
                            cardDiffValue.setTextColor(0xFF22C55E.toInt())
                            cardDiffLabel.text = "盈 ${String.format("%.2f", diff)} 度"
                        } else if (diff < 0) {
                            cardDiffValue.setTextColor(0xFFEF4444.toInt())
                            cardDiffLabel.text = "亏 ${String.format("%.2f", kotlin.math.abs(diff))} 度"
                        } else {
                            cardDiffValue.setTextColor(0xFFFFFFFF.toInt())
                            cardDiffLabel.text = "平衡"
                        }

                        loadingText.visibility = View.GONE
                        refreshBtn.isEnabled = true
                    }
                } else {
                    showError("服务器响应异常")
                }
            } catch (e: IOException) {
                showError("网络连接失败: ${e.message}")
            } catch (e: Exception) {
                showError("数据解析失败: ${e.message}")
            }
        }.start()
    }

    private fun showError(msg: String) {
        runOnUiThread {
            loadingText.visibility = View.GONE
            errorText.visibility = View.VISIBLE
            errorText.text = "❌ $msg"
            refreshBtn.isEnabled = true
            Toast.makeText(this, "加载失败: $msg", Toast.LENGTH_LONG).show()
        }
    }
}