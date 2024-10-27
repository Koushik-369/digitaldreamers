package com.example.myapplication;

import android.Manifest;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    // User Profile Management
    private static final int REQUEST_CODE_PERMISSIONS = 123;
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 124;
    private String profilePicture;
    private String idProof;
    private String phoneNumber;
    private List<String> emergencyContacts = new ArrayList<>();

    // Notification Timer System
    private List<Long> notificationTimers;
    private String securityCode;
    private int incorrectCodeAttempts;
    private static final String CHANNEL_ID = "emergency_notifications";
    private static final int NOTIFICATION_ID = 1;
    private static final String ACTION_SECURITY_CODE_ENTRY = "com.example.app.ACTION_SECURITY_CODE_ENTRY";
    private static final String EXTRA_SECURITY_CODE = "com.example.app.EXTRA_SECURITY_CODE";

    // UI Elements
    private ImageView profilePictureView;
    private TextView idProofView, phoneNumberView;
    private EditText securityCodeInput;
    private Button setTimerButton;
    private TextView timerStatusView;
    private RecyclerView emergencyContactsListView;
    private EmergencyContactsAdapter emergencyContactsAdapter;

    // Emergency Handling
    private BackendService backendService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize the backend service
        backendService = new BackendService();

        // Find and bind the UI elements
        profilePictureView = findViewById(R.id.profile_picture);
        idProofView = findViewById(R.id.id_proof);
        phoneNumberView = findViewById(R.id.phone_number);
        securityCodeInput = findViewById(R.id.security_code);
        setTimerButton = findViewById(R.id.set_timer);
        timerStatusView = findViewById(R.id.timer_status);
        emergencyContactsListView = findViewById(R.id.emergency_contacts_list);

        // Request permissions
        if (!requestPermissions()) {
            // Permission request denied, handle the error
            showPermissionDeniedError();
            return;
        }

        // Set up user profile
        if (!setupUserProfile()) {
            // User profile setup failed, handle the error
            showUserProfileSetupError();
            return;
        }

        // Set up notification timer
        setupNotificationTimer();

        // Set up emergency contacts list
        setupEmergencyContactsList();

        // Handle set timer button click
        setTimerButton.setOnClickListener(v -> {
            String enteredSecurityCode = securityCodeInput.getText().toString();
            if (setNotificationTimer(enteredSecurityCode)) {
                timerStatusView.setText("Timer set");
            } else {
                timerStatusView.setText("Error setting timer");
            }
        });
    }

    private boolean requestPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.RECEIVE_BOOT_COMPLETED,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.CALL_PHONE
        };

        // Request the POST_NOTIFICATIONS permission if the device is running Android 13 or higher
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = Arrays.copyOf(permissions, permissions.length + 1);
            permissions[permissions.length - 1] = Manifest.permission.POST_NOTIFICATIONS;
        }

        boolean allPermissionsGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allPermissionsGranted = false;
                break;
            }
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(this, permissions, REQUEST_CODE_PERMISSIONS);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            // Handle the permission request result
            if (hasPostNotificationsPermission()) {
                // Proceed with the notification setup
                setupNotificationTimer();
            } else {
                // The POST_NOTIFICATIONS permission is not granted, handle the situation accordingly
                showPostNotificationsPermissionDeniedError();
            }
        } else if (requestCode == REQUEST_CODE_POST_NOTIFICATIONS) {
            // Handle the POST_NOTIFICATIONS permission request result
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Proceed with the notification setup
                setupNotificationTimer();
            } else {
                // The POST_NOTIFICATIONS permission is not granted, handle the situation accordingly
                showPostNotificationsPermissionDeniedError();
            }
        }
    }

    private boolean setupUserProfile() {
        try {
            // Implement user profile setup logic
            profilePictureView.setImageURI(Uri.parse(profilePicture));
            idProofView.setText(idProof);
            phoneNumberView.setText(phoneNumber);
            return true;
        } catch (Exception e) {
            Log.e("MainActivity", "Error setting up user profile", e);
            return false;
        }
    }

    private void setupNotificationTimer() {
        try {
            // Implement notification timer setup logic
            notificationTimers = getNotificationTimers();
            securityCode = getSecurityCode();
            incorrectCodeAttempts = 0;

            backendService.saveNotificationTimerSettings(notificationTimers, securityCode);
            scheduleNotifications(notificationTimers);
        } catch (Exception e) {
            Log.e("MainActivity", "Error setting up notification timer", e);
        }
    }

    private void setupEmergencyContactsList() {
        emergencyContactsAdapter = new EmergencyContactsAdapter(emergencyContacts);
        emergencyContactsListView.setLayoutManager(new LinearLayoutManager(this));
        emergencyContactsListView.setAdapter(emergencyContactsAdapter);
    }

    private List<Long> getNotificationTimers() {
        // Return a list of notification timer values (in milliseconds)
        return Arrays.asList(60000L, 120000L, 180000L);
    }

    private String getSecurityCode() {
        // Prompt the user to set a security code
        return "1234";
    }

    private void scheduleNotifications(List<Long> notificationTimers) {
        // Schedule the notifications
        for (long timer : notificationTimers) {
            scheduleNotification();
        }
    }

    private void scheduleNotification() {
        // Use the notification manager to schedule a notification after the specified timer
        if (hasPostNotificationsPermission()) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Time to check in")
                    .setContentText("Please enter your security code to mark your journey as safe.")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        } else {
            // The POST_NOTIFICATIONS permission is not available, use an alternative notification mechanism
            showAlternativeNotification();
        }

        // Register a receiver to handle the security code entry
        registerSecurityCodeReceiver(new SecurityCodeReceiver());
    }

    private boolean hasPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        } else {
            // The POST_NOTIFICATIONS permission is not required on older Android versions
            return true;
        }
    }

    private void requestPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_CODE_POST_NOTIFICATIONS);
        }
    }

    private void registerSecurityCodeReceiver(BroadcastReceiver receiver) {
        // Register the SecurityCodeReceiver using the LocalBroadcastManager
        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, new IntentFilter(ACTION_SECURITY_CODE_ENTRY));
    }

    private class SecurityCodeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String enteredCode = intent.getStringExtra(EXTRA_SECURITY_CODE);
            handleSecurityCodeEntry(enteredCode);
        }
    }

    private boolean setNotificationTimer(String enteredSecurityCode) {
        try {
            // Implement notification timer setup logic using the provided security code
            if (enteredSecurityCode.equals(securityCode)) {
                // Security code is correct, proceed with the timer setup
                backendService.saveNotificationTimerSettings(notificationTimers, securityCode);
                return true;
            } else {
                // Security code is incorrect, handle the situation accordingly
                incorrectCodeAttempts++;
                if (incorrectCodeAttempts < 3) {
                    // Show an error message and prompt the user to enter the code again
                    showIncorrectSecurityCodeError();
                } else {
                    // Call the user
                    callUser();

                    if (!userResponded()) {
                        // Contact emergency contacts
                        contactEmergencyContacts();

                        // Contact cybersecurity officials
                        contactCyberSecurityOfficials();
                    }
                }
                return false;
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error setting up notification timer", e);
            return false;
        }
    }

    private void callUser() {
        // Use the user's phone number to call them
        Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + phoneNumber));
        startActivity(intent);
    }

    private boolean userResponded() {
        // Check if the user responded to the call
        return false;
    }

    private void contactEmergencyContacts() {
        // Use the emergency contacts to notify them of the situation
        for (String contact : emergencyContacts) {
            sendSMS(contact);
        }
    }

    private void sendSMS(String phoneNumber) {
        // Use the SMS manager to send an SMS message to the given phone number
        // The message content is always the same in this case
        String message = "Emergency alert! Please contact the authorities immediately.";
    }

    private void contactCyberSecurityOfficials() {
        // Use the backend service to notify the cybersecurity officials of the emergency
        backendService.handleEmergency();
    }

    private void showPermissionDeniedError() {
        // Display an error message or dialog to the user, indicating that the required permissions were denied
        Toast.makeText(this, "Permission denied. Cannot proceed.", Toast.LENGTH_SHORT).show();
    }

    private void showPostNotificationsPermissionDeniedError() {
        // Display an error message or dialog to the user, indicating that the POST_NOTIFICATIONS permission was denied
        Toast.makeText(this, "POST_NOTIFICATIONS permission denied. Notification functionality may be limited.", Toast.LENGTH_SHORT).show();
    }

    private void showUserProfileSetupError() {
        // Display an error message or dialog to the user, indicating that the user profile setup failed
        Toast.makeText(this, "Error setting up user profile. Please try again.", Toast.LENGTH_SHORT).show();
    }

    private void showIncorrectSecurityCodeError() {
        // Display an error message or dialog to the user, indicating that the security code was incorrect
        Toast.makeText(this, "Incorrect security code. Please try again.", Toast.LENGTH_SHORT).show();
    }

    private void showAlternativeNotification() {
        // Display a Toast message or a custom dialog to notify the user
        Toast.makeText(this, "Time to check in. Please enter your security code.", Toast.LENGTH_SHORT).show();
    }
}

class BackendService {
    public void saveUserProfile(UserProfile userProfile) {
        // Implement the logic to save the user profile
        // This may involve storing the user profile in a database or sending it to a remote server
        saveUserProfileToDatabase(userProfile);
    }

    private void saveUserProfileToDatabase(UserProfile userProfile) {
        // Code to save the user profile to a database
        // This is a placeholder implementation
        Log.d("BackendService", "Saving user profile: " + userProfile);
    }

    public void saveNotificationTimerSettings(List<Long> notificationTimers, String securityCode) {
        // Implement the logic to save the notification timer settings
        // This may involve storing the settings in a database or sending them to a remote server
        saveNotificationTimerSettingsToDatabase(notificationTimers, securityCode);
    }

    private void saveNotificationTimerSettingsToDatabase(List<Long> notificationTimers, String securityCode) {
        // Code to save the notification timer settings to a database
        // This is a placeholder implementation
        Log.d("BackendService", "Saving notification timer settings: " + notificationTimers + ", Security Code: " + securityCode);
    }

    public void handleEmergency() {
        // Implement the logic to handle the emergency situation
        // This may involve notifying emergency contacts, contacting cybersecurity officials, or triggering other emergency response mechanisms
        notifyEmergencyContacts();
        notifyCyberSecurityOfficials();
    }

    private void notifyEmergencyContacts() {
        // Code to notify the emergency contacts
        // This is a placeholder implementation
        Log.d("BackendService", "Notifying emergency contacts");
    }

    private void notifyCyberSecurityOfficials() {
        // Code to notify the cybersecurity officials
        // This is a placeholder implementation
        Log.d("BackendService", "Notifying cybersecurity officials");
    }
}

class UserProfile {
    private final String profilePicture;
    private final String idProof;
    private final String phoneNumber;
    private final List<String> emergencyContacts;

    public UserProfile(String profilePicture, String idProof, String phoneNumber, List<String> emergencyContacts) {
        this.profilePicture = profilePicture;
        this.idProof = idProof;
        this.phoneNumber = phoneNumber;
        this.emergencyContacts = emergencyContacts;
    }
}


