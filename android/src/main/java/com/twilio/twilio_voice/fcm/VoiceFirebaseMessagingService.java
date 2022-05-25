package com.twilio.twilio_voice.fcm;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;
import com.twilio.twilio_voice.AnswerJavaActivity;
import com.twilio.twilio_voice.Constants;
import com.twilio.twilio_voice.IncomingCallNotificationService;
import com.twilio.twilio_voice.TwilioVoicePlugin;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.CancelledCallInvite;
import com.twilio.voice.MessageListener;
import com.twilio.voice.Voice;

import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class VoiceFirebaseMessagingService extends FirebaseMessagingService {
    
    public static final String ACTION_TOKEN = "io.flutter.plugins.firebase.messaging.TOKEN";
    public static final String EXTRA_TOKEN = "token";
    private ArrayList<String> canceledCallSID = new ArrayList<String>();

    private static final String TAG = "FlutterFcmService";
    @Override
    public void onNewToken(@NonNull String token) {
        Intent onMessageIntent = new Intent(ACTION_TOKEN);
        onMessageIntent.putExtra(EXTRA_TOKEN, token);
        LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(onMessageIntent);
    }

    /**
     * Called when message is received.
     *
     * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
     */
    @Override
    public void onMessageReceived(final RemoteMessage remoteMessage) {
        Log.d(TAG, "Received onMessageReceived()");
        Log.d(TAG, "Bundle data: " + remoteMessage.getData());
        Log.d(TAG, "From: " + remoteMessage.getFrom());
        // If application is running in the foreground use local broadcast to handle message.
        // Otherwise use the background isolate to handle message.

        if (remoteMessage.getData().size() > 0) {
            boolean valid = Voice.handleMessage(this, remoteMessage.getData(), new MessageListener() {
                @Override
                public void onCallInvite(@NonNull CallInvite callInvite) {
                    Log.d(TAG, "onCallInvite " + System.currentTimeMillis());
                    // Cancel the incoming call if there is a call ongoing
                    if(TwilioVoicePlugin.activeCall != null || AnswerJavaActivity.activeCall != null) {
                        callInvite.reject(getApplicationContext());
                        return;
                    }
                    final Handler handler = new Handler(Looper.getMainLooper());
                    handler.postDelayed(() -> {
                        if(canceledCallSID.contains(callInvite.getCallSid())) {
                            Log.d(TAG, "Cancel late incoming call");
                            canceledCallSID.remove(callInvite.getCallSid());
                            return;
                        }
                        final int notificationId = (int) System.currentTimeMillis();
                        handleInvite(callInvite, notificationId);
                    }, 1000);
                }

                @Override
                public void onCancelledCallInvite(@NonNull CancelledCallInvite cancelledCallInvite, @Nullable CallException callException) {
                    Log.d(TAG, "onCancelledCallInvite " + System.currentTimeMillis());
                    if(canceledCallSID.size() > 15) {
                        canceledCallSID.clear();    
                    }
                    canceledCallSID.add(cancelledCallInvite.getCallSid());
                    handleCanceledCallInvite(cancelledCallInvite);
                }
            });

            if (!valid) {
                Log.e(TAG, "The message was not a valid Twilio Voice SDK payload: " +
                 remoteMessage.getData());
                 notificationReceived(remoteMessage);
            }
        }
        
    }
    // Override if you also receive notifications from other plugins
    public void notificationReceived(final RemoteMessage remoteMessage){ }

    private void handleInvite(CallInvite callInvite, int notificationId) {
        Intent intent = new Intent(this, IncomingCallNotificationService.class);
        intent.setAction(Constants.ACTION_INCOMING_CALL);
        intent.putExtra(Constants.INCOMING_CALL_NOTIFICATION_ID, notificationId);
        intent.putExtra(Constants.INCOMING_CALL_INVITE, callInvite);

        startService(intent);
    }

    private void handleCanceledCallInvite(CancelledCallInvite cancelledCallInvite) {
        Intent intent = new Intent(this, IncomingCallNotificationService.class);
        intent.setAction(Constants.ACTION_CANCEL_CALL);
        intent.putExtra(Constants.CANCELLED_CALL_INVITE, cancelledCallInvite);
        startService(intent);
    }
}



