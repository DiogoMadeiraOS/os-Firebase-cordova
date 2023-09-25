package org.apache.cordova.firebase;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

public class OnNotificationOpenActivity extends Activity {

    private static final String TAG = "FirebasePlugin";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "OnNotificationOpenActivity onCreate called");
        super.onCreate(savedInstanceState);
        processNotification();
        this.finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "OnNotificationOpenActivity onNewIntent called");
        super.onNewIntent(intent);
        processNotification();
        this.finish();
    }

    private void processNotification() {
        Context context = getApplicationContext();
        PackageManager pm = context.getPackageManager();
        Intent launchIntent = pm.getLaunchIntentForPackage(context.getPackageName());
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        
        Bundle data = this.getIntent().getExtras();
        data.putBoolean("tap", true);

        FirebasePlugin.sendNotification(data, context);
    
        launchIntent.putExtras(data);
        context.startActivity(launchIntent);
    }
}