package com.example.ketchup.ui

import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import com.example.ketchup.R

class PinViewController(
    rootView: View,
    private val pinLength: Int = 4,
    private val onPinComplete: (String) -> Unit
) {
    private val dots = mutableListOf<ImageView>()
    private var pin = StringBuilder()

    private val errorText: TextView? = rootView.findViewById(R.id.tv_pin_error)

    init {
        // Dots
        val dotIds = listOf(R.id.pin_dot_1, R.id.pin_dot_2, R.id.pin_dot_3, R.id.pin_dot_4)
        dotIds.forEach { id ->
            rootView.findViewById<ImageView?>(id)?.let { dots.add(it) }
        }

        // Number buttons
        val numButtonIds = mapOf(
            R.id.btn_0 to "0", R.id.btn_1 to "1", R.id.btn_2 to "2",
            R.id.btn_3 to "3", R.id.btn_4 to "4", R.id.btn_5 to "5",
            R.id.btn_6 to "6", R.id.btn_7 to "7", R.id.btn_8 to "8",
            R.id.btn_9 to "9"
        )
        numButtonIds.forEach { (id, digit) ->
            rootView.findViewById<Button?>(id)?.setOnClickListener { appendDigit(digit) }
        }

        // Delete button
        rootView.findViewById<View?>(R.id.btn_delete)?.setOnClickListener { deleteDigit() }

        updateDots()
    }

    private fun appendDigit(digit: String) {
        if (pin.length >= pinLength) return
        pin.append(digit)
        updateDots()
        if (pin.length == pinLength) {
            val completed = pin.toString()
            onPinComplete(completed)
        }
    }

    private fun deleteDigit() {
        if (pin.isNotEmpty()) {
            pin.deleteCharAt(pin.length - 1)
            updateDots()
        }
    }

    private fun updateDots() {
        dots.forEachIndexed { index, dot ->
            dot.setImageResource(
                if (index < pin.length) R.drawable.pin_dot_filled else R.drawable.pin_dot
            )
        }
    }

    fun showError(message: String) {
        errorText?.text = message
        errorText?.visibility = View.VISIBLE
        dots.forEach { it.setImageResource(R.drawable.pin_dot_error) }
        pin.clear()
        // Reset dots after short delay
        errorText?.postDelayed({
            updateDots()
        }, 1000)
    }

    fun reset() {
        pin.clear()
        errorText?.visibility = View.GONE
        updateDots()
    }

    fun clearPin() {
        for (i in pin.indices) pin[i] = '0'  // zero memory before clearing
        pin.clear()
        updateDots()
    }
}
