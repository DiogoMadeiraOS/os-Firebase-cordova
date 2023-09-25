package org.apache.cordova.firebase;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;

import androidx.core.app.NotificationManagerCompat;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.crashlytics.FirebaseCrashlytics;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.PendingDynamicLinkData;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigInfo;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import androidx.annotation.NonNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import me.leolin.shortcutbadger.ShortcutBadger;

public class FirebasePlugin extends CordovaPlugin {

    private FirebaseAnalytics mFirebaseAnalytics;
    private static final String TAG = "FirebasePlugin";
    protected static final String KEY = "badge";

    private static boolean inBackground = true;
    private static ArrayList<Bundle> notificationStack = null;
    private static CallbackContext notificationCallbackContext;
    private static CallbackContext tokenRefreshCallbackContext;
    private static CallbackContext dynamicLinkCallback;

    @Override
    protected void pluginInitialize() {
        final Context context = cordova.getActivity().getApplicationContext();
        final Bundle extras = cordova.getActivity().getIntent().getExtras();
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                Log.d(TAG, "Starting Firebase plugin");
                mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
                mFirebaseAnalytics.setAnalyticsCollectionEnabled(true);
                if (extras != null && extras.size() > 1) {
                    if (FirebasePlugin.notificationStack == null) {
                        FirebasePlugin.notificationStack = new ArrayList<Bundle>();
                    }
                    if (extras.containsKey("google.message_id")) {
                        extras.putBoolean("tap", true);
                        notificationStack.add(extras);
                    }
                }
            }
        });
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        switch (action) {
            case "getId":
                getId(callbackContext);
                return true;
            case "getToken":
                getToken(callbackContext);
                return true;
            case "hasPermission":
                hasPermission(callbackContext);
                return true;
            case "setBadgeNumber":
                setBadgeNumber(callbackContext, args.getInt(0));
                return true;
            case "getBadgeNumber":
                getBadgeNumber(callbackContext);
                return true;
            case "subscribe":
                subscribe(callbackContext, args.getString(0));
                return true;
            case "unsubscribe":
                unsubscribe(callbackContext, args.getString(0));
                return true;
            case "unregister":
                unregister(callbackContext);
                return true;
            case "onNotificationOpen":
                onNotificationOpen(callbackContext);
                return true;
            case "onTokenRefresh":
                onTokenRefresh(callbackContext);
                return true;
            case "logEvent":
                logEvent(callbackContext, args.getString(0), args.getJSONObject(1));
                return true;
            case "logError":
                logError(callbackContext, args.getString(0));
                return true;
            case "logMessage":
                logMessage(callbackContext, args.getString(0));
                return true;
            case "setCrashlyticsUserId":
                setCrashlyticsUserId(callbackContext, args.getString(0));
                return true;
            case "setScreenName":
                setScreenName(callbackContext, args.getString(0));
                return true;
            case "setUserId":
                setUserId(callbackContext, args.getString(0));
                return true;
            case "setUserProperty":
                setUserProperty(callbackContext, args.getString(0), args.getString(1));
                return true;
            case "activateFetched":
                activateFetched(callbackContext);
                return true;
            case "fetch":
                if (args.length() > 0) fetch(callbackContext, args.getLong(0));
                else fetch(callbackContext);
                return true;
            case "getByteArray":
                getByteArray(callbackContext, args.getString(0));
                return true;
            case "getValue":
                getValue(callbackContext, args.getString(0));
                return true;
            case "getInfo":
                getInfo(callbackContext);
                return true;
            case "setConfigSettings":
                setConfigSettings(callbackContext, args.getJSONObject(0));
                return true;
            case "setDefaults":
                setDefaults(callbackContext, args.getJSONObject(0));
                return true;
            case "startTrace":
                startTrace(callbackContext, args.getString(0));
                return true;
            case "incrementCounter":
                incrementCounter(callbackContext, args.getString(0), args.getString(1));
                return true;
            case "stopTrace":
                stopTrace(callbackContext, args.getString(0));
                return true;
            case "addTraceAttribute":
                addTraceAttribute(callbackContext, args.getString(0), args.getString(1), args.getString(2));
                return true;
            case "forceCrashlytics":
                forceCrashlytics();
                return true;
            case "setPerformanceCollectionEnabled":
                setPerformanceCollectionEnabled(callbackContext, args.getBoolean(0));
                return true;
            case "setAnalyticsCollectionEnabled":
                setAnalyticsCollectionEnabled(callbackContext, args.getBoolean(0));
                return true;
            case "onDynamicLink":
                onDynamicLink(callbackContext);
                return true;
            case "clearAllNotifications":
                clearAllNotifications(callbackContext);
                return true;
        }

        return false;
    }

    @Override
    public void onPause(boolean multitasking) {
        FirebasePlugin.inBackground = true;
    }

    @Override
    public void onResume(boolean multitasking) {
        FirebasePlugin.inBackground = false;
    }

    @Override
    public void onReset() {
        FirebasePlugin.notificationCallbackContext = null;
        FirebasePlugin.tokenRefreshCallbackContext = null;
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        final Bundle data = intent.getExtras();
        if (dynamicLinkCallback != null) {
            respondWithDynamicLink(intent);
        }
        if (data != null && data.containsKey("google.message_id")) {
            data.putBoolean("tap", true);
            FirebasePlugin.sendNotification(data, cordova.getActivity().getApplicationContext());
        }
    }

    public static boolean inBackground() {
        return FirebasePlugin.inBackground;
    }

    public static boolean hasNotificationsCallback() {
        return FirebasePlugin.notificationCallbackContext != null;
    }

    //
    // Cloud Messaging FCM
    //
    public static void sendNotification(Bundle bundle, Context context) {
        Log.d(TAG, "sendNotification called");
        if (!FirebasePlugin.hasNotificationsCallback()) {
            if (FirebasePlugin.notificationStack == null) {
                FirebasePlugin.notificationStack = new ArrayList<Bundle>();
            }
            notificationStack.add(bundle);

            Log.d(TAG, "sendNotification notificationStack.add");
            return;
        }

        final CallbackContext callbackContext = FirebasePlugin.notificationCallbackContext;
        if (callbackContext != null && bundle != null) {
            JSONObject json = new JSONObject();
            Set<String> keys = bundle.keySet();
            for (String key : keys) {
                try {
                    json.put(key, bundle.get(key));
                } catch (JSONException e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                    return;
                }
            }

            PluginResult pluginresult = new PluginResult(PluginResult.Status.OK, json);
            pluginresult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginresult);
            Log.d(TAG, "sendNotification success");
        }
    }

    public static void sendToken(String token) {
        Log.d(TAG, "sendToken called");
        if (FirebasePlugin.tokenRefreshCallbackContext == null) {
            Log.d(TAG, "sendToken tokenRefreshCallbackContext null");
            return;
        }

        final CallbackContext callbackContext = FirebasePlugin.tokenRefreshCallbackContext;
        if (callbackContext != null && token != null) {
            PluginResult pluginresult = new PluginResult(PluginResult.Status.OK, token);
            pluginresult.setKeepCallback(true);
            callbackContext.sendPluginResult(pluginresult);
            Log.d(TAG, "sendToken success. token: " + token);
        }
    }

    private void onNotificationOpen(final CallbackContext callbackContext) {
        Log.d(TAG, "onNotificationOpen called");
        FirebasePlugin.notificationCallbackContext = callbackContext;
        if (FirebasePlugin.notificationStack != null) {
            for (Bundle bundle : FirebasePlugin.notificationStack) {
                FirebasePlugin.sendNotification(bundle, cordova.getActivity().getApplicationContext());
                Log.d(TAG, "onNotificationOpen sendNotification");
            }
            FirebasePlugin.notificationStack.clear();
            Log.d(TAG, "onNotificationOpen notificationStack.clear");
        }
    }

    private void onTokenRefresh(final CallbackContext callbackContext) {
        Log.d(TAG, "onTokenRefresh called");
        FirebasePlugin.tokenRefreshCallbackContext = callbackContext;

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    String currentToken = FirebaseInstanceId.getInstance().getToken();
                    if (currentToken != null) {
                        FirebasePlugin.sendToken(currentToken);
                        Log.d(TAG, "onTokenRefresh success. token: " + currentToken);
                    }
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void getId(final CallbackContext callbackContext) {
        Log.d(TAG, "getId called");
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    String id = FirebaseInstanceId.getInstance().getId();
                    callbackContext.success(id);
                    Log.d(TAG, "getId success. id: " + id);
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void getToken(final CallbackContext callbackContext) {
        Log.d(TAG, "getToken called");
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    String token = FirebaseInstanceId.getInstance().getToken();
                    callbackContext.success(token);
                    Log.d(TAG, "getToken success. token: " + token);
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void hasPermission(final CallbackContext callbackContext) {
        Log.d(TAG, "hasPermission called");
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    Context context = cordova.getActivity();
                    NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
                    boolean areNotificationsEnabled = notificationManagerCompat.areNotificationsEnabled();
                    JSONObject object = new JSONObject();
                    object.put("isEnabled", areNotificationsEnabled);
                    callbackContext.success(object);
                    Log.d(TAG, "hasPermission success. areEnabled: " + (areNotificationsEnabled ? "true" : "false"));
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setBadgeNumber(final CallbackContext callbackContext, final int number) {
        Log.d(TAG, "setBadgeNumber called. number: " + Integer.toString(number));
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    Context context = cordova.getActivity();
                    SharedPreferences.Editor editor = context.getSharedPreferences(KEY, Context.MODE_PRIVATE).edit();
                    editor.putInt(KEY, number);
                    editor.apply();
                    ShortcutBadger.applyCount(context, number);
                    callbackContext.success();
                    Log.d(TAG, "setBadgeNumber success");
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void getBadgeNumber(final CallbackContext callbackContext) {
        Log.d(TAG, "getBadgeNumber called");
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    Context context = cordova.getActivity();
                    SharedPreferences settings = context.getSharedPreferences(KEY, Context.MODE_PRIVATE);
                    int number = settings.getInt(KEY, 0);
                    callbackContext.success(number);
                    Log.d(TAG, "getBadgeNumber success. number: " + Integer.toString(number));
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void subscribe(final CallbackContext callbackContext, final String topic) {
        Log.d(TAG, "subscribe called. topic: " + topic);
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseMessaging.getInstance().subscribeToTopic(topic);
                    callbackContext.success();
                    Log.d(TAG, "subscribe success");
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void unsubscribe(final CallbackContext callbackContext, final String topic) {
        Log.d(TAG, "unsubscribe called. topic: " + topic);
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseMessaging.getInstance().unsubscribeFromTopic(topic);
                    callbackContext.success();
                    Log.d(TAG, "unsubscribe success");
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void unregister(final CallbackContext callbackContext) {
        Log.d(TAG, "unregister called");
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseInstanceId.getInstance().deleteInstanceId();
                    String currentToken = FirebaseInstanceId.getInstance().getToken();
                    if (currentToken != null) {
                        FirebasePlugin.sendToken(currentToken);
                    }
                    callbackContext.success();
                    Log.d(TAG, "unregister success. currentToken: " + currentToken);
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void clearAllNotifications(final CallbackContext callbackContext) {
        Log.d(TAG, "clearAllNotifications called");
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    Context context = cordova.getActivity();
                    NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
                    nm.cancelAll();
                    callbackContext.success();
                    Log.d(TAG, "clearAllNotifications success");
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                }
            }
        });
    }

    //
    // Dynamic Links
    //
    private void onDynamicLink(final CallbackContext callbackContext) {
        dynamicLinkCallback = callbackContext;

        respondWithDynamicLink(cordova.getActivity().getIntent());
    }

    private void respondWithDynamicLink(Intent intent) {
        FirebaseDynamicLinks.getInstance().getDynamicLink(intent)
                .addOnSuccessListener(cordova.getActivity(), new OnSuccessListener<PendingDynamicLinkData>() {
                    @Override
                    public void onSuccess(PendingDynamicLinkData pendingDynamicLinkData) {
                        if (pendingDynamicLinkData != null) {
                            Uri deepLink = pendingDynamicLinkData.getLink();

                            if (deepLink != null) {
                                JSONObject response = new JSONObject();
                                try {
                                    response.put("deepLink", deepLink);
                                    response.put("clickTimestamp", pendingDynamicLinkData.getClickTimestamp());

                                    PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, response);
                                    pluginResult.setKeepCallback(true);
                                    dynamicLinkCallback.sendPluginResult(pluginResult);

                                    doOnDynamicLink(deepLink.toString());
                                } catch (JSONException e) {
                                    Log.e(TAG, "Fail to handle dynamic link data", e);
                                }
                            }
                        }
                    }
                });
    }

    private void doOnDynamicLink(final String dynamicLink) {
        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String method = String.format("javascript:window.fp.dynamicLinkCallback( '%s' );", dynamicLink);
                ;
                webView.loadUrl(method);
            }
        });
    }

    //
    // Analytics
    //
    private void logEvent(final CallbackContext callbackContext, final String name, final JSONObject params) throws JSONException {
        Log.d(TAG, "logEvent called. name: " + name);
        final Bundle bundle = new Bundle();
        Iterator iter = params.keys();
        while (iter.hasNext()) {
            String key = (String) iter.next();
            Object value = params.get(key);

            if (value instanceof Integer || value instanceof Double) {
                bundle.putFloat(key, ((Number) value).floatValue());
            } else {
                bundle.putString(key, value.toString());
            }
        }

        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    mFirebaseAnalytics.logEvent(name, bundle);
                    callbackContext.success();
                    Log.d(TAG, "logEvent success");
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setScreenName(final CallbackContext callbackContext, final String name) {
        Log.d(TAG, "setScreenName called. name: " + name);
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                try {
                    mFirebaseAnalytics.setCurrentScreen(cordova.getActivity(), name, null);
                    callbackContext.success();
                    Log.d(TAG, "setScreenName success");
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setUserId(final CallbackContext callbackContext, final String id) {
        Log.d(TAG, "setUserId called. id: " + id);
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    mFirebaseAnalytics.setUserId(id);
                    callbackContext.success();
                    Log.d(TAG, "setUserId success");
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setUserProperty(final CallbackContext callbackContext, final String name, final String value) {
        Log.d(TAG, "setUserProperty called. name: " + name + " value: " + value);
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    mFirebaseAnalytics.setUserProperty(name, value);
                    callbackContext.success();
                    Log.d(TAG, "setUserProperty success");
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setAnalyticsCollectionEnabled(final CallbackContext callbackContext, final boolean enabled) {
        Log.d(TAG, "setAnalyticsCollectionEnabled called. enabled: " + (enabled ? "true" : "false"));
        final FirebasePlugin self = this;
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    mFirebaseAnalytics.setAnalyticsCollectionEnabled(enabled);
                    callbackContext.success();
                    Log.d(TAG, "setAnalyticsCollectionEnabled success");
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    //
    // Performance monitoring
    //
    private HashMap<String, Trace> traces = new HashMap<String, Trace>();

    private void startTrace(final CallbackContext callbackContext, final String name) {
        Log.d(TAG, "startTrace called. name: " + name);
        final FirebasePlugin self = this;
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    Trace myTrace = null;
                    if (self.traces.containsKey(name)) {
                        myTrace = self.traces.get(name);
                    }
                    if (myTrace == null) {
                        myTrace = FirebasePerformance.getInstance().newTrace(name);
                        myTrace.start();
                        self.traces.put(name, myTrace);
                    }
                    callbackContext.success();
                    Log.d(TAG, "startTrace success");
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void incrementCounter(final CallbackContext callbackContext, final String name, final String counterNamed) {
        Log.d(TAG, "incrementCounter called. name: " + name + " counterNamed: " + counterNamed);
        final FirebasePlugin self = this;
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    Trace myTrace = null;
                    if (self.traces.containsKey(name)) {
                        myTrace = self.traces.get(name);
                    }
                    if (myTrace != null && myTrace instanceof Trace) {
                        myTrace.incrementMetric(counterNamed, 1);
                        callbackContext.success();
                        Log.d(TAG, "incrementCounter success");
                    } else {
                        callbackContext.error("Trace not found");
                        Log.d(TAG, "incrementCounter trace not found");
                    }
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void stopTrace(final CallbackContext callbackContext, final String name) {
        Log.d(TAG, "stopTrace called. name: " + name);
        final FirebasePlugin self = this;
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    Trace myTrace = null;
                    if (self.traces.containsKey(name)) {
                        myTrace = self.traces.get(name);
                    }
                    if (myTrace != null && myTrace instanceof Trace) {
                        myTrace.stop();
                        self.traces.remove(name);
                        callbackContext.success();
                        Log.d(TAG, "stopTrace success");
                    } else {
                        callbackContext.error("Trace not found");
                        Log.d(TAG, "stopTrace trace not found");
                    }
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void addTraceAttribute(final CallbackContext callbackContext, final String traceName, final String attribute, final String value) {
        Log.d(TAG, "addTraceAttribute called. traceName: " + traceName + " attribute: " + attribute + " value: " + value);
        final FirebasePlugin self = this;
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    Trace myTrace = null;
                    if (self.traces.containsKey(traceName)) {
                        myTrace = self.traces.get(traceName);
                    }
                    if (myTrace != null && myTrace instanceof Trace) {
                        myTrace.putAttribute(attribute, value);
                        callbackContext.success();
                        Log.d(TAG, "addTraceAttribute success");
                    } else {
                        callbackContext.error("Trace not found");
                        Log.d(TAG, "addTraceAttribute trace not found");
                    }
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setPerformanceCollectionEnabled(final CallbackContext callbackContext, final boolean enabled) {
        Log.d(TAG, "setPerformanceCollectionEnabled called. enabled: " + (enabled ? "true" : "false"));
        final FirebasePlugin self = this;
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebasePerformance.getInstance().setPerformanceCollectionEnabled(enabled);
                    callbackContext.success();
                    Log.d(TAG, "setPerformanceCollectionEnabled success");
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    //
    // Crashlytics
    //
    private void forceCrashlytics() {
        Log.d(TAG, "forceCrashlytics called");
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                throw new RuntimeException("Firebase crash test");
            }
        });
    }

    // Logs a non-fatal error to Crashlytics
    private void logError(final CallbackContext callbackContext, final String message) {
        Log.d(TAG, "logError called. message: " + message);
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseCrashlytics.getInstance().recordException(new Exception(message));
                    callbackContext.success(1);
                    Log.d(TAG, "logError success");
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    // Logs custom info that will appear in case a crash occurs
    private void logMessage(final CallbackContext callbackContext, final String message) {
        Log.d(TAG, "logMessage called. message: " + message);
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseCrashlytics.getInstance().log(message);
                    callbackContext.success(1);
                    Log.d(TAG, "logMessage success");
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setCrashlyticsUserId(final CallbackContext callbackContext, final String userId) {
        Log.d(TAG, "setCrashlyticsUserId called. userId: " + userId);
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                try {
                    FirebaseCrashlytics.getInstance().setUserId(userId);
                    callbackContext.success();
                    Log.d(TAG, "setCrashlyticsUserId success");
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    //
    // Remote Configuration
    //
    private void activateFetched(final CallbackContext callbackContext) {
        Log.d(TAG, "activateFetched called");
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    final boolean activated = FirebaseRemoteConfig.getInstance().activateFetched();
                    Log.d(TAG, "activateFetched success. activated: " + String.valueOf(activated));
                    callbackContext.success(String.valueOf(activated));
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void fetch(CallbackContext callbackContext) {
        Log.d(TAG, "fetch called");
        fetch(callbackContext, FirebaseRemoteConfig.getInstance().fetch());
    }

    private void fetch(CallbackContext callbackContext, long cacheExpirationSeconds) {
        Log.d(TAG, "fetch called. cacheExpirationSeconds: " + String.valueOf(cacheExpirationSeconds));
        fetch(callbackContext, FirebaseRemoteConfig.getInstance().fetch(cacheExpirationSeconds));
    }

    private void fetch(final CallbackContext callbackContext, final Task<Void> task) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    task.addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void data) {
                            Log.d(TAG, "fetch success");
                            callbackContext.success();
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            Log.d(TAG, "fetch error. error: " + e.getMessage());
                            callbackContext.error(e.getMessage());
                        }
                    });
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void getByteArray(final CallbackContext callbackContext, final String key) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    byte[] bytes = FirebaseRemoteConfig.getInstance().getByteArray(key);
                    JSONObject object = new JSONObject();
                    object.put("base64", Base64.encodeToString(bytes, Base64.DEFAULT));
                    object.put("array", new JSONArray(bytes));
                    callbackContext.success(object);
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void getValue(final CallbackContext callbackContext, final String key) {
        Log.d(TAG, "getValue called. key: " + key);
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseRemoteConfigValue value = FirebaseRemoteConfig.getInstance().getValue(key);
                    Log.d(TAG, "getValue success. value: " + value.asString());
                    callbackContext.success(value.asString());
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void getInfo(final CallbackContext callbackContext) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseRemoteConfigInfo remoteConfigInfo = FirebaseRemoteConfig.getInstance().getInfo();
                    JSONObject info = new JSONObject();

                    JSONObject settings = new JSONObject();
                    settings.put("developerModeEnabled", remoteConfigInfo.getConfigSettings().isDeveloperModeEnabled());
                    info.put("configSettings", settings);

                    info.put("fetchTimeMillis", remoteConfigInfo.getFetchTimeMillis());
                    info.put("lastFetchStatus", remoteConfigInfo.getLastFetchStatus());

                    callbackContext.success(info);
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setConfigSettings(final CallbackContext callbackContext, final JSONObject config) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    boolean devMode = config.getBoolean("developerModeEnabled");
                    FirebaseRemoteConfigSettings.Builder settings = new FirebaseRemoteConfigSettings.Builder()
                            .setDeveloperModeEnabled(devMode);
                    FirebaseRemoteConfig.getInstance().setConfigSettings(settings.build());
                    callbackContext.success();
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private void setDefaults(final CallbackContext callbackContext, final JSONObject defaults) {
        cordova.getThreadPool().execute(new Runnable() {
            public void run() {
                try {
                    FirebaseRemoteConfig.getInstance().setDefaults(defaultsToMap(defaults));
                    callbackContext.success();
                } catch (Exception e) {
                    FirebaseCrashlytics.getInstance().recordException(e);
                    callbackContext.error(e.getMessage());
                }
            }
        });
    }

    private static Map<String, Object> defaultsToMap(JSONObject object) throws JSONException {
        final Map<String, Object> map = new HashMap<String, Object>();

        for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
            String key = keys.next();
            Object value = object.get(key);

            if (value instanceof Integer) {
                //setDefaults() should take Longs
                value = new Long((Integer) value);
            } else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                if (array.length() == 1 && array.get(0) instanceof String) {
                    //parse byte[] as Base64 String
                    value = Base64.decode(array.getString(0), Base64.DEFAULT);
                } else {
                    //parse byte[] as numeric array
                    byte[] bytes = new byte[array.length()];
                    for (int i = 0; i < array.length(); i++)
                        bytes[i] = (byte) array.getInt(i);
                    value = bytes;
                }
            }

            map.put(key, value);
        }
        return map;
    }
}
