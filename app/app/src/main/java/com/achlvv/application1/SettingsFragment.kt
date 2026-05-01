package com.achlvv.application1

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Link to our XML fields
        val ageInput = view.findViewById<TextInputEditText>(R.id.et_settings_age)
        val heightInput = view.findViewById<TextInputEditText>(R.id.et_settings_height)
        val weightInput = view.findViewById<TextInputEditText>(R.id.et_settings_weight)
        val sexInput = view.findViewById<AutoCompleteTextView>(R.id.dropdown_settings_sex)
        val btnSave = view.findViewById<Button>(R.id.btn_save_settings)
        // Open our hidden save file
        val sharedPreferences = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
        // Load the existing data and put it INTO the text boxes
        ageInput.setText(sharedPreferences.getString("USER_AGE", ""))
        heightInput.setText(sharedPreferences.getString("USER_HEIGHT", ""))
        weightInput.setText(sharedPreferences.getString("USER_WEIGHT", ""))
        sexInput.setText(sharedPreferences.getString("USER_SEX", ""), false)

        // Listen for the Save button click
        btnSave.setOnClickListener {
            val newAge = ageInput.text.toString()
            val newHeight = heightInput.text.toString()
            val newWeight = weightInput.text.toString()
            val newSex = sexInput.text.toString()

            // Quick check to make sure they didn't accidentally delete everything
            if (newAge.isEmpty() || newHeight.isEmpty() || newWeight.isEmpty() || newSex.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill out all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Save the newly typed data back to SharedPreferences
            val editor = sharedPreferences.edit()
            editor.putString("USER_AGE", newAge)
            editor.putString("USER_HEIGHT", newHeight)
            editor.putString("USER_WEIGHT", newWeight)
            editor.putString("USER_SEX", newSex)
            editor.apply()

            // Let the user know it worked
            Toast.makeText(requireContext(), "Profile Updated Successfully! ✅", Toast.LENGTH_SHORT).show()
        }
    }
}