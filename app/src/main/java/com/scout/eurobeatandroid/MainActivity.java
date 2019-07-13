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
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.protocol.EchoOffCommand;
import com.github.pires.obd.commands.protocol.HeadersOffCommand;
import com.github.pires.obd.commands.protocol.LineFeedOffCommand;
import com.github.pires.obd.commands.protocol.SelectProtocolCommand;
import com.github.pires.obd.commands.protocol.SpacesOffCommand;
import com.github.pires.obd.commands.protocol.TimeoutCommand;
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

    // media player
    private MediaPlayer mediaPlayer;
    private BluetoothAdapter bluetoothAdapter;
    private Bluetooth bluetooth;
    private final static int REQUEST_ENABLE_BT = 1;
    private ListView bluetoothPairedList;
    private boolean euroBeatPlaying;

    private int lastEuroBeatTrack;
    private int lastCalmTrack;
    private int lastEuroBeatBackdrop;
    private int lastCalmBackdrop;

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

        // initialise media player
        mediaPlayer = new MediaPlayer();
        euroBeatPlaying = false;

        // initialise last track index and backdrops
        lastEuroBeatTrack = 0;
        lastCalmTrack = 0;
        lastEuroBeatBackdrop = 0;
        lastCalmBackdrop = 0;

        // bluetooth adapter
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // bluetooth library init
        bluetooth = new Bluetooth(this);
        bluetooth.setBluetoothCallback(bluetoothCallback);

        // play calm music on start
        chooseCalmMusic();

        // choose calm backdrop on start
        chooseRandomBackdrop();

        // change backdrop every 30 seconds
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (euroBeatPlaying) {
                    handler.postDelayed(this, 3000);
                } else {
                    handler.postDelayed(this, 15000);
                }

                chooseRandomBackdrop();
            }
        }, 15000);

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
                    new TimeoutCommand(255).run(socket.getInputStream(), socket.getOutputStream());
                    new SelectProtocolCommand(ObdProtocols.AUTO).run(socket.getInputStream(), socket.getOutputStream());


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
            connect("OBDII");
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

                System.out.println("RPM > " + rpm);

                // show RPM on display
                runOnUiThread(new Runnable() {
                    public void run() {
                        // choose new euro beat gif
                        TextView homeText = findViewById(R.id.fullscreen_content);
                        homeText.setText("Initial D / " + rpm + " RPM");
                        homeText.setTextSize(50);


                    }
                });

                if (rpm > 5000 && !euroBeatPlaying) {
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
                        // choose new euro beat gif
                        show();
                        findViewById(R.id.connect_button).setBackgroundColor(getResources().getColor(R.color.colorAccent));
                        Button connectButton = findViewById(R.id.connect_button);
                        connectButton.setText("Error - " + e.getMessage());
                    }
                });
            }
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
            } else {
                gifDrawable = new GifDrawable( getResources(), chooseCalmBackdrop() );
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

        Random random = new Random();
        int bound = 12;
        int number = random.nextInt(bound);

        // if backdrop was same as the last, try to pick another
        if (number == lastCalmBackdrop) {
            number = random.nextInt(bound);
        }

        // update the backdrop id
        lastCalmBackdrop = number;

        switch(number){
            case 0 :
                return R.drawable.backdrop_landscape1;
            case 1 :
                return R.drawable.backdrop_landscape2;
            case 2 :
                return R.drawable.backdrop_landscape3;
            case 3 :
                return R.drawable.backdrop_landscape4;
            case 4 :
                return R.drawable.backdrop_landscape5;
            case 5 :
                return R.drawable.backdrop_landscape6;
            case 6 :
                return R.drawable.backdrop_landscape7;
            case 7 :
                return R.drawable.backdrop_landscape8;
            case 8 :
                return R.drawable.backdrop_landscape9;
            case 9 :
                return R.drawable.backdrop_landscape10;
            case 10 :
                return R.drawable.backdrop_landscape11;
            case 11 :
                return R.drawable.backdrop_landscape12;
             default:
                return R.drawable.backdrop_landscape12;
        }

    }

    /**
     * Choose a Euro Beat backdrop gif.
     */
    private int chooseEuroBeatBackdrop() {

        Random random = new Random();
        int bound = 28;
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
        float volume = 0.20f;

        Random random = new Random();
        int bound = 19;
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
        }
    }

    /**
     * Choose a euro beat track.
     */
    private void chooseEuroBeatMusic() {
        float volume = 1.0f;

        Random random = new Random();
        int bound = 3;
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
            case 1 :
                playSong("running_in_the_90s.mp3", volume, 207000);
                break;
            case 2 :
                playSong("looka_bomba.mp3", volume, 261100);
                break;
//            case 3 :
//                playSong("rider_of_the_sky.mp3", volume, 281200);
//                break;
//            case 4 :
//                playSong("night_of_fire.mp3", volume, 277800);
//                break;
//            case 5 :
//                playSong("back_on_the_rocks.mp3", volume, 260900);
//                break;
//            case 6 :
//                playSong("the_top.mp3", volume, 203400);
//                break;
//            case 3 :
//                playSong("gas_gas_gas.mp3", volume, 241200);
//                break;
//            case 6 :
//                playSong("rockin_hardcore.mp3", volume, 251400);
//                break;
            default :
                playSong("looka_bomba.mp3", volume, 261100);
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
