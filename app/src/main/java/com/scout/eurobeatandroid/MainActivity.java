package com.scout.eurobeatandroid;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.MediaPlayer;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.Time;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.engine.ThrottlePositionCommand;
import com.github.pires.obd.commands.pressure.IntakeManifoldPressureCommand;
import com.github.pires.obd.commands.protocol.AdaptiveTimingCommand;
import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.HeadersOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.protocol.SpacesOffCommand;
import com.github.pires.obd.commands.temperature.AirIntakeTemperatureCommand;
import com.github.pires.obd.enums.ObdProtocols;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import me.aflak.bluetooth.Bluetooth;
import me.aflak.bluetooth.interfaces.BluetoothCallback;
import me.aflak.bluetooth.interfaces.DeviceCallback;
import pl.droidsonroids.gif.GifDrawable;
import pl.droidsonroids.gif.GifImageView;

/**
 * An example full-screen activity that shows and hides the system UI (i.e.
 * status bar and navigation/system bar) with user interaction.
 */
public class MainActivity extends AppCompatActivity {

    // music media player
    private MediaPlayer mediaPlayer;
    // ae86 speed chime player
    private MediaPlayer speedChimePlayer;
    // cool vibrations player
    private MediaPlayer coolVibrationsPlayer;
    private BluetoothAdapter bluetoothAdapter;
    private Bluetooth bluetooth;
    private final static int REQUEST_ENABLE_BT = 1;
    private ListView bluetoothPairedList;
    private boolean euroBeatPlaying;
    private boolean speedChimePlaying;

    // -------- vehicle spec -----------
    private final int ACCORD_CL7_EUROR_TRIGGER = 90;
    private final int S2000_TRIGGER = 87;
    private int THROTTLE_TRIGGER_PERCENTAGE = S2000_TRIGGER;

    private final int AE86_WARNING_SPEED_TRIGGER = 102;
    private final int VTEC_RPM_TRIGGER = 5500;
    private final String OBD_DEVICE_NAME = "OBDII";
    // -------- vehicle spec -----------

    private int lastEuroBeatTrack;
    private int lastCalmTrack;
    private int lastEuroBeatBackdrop;
    private int lastCalmBackdrop;
    private int mode;

    private boolean chimeEnabled;
    private Time currentTime;

    /**
     * Whether or not the system UI should be auto-hidden after
     * {@link #AUTO_HIDE_DELAY_MILLIS} milliseconds.
     */
    private static final boolean AUTO_HIDE = true;

    /**
     * If {@link #AUTO_HIDE} is set, the number of milliseconds to wait after
     * user interaction before hiding the system UI.
     */
    private static final int AUTO_HIDE_DELAY_MILLIS = 3000;

    /**
     * Some older devices needs a small delay between UI widget updates
     * and a change of the status and navigation bar.
     */
    private static final int UI_ANIMATION_DELAY = 300;
    private final Handler mHideHandler = new Handler();
    private View mContentView;
    private final Runnable mHidePart2Runnable = new Runnable() {
        @SuppressLint("InlinedApi")
        @Override
        public void run() {
            // Delayed removal of status and navigation bar

            // Note that some of these constants are new as of API 16 (Jelly Bean)
            // and API 19 (KitKat). It is safe to use them, as they are inlined
            // at compile-time and do nothing on earlier devices.
            mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    };
    private View mControlsView;
    private final Runnable mShowPart2Runnable = new Runnable() {
        @Override
        public void run() {
            // Delayed display of UI elements
            ActionBar actionBar = getSupportActionBar();
            if (actionBar != null) {
                actionBar.show();
            }
            mControlsView.setVisibility(View.VISIBLE);
        }
    };
    private boolean mVisible;
    private final Runnable mHideRunnable = new Runnable() {
        @Override
        public void run() {
            hide();
        }
    };
    /**
     * Touch listener to use for in-layout UI controls to delay hiding the
     * system UI. This is to prevent the jarring behavior of controls going away
     * while interacting with activity UI.
     */
    private final View.OnTouchListener mDelayHideTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View view, MotionEvent motionEvent) {
            if (AUTO_HIDE) {
                delayedHide(AUTO_HIDE_DELAY_MILLIS);
            }
            return false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        mVisible = true;
        mControlsView = findViewById(R.id.fullscreen_content_controls);
        mContentView = findViewById(R.id.fullscreen_content);

        // initialise media players
        mediaPlayer = new MediaPlayer();
        speedChimePlayer = new MediaPlayer();
        coolVibrationsPlayer = new MediaPlayer();
        euroBeatPlaying = false;
        speedChimePlaying = false;
        chimeEnabled = true;

        // initialise last track index and backdrops
        lastEuroBeatTrack = 0;
        lastCalmTrack = 0;
        lastEuroBeatBackdrop = 0;
        lastCalmBackdrop = 0;

        // initialise the mode
        mode = 1;

        // bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // bluetooth library init
        bluetooth = new Bluetooth(this);
        bluetooth.setBluetoothCallback(bluetoothCallback);

        // play calm music on start
        chooseCalmMusic();

        // choose calm backdrop on start
        chooseRandomBackdrop();

        // set time
        currentTime = new Time();
        setTime();

        // change backdrop every 2 seconds
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                handler.postDelayed(this, 2000);
                chooseRandomBackdrop();
            }
        }, 2000);

        // update the time every 1 second
        final Handler timeHandler = new Handler();
        timeHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                handler.postDelayed(this, 999);
                setTime();
            }
        }, 999);

        // check if bluetooth is enabled, if not prompt
        checkBluetoothEnabled();

        // Set up the user interaction to manually show or hide the system UI.
        mContentView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                toggle();
            }
        });

        bluetoothPairedList = (ListView) findViewById(R.id.bluetooth_devices);
        bluetoothPairedList.setVisibility(View.GONE);

        // Connect Button
        findViewById(R.id.connect_button).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //chooseEuroBeat();
                showPairedDevices();
            }
        });

        // Switch to a new track when vtec is tapped
        findViewById(R.id.vtec).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchTrack();
            }
        });

        // Switch the Chime Mode
        findViewById(R.id.speedChime).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TextView chimeText = findViewById(R.id.speedChime);
                chimeEnabled = !chimeEnabled;

                if (chimeEnabled) {
                    chimeText.setBackgroundColor(getResources().getColor(R.color.transparent));
                } else {
                    chimeText.setBackgroundColor(getResources().getColor(R.color.red));
                }
            }
        });

        // Change mode when the mode is tapped
        findViewById(R.id.mode).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                changeMode();
            }
        });

        findViewById(R.id.backdrop).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (euroBeatPlaying) {
                    chooseEuroBeatBackdrop();
                } else {
                    chooseCalmBackdrop();
                }
            }
        });

        // Bluetooth device list connect listener
        bluetoothPairedList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view,int position, long id) {
                connect((bluetoothPairedList.getItemAtPosition(position).toString()));
            }});

        bluetooth.setDeviceCallback(new DeviceCallback() {
            @Override public void onDeviceConnected(final BluetoothDevice device) {
                runOnUiThread(new Runnable() {public void run() {
                    final Toast toast = Toast.makeText(getApplicationContext(), "Connected to " + device.getName(), Toast.LENGTH_SHORT);
                    toast.show();
                    bluetoothPairedList.setVisibility(View.GONE);
                    findViewById(R.id.connect_button).setBackgroundColor(getResources().getColor(R.color.colorPositive));
                    Button connectButton = findViewById(R.id.connect_button);
                    connectButton.setText("Connected to " + device.getName());
                }});

                try {
                    BluetoothSocket socket = bluetooth.getSocket();
                    new EchoOffCommand().run(socket.getInputStream(), socket.getOutputStream());
                    new LineFeedOffCommand().run(socket.getInputStream(), socket.getOutputStream());
                    new SpacesOffCommand().run(socket.getInputStream(), socket.getOutputStream());
                    new HeadersOffCommand().run(socket.getInputStream(), socket.getOutputStream());
                    new AdaptiveTimingCommand(2).run(socket.getInputStream(), socket.getOutputStream());
                    //new TimeoutCommand(3).run(socket.getInputStream(), socket.getOutputStream());
                    new SelectProtocolCommand(ObdProtocols.ISO_9141_2).run(socket.getInputStream(), socket.getOutputStream());

                    // poll the vehicle
                    while(bluetooth.isConnected()) {
                        monitorParameters();
                    }
                } catch (final Exception e) {
                    // handle errors
                    e.printStackTrace();
                }
            }
            @Override public void onDeviceDisconnected(final BluetoothDevice device, String message) {
                runOnUiThread(new Runnable() {public void run() {
                    final Toast toast = Toast.makeText(getApplicationContext(), "Disconnected from OBD-II", Toast.LENGTH_SHORT);
                    toast.show();
                    findViewById(R.id.connect_button).setBackgroundColor(getResources().getColor(R.color.colorPrimary));
                }});

            }
            @Override public void onMessage(final String message) {
                runOnUiThread(new Runnable() { public void run() {final Toast toast = Toast.makeText(getApplicationContext(), "Message Received " + message, Toast.LENGTH_SHORT);toast.show(); }});
            }
            @Override public void onError(final int message) {
                runOnUiThread(new Runnable() {public void run() {final Toast toast = Toast.makeText(getApplicationContext(), "Error " + message, Toast.LENGTH_LONG); toast.show();}});
            }
            @Override public void onConnectError(final BluetoothDevice device, final String message) {
                runOnUiThread(new Runnable() {public void run() {final Toast toast = Toast.makeText(getApplicationContext(), "Error Connecting to " + device.getName() + " - " + message, Toast.LENGTH_LONG); toast.show();}});
            }
        });
    }

    /**
     * Bluetooth Start Handler
     */
    @Override
    protected void onStart() {
        super.onStart();
        bluetooth.onStart();

        // attempt to connect on app start if bluetooth enabled
        if (bluetooth.isEnabled()) {
            connect(OBD_DEVICE_NAME);
        }
    }

    /**
     * Bluetooth End Handler
     */
    @Override
    protected void onStop() {
        super.onStop();
        bluetooth.onStop();
    }

    /**
     * Set time.
     */
    private void setTime() {
        currentTime.setToNow();
        TextView time = findViewById(R.id.time);

        String currentHour = String.valueOf(currentTime.hour);
        if (String.valueOf(currentTime.hour).length() < 2) {
            currentHour = "0" + currentHour;
        }

        String currentMinute = String.valueOf(currentTime.minute);
        if (String.valueOf(currentTime.minute).length() < 2) {
            currentMinute = "0" + currentMinute;
        }

        String currentSecond = String.valueOf(currentTime.second);
        if (String.valueOf(currentTime.second).length() < 2) {
            currentSecond = "0" + currentSecond;
        }

        time.setText(currentHour + ":" + currentMinute + ":" + currentSecond);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        bluetooth.onActivityResult(requestCode, resultCode);
    }

    private BluetoothCallback bluetoothCallback = new BluetoothCallback() {
        @Override public void onBluetoothTurningOn() {}
        @Override public void onBluetoothTurningOff() {}
        @Override public void onBluetoothOff() {}
        @Override public void onUserDeniedActivation() {}

        @Override
        public void onBluetoothOn() {

        }
    };

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);

        // Trigger the initial hide() shortly after the activity has been
        // created, to briefly hint to the user that UI controls
        // are available.
        delayedHide(100);
    }

    /**
     * Toggle the UI status bars on or off
     */
    private void toggle() {
        if (mVisible) {
            hide();
        } else {
            show();
        }
    }

    private void hide() {
        // Hide UI first
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
        mControlsView.setVisibility(View.GONE);
        mVisible = false;

        // Schedule a runnable to remove the status and navigation bar after a delay
        mHideHandler.removeCallbacks(mShowPart2Runnable);
        mHideHandler.postDelayed(mHidePart2Runnable, UI_ANIMATION_DELAY);
    }

    @SuppressLint("InlinedApi")
    private void show() {
        // Show the system bar
        mContentView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        mVisible = true;

        // Schedule a runnable to display UI elements after a delay
        mHideHandler.removeCallbacks(mHidePart2Runnable);
        mHideHandler.postDelayed(mShowPart2Runnable, UI_ANIMATION_DELAY);
    }

    /**
     * Prompt the user to enable bluetooth is not enabled
     */
    private void checkBluetoothEnabled() {
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    /**
     * Show paired bluetooth devices
     */
    private void showPairedDevices() {

        // show list view
        bluetoothPairedList.setVisibility(View.VISIBLE);

        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            List<String> devices = new ArrayList<String>();
            for(BluetoothDevice bt : pairedDevices)
                devices.add(bt.getName());

            ArrayAdapter adapter = new ArrayAdapter<String>(this,
                    R.layout.list_item, devices);
            bluetoothPairedList.setAdapter(adapter);
        }
    }

    /**
     * Connect to a bluetooth device
     * @param name - the device name
     */
    private void connect(String name) {
        Toast.makeText(getApplicationContext(),"Attempting to Connect to " + name, Toast.LENGTH_SHORT).show();
        bluetooth.connectToName(name);
    }

    /**
     * Monitor the vehicle RPM and Throttle %.
     */
    private void monitorParameters() {

            try {
                BluetoothSocket socket = bluetooth.getSocket();

                RPMCommand rpmCommand = new RPMCommand();
                rpmCommand.run(socket.getInputStream(), socket.getOutputStream());
                final int rpm = rpmCommand.getRPM();
                final String rpmResult = rpmCommand.getFormattedResult();

                SpeedCommand speedCommand = new SpeedCommand();
                speedCommand.run(socket.getInputStream(), socket.getOutputStream());
                final String speedResult = speedCommand.getFormattedResult();
                final float speed = speedCommand.getMetricSpeed();
               // final String speedResult = "N/A";

//                EngineCoolantTemperatureCommand coolantCommand = new EngineCoolantTemperatureCommand();
//                coolantCommand.run(socket.getInputStream(), socket.getOutputStream());
//                final String coolantResult = coolantCommand.getFormattedResult();
                final String coolantResult = "N/A";

                ThrottlePositionCommand throttlePositionCommand = new ThrottlePositionCommand();
                throttlePositionCommand.run(socket.getInputStream(), socket.getOutputStream());
                final float throttle = throttlePositionCommand.getPercentage();
                final String thottlePositionResult = throttlePositionCommand.getFormattedResult();

//                OilTempCommand oilTempCommand = new OilTempCommand();
//                oilTempCommand.run(socket.getInputStream(), socket.getOutputStream());
//                final String oilTempResult = oilTempCommand.getFormattedResult();
                final String oilTempResult = "N/A";

                IntakeManifoldPressureCommand intakeManifoldPressureCommand = new IntakeManifoldPressureCommand();
                intakeManifoldPressureCommand.run(socket.getInputStream(), socket.getOutputStream());
                final String intakeManifoldPressureResult = intakeManifoldPressureCommand.getFormattedResult();
//                final String intakeManifoldPressureResult = "N/A";

//                FuelPressureCommand fuelPressureCommand = new FuelPressureCommand();
//                fuelPressureCommand.run(socket.getInputStream(), socket.getOutputStream());
//                final String fuelPressureResult = fuelPressureCommand.getFormattedResult();
                final String fuelPressureResult = "N/A";

                AirIntakeTemperatureCommand airIntakeTemperatureCommand = new AirIntakeTemperatureCommand();
                airIntakeTemperatureCommand.run(socket.getInputStream(), socket.getOutputStream());
                final String airIntakeTemperatureResult = airIntakeTemperatureCommand.getFormattedResult();
                final float airTemp = airIntakeTemperatureCommand.getTemperature();
//                final String airIntakeTemperatureResult = "N/A";

//                ModuleVoltageCommand moduleVoltageCommand = new ModuleVoltageCommand();
//                moduleVoltageCommand.run(socket.getInputStream(), socket.getOutputStream());
//                final String moduleVoltageResult = moduleVoltageCommand.getFormattedResult();
                final String moduleVoltageResult = "N/A";

//                FuelLevelCommand fuelLevelCommand = new FuelLevelCommand();
//                fuelLevelCommand.run(socket.getInputStream(), socket.getOutputStream());
//                final String fuelLevelResult = fuelLevelCommand.getFormattedResult();
                final String fuelLevelResult = "N/A";

                // show RPM on display
                runOnUiThread(new Runnable() {
                    public void run() {

                        // update live values
                        TextView rpmText = findViewById(R.id.rpmValue);
                        rpmText.setText(rpmResult);

                        if (rpm > 7500) {
                            rpmText.setTextColor(getResources().getColor(R.color.red));
                        } else {
                            rpmText.setTextColor(getResources().getColor(R.color.colorWhite));
                        }

                        TextView speedText = findViewById(R.id.speedValue);
                        speedText.setText(speedResult);

                        if (speed > 100) {
                            speedText.setTextColor(getResources().getColor(R.color.red));
                        } else {
                            speedText.setTextColor(getResources().getColor(R.color.colorWhite));
                        }


                        TextView throttleText = findViewById(R.id.throttleValue);
                        throttleText.setText(thottlePositionResult);

                        if (throttle > 70) {
                            throttleText.setTextColor(getResources().getColor(R.color.red));
                        } else {
                            throttleText.setTextColor(getResources().getColor(R.color.colorWhite));
                        }

                        TextView intakeManifoldText = findViewById(R.id.intakeManifoldValue);
                        intakeManifoldText.setText(intakeManifoldPressureResult);

                        TextView intakeAirTempText = findViewById(R.id.intakeAirTempValue);
                        intakeAirTempText.setText(airIntakeTemperatureResult);

                        if (airTemp < 10) {
                            intakeAirTempText.setTextColor(getResources().getColor(R.color.lightBlue));
                        } else if (airTemp < 15){
                            intakeAirTempText.setTextColor(getResources().getColor(R.color.darkBlue));
                        } else if (airTemp < 20){
                            intakeAirTempText.setTextColor(getResources().getColor(R.color.green));
                        } else {
                            intakeAirTempText.setTextColor(getResources().getColor(R.color.red));
                        }

                        TextView vtecText = findViewById(R.id.vtec);
                        vtecText.setText("VTEC");

                        if (rpm > VTEC_RPM_TRIGGER) {
                            vtecText.setBackgroundColor(getResources().getColor(R.color.red));
                        } else {
                            vtecText.setBackgroundColor(getResources().getColor(R.color.transparent));
                        }
                    }
                });

                // AE86 speed chime logic
                if (speed > AE86_WARNING_SPEED_TRIGGER && !speedChimePlaying && chimeEnabled && mode == 3) {
                    playSpeedChime();
                    speedChimePlaying = true;
                }

                // music trigger logic
                if (throttle > THROTTLE_TRIGGER_PERCENTAGE && !euroBeatPlaying && mode == 3) {

                        // choose euro beat song
                        chooseEuroBeatMusic();

                        runOnUiThread(new Runnable() {
                            public void run() {
                                // choose new euro beat gif
                                GifDrawable gifDrawable;
                                GifImageView imageView = findViewById(R.id.backdrop);
                                try {
                                    gifDrawable = new GifDrawable(getResources(), chooseEuroBeatBackdrop());
                                    imageView.setImageDrawable(gifDrawable);

                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                }
            } catch( final Exception e){
                // handle errors
                e.printStackTrace();
                runOnUiThread(new Runnable() {
                    public void run() {
                        // update bluetooth connect button to show error
                        findViewById(R.id.connect_button).setBackgroundColor(getResources().getColor(R.color.colorAccent));
                        Button connectButton = findViewById(R.id.connect_button);
                        connectButton.setText("Error - " + e.getMessage());
                    }
                });
            }
        }

    /**
     * Switch to the next track
     */
    private void switchTrack() {
        mediaPlayer.stop();
        mediaPlayer.reset();
        euroBeatPlaying = false;
        ImageView hondaPower = findViewById(R.id.hondaPower);

        if (mode == 2) {
            chooseFullEurobeatMusic();
            hondaPower.setImageResource(chooseCalmWallpaperBackdrop());
        } else if (mode == 3) {
            // play calm music
            chooseCalmMusic();

            // set euro beat indicator off
            euroBeatPlaying = false;

            // show new calm gif on resume
            GifDrawable gifDrawable;
            GifImageView imageView = findViewById(R.id.backdrop);
            try {
                gifDrawable = new GifDrawable( getResources(), chooseCalmBackdrop() );
                imageView.setImageDrawable(gifDrawable);
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            chooseCalmMusic();
        }
    }

    /**
     * Play the AE86 Speed Chime
     */
    private void playSpeedChime() {
        speedChimePlayer.reset();
        String path = "ae86Chime.m4a";
        float volume = 1f;

        AssetFileDescriptor afd = null;
        try {
            afd = getAssets().openFd(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            speedChimePlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            speedChimePlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        speedChimePlayer.setVolume(volume, volume);
        speedChimePlayer.start();

        // set state to false
        speedChimePlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mediaPlayer) {
                speedChimePlaying = false;
            }
        });
    }

    /**
     * Play the cool vibrations words.
     */
    private void playCoolVibrations() {
        coolVibrationsPlayer.reset();
        String path = "coolVibrations.m4a";
        float volume = 1f;

        AssetFileDescriptor afd = null;
        try {
            afd = getAssets().openFd(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            coolVibrationsPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            coolVibrationsPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        coolVibrationsPlayer.setVolume(volume, volume);
        coolVibrationsPlayer.start();
    }

    /**
     * Change the mode
     */
    private void changeMode() {
        TextView modeText = findViewById(R.id.mode);
        ImageView hondaPower = findViewById(R.id.hondaPower);

        // change the music
        mediaPlayer.stop();
        mediaPlayer.reset();
        euroBeatPlaying = false;

        if (mode == 1) {
            mode = 2;
            hondaPower.setImageResource(chooseCalmWallpaperBackdrop());
            chooseFullEurobeatMusic();
        } else if (mode == 2) {
            mode = 3;
            hondaPower.setVisibility(View.GONE);
            playCoolVibrations();
            chooseCalmMusic();
        } else if (mode == 3) {
            mode = 1;
            hondaPower.setVisibility(View.VISIBLE);
            hondaPower.setImageResource(R.drawable.power);
            chooseCalmMusic();
        }

        // update mode text
        modeText.setText("Mode $0" + mode);
        }

    /**
     * Play a song based on certain parameters.
     * @param path the path to the file
     * @param volume the floating point volume level
     * @param startTime the start time for the given song
     */
    private void playSong(String path, float volume, int startTime) {
        mediaPlayer.reset();

        AssetFileDescriptor afd = null;
        try {
            afd = getAssets().openFd(path);
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            mediaPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mediaPlayer.setVolume(volume, volume);
        mediaPlayer.seekTo(startTime);
        mediaPlayer.start();

        // returns to playing calm music
        mediaPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mediaPlayer) {

                if (mode == 1) {
                    // play calm music
                    chooseCalmMusic();
                } else if (mode == 2) {
                    chooseFullEurobeatMusic();
                    ImageView hondaPower = findViewById(R.id.hondaPower);
                    hondaPower.setImageResource(chooseCalmWallpaperBackdrop());
                } else {
                    // play calm music
                    chooseCalmMusic();

                    // set euro beat indicator off
                    euroBeatPlaying = false;

                    // show new calm gif on resume
                    GifDrawable gifDrawable;
                    GifImageView imageView = findViewById(R.id.backdrop);
                    try {
                        gifDrawable = new GifDrawable( getResources(), chooseCalmBackdrop() );
                        imageView.setImageDrawable(gifDrawable);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    /**
     * Choose a random backdrop
     */
    private void chooseRandomBackdrop() {
        GifDrawable gifDrawable;
        GifImageView imageView = findViewById(R.id.backdrop);
        try {
            if (euroBeatPlaying) {
                gifDrawable = new GifDrawable( getResources(), chooseEuroBeatBackdrop() );
                gifDrawable.setLoopCount(5);
            } else {
                gifDrawable = new GifDrawable( getResources(), chooseCalmWallpaperBackdrop() );
                gifDrawable.setLoopCount(100);
            }
            imageView.setImageDrawable(gifDrawable);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Choose a calm backdrop gif.
     */
    private int chooseCalmBackdrop() {
        return R.drawable.street1;
    }

    /**
     * Choose a calm mode 2 backdrop.
     */
    private int chooseCalmWallpaperBackdrop() {

        Random random = new Random();
        int bound = 21;
        int number = random.nextInt(bound);

        switch(number){
            case 0 :
                return R.drawable.a1;
            case 1 :
                return R.drawable.a2;
            case 2 :
                return R.drawable.a3;
            case 3 :
                return R.drawable.a4;
            case 4 :
                return R.drawable.a5;
            case 5 :
                return R.drawable.a6;
            case 6 :
                return R.drawable.a7;
            case 7 :
                return R.drawable.a8;
            case 8 :
                return R.drawable.a9;
            case 9 :
                return R.drawable.a10;
            case 10 :
                return R.drawable.a11;
            case 11 :
                return R.drawable.a12;
            case 12 :
                return R.drawable.a13;
            case 13 :
                return R.drawable.a14;
            case 14 :
                return R.drawable.a15;
            case 15 :
                return R.drawable.a16;
            case 16 :
                return R.drawable.a17;
            case 17 :
                return R.drawable.a18;
            case 18 :
                return R.drawable.a19;
            case 19 :
                return R.drawable.a20;
            default:
                return R.drawable.a1;
        }

    }

    /**
     * Choose a Euro Beat backdrop gif.
     */
    private int chooseEuroBeatBackdrop() {

        Random random = new Random();
        int bound = 63;
        int number = random.nextInt(bound);

        // if backdrop was same as the last, try to pick another
        if (number == lastEuroBeatBackdrop) {
            number = random.nextInt(bound);
        }

        // update the backdrop id
        lastEuroBeatBackdrop = number;

        switch(number){
            case 0 :
                return R.drawable.eurobeat1;
            case 1 :
                return R.drawable.eurobeat2;
            case 2 :
                return R.drawable.eurobeat3;
            case 3 :
                return R.drawable.eurobeat4;
            case 4 :
                return R.drawable.eurobeat5;
            case 5 :
                return R.drawable.eurobeat6;
            case 6 :
                return R.drawable.eurobeat7;
            case 7 :
                return R.drawable.eurobeat8;
            case 8 :
                return R.drawable.eurobeat9;
            case 9 :
                return R.drawable.eurobeat10;
            case 10 :
                return R.drawable.eurobeat11;
            case 11 :
                return R.drawable.eurobeat12;
            case 12 :
                return R.drawable.eurobeat13;
            case 13 :
                return R.drawable.eurobeat14;
            case 14 :
                return R.drawable.eurobeat15;
            case 15 :
                return R.drawable.eurobeat16;
            case 16 :
                return R.drawable.eurobeat17;
            case 17 :
                return R.drawable.eurobeat18;
            case 18 :
                return R.drawable.eurobeat19;
            case 19 :
                return R.drawable.eurobeat20;
            case 20 :
                return R.drawable.eurobeat21;
            case 21 :
                return R.drawable.eurobeat22;
            case 22 :
                return R.drawable.eurobeat23;
            case 23 :
                return R.drawable.eurobeat24;
            case 24 :
                return R.drawable.eurobeat25;
            case 25 :
                return R.drawable.eurobeat26;
            case 26 :
                return R.drawable.eurobeat27;
            case 27 :
                return R.drawable.eurobeat28;
            case 28 :
                return R.drawable.eurobeat29;
            case 29 :
                return R.drawable.eurobeat30;
            case 30 :
                return R.drawable.eurobeat31;
            case 31 :
                return R.drawable.eurobeat32;
            case 32 :
                return R.drawable.eurobeat33;
            case 33 :
                return R.drawable.eurobeat34;
            case 34 :
                return R.drawable.eurobeat35;
            case 35 :
                return R.drawable.eurobeat36;
            case 36 :
                return R.drawable.eurobeat37;
            case 37 :
                return R.drawable.eurobeat38;
            case 38 :
                return R.drawable.eurobeat39;
            case 39 :
                return R.drawable.eurobeat40;
            case 40 :
                return R.drawable.eurobeat41;
            case 41 :
                return R.drawable.eurobeat42;
            case 42 :
                return R.drawable.eurobeat43;
            case 43 :
                return R.drawable.eurobeat44;
            case 44 :
                return R.drawable.eurobeat45;
            case 45 :
                return R.drawable.eurobeat46;
            case 46 :
                return R.drawable.eurobeat47;
            case 47 :
                return R.drawable.eurobeat48;
            case 48 :
                return R.drawable.eurobeat49;
            case 49 :
                return R.drawable.eurobeat50;
            case 50 :
                return R.drawable.eurobeat51;
            case 51 :
                return R.drawable.eurobeat52;
            case 52 :
                return R.drawable.eurobeat53;
            case 53 :
                return R.drawable.eurobeat54;
            case 54 :
                return R.drawable.eurobeat55;
            case 55 :
                return R.drawable.eurobeat56;
            case 56 :
                return R.drawable.eurobeat57;
            case 57 :
                return R.drawable.eurobeat58;
            case 58 :
                return R.drawable.eurobeat59;
            case 59 :
                return R.drawable.eurobeat60;
            case 60 :
                return R.drawable.eurobeat61;
            case 61 :
                return R.drawable.eurobeat62;
            case 62 :
                return R.drawable.eurobeat63;
            default:
                return R.drawable.eurobeat1;
        }

    }

    /**
     * Play a calm song.
     */
    private void chooseCalmMusic() {
        // Calm Music Source https://www.youtube.com/watch?v=jrTMMG0zJyI
        String path1 = "calm.mp3";
        float volume = 0.30f;

        Random random = new Random();
        int bound = 33;
        int number = random.nextInt(bound);

        // if track was same as the last, try to pick another
        if (number == lastCalmTrack) {
            number = random.nextInt(bound);
        }

        // update the track id
        lastCalmTrack = number;

        switch(number){
            case 0 :
                playSong(path1, volume, 0);
                break;
            case 1 :
                playSong(path1, volume, 195800);
                break;
            case 2 :
                playSong(path1, volume, 314400);
                break;
            case 3 :
                playSong(path1, volume, 448800);
                break;
            case 4 :
                playSong(path1, volume, 667200);
                break;
            case 5 :
                playSong(path1, volume, 848800);
                break;
            case 6 :
                playSong(path1, volume, 1032000);
                break;
            case 7 :
                playSong(path1, volume, 1167000);
                break;
            case 8 :
                playSong(path1, volume, 1383000);
                break;
            case 9 :
                playSong(path1, volume, 1532400);
                break;
            case 10 :
                playSong(path1, volume, 1815800);
                break;
            case 11 :
                playSong(path1, volume, 2003400);
                break;
            case 12 :
                playSong(path1, volume, 2174400);
                break;
            case 13 :
                playSong(path1, volume, 2302200);
                break;
            case 14 :
                playSong("calm2.mp3", volume, 0);
                break;
            case 15 :
                playSong("calm3.mp3", volume, 0);
                break;
            case 16 :
                playSong("calm4.mp3", volume, 0);
                break;
            case 17 :
                playSong("calm5.mp3", volume, 0);
                break;
            case 18 :
                playSong("calm6.mp3", volume, 0);
                break;
            case 19 :
                playSong("calm7.mp3", volume, 0);
                break;
            case 20 :
                playSong("calm8.mp3", volume, 0);
                break;
            case 21 :
                playSong("calm9.mp3", volume, 7000);
                break;
            case 22 :
                playSong("calm10.mp3", volume, 0);
                break;
            case 23 :
                playSong("calm11.mp3", volume, 0);
                break;
            case 24 :
                playSong("calm12.mp3", volume, 0);
                break;
            case 25 :
                playSong("calm13.mp3", volume, 0);
                break;
            case 26 :
                playSong("calm14.mp3", volume, 0);
                break;
            case 27 :
                playSong("calm15.mp3", volume, 0);
                break;
            case 28 :
                playSong("calm16.mp3", volume, 0);
                break;
            case 29 :
                playSong("calm17.mp3", volume, 0);
                break;
            case 30 :
                playSong("calm18.mp3", volume, 0);
                break;
            case 31 :
                playSong("calm19.mp3", volume, 0);
                break;
            case 32 :
                playSong("calm20.mp3", volume, 0);
                break;
        }
    }

    /**
     * Choose a euro beat track.
     */
    private void chooseEuroBeatMusic() {
        float volume = 1.0f;

        Random random = new Random();
        int bound = 5;
        int number = random.nextInt(bound);

        // if track was same as the last, try to pick another
        if (number == lastEuroBeatTrack) {
            number = random.nextInt(bound);
        }

        // update the track id
        lastEuroBeatTrack = number;

        // set eurobeat active
        euroBeatPlaying = true;
        
        switch(number){
            case 0 :
                playSong("deja_vu.mp3", volume, 209400);
                break;
//            case 1 :
//                playSong("running_in_the_90s.mp3", volume, 207000);
//                break;
            case 1 :
                playSong("looka_bomba.mp3", volume, 261100);
                break;
//            case 4 :
//                playSong("rider_of_the_sky.mp3", volume, 281200);
//                break;
            case 2 :
                playSong("night_of_fire.mp3", volume, 277800);
                break;
//            case 6 :
//                playSong("back_on_the_rocks.mp3", volume, 260900);
//                break;
//            case 7 :
//                playSong("gas_gas_gas.mp3", volume, 241200);
//                break;
//            case 3 :
//                playSong("king_of_the_world.mp3", volume, 250400);
//                break;
//            case 3 :
//                playSong("let_it_burn.mp3", volume, 207600);
//                break;
//            case 10 :
//                playSong("dont_turn_it_off.mp3", volume, 275600);
//                break;
            case 3 :
                playSong("beat_of_the_rising_sun.mp3", volume, 186800);
                break;
            case 4 :
                playSong("spitfire.mp3", volume, 286000);
                break;
//            case 5:
//                playSong("spitfire_dr.mp3", volume, 233000);
//                break;
            default :
                playSong("deja_vu.mp3", volume, 261100);
                break;
        }
    }

    /**
     * Choose a full eurobeat track.
     */
    private void chooseFullEurobeatMusic() {
        float volume = 0.50f;

        Random random = new Random();
        int bound = 46;
        int number = random.nextInt(bound);

        // if track was same as the last, try to pick another
        if (number == lastEuroBeatTrack) {
            number = random.nextInt(bound);
        }

        // update the track id
        lastEuroBeatTrack = number;

        switch(number){
            case 0 :
                playSong("deja_vu.mp3", volume, 0);
                break;
            case 1 :
                playSong("running_in_the_90s.mp3", volume, 0);
                break;
            case 2 :
                playSong("looka_bomba.mp3", volume, 0);
                break;
            case 3 :
                playSong("rider_of_the_sky.mp3", volume, 0);
                break;
            case 4 :
                playSong("night_of_fire.mp3", volume, 0);
                break;
            case 5 :
                playSong("back_on_the_rocks.mp3", volume, 0);
                break;
            case 6:
                playSong("gas_gas_gas.mp3", volume, 0);
                break;
            case 7 :
                playSong("king_of_the_world.mp3", volume, 0);
                break;
            case 8 :
                playSong("let_it_burn.mp3", volume, 0);
                break;
            case 9 :
                playSong("dont_turn_it_off.mp3", volume, 0);
                break;
            case 10:
                playSong("beat_of_the_rising_sun.mp3", volume, 0);
                break;
            case 11:
                playSong("around_the_world.mp3", volume, 0);
                break;
            case 12:
                playSong("break_in2_the_nite.mp3", volume, 0);
                break;
            case 13:
                playSong("dancing_out_of_danger.mp3", volume, 0);
                break;
             case 14:
                playSong("running_in_the_90s_dave_rodgers.mp3", volume, 0);
                break;
             case 15:
                playSong("spitfire.mp3", volume, 0);
                break;
             case 16:
                playSong("space_boy.mp3", volume, 0);
                break;
             case 17:
                playSong("no_one_sleep_in_tokyo.mp3", volume, 0);
                break;
             case 18:
                playSong("welcome_to_the_universe.mp3", volume, 0);
                break;
             case 19:
                playSong("hot_hot_racin_car.mp3", volume, 0);
                break;
             case 20:
                playSong("disconnected.mp3", volume, 0);
                break;
             case 21:
                playSong("boom_boom_japan.mp3", volume, 0);
                break;
             case 22:
                playSong("burning_desire.mp3", volume, 0);
                break;
             case 23:
                playSong("rage_your_dream.mp3", volume, 0);
                break;
             case 24:
                playSong("move_break_in2_the_nite.mp3", volume, 0);
                break;
             case 25:
                playSong("move_around_the_world.mp3", volume, 0);
                break;
             case 26:
                playSong("blazin_beat.mp3", volume, 0);
                break;
             case 27:
                playSong("dogfight.mp3", volume, 0);
                break;
             case 28:
                playSong("heartbeat.mp3", volume, 0);
                break;
             case 29:
                playSong("max_power.mp3", volume, 0);
                break;
             case 30:
                playSong("dance_around_the_world.mp3", volume, 0);
                break;
             case 31:
                playSong("i_need_your_love.mp3", volume, 0);
                break;
             case 32:
                playSong("lets_go_come_on.mp3", volume, 0);
                break;
            case 33:
                playSong("speedy_speedy_boy.mp3", volume, 0);
                break;
            case 34:
                playSong("power_two.mp3", volume, 0);
                break;
            case 35:
                playSong("like_a_thunder.mp3", volume, 0);
                break;
            case 36:
                playSong("get_me_power.mp3", volume, 0);
                break;
            case 37:
                playSong("break_the_night.mp3", volume, 0);
                break;
            case 38:
                playSong("dont_you_forget_about_my_love.mp3", volume, 0);
                break;
            case 39:
                playSong("dancing.mp3", volume, 0);
                break;
            case 40:
                playSong("everybody_is_looking.mp3", volume, 0);
                break;
            case 41:
                playSong("spitfire_dr.mp3", volume, 0);
                break;
            case 42:
                playSong("space_rocket.mp3", volume, 0);
                break;
            case 43:
                playSong("kingdom_come.mp3", volume, 0);
                break;
            case 44:
                playSong("project_d.mp3", volume, 0);
                break;
             default :
                playSong("looka_bomba.mp3", volume, 0);
                break;
        }
    }

    /**
     * Schedules a call to hide() in delay milliseconds, canceling any
     * previously scheduled calls.
     */
    private void delayedHide(int delayMillis) {
        mHideHandler.removeCallbacks(mHideRunnable);
        mHideHandler.postDelayed(mHideRunnable, delayMillis);
    }
}
