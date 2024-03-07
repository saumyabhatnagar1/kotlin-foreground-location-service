package com.saumya.locationforeground

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {


    private lateinit var startTrackingButton: Button
    private lateinit var stopTrackingButton: Button
    private lateinit var agentIdEditText: EditText
    private lateinit var statusTextView: TextView
    private lateinit var sharedPref: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.layout)
        sharedPref = getSharedPreferences("agentId", Context.MODE_PRIVATE)
        startTrackingButton = findViewById(R.id.button_start_tracking)
        stopTrackingButton = findViewById(R.id.button_stop_tracking)
        statusTextView = findViewById(R.id.text_view_status)

        agentIdEditText = findViewById(R.id.edit_text_agent_id)

        var agentId = sharedPref.getString("agentId", null)
        if (agentId != null) {
            agentIdEditText.setText(agentId)
        }

        val isServiceRunning =
            isMyServiceRunning(applicationContext, LocationForegroundService::class.java)

        if (isServiceRunning) {
            disableButton(startTrackingButton)
            enableButton(stopTrackingButton)
        } else {
            disableButton(stopTrackingButton)
            enableButton(startTrackingButton)
        }
        setServiceStatusText(isServiceRunning, statusTextView)


        startTrackingButton.setOnClickListener {
            val agentIdText = agentIdEditText.text
            if (agentIdText.isEmpty()) {
                Toast.makeText(this, "Enter agent Id", Toast.LENGTH_SHORT).show()
            } else {
                val sharedPref = getSharedPreferences("agentId", Context.MODE_PRIVATE)
                with(sharedPref?.edit()) {
                    this?.putString("agentId", agentIdText.toString())
                    agentId = agentIdText.toString()
                    this?.apply()
                }
                val serviceIntent = Intent(this, LocationForegroundService::class.java)
                startService(serviceIntent)
                disableButton(startTrackingButton)
                enableButton(stopTrackingButton)
                setServiceStatusText(true, statusTextView)

            }
        }

        stopTrackingButton.setOnClickListener {
            val stopServiceIntent = Intent(this, LocationForegroundService::class.java)
            stopService(stopServiceIntent)
            disableButton(stopTrackingButton)
            enableButton(startTrackingButton)
            setServiceStatusText(false, statusTextView)
        }


    }

    private fun disableButton(button: Button) {
        button.isEnabled = false
        button.isClickable = false
        button.setTextColor(
            ContextCompat.getColor(
                applicationContext,
                com.google.android.gms.base.R.color.common_google_signin_btn_text_light_disabled
            )
        )
    }

    private fun enableButton(button: Button) {
        button.isEnabled = true
        button.isClickable = true
        button.setTextColor(ContextCompat.getColor(applicationContext, R.color.teal_700))
    }

    private fun setServiceStatusText(status: Boolean, textView: TextView) {
        if (status) {
            textView.text = "Active"
        } else textView.text = "Inactive"
    }
}

