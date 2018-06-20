package com.ardic.android.sampleiotigniteprojectwithdynamicconfigurations.activity;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.ardic.android.iot.hwnodeapptemplate.base.BaseWifiNodeDevice;
import com.ardic.android.iot.hwnodeapptemplate.listener.CompatibilityListener;
import com.ardic.android.iot.hwnodeapptemplate.listener.ThingEventListener;
import com.ardic.android.iot.hwnodeapptemplate.listener.WifiNodeManagerListener;
import com.ardic.android.iot.hwnodeapptemplate.manager.GenericWifiNodeManager;
import com.ardic.android.iot.hwnodeapptemplate.node.GenericWifiNodeDevice;
import com.ardic.android.iot.hwnodeapptemplate.service.WifiNodeService;
import com.ardic.android.iotignite.exceptions.UnsupportedVersionExceptionType;
import com.ardic.android.sampleiotigniteprojectwithdynamicconfigurations.R;
import com.ardic.android.sampleiotigniteprojectwithdynamicconfigurations.constants.DynamicNodeConstants;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class HomeActivity extends Activity implements View.OnClickListener, WifiNodeManagerListener, CompatibilityListener {

    private static final String TAG = "Dynamic Node App";
    private static final String LED_TEXT_OFF = "OFF";
    private static final String LED_TEXT_ON = "ON";
    private static final String DEGREE = "\u00b0" + "C";
    private static boolean versionError = false;
    private boolean isActiveEspConnected = false;
    private TextView tempText, humText, ledText, nodeIDText;
    private ImageView ledImageView, socketStateImageView, deleteNodeImageView;

    private GenericWifiNodeManager espManager;
    private List<BaseWifiNodeDevice> espNodeList = new CopyOnWriteArrayList<>();
    private BaseWifiNodeDevice activeEsp;
    private ThingEventListener espEventListener = new ThingEventListener() {

        // update connection state in all callbacks. Sometimes connectian callback could be triggered after other messages.
        @Override
        public void onDataReceived(final String s, final String s1, final com.ardic.android.iotignite.things.ThingData thingData) {

            Log.i(TAG, "onDataReceived [" + s + "][" + s1 + "][" + thingData.getDataList() + "]");

            if (activeEsp != null) {
                Log.i(TAG, " Socket : " + activeEsp.getWifiNodeDevice().getNodeSocket());

                setConnectionState(true);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        setUINodeId(activeEsp.getWifiNodeDevice().getHolder().getNodeId());
                        float data = Float.valueOf(thingData.getDataList().get(0));
                        int data_int = (int) data;
                        if (DynamicNodeConstants.TEMPERATURE_SENSOR.equals(s1)) {
                            tempText.setText(data_int + DEGREE);

                        } else if (DynamicNodeConstants.HUMIDITY_SENSOR.equals(s1)) {
                            humText.setText(data_int + "%");
                        }

                    }

                });
            }

        }

        @Override
        public void onConnectionStateChanged(final String s, final boolean b) {
            Log.i(TAG, "onConnectionStateChanged [" + s + "][" + b + "]");
            if (activeEsp != null) {
                setConnectionState(b);
            }
        }

        @Override
        public void onActionReceived(String s, String s1, String s2) {
            Log.i(TAG, "onActionReceived [" + s + "][" + s1 + "][" + s2 + "]");
            if (activeEsp != null) {
                setConnectionState(true);
            }
        }

        @Override
        public void onConfigReceived(String s, String s1, com.ardic.android.iotignite.things.ThingConfiguration thingConfiguration) {
            Log.i(TAG, "onConfigReceived [" + s + "][" + s1 + "][" + thingConfiguration.getDataReadingFrequency() + "]");

            if (activeEsp != null) {
                setConnectionState(true);
            }
        }

        @Override
        public void onUnknownMessageReceived(String s, String s1) {
            Log.i(TAG, "onUnknownMessageReceived [" + s + "][" + s1 + "]");

            if (activeEsp != null) {
                setConnectionState(true);
                // receive syncronization message here. Message received here becasue we set a custom message.

                try {
                    JSONObject ledStateJson = new JSONObject(s1);
                    if (ledStateJson.has("ledState")) {
                        int ledState = ledStateJson.getInt("ledState");
                        setLedUI(ledState == 0 ? false : true);
                    }
                } catch (JSONException e) {
                    Log.i(TAG, "JSONException on onUnknownMessageReceived() : " + e);

                }
            }
        }

        @Override
        public void onNodeUnregistered(String s) {
            Log.i(TAG, "onNodeUnregistered [" + s + "]");
            if (activeEsp != null) {
                // sendResetMessage();
                activeEsp.removeThingEventListener(espEventListener);

                for (BaseWifiNodeDevice dvc : espNodeList) {
                    if (dvc.getNode().getNodeID() != null && s.equals(dvc.getNode().getNodeID())) {
                        Log.i(TAG, "Removing [" + s + "]");

                        if (espNodeList.indexOf(dvc) != -1) {
                            espNodeList.remove(espNodeList.indexOf(dvc));
                            // remove from list. Update UI.
                            if (s.equals(activeEsp.getNode().getNodeID())) {
                                Log.i(TAG, "Updating... [" + s + "]");

                                updateActiveEsp();
                            }

                            break;
                        } else {
                            Log.i(TAG, "Fail to remove : [" + s + "]");
                        }
                    }
                }
            }
        }

        @Override
        public void onThingUnregistered(String s, String s1) {
            Log.i(TAG, "onThingUnregistered [" + s + "][" + s1 + "]");
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startService(new Intent(this, WifiNodeService.class));
        WifiNodeService.setCompatibilityListener(this);
        Log.i(TAG, "Dynamic Node Application started...");
        initUIComponents();
        initSensorDatas();
        initEspDeviceAndNodeManager();

    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, WifiNodeService.class));
        super.onDestroy();
        versionError = false;
    }

    private void initUIComponents() {

        // textviews
        tempText = (TextView) findViewById(R.id.temperatureTextView);
        humText = (TextView) findViewById(R.id.humidityTextView);
        ledText = (TextView) findViewById(R.id.ledTextView);
        nodeIDText = (TextView) findViewById(R.id.nodeIDTextView);

        // image view of led for on/off
        ledImageView = (ImageView) findViewById(R.id.ledImageView);
        socketStateImageView = (ImageView) findViewById(R.id.igniteStatusImgView);
        deleteNodeImageView = (ImageView) findViewById(R.id.deleteNodeImgView);
        deleteNodeImageView.setOnClickListener(this);
        ledImageView.setOnClickListener(this);
    }

    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {


        if (v.equals(ledImageView)) {
            //final Esp esp = getActiveEsp();

            if (activeEsp != null && isActiveEspConnected) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {

                        // change the background
                        if (LED_TEXT_ON.equals(ledText.getText().toString())) {
                            // send led_off message here.
                            Log.i(TAG, "Sending LED_OFF message..");

                            if (activeEsp.sendActionMessage(DynamicNodeConstants.ACTUATOR_BLUE_LED, DynamicNodeConstants.LED_OFF_ACTION)) {
                                setLedUI(false);
                            }
                        } else if (LED_TEXT_OFF.equals(ledText.getText().toString())) {
                            // send led_on message here.
                            Log.i(TAG, "Sending LED_ON message..");
                            if (activeEsp.sendActionMessage(DynamicNodeConstants.ACTUATOR_BLUE_LED, DynamicNodeConstants.LED_ON_ACTION)) {
                                setLedUI(true);
                            }
                        }
                    }


                }).start();
            } else {
                if (activeEsp == null) {
                    Log.i(TAG, "Active Esp is NULL ");
                } else {
                    Toast.makeText(getApplicationContext(), "Esp -> [" + activeEsp.getWifiNodeDevice().getHolder().getNodeId() + "] <- is disconnected!", Toast.LENGTH_LONG).show();
                }
            }

        } else if (v.equals(deleteNodeImageView)) {
            //delete active node here.
            new AlertDialog.Builder(HomeActivity.this)
                    .setTitle("Delete Node")
                    .setMessage("Are you sure you want to delete this node?")
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // continue with delete
                            if (activeEsp != null && activeEsp.getNode() != null) {
                                sendResetMessage();
                            } else {
                                Log.i(TAG, "There is no active esp!!! ");
                                showDeleteNodeErrorToast();
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            // do nothing
                        }
                    })
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .show();

        }


    }

    public void setLedUI(final boolean state) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (state) {
                    ledImageView.setImageResource(R.mipmap.led2_on);
                    ledText.setText(LED_TEXT_ON);
                } else {
                    ledImageView.setImageResource(R.mipmap.led2_off);
                    ledText.setText(LED_TEXT_OFF);
                }
            }
        });

    }

    public void setConnectionState(final boolean state) {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (state) {
                    socketStateImageView.setImageResource(R.drawable.connected);
                    isActiveEspConnected = true;
                } else {
                    socketStateImageView.setImageResource(R.drawable.disconnected);
                    isActiveEspConnected = false;
                }
            }
        });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        menu.clear();
        getMenuInflater().inflate(R.menu.main_menu, menu);

        for (GenericWifiNodeDevice dvc : espManager.getWifiNodeDeviceList()) {
            checkAndUpdateDeviceList(dvc);
        }

        if (!espNodeList.isEmpty()) {
            Log.i(TAG, "EspNode List Size : " + espNodeList.size());
            for (BaseWifiNodeDevice esp : espNodeList) {
                Log.i(TAG, "Esp : " + esp.getWifiNodeDevice().getHolder().getNodeId());
                if (esp.getWifiNodeDevice().getHolder().getNodeId() != null && !TextUtils.isEmpty(esp.getWifiNodeDevice().getHolder().getNodeId())) {
                    menu.add(Menu.NONE, Menu.NONE, Menu.NONE, esp.getWifiNodeDevice().getHolder().getNodeId());
                }
            }
        } else {
            Log.i(TAG, "EspNode List EMPTY");

        }
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        for (BaseWifiNodeDevice e : espNodeList) {
            if (!TextUtils.isEmpty(item.getTitle()) && e.getNode() != null && !TextUtils.isEmpty(e.getNode().getNodeID()) && item.getTitle().equals(e.getNode().getNodeID())) {
                activeEsp.removeThingEventListener(espEventListener);
                activeEsp = e;
                updateActiveEsp();
                break;
            }
        }
        return super.onOptionsItemSelected(item);
    }


    private void initSensorDatas() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                tempText.setText(DEGREE);
                humText.setText("%");
                setLedUI(false);
                setConnectionState(false);
                deleteNodeImageView.setVisibility(View.INVISIBLE);
                nodeIDText.setText(R.string.no_available_node);
            }
        });
    }

    @Override
    public void onWifiNodeDeviceAdded(BaseWifiNodeDevice baseWifiNodeDevice) {
        Log.i(TAG, ">>>>>>>>>> DEVICE ADDED <<<<<<<<<<");
        checkAndUpdateDeviceList(baseWifiNodeDevice);
    }

    @Override
    public void onUnsupportedVersionExceptionReceived(com.ardic.android.iotignite.exceptions.UnsupportedVersionException e) {
        Log.i(TAG, "Ignite onUnsupportedVersionExceptionReceived :  " + e);
        if (!versionError) {
            versionError = true;
            if (UnsupportedVersionExceptionType.UNSUPPORTED_IOTIGNITE_AGENT_VERSION.toString().equals(e.getMessage())) {
                Log.e(TAG, "UNSUPPORTED_IOTIGNITE_AGENT_VERSION");
                showAgentInstallationDialog();
            } else {
                Log.e(TAG, "UNSUPPORTED_IOTIGNITE_SDK_VERSION");
                showAppInstallationDialog();
            }

        }
    }

    @Override
    public void onIgniteConnectionChanged(boolean b) {
        Log.i(TAG, "Ignite Connection State Changed To -> " + b);
    }


    private void initEspDeviceAndNodeManager() {
        espManager = GenericWifiNodeManager.getInstance(getApplicationContext());
        espManager.addWifiNodeManagerListener(this);

        for (GenericWifiNodeDevice dvc : espManager.getWifiNodeDeviceList()) {
            checkAndUpdateDeviceList(dvc);
        }

    }

    private void checkAndUpdateDeviceList(BaseWifiNodeDevice device) {
        if (DynamicNodeConstants.TYPE.equals(device.getWifiNodeDevice().getNodeType())) {

            if (!espNodeList.contains(device)) {
                Log.i(TAG, "New node found adding to list.");
                espNodeList.add(device);
            } else {
                Log.i(TAG, "New node already in list.Updating...");
                if (espNodeList.indexOf(device) != -1) {
                    Log.i(TAG, "New node already in list.Removing...");
                    espNodeList.remove(espNodeList.indexOf(device));
                }
                espNodeList.add(device);
            }
        }

        updateActiveEsp();
    }


    private void showAgentInstallationDialog() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new MaterialDialog.Builder(HomeActivity.this)
                        .title("Confirm")
                        .content("Your IoT Ignite Agent version is out of date! Install the latest version?")
                        .positiveText("Agree")
                        .negativeText("Disagree")
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                openUrl("http://iotapp.link/");
                            }
                        })
                        .show();
            }
        });
    }

    private void showAppInstallationDialog() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new MaterialDialog.Builder(HomeActivity.this)
                        .title("Confirm")
                        .content("Your Demo App is out of date! Install the latest version?")
                        .positiveText("Agree")
                        .negativeText("Disagree")
                        .onPositive(new MaterialDialog.SingleButtonCallback() {
                            @Override
                            public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                                openUrl("https://download.iot-ignite.com/DynamicNodeExample/");
                            }
                        })
                        .show();
            }
        });
    }

    private void openUrl(String url) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            showDialog("Browser could not opened!");
        }
    }

    private void showDialog(String message) {
        new MaterialDialog.Builder(HomeActivity.this)
                .content(message)
                .neutralText("Ok")
                .show();
    }

    private void setUINodeId(final String nodeId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                nodeIDText.setText(nodeId);
                deleteNodeImageView.setVisibility(View.VISIBLE);
            }
        });
    }

    private void sendResetMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (activeEsp != null) {
                    updateActiveEsp();
                }
            }
        });
    }

    private void showDeleteNodeErrorToast() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), "There is no active esp to delete.", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateActiveEsp() {
        // if only one device found set this device active.
        if (espNodeList.size() > 0) {
            Log.i(TAG, " NODE LIST SIZE :" + espNodeList.size());


            if (espNodeList.size() == 1) {
                activeEsp = espNodeList.get(0);
            }

            if (activeEsp.getNode() != null) {
                activeEsp.addThingEventListener(espEventListener);
                setUINodeId(activeEsp.getNode().getNodeID());
                isActiveEspConnected = activeEsp.getNode().isConnected();
                setConnectionState(isActiveEspConnected);
            } else {
                Log.i(TAG, "ACTIVE NODE IS NULL");
            }
        } else {
            initSensorDatas();
            activeEsp = null;
        }
    }
}
