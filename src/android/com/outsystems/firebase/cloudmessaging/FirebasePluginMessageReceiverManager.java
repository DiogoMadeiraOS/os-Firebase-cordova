package org.apache.cordova.firebase;

import android.util.Log;
import com.google.firebase.messaging.RemoteMessage;

import java.util.ArrayList;
import java.util.List;

public class FirebasePluginMessageReceiverManager {

  private static final String TAG = "FirebasePlugin";

  private static List<FirebasePluginMessageReceiver> receivers = new ArrayList<FirebasePluginMessageReceiver>();

  public static void register(FirebasePluginMessageReceiver receiver) {
    Log.d(TAG, "FirebasePluginMessageReceiverManager register called");
    receivers.add(receiver);
  }

  public static boolean onMessageReceived(RemoteMessage remoteMessage) {
    Log.d(TAG, "FirebasePluginMessageReceiverManager onMessageReceived called");
    boolean handled = false;
    for (FirebasePluginMessageReceiver receiver : receivers) {
      boolean wasHandled = receiver.onMessageReceived(remoteMessage);
      if (wasHandled) {
        handled = true;
      }
    }

    Log.d(TAG, "FirebasePluginMessageReceiverManager onMessageReceived handled: " + (handled ? "true" : "false"));
    return handled;
  }
}