package com.wifidirect.wifidirectproxy;

import android.os.*;
import android.view.*;
import android.content.*;
import android.widget.*;
import android.app.*;

public class MainActivity extends Activity {

    boolean running;
    java.util.Timer timer;
    BroadcastReceiver receiver;
    IntentFilter intentFilter;
    String status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        android.util.Log.d("WDPS", "MainActivity.onCreate()");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        WifiNotification.initOnce(getApplicationContext());

        Button button = (Button)findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
            Button button = (Button)findViewById(R.id.button);
            if (!running) {
                startWifi();
                running = true;
                button.setText("STOP");
            } else {
                stopWifi();
                running = false;
                button.setText("START");
                status = "Ready";
            }
            }
        });
        status = "Ready";
        if (timer == null) {
            timer = new java.util.Timer();
            timer.schedule(new Updater(), 1000, 1000);
        }
        Context context = getApplicationContext();
        intentFilter = new IntentFilter();
        intentFilter.addAction(WifiDirectService.ACTION_SEND_STATUS);
        receiver = new MainBroadcastReceiver();
        registerReceiver(receiver, intentFilter);
        WifiDirectService.getStatus(context);
    }

    public void onResume() {
        super.onResume();
    }

    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        android.util.Log.d("WDPS", "MainActivity.onDestroy()");
        super.onDestroy();
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        unregisterReceiver(receiver);
    }

    private void startWifi() {
        Context ctx = getApplicationContext();
        WifiDirectService.start(ctx);
    }

    private void stopWifi() {
        Context ctx = getApplicationContext();
        WifiDirectService.stop(getApplicationContext());
    }

    public class Updater extends java.util.TimerTask {
        public void run() {
            runOnUiThread(new Runnable() {public void run() {
                TextView tv = (TextView)findViewById(R.id.status);
                tv.setText(status);
                WifiDirectService.getStatus(getApplicationContext());
            }});
        }
    }

    public class MainBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            android.util.Log.d("WDPS", "MainActivity.onReceive() action=" + action);

            switch (action) {
                case WifiDirectService.ACTION_SEND_STATUS:
                    status = intent.getStringExtra("status");
                    if (!running) {
                        running = true;
                        Button button = (Button)findViewById(R.id.button);
                        button.setText("STOP");
                    }
                    break;
            }
        }
    }
}
