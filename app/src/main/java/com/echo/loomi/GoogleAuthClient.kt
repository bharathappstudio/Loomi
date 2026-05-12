package com.echo.loomi

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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

class GoogleAuthClient(
    private val activity: ComponentActivity,
    private val onResult: (Boolean) -> Unit
) {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance("https://echo-loomi-app-default-rtdb.firebaseio.com/").reference

    private val googleSignInClient: GoogleSignInClient by lazy {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken("125517755986-d90kcmnq1bhohv9n460girmg988r9eaq.apps.googleusercontent.com")
            .build()
        GoogleSignIn.getClient(activity, gso)
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
        val updates = mapOf(
            "uid" to user.uid,
            "name" to (user.displayName ?: "Anonymous"),
            "email" to (user.email ?: ""),
            "lastSeen" to System.currentTimeMillis()
        )

        // Use updateChildren instead of setValue to avoid deleting 'imageName' if it exists
        database.child("users").child(user.uid).updateChildren(updates)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("AUTH_LOG", "User data updated")
                } else {
                    Log.e("AUTH_LOG", "Failed to update user data", task.exception)
                }
                onResult(true)
            }
    }

    private val permissionLauncher = activity.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false

        if (fineLocationGranted || coarseLocationGranted) {
            startGoogleSignIn()
        } else {
            Log.e("AUTH_LOG", "Location permission denied")
            startGoogleSignIn()
        }
    }

    fun signIn() {
        if (hasLocationPermission()) {
            startGoogleSignIn()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun startGoogleSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        signInLauncher.launch(signInIntent)
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            activity,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }
}
