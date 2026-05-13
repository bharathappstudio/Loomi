package com.echo.loomi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.Context
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import org.json.JSONArray
import org.json.JSONObject

class GoogleAuthClient(
    private val activity: ComponentActivity,
    private val onResult: (Boolean) -> Unit
) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance("https://echo-loomi-app-default-rtdb.firebaseio.com/").reference

    fun getGoogleSignInClient(): GoogleSignInClient {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken("125517755986-d90kcmnq1bhohv9n460girmg988r9eaq.apps.googleusercontent.com")
            .build()
        return GoogleSignIn.getClient(activity, gso)
    }

    private val signInLauncher: ActivityResultLauncher<Intent> =
        activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                if (account != null && account.idToken != null) {
                    signInWithFirebase(account.idToken!!)
                } else {
                    onResult(false)
                }
            } catch (e: ApiException) {
                Log.e("AUTH_LOG", "Sign in failed: ${e.statusCode}")
                onResult(false)
            }
        }

    private fun signInWithFirebase(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(activity) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        saveUserToDatabase(user)
                    } else {
                        onResult(true)
                    }
                } else {
                    Log.e("AUTH_LOG", "Firebase Auth failed", task.exception)
                    onResult(false)
                }
            }
    }

    private fun saveUserToDatabase(user: com.google.firebase.auth.FirebaseUser) {
        val uid = user.uid
        val name = user.displayName ?: "Anonymous"
        val email = user.email ?: ""
        val photoUrl = user.photoUrl?.toString() ?: ""

        val updates = mapOf(
            "uid" to uid,
            "name" to name,
            "email" to email,
            "lastSeen" to System.currentTimeMillis()
        )

        database.child("users").child(uid).updateChildren(updates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    saveAccountLocally(uid, name, email, photoUrl)
                    Log.d("AUTH_LOG", "User data updated")
                }
                onResult(true)
            }
    }

    private fun saveAccountLocally(uid: String, name: String, email: String, photoUrl: String) {
        val prefs = activity.getSharedPreferences("echo_accounts", Context.MODE_PRIVATE)
        val accountsJson = prefs.getString("accounts_list", "[]") ?: "[]"
        val accountsArray = JSONArray(accountsJson)
        
        var exists = false
        for (i in 0 until accountsArray.length()) {
            val obj = accountsArray.getJSONObject(i)
            if (obj.getString("uid") == uid) {
                obj.put("name", name)
                obj.put("email", email)
                obj.put("photoUrl", photoUrl)
                exists = true
                break
            }
        }
        
        if (!exists) {
            val newAcc = JSONObject().apply {
                put("uid", uid)
                put("name", name)
                put("email", email)
                put("photoUrl", photoUrl)
            }
            accountsArray.put(newAcc)
        }
        
        prefs.edit().putString("accounts_list", accountsArray.toString()).apply()
    }

    fun signIn(forcePicker: Boolean = false) {
        if (forcePicker) {
            getGoogleSignInClient().signOut().addOnCompleteListener {
                val signInIntent = getGoogleSignInClient().signInIntent
                signInLauncher.launch(signInIntent)
            }
        } else {
            val signInIntent = getGoogleSignInClient().signInIntent
            signInLauncher.launch(signInIntent)
        }
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun signOut() {
        auth.signOut()
        getGoogleSignInClient().signOut()
    }
}
