package com.kreativekoala.riddleverse

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

class IntroWelcomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currentUser = FirebaseAuth.getInstance().currentUser
        Log.d("WelcomeActivity", "Current user: $currentUser")
        if (currentUser != null) {
            Log.d("WelcomeActivity", "✅ User already logged in: ${currentUser.email}")
            // User is logged in, go directly to home
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }
        setContentView(R.layout.activity_intro_welcome)

        val startButton = findViewById<ImageButton>(R.id.startButton)
        startButton.setOnClickListener {
            startActivity(Intent(this, WelcomeActivity::class.java))
        }
    }
}
