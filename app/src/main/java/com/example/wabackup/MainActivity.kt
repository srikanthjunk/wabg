package com.example.wabackup

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes

class MainActivity : AppCompatActivity() {
    private lateinit var phoneNumberInput: EditText
    private lateinit var pathInput: EditText
    private lateinit var backupTypeGroup: RadioGroup
    private lateinit var apiKeyInput: EditText
    private lateinit var saveButton: Button
    private lateinit var browseButton: Button
    private lateinit var folderSelectButton: Button
    private lateinit var statusText: TextView
    private lateinit var prefs: SharedPreferences
    private lateinit var googleSignInClient: GoogleSignInClient

    companion object {
        const val PREF_NAME = "WABackupPrefs"
        const val PREF_PHONE = "phone_number"
        const val PREF_BACKUP_TYPE = "backup_type"
        const val PREF_API_KEY = "api_key"
        const val PREF_FOLDER_ID = "folder_id"
        const val PREF_PATH = "backup_path"
    }

    private val directoryPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            pathInput.setText(uri.toString())
            prefs.edit().putString(PREF_PATH, uri.toString()).apply()
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            startFolderPicker()
        }
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("folder_id")?.let { folderId ->
                prefs.edit().putString(PREF_FOLDER_ID, folderId).apply()
                statusText.text = "Google Drive folder selected"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize SharedPreferences
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE)

        // Initialize views
        phoneNumberInput = findViewById(R.id.phoneNumberInput)
        pathInput = findViewById(R.id.pathInput)
        backupTypeGroup = findViewById(R.id.backupTypeGroup)
        apiKeyInput = findViewById(R.id.apiKeyInput)
        saveButton = findViewById(R.id.saveButton)
        browseButton = findViewById(R.id.browseButton)
        folderSelectButton = findViewById(R.id.folderSelectButton)
        statusText = findViewById(R.id.statusText)

        // Initialize Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Load saved settings
        loadSavedSettings()

        // Setup listeners
        browseButton.setOnClickListener {
            directoryPicker.launch(null)
        }

        backupTypeGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioBytescale -> {
                    apiKeyInput.isEnabled = true
                    folderSelectButton.isEnabled = false
                }
                R.id.radioGoogleDrive -> {
                    apiKeyInput.isEnabled = false
                    folderSelectButton.isEnabled = true
                }
            }
        }

        folderSelectButton.setOnClickListener {
            startGoogleSignIn()
        }

        saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSavedSettings() {
        phoneNumberInput.setText(prefs.getString(PREF_PHONE, ""))
        apiKeyInput.setText(prefs.getString(PREF_API_KEY, ""))
        pathInput.setText(prefs.getString(PREF_PATH, ""))

        when (prefs.getString(PREF_BACKUP_TYPE, "bytescale")) {
            "bytescale" -> backupTypeGroup.check(R.id.radioBytescale)
            "drive" -> backupTypeGroup.check(R.id.radioGoogleDrive)
        }
    }

    private fun startGoogleSignIn() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun startFolderPicker() {
        // Option 1: Implement folder picker logic here
        statusText.text = "Folder picker not implemented"

        // Option 2: If you have a FolderPickerActivity, use this:
        // val intent = Intent(this, FolderPickerActivity::class.java)
        // folderPickerLauncher.launch(intent)
    }

    private fun saveSettings() {
        val phoneNumber = phoneNumberInput.text.toString()
        val apiKey = apiKeyInput.text.toString()
        val path = pathInput.text.toString()
        val backupType = when (backupTypeGroup.checkedRadioButtonId) {
            R.id.radioBytescale -> "bytescale"
            R.id.radioGoogleDrive -> "drive"
            else -> "bytescale"
        }

        prefs.edit().apply {
            putString(PREF_PHONE, phoneNumber)
            putString(PREF_API_KEY, apiKey)
            putString(PREF_PATH, path)
            putString(PREF_BACKUP_TYPE, backupType)
            apply()
        }

        statusText.text = "Settings saved"
    }
}