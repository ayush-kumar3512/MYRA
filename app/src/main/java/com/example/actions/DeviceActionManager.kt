package com.example.actions

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import com.example.data.DiagnosticLogger

data class DeviceActionResult(
    val success: Boolean,
    val action: String,
    val message: String,
    val data: Map<String, Any?> = emptyMap()
)

data class ContactInfo(
    val name: String,
    val phoneNumber: String
)

class DeviceActionManager(private val context: Context) {
    companion object {
        private const val TAG = "DeviceActionManager"

        private val ALLOWED_PACKAGES = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "instagram" to "com.instagram.android",
            "spotify" to "com.spotify.music",
            "maps" to "com.google.android.apps.maps",
            "google maps" to "com.google.android.apps.maps",
            "chrome" to "com.android.chrome",
            "browser" to "com.android.chrome"
        )
    }

    /**
     * Opens WhatsApp via intent or deep-link.
     */
    fun openWhatsApp(): DeviceActionResult {
        DiagnosticLogger.i(TAG, "Executing openWhatsApp()")
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage("com.whatsapp")

        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            DiagnosticLogger.i(TAG, "WhatsApp launch intent sent")
            DeviceActionResult(
                success = true,
                action = "openWhatsApp",
                message = "WhatsApp opened successfully"
            )
        } else {
            // Try WhatsApp uri deep link fallback
            try {
                val uriIntent = Intent(Intent.ACTION_VIEW, Uri.parse("whatsapp://send")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(uriIntent)
                DiagnosticLogger.i(TAG, "WhatsApp opened via deep link")
                DeviceActionResult(
                    success = true,
                    action = "openWhatsApp",
                    message = "WhatsApp opened successfully via deep link"
                )
            } catch (e: Exception) {
                DiagnosticLogger.w(TAG, "WhatsApp is not installed on this device", e)
                DeviceActionResult(
                    success = false,
                    action = "openWhatsApp",
                    message = "WhatsApp is not installed on this device"
                )
            }
        }
    }

    /**
     * Opens a supported allowlisted application.
     */
    fun openApp(appName: String): DeviceActionResult {
        val normalized = appName.trim().lowercase()
        DiagnosticLogger.i(TAG, "Executing openApp('$normalized')")

        if (normalized.contains("whatsapp")) {
            return openWhatsApp()
        }

        // Check system apps / intent-based apps
        when {
            normalized.contains("camera") -> {
                return try {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    DeviceActionResult(true, "openApp", "Camera launched")
                } catch (e: Exception) {
                    DeviceActionResult(false, "openApp", "Could not open camera: ${e.message}")
                }
            }
            normalized.contains("calculator") -> {
                val intent = Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_APP_CALCULATOR)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                return try {
                    context.startActivity(intent)
                    DeviceActionResult(true, "openApp", "Calculator opened")
                } catch (e: Exception) {
                    openByPackage("com.google.android.calculator", "Calculator")
                }
            }
            normalized.contains("alarm") || normalized.contains("clock") -> {
                return try {
                    val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    DeviceActionResult(true, "openApp", "Clock / Alarms opened")
                } catch (e: Exception) {
                    DeviceActionResult(false, "openApp", "Could not open clock: ${e.message}")
                }
            }
            normalized.contains("settings") -> {
                return try {
                    val intent = Intent(Settings.ACTION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    DeviceActionResult(true, "openApp", "Device Settings opened")
                } catch (e: Exception) {
                    DeviceActionResult(false, "openApp", "Could not open settings: ${e.message}")
                }
            }
        }

        // Check allowlist of known third-party / google apps
        val matchedPackage = ALLOWED_PACKAGES.entries.firstOrNull { normalized.contains(it.key) }?.value
        if (matchedPackage != null) {
            return openByPackage(matchedPackage, appName)
        }

        DiagnosticLogger.w(TAG, "App '$appName' is not in the safe allowlist")
        return DeviceActionResult(
            success = false,
            action = "openApp",
            message = "App '$appName' is not in the allowed apps list or is unsupported."
        )
    }

    private fun openByPackage(packageName: String, readableName: String): DeviceActionResult {
        val pm = context.packageManager
        val launchIntent = pm.getLaunchIntentForPackage(packageName)
        return if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            DiagnosticLogger.i(TAG, "Launched $readableName ($packageName)")
            DeviceActionResult(true, "openApp", "$readableName opened successfully")
        } else {
            DiagnosticLogger.w(TAG, "$readableName ($packageName) is not installed")
            DeviceActionResult(false, "openApp", "$readableName is not installed on this device")
        }
    }

    /**
     * Validates and opens a web URL safely in the browser.
     */
    fun openUrl(url: String): DeviceActionResult {
        DiagnosticLogger.i(TAG, "Executing openUrl('$url')")
        var targetUrl = url.trim()
        if (!targetUrl.startsWith("http://") && !targetUrl.startsWith("https://")) {
            targetUrl = "https://$targetUrl"
        }

        return try {
            val uri = Uri.parse(targetUrl)
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DiagnosticLogger.i(TAG, "Browser opened with URL: $targetUrl")
            DeviceActionResult(true, "openUrl", "Opened $targetUrl in browser")
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Failed to open URL: $targetUrl", e)
            DeviceActionResult(false, "openUrl", "Failed to open URL: ${e.message}")
        }
    }

    /**
     * Safe phone dialer opening with phone number.
     */
    fun makeCall(phoneNumber: String): DeviceActionResult {
        DiagnosticLogger.i(TAG, "Executing makeCall('$phoneNumber')")
        val cleanNumber = phoneNumber.filter { it.isDigit() || it == '+' || it == '*' || it == '#' }
        if (cleanNumber.isBlank()) {
            return DeviceActionResult(false, "makeCall", "Invalid phone number provided")
        }

        return try {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$cleanNumber")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            DiagnosticLogger.i(TAG, "Opened dialer for $cleanNumber")
            DeviceActionResult(true, "makeCall", "Opened dialer for $cleanNumber")
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Failed to open dialer", e)
            DeviceActionResult(false, "makeCall", "Could not initiate call: ${e.message}")
        }
    }

    /**
     * Searches device contacts by name and initiates a call if exactly one match exists.
     * Safely handles 0, 1, or multiple matches.
     */
    fun callContact(contactName: String): DeviceActionResult {
        DiagnosticLogger.i(TAG, "Executing callContact('$contactName')")
        val query = contactName.trim()
        if (query.isBlank()) {
            return DeviceActionResult(false, "callContact", "Please specify a contact name")
        }

        // Check permission
        if (context.checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            DiagnosticLogger.w(TAG, "READ_CONTACTS permission not granted")
            return DeviceActionResult(
                success = false,
                action = "callContact",
                message = "Contacts permission is required to search contacts. Please grant Contacts permission in the app."
            )
        }

        val matchingContacts = mutableListOf<ContactInfo>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)

                while (cursor.moveToNext()) {
                    val name = cursor.getString(nameIndex)
                    val number = cursor.getString(numberIndex)
                    if (!name.isNullOrBlank() && !number.isNullOrBlank()) {
                        matchingContacts.add(ContactInfo(name = name, phoneNumber = number))
                    }
                }
            }
        } catch (e: Exception) {
            DiagnosticLogger.e(TAG, "Error querying contacts", e)
            return DeviceActionResult(false, "callContact", "Failed to access contacts: ${e.message}")
        }

        DiagnosticLogger.i(TAG, "Found ${matchingContacts.size} contacts matching '$query'")

        return when (matchingContacts.size) {
            0 -> {
                DeviceActionResult(
                    success = false,
                    action = "callContact",
                    message = "I couldn't find any contact named '$query' in your phonebook."
                )
            }
            1 -> {
                val match = matchingContacts.first()
                makeCall(match.phoneNumber)
                DeviceActionResult(
                    success = true,
                    action = "callContact",
                    message = "Calling ${match.name} at ${match.phoneNumber}",
                    data = mapOf("contact" to match.name, "number" to match.phoneNumber)
                )
            }
            else -> {
                // Multiple matches - do NOT guess!
                val namesList = matchingContacts.take(3).map { "${it.name} (${it.phoneNumber})" }
                DeviceActionResult(
                    success = false,
                    action = "callContact",
                    message = "I found ${matchingContacts.size} contacts matching '$query': ${namesList.joinToString(", ")}. Which one should I call?",
                    data = mapOf("matches" to matchingContacts.map { mapOf("name" to it.name, "number" to it.phoneNumber) })
                )
            }
        }
    }
}
