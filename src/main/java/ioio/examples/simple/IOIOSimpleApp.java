package ioio.examples.simple;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

public class IOIOSimpleApp extends Activity {
    private AudioManager audio;
    private Button preset1;
    private Button preset2;
    private Button mute;
    private SeekBar volBar;
    private Button seekUp;
    private Button seekDown;
    private ProgressBar signalBar;
    private TextView frequencyView;
    private TextView stationnameView;
    private boolean isMuted = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        startService(new Intent(this, IOIOSI4703Service.class));

        preset1 = (Button) findViewById(R.id.preset1);
        preset2 = (Button) findViewById(R.id.preset2);
        mute= (Button) findViewById(R.id.muteButton);

        volBar = (SeekBar) findViewById(R.id.volbar);
        seekUp = (Button) findViewById(R.id.seekUp);
        seekDown = (Button) findViewById(R.id.seekDown);
        signalBar = (ProgressBar) findViewById(R.id.signalBar);
        frequencyView = (TextView) findViewById(R.id.frequencyLabel);
        stationnameView = (TextView) findViewById(R.id.senderLabel);

        preset1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                IOIOSI4703Service.tune(getApplicationContext(), 1024);
                stationnameView.setText("");
            }
        });
        preset2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                IOIOSI4703Service.tune(getApplicationContext(), 1004);
                stationnameView.setText("");
            }
        });
        mute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                IOIOSI4703Service.mute(getApplicationContext(), !isMuted);
                volBar.setEnabled(isMuted);
                isMuted = !isMuted;
            }
        });
        seekUp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                IOIOSI4703Service.seek(getApplicationContext(), true, true);
                stationnameView.setText("");
            }
        });
        seekDown.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                IOIOSI4703Service.seek(getApplicationContext(), false, true);
                stationnameView.setText("");
            }
        });

        volBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                IOIOSI4703Service.setVolume(getApplicationContext(), progress);
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
                Log.d("IOIO", increase ? "raise volume" : "lower volume");
                //audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, increase ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI);
                return null;
            }
        }.doInBackground(null);
    }
}