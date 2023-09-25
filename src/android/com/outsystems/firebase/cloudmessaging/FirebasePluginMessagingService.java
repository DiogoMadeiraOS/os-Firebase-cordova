package org.apache.cordova.firebase;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import androidx.core.app.NotificationCompat;
import android.util.Log;
import android.app.Notification;
import android.text.TextUtils;
import android.content.ContentResolver;
import android.graphics.Color;
import android.media.AudioAttributes;
import me.leolin.shortcutbadger.ShortcutBadger;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class FirebasePluginMessagingService extends FirebaseMessagingService {

  private static final String TAG = "FirebasePlugin";
  protected static final String KEY = "badge";

  //CMT needed variables
  private String CMT_DATA_MESSAGE_TYPE_KEY = "trigger_type";
  private String CMT_DATA_MESSAGE_CUSTOM_TEXT_KEY = "custom_text";
  private String RESULTS_CMT_DATA_MESSAGE_TYPE = "RESULTS";
  private List<String> CMT_SUPPORTED_DATA_MESSAGE_TYPES = Arrays.asList(RESULTS_CMT_DATA_MESSAGE_TYPE);


  /**
   * Get a string from resources without importing the .R package
   * 
   * @param name Resource Name
   * @return Resource
   */
  private String getStringResource(String name) {
    return this.getString(
    this.getResources().getIdentifier(
      name, "string", this.getPackageName()));
  }

  /**
   * Called if InstanceID token is updated. This may occur if the security of
   * the previous token had been compromised. Note that this is called when the InstanceID token
   * is initially generated so this is where you would retrieve the token.
  */
  @Override
  public void onNewToken(String token) {
    Log.d(TAG, "Refreshed token: " + token);
    FirebasePlugin.sendToken(token);
  }

  /**
   * Called when message is received.
   *
   * @param remoteMessage Object representing the message received from Firebase Cloud Messaging.
   */
  @Override
  public void onMessageReceived(RemoteMessage remoteMessage) {
    // [START_EXCLUDE]
    // There are two types of messages data messages and notification messages. Data messages are handled
    // here in onMessageReceived whether the app is in the foreground or background. Data messages are the type
    // traditionally used with GCM. Notification messages are only received here in onMessageReceived when the app
    // is in the foreground. When the app is in the background an automatically generated notification is displayed.
    // When the user taps on the notification they are returned to the app. Messages containing both notification
    // and data payloads are treated as notification messages. The Firebase console always sends notification
    // messages. For more see: https://firebase.google.com/docs/cloud-messaging/concept-options
    // [END_EXCLUDE]
    
    Log.d(TAG, "FirebasePluginMessagingService onMessageReceived called");

    // Pass the message to the receiver manager so any registered receivers can decide to handle it
    boolean wasHandled = FirebasePluginMessageReceiverManager.onMessageReceived(remoteMessage);
    if (wasHandled) {
      Log.d(TAG, "Message was handled by a registered receiver");

      // Don't process the message in this method.
      return;
    }

    // TODO(developer): Handle FCM messages here.
    // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
    String title;
    String text;
    String id;
    String sound = null;
    String lights = null;
    Map<String, String> data = remoteMessage.getData();
    
    if (remoteMessage.getNotification() != null) {
      title = remoteMessage.getNotification().getTitle();
      text = remoteMessage.getNotification().getBody();
      id = remoteMessage.getMessageId();
    } else {
      title = data.get("title");
      text = data.get("text");
      id = data.get("id");
      sound = data.get("sound");
      lights = data.get("lights"); //String containing hex ARGB color, miliseconds on, miliseconds off, example: '#FFFF00FF,1000,3000'

      if (TextUtils.isEmpty(text)) {
        text = data.get("body");
      }
      if (TextUtils.isEmpty(text)) {
        text = data.get("message");
      }
    }

    if (TextUtils.isEmpty(id)) {
      Random rand = new Random();
      int n = rand.nextInt(50) + 1;
      id = Integer.toString(n);
    }

    String badge = data.get("badge");

    if (isCMTNDataMessage(remoteMessage)) {
      data.put("provider", "CMT");
      //Turn this data messages to notifications only if the app is not in the foreground
      if (FirebasePlugin.inBackground()) {
        HashMap<String, String> messageContents = handleCMTDataMessage(remoteMessage);
        if (messageContents != null) {
          Log.d(TAG, "Data Message received from CMT");
          title = messageContents.get("title");
          text = messageContents.get("text");
        }
      }
    }

    Log.d(TAG, "From: " + remoteMessage.getFrom());
    Log.d(TAG, "Notification Message id: " + id);
    Log.d(TAG, "Notification Message Title: " + title);
    Log.d(TAG, "Notification Message Body/Text: " + text);
    Log.d(TAG, "Notification Message Sound: " + sound);
    Log.d(TAG, "Notification Message Lights: " + lights);
    Log.d(TAG, "Notification Badge: " + badge);

    if (badge != null && !badge.isEmpty()) {
      setBadgeNumber(badge);
    }

    // TODO: Add option to developer to configure if show notification when app on foreground
    if (!TextUtils.isEmpty(text) || !TextUtils.isEmpty(title) || (!data.isEmpty())) {
      boolean showNotification = (FirebasePlugin.inBackground() || !FirebasePlugin.hasNotificationsCallback()) && (!TextUtils.isEmpty(text) || !TextUtils.isEmpty(title));
      Log.d(TAG, "showNotification: " + (showNotification ? "true" : "false"));
      sendNotification(id, title, text, data, showNotification, sound, lights);
    }
  }

  private void sendNotification(String id, String title, String messageBody, Map<String, String> data, boolean showNotification, String sound, String lights) {
    Bundle bundle = new Bundle();
    for (String key : data.keySet()) {
      bundle.putString(key, data.get(key));
    }

    if (showNotification) {
      // Android 12 introduced changes to the notification trampoline, disallowing activities
      // to be started from a service or a broadcast receiver. This means that for Android 12
      // and above we'll handle the notification in a transparent activity.
      // The hardcoded value 31 corresponds to android.os.Build.VERSION_CODES.S which is an
      // unknown value when building the application using MABS prior to version 8.
      PendingIntent pendingIntent;

      if (android.os.Build.VERSION.SDK_INT >= 31) {
        Intent intent = new Intent(this, OnNotificationOpenActivity.class);
        intent.putExtras(bundle);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        pendingIntent = PendingIntent.getActivity(this, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
      } else {
        Intent intent = new Intent(this, OnNotificationOpenReceiver.class);
        intent.putExtras(bundle);
        pendingIntent = PendingIntent.getBroadcast(this, id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
      }

      String channelId = this.getStringResource("default_notification_channel_id");
      String channelName = this.getStringResource("default_notification_channel_name");
      Uri defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

      NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, channelId);
      notificationBuilder
        .setContentTitle(title)
        .setContentText(messageBody)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setStyle(new NotificationCompat.BigTextStyle().bigText(messageBody))
        .setAutoCancel(true)
        .setSound(defaultSoundUri)
        .setContentIntent(pendingIntent)
        .setPriority(NotificationCompat.PRIORITY_MAX);

      // Title
      if (TextUtils.isEmpty(title) && android.os.Build.VERSION.SDK_INT <= android.os.Build.VERSION_CODES.M) {
        int stringId = getApplicationInfo().labelRes;
        String appName = stringId == 0 ? getApplicationInfo().nonLocalizedLabel.toString() : getString(stringId);
        notificationBuilder.setContentTitle(appName);
      }

      // Icon
      int resID = getResources().getIdentifier("notification_icon", "drawable", getPackageName());
      if (resID != 0) {
        notificationBuilder.setSmallIcon(resID);
      } else {
        if (android.os.Build.VERSION.SDK_INT == android.os.Build.VERSION_CODES.LOLLIPOP) {
          resID = getResources().getIdentifier("icon", "mipmap", getPackageName());
          if (resID != 0) {
            notificationBuilder.setSmallIcon(resID);
            notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(getResources(), resID));
          } else {
            notificationBuilder.setSmallIcon(getApplicationInfo().icon);
          }
        } else {
          notificationBuilder.setSmallIcon(getApplicationInfo().icon);
        }
      }

      // Sound
      if (sound != null) {
        Log.d(TAG, "sound before path is: " + sound);

        Uri soundPath = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE
          + "://" + getPackageName() + "/raw/" + sound);

        Log.d(TAG, "Parsed sound is: " + soundPath.toString());

        notificationBuilder.setSound(soundPath);
      } else {
        Log.d(TAG, "Sound was null ");
      }

      int lightArgb = 0;
      if (lights != null) {
        try {
          String[] lightsComponents = lights.replaceAll("\\s", "").split(",");
          if (lightsComponents.length == 3) {
            lightArgb = Color.parseColor(lightsComponents[0]);
            int lightOnMs = Integer.parseInt(lightsComponents[1]);
            int lightOffMs = Integer.parseInt(lightsComponents[2]);
            
            notificationBuilder.setLights(lightArgb, lightOnMs, lightOffMs);
          }
        } catch (Exception e) { }
      }

      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
        int accentID = getResources().getIdentifier("accent", "color", getPackageName());
        Log.d(TAG, "AccentId: " + Integer.toString(accentID));
        notificationBuilder.setColor(getResources().getColor(accentID, null));
      }

      Notification notification = notificationBuilder.build();

      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
        int iconID = android.R.id.icon;
        int notiID = getResources().getIdentifier("notification_big", "drawable", getPackageName());
        if (notification.contentView != null) {
          notification.contentView.setImageViewResource(iconID, notiID);
        }
      }

      NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

      // Since android Oreo notification channel is needed.
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
        List<NotificationChannel> channels = notificationManager.getNotificationChannels();

        boolean channelExists = false;
        for (int i = 0; i < channels.size(); i++) {
          if (channelId.equals(channels.get(i).getId())) {
            channelExists = true;
          }
        }
        
        if (!channelExists) {
          NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
          channel.enableLights(true);
          channel.enableVibration(true);
          channel.setShowBadge(true);
          
          if (lights != null) {
            Log.d(TAG, "lightArgb: " + Integer.toString(lightArgb));
            channel.setLightColor(lightArgb);
          }
          
          if (sound != null) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build();
            Uri soundPath = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE
                + "://" + getPackageName() + "/raw/" + sound);
            channel.setSound(soundPath, attributes);
          }
          
          notificationManager.createNotificationChannel(channel);
        }
      }

      notificationManager.notify(id.hashCode(), notification);
    } else {
      bundle.putBoolean("tap", false);
      bundle.putString("title", title);
      bundle.putString("body", messageBody);
      FirebasePlugin.sendNotification(bundle, this.getApplicationContext());
    }
  }

  private void setBadgeNumber(String badge) {
    try {
      int count = 0;
      Context applicationContext = getApplicationContext();

      if (isPositiveInteger(badge)) {
        count = Integer.parseInt(badge);
        applyBadgeCount(applicationContext, count);
      } else {
        if (badge.startsWith("++") || badge.startsWith("--")) {
          int delta = 0;
          int currentBadgeNumber = getCurrentBadgeNumber(applicationContext);
          boolean toIncrement = badge.startsWith("++");
          badge = badge.substring(2);
          if (badge.isEmpty()) {
            delta = 1;
          } else {
            delta = Integer.parseInt(badge);
          }
          count = toIncrement ? currentBadgeNumber + delta : currentBadgeNumber - delta;
          if (count < 0) {
            count = 0;
          }
          applyBadgeCount(applicationContext, count);
        }
      }
    } catch (Exception e) {
      Log.e(TAG, e.getLocalizedMessage(), e);
    }
  }

  private void applyBadgeCount(Context context, int count) {
    Log.d(TAG, "Applying badge count: " + count);
    ShortcutBadger.applyCount(context, count);
    SharedPreferences.Editor editor = context.getSharedPreferences(KEY, Context.MODE_PRIVATE).edit();
    editor.putInt(KEY, count);
    editor.apply();
  }

  private int getCurrentBadgeNumber(Context context) {
    SharedPreferences settings = context.getSharedPreferences(KEY, Context.MODE_PRIVATE);
    int currentBadgeNumber = settings.getInt(KEY, 0);
    Log.d(TAG, "Current badge count: " + currentBadgeNumber);
    return currentBadgeNumber;
  }

  // Defaults radix = 10
  private static boolean isPositiveInteger(String s) {
    if (s == null || s.isEmpty()) {
      return false;
    }
    for (int i = 0; i < s.length(); i++) {
      if (Character.digit(s.charAt(i), 10) < 0) {
        return false;
      }
    }
    return true;
  }

  /**
   * Method responsible to check is the received remote message is a data message sent from CMT (e.g. from the
   * portal or directly from CMT backend). We should consider that if the data message has the "trigger_type" key
   * then it is a data message from CMT.
   *
   * @param remoteMessage - remote message received from FCM
   * @return boolean indicating if the received remote message is a data message from CMT
   */
  private boolean isCMTNDataMessage(RemoteMessage remoteMessage) {
    return remoteMessage.getData().containsKey(CMT_DATA_MESSAGE_TYPE_KEY);
  }

  /**
   * Method responsible to check if the data message type received from CMT is from a supported type (e.g. RESULTS)
   * and if so set the notification title and text.
   *
   * @param remoteMessage - remote message received from FCM
   * @return boolean indicating if the received data message from CMT is from a supported type
   */
  private HashMap<String, String> handleCMTDataMessage(RemoteMessage remoteMessage) {
    String triggerType = remoteMessage.getData().get(CMT_DATA_MESSAGE_TYPE_KEY);

    if (CMT_SUPPORTED_DATA_MESSAGE_TYPES.contains(triggerType)) {
      String text = remoteMessage.getData().get(CMT_DATA_MESSAGE_CUSTOM_TEXT_KEY);

      if (text != null && text.isEmpty()) {
        Log.d(TAG, "Expected CMT Data Message properties are empty");
        return null;
      }

      String title = null;
      if (RESULTS_CMT_DATA_MESSAGE_TYPE.equals(triggerType)) {
        title = getStringResource("new_trip_results_notification_title");
      }

      HashMap<String, String> properties = new HashMap<>();
      properties.put("text", text);
      properties.put("title", title);
      return properties;

    } else {
      Log.d(TAG, "Unsupported CMT Data Message Trigger Type: " + triggerType);
      return null;

    }
  }

}
