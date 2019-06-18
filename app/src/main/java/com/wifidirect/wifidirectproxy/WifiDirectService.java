package com.wifidirect.wifidirectproxy;

import android.app.ActivityManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.IBinder;

/**
 * An {@link Service} subclass for handling asynchronous task requests in
 * a service on a separate handler thread.
 */

public class WifiDirectService extends Service {
    private static final String ACTION_START = "com.wifidirect.wifidirectproxy.action.START";
    private static final String ACTION_STOP = "com.wifidirect.wifidirectproxy.action.STOP";

    private static final String ACTION_GET_STATUS = "com.wifidirect.wifidirectproxy.action.GET_STATUS";
    public static final String ACTION_SEND_STATUS = "com.wifidirect.wifidirectproxy.action.SEND_STATUS";

    public static String status = "Ready";

    //WifiDirect
    private static WifiP2pManager manager;
    private static WifiP2pManager.Channel channel;
    private static BroadcastReceiver receiver;
    private static IntentFilter intentFilter;

    private static int wifiClientCount = -1;

    private static ProxyServer proxyServer;

    public static boolean isRunning() {
        return manager != null;
    }

    public WifiDirectService() {}

    private void setStatus(String str) {
        status = str;
    }

    public static void start(Context context) {
        Intent intent = new Intent(context, WifiDirectService.class);
        intent.setAction(ACTION_START);
        context.startService(intent);
    }

    public static void stop(Context context) {
        Intent intent = new Intent();
        intent.setAction(ACTION_STOP);
        context.sendBroadcast(intent);
    }

    public static void getStatus(Context context) {
        Intent intent = new Intent();
        intent.setAction(ACTION_GET_STATUS);
        context.sendBroadcast(intent);
        android.util.Log.d("WDPS", "WifiDirectService.getStatus()");
    }

    public static boolean isBackgroundServiceRunning(Context context, Class<?> service)
    {
        ActivityManager manager = (ActivityManager)(context.getSystemService(ACTIVITY_SERVICE));

        if (manager != null)
        {
            for(ActivityManager.RunningServiceInfo info : manager.getRunningServices(Integer.MAX_VALUE))
            {
                if(service.getName().equals(info.service.getClassName()))
                    return true;
            }
        }
        return false;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_STICKY; //???causes crash when exiting???
        String action = intent.getAction();
        android.util.Log.d("WDPS", "WifiDirectService.onStartCommand() action=" + action);
        intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_STOP);
        intentFilter.addAction(ACTION_GET_STATUS);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        receiver = new WiFiDirectBroadcastReceiver();
        registerReceiver(receiver, intentFilter);
        startGroup();
        startForeground(1, WifiNotification.create());
        return START_STICKY;
    }

    private void startGroup() {
        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);
        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                android.util.Log.d("WDPS", "WifiDirectService.createGroup() onSuccess()");
                setStatus("Create Group (Access Point) successful");
                createProxy();
            }

            @Override
            public void onFailure(int reason) {
                android.util.Log.d("WDPS", "WifiDirectService.createGroup() onFailure()");
                setStatus("Create Group (Access Point) failed\r\nreason=" + reason);
                //onFailure still works !?!?
                createProxy();
            }
        });
    }

    private void stopGroup() {
        if (manager != null) {
            manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    if (channel != null) {
                        channel = null;
                    }
                    if (receiver != null) {
                        try {
                            unregisterReceiver(receiver);
                        } catch (Exception e) {
                            setStatus("Exception:" + e.toString());
                        }
                        receiver = null;
                    }
                    setStatus("Ready");
                }

                @Override
                public void onFailure(int i) {
                    setStatus("removeGroup() failed:reason=" + i);
                }
            });
            manager = null;
        }
        if (channel != null) {
            channel = null;
        }
        if (proxyServer != null) {
            proxyServer.close();
            proxyServer = null;
        }
    }

    private void restartGroup() {
        stopGroup();
        try {Thread.sleep(1000);} catch (Exception e) {}
        startGroup();
    }

    private void createProxy() {
        if (proxyServer == null) {
            proxyServer = new ProxyServer();
            proxyServer.start();
        }
    }

    @Override
    public void onDestroy() {
        android.util.Log.d("WDPS", "WifiDirectService.onDestroy()");
    }

    private void doStop() {
        stopForeground(true);
        setStatus("Ready");
        stopGroup();
    }

    public class WiFiDirectBroadcastReceiver extends BroadcastReceiver {

        public WiFiDirectBroadcastReceiver() {
            super();
        }

        public void getGroupInfo() {
            if (manager == null) return;
            manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
                @Override
                public void onGroupInfoAvailable(WifiP2pGroup group) {
                if (group == null) {
                    getGroupInfo();
                    return;
                }
                String ssid = group.getNetworkName();
                String pass = group.getPassphrase();
                setStatus("SSID:\r\n" + ssid + "\r\n\r\nPASS:\r\n" + pass + "\r\n\r\nSet Proxy to:\r\n192.168.49.1:8000");
                }
            });
        }

        public void getClientsInfo() {
            manager.requestPeers(channel, new WifiP2pManager.PeerListListener() {
                @Override
                public void onPeersAvailable(WifiP2pDeviceList list) {
                wifiClientCount = list.getDeviceList().size();
//                setStatus("clients = " + clientCount);
                }
            });
        }

        private void sendStatus(Context context) {
            Intent intent = new Intent();
            intent.setAction(ACTION_SEND_STATUS);
            intent.putExtra("status", status);
            context.sendBroadcast(intent);
        }

        private void checkRestart() {
            if (proxyServer == null) return;
            long now = System.currentTimeMillis();
            long last = ProxyServer.lastAccess;
            if (last < now - (15 * 1000 * 1000)) {
                restartGroup();
            }
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            android.util.Log.d("WDPS", "WifiDirectService.onReceive() action=" + action);

            switch (action) {
                case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION: {
                    // Check to see if Wi-Fi is enabled and notify appropriate activity
                    getGroupInfo();
                    break;
                }
                case WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION: {
                    // Call WifiP2pManager.requestPeers() to get a list of current peers
                    getGroupInfo();
                    getClientsInfo();
                    break;
                }
                case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION: {
                    // Respond to new connection or disconnections
                    getGroupInfo();
                    break;
                }
                case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION: {
                    // Respond to this device's wifi state changing
                    getGroupInfo();
                    break;
                }
                case ACTION_STOP: {
                    status = "Ready";
                    doStop();
                    break;
                }
                case ACTION_GET_STATUS: {
                    sendStatus(context);
                    checkRestart();
                    break;
                }
            }
        }
    }
}
