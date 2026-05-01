package com.achlvv.application1

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.textfield.TextInputEditText

class WelcomeFragment : Fragment(R.layout.fragment_welcome) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Link Kotlin variables to the XML UI elements
        val ageInput = view.findViewById<TextInputEditText>(R.id.et_age)
        val heightInput = view.findViewById<TextInputEditText>(R.id.et_height)
        val weightInput = view.findViewById<TextInputEditText>(R.id.et_weight)
        val sexInput = view.findViewById<AutoCompleteTextView>(R.id.dropdown_sex)
        val btnContinue = view.findViewById<Button>(R.id.btn_continue)

        // Tell the button what to do when clicked
        btnContinue.setOnClickListener {

            // Get the text the user actually typed/selected
            val age = ageInput.text.toString()
            val height = heightInput.text.toString()
            val weight = weightInput.text.toString()
            val sex = sexInput.text.toString()

            // Quick check: Make sure they didn't leave anything blank
            if (age.isEmpty() || height.isEmpty() || weight.isEmpty() || sex.isEmpty()) {
                // Show a tiny popup message asking them to fill everything out
                Toast.makeText(requireContext(), "Please fill in all details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener // Stop running the rest of the code
            }

            // Save the data to SharedPreferences
            // "UserPrefs" is the name of our hidden save file
            val sharedPreferences = requireActivity().getSharedPreferences("UserPrefs", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()

            editor.putString("USER_AGE", age)
            editor.putString("USER_HEIGHT", height)
            editor.putString("USER_WEIGHT", weight)
            editor.putString("USER_SEX", sex)

            // saves the data safely in the background
            editor.apply()
            (requireActivity() as MainActivity).showBottomNav()
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, DashboardFragment())
                .commit()
        }
    }
}