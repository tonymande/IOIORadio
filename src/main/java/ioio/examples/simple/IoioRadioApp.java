package ioio.examples.simple;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import org.genas.headunit.IIoioService;

public class IoioRadioApp extends Activity {
    private IOIORadioBroadcastReceiver broadcastReceiver;
    private AudioManager audio;
    private Button preset1;
    private Button preset2;
    private Button preset3;
    private Button mute;
    private SeekBar volBar;
    private Button seekUp;
    private Button seekDown;
    private ProgressBar signalBar;
    private TextView frequencyView;
    private TextView stationnameView;
    private boolean isMuted = false;

    IIoioService service;
    IoioServiceConnection connection;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        initService();

        preset1 = (Button) findViewById(R.id.preset1);
        preset2 = (Button) findViewById(R.id.preset2);
        preset3 = (Button) findViewById(R.id.preset3);
        mute= (Button) findViewById(R.id.muteButton);

        volBar = (SeekBar) findViewById(R.id.volbar);
        seekUp = (Button) findViewById(R.id.seekUp);
        seekDown = (Button) findViewById(R.id.seekDown);
        signalBar = (ProgressBar) findViewById(R.id.signalBar);
        frequencyView = (TextView) findViewById(R.id.frequencyLabel);
        stationnameView = (TextView) findViewById(R.id.senderLabel);

        preset1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    service.tune(1024);
                    stationnameView.setText("");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        preset2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    service.tune(1004);
                    stationnameView.setText("");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        preset3.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    service.sendCommand();
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        mute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    service.mute(!isMuted);
                    volBar.setEnabled(isMuted);
                    isMuted = !isMuted;
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        seekUp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    service.seek(true, true);
                    stationnameView.setText("");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });
        seekDown.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                try {
                    service.seek(false, true);
                    stationnameView.setText("");
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
        });

        volBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                try {
                    service.setVolume(progress);
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        enableUi(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(broadcastReceiver == null) {
            broadcastReceiver = new IOIORadioBroadcastReceiver();
        }
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(IoioActions.ENABLEUI);
        intentFilter.addAction(IoioActions.DISABLEUI);
        intentFilter.addAction(IoioActions.UPDATE_SIGNAL_STRENGTH);
        intentFilter.addAction(IoioActions.UPDATE_FREQUENCY);
        intentFilter.addAction(IoioActions.UPDATE_VOLUME);
        registerReceiver(broadcastReceiver, intentFilter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(broadcastReceiver != null) {
            unregisterReceiver(broadcastReceiver);
        }
    }

    private void enableUi(final boolean enable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                preset1.setEnabled(enable);
                preset2.setEnabled(enable);
                mute.setEnabled(enable);
                volBar.setEnabled(enable);
                seekDown.setEnabled(enable);
                seekUp.setEnabled(enable);
            }
        });
    }

    private void setVolume(final boolean increase) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... params) {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, increase ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                return null;
            }
        }.doInBackground(null);
    }

    /** Binds this activity to the service. */
    private void initService() {
        connection = new IoioServiceConnection();
        Intent intend = new Intent();
        intend.setAction("org.genas.headunit.IoioService.STARTSERVICE");
        boolean ret = bindService(intend, connection, Context.BIND_AUTO_CREATE);
        Log.d("IoioRadioApp", "initService() bound with " + ret);
    }

    /** Unbinds this activity from the service. */
    private void releaseService() {
        unbindService(connection);
        connection = null;
        Log.d("IoioRadioApp", "releaseService() unbound.");
    }

    private class IOIORadioBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case IoioActions.ENABLEUI:
                    enableUi(true);
                    break;
                case IoioActions.DISABLEUI:
                    enableUi(false);
                    break;
                case IoioActions.UPDATE_SIGNAL_STRENGTH:
                    int strength = intent.getIntExtra(IoioActions.EXTRA_PARAM1, 50);
                    signalBar.setProgress(strength);
                    break;
                case IoioActions.UPDATE_FREQUENCY:
                    frequencyView.setText(intent.getStringExtra(IoioActions.EXTRA_PARAM1));
                    break;
                case IoioActions.UPDATE_VOLUME:
                    setVolume(intent.getBooleanExtra(IoioActions.EXTRA_PARAM1, false));
                    break;
            }
        }
    }

    private class IoioServiceConnection implements ServiceConnection {

        public void onServiceConnected(ComponentName name, IBinder boundService) {
            service = IIoioService.Stub.asInterface((IBinder) boundService);
            Log.d("IoioRadioApp", "onServiceConnected() connected");
        }

        public void onServiceDisconnected(ComponentName name) {
            service = null;
            Log.d("IoioRadioApp", "onServiceDisconnected() disconnected");
        }
    }
}