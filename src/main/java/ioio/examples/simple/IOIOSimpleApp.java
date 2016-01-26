package ioio.examples.simple;

import ioio.lib.api.AnalogInput;
import ioio.lib.api.DigitalInput;
import ioio.lib.api.DigitalOutput;
import ioio.lib.api.IOIO;
import ioio.lib.api.PwmOutput;
import ioio.lib.api.TwiMaster;
import ioio.lib.api.exception.ConnectionLostException;
import ioio.lib.util.BaseIOIOLooper;
import ioio.lib.util.IOIOLooper;
import ioio.lib.util.android.IOIOActivity;

import android.content.Context;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class IOIOSimpleApp extends IOIOActivity {
    private SI4703Looper si4703Looper;
    private AudioManager audio;
    private TwiMaster twi;
    private Button preset1;
    private Button preset2;
    private SeekBar volBar;
    private Button seekUp;
    private Button seekDown;

    char[] _PSName1 = new char[10];  // including trailing '\00' character.
    char[] _PSName2 = new char[10];  // including trailing '\00' character.
    char[] programServiceName = "          ".toCharArray();    // found station name or empty. Is max. 8 character long.

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        preset1 = (Button) findViewById(R.id.preset1);
        preset2 = (Button) findViewById(R.id.preset2);
        volBar = (SeekBar) findViewById(R.id.volbar);
        seekUp = (Button) findViewById(R.id.seekUp);
        seekDown = (Button) findViewById(R.id.seekDown);

        preset1.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                si4703Looper.tune(1024);
            }
        });
        preset2.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                si4703Looper.tune(1004);
            }
        });
        seekUp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                si4703Looper.seek(true, true);
            }
        });
        seekDown.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                si4703Looper.seek(false, true);
            }
        });

        volBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                si4703Looper.setSi4703Volume(progress);
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

    class SI4703Looper extends BaseIOIOLooper {

        // Rotary Encode
        private DigitalInput clk_;
        private DigitalInput dt_;

        // SI4703
        private DigitalOutput resetPin_;
        private DigitalOutput SDIO_;
        private DigitalInput GPIO2_;

        private int[] si4703_registers = new int[16];

        public void setup() throws ConnectionLostException {
            try {
                dt_ = ioio_.openDigitalInput(20, DigitalInput.Spec.Mode.PULL_UP);
                clk_ = ioio_.openDigitalInput(19, DigitalInput.Spec.Mode.PULL_UP);
                si4703_init();
                si4703_powerOn();
                enableUi(true);
                tune(1024);
            } catch (ConnectionLostException e) {
                enableUi(false);
                throw e;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void disconnected() {
            enableUi(false);
        }

        //To get the Si4703 inito 2-wire mode, SEN needs to be high and SDIO needs to be low after a reset
        //The breakout board has SEN pulled high, but also has SDIO pulled high. Therefore, after a normal power up
        //The Si4703 will be in an unknown state. RST must be controlled
        public void si4703_init() throws InterruptedException, ConnectionLostException {
            Log.d("SI4703", "InitialiseSi4703 to 2-wire mode");
            SDIO_ = ioio_.openDigitalOutput(SI4703Consts.SDIOPIN, false);
            resetPin_ = ioio_.openDigitalOutput(SI4703Consts.RESETPIN, false);
            GPIO2_ = ioio_.openDigitalInput(SI4703Consts.GPIO2PIN, DigitalInput.Spec.Mode.PULL_UP);
            Thread.sleep(1);
            resetPin_.write(true); //Bring Si4703 out of reset with SDIO set to low and SEN pulled high with on-board resistor
            Thread.sleep(1);
            SDIO_.close();
            twi = ioio_.openTwiMaster(SI4703Consts.TWINUM, TwiMaster.Rate.RATE_100KHz, false);
        }

        public void si4703_powerOn() throws InterruptedException, ConnectionLostException {
            Log.d("SI4703", "Power on SI4703");
            si4703_readRegisters();
            //si4703_registers[0x07] = 0xBC04; //Enable the oscillator, from AN230 page 9, rev 0.5 (DOES NOT WORK, wtf Silicon Labs datasheet?)
            si4703_registers[SI4703Consts.TEST1] = buildRegister((byte) (0x8100 >> 8), (byte) (0x8100 & 0x00ff)); //Enable the oscillator, from AN230 page 9, rev 0.61 (works)
            //print for debug
            //setText(String.format(" %8s", Integer.toBinaryString(si4703_registers[TEST1] & 0x000000FF)).replace(" ", "0"));
            si4703_updateRegisters();
            Thread.sleep(500); //Wait for clock to settle - from AN230 page 9
            si4703_readRegisters();
            si4703_registers[SI4703Consts.POWERCFG] = buildRegister((byte) (0x4001 >> 8), (byte) (0x4001 & 0x00ff)); //Enable the IC

            si4703_registers[SI4703Consts.POWERCFG] |= (1 << SI4703Consts.SMUTE) | (1 << SI4703Consts.DMUTE); //Disable Mute, disable softmute
            si4703_registers[SI4703Consts.SYSCONFIG1] |= (1 << SI4703Consts.RDS); //Enable RDS-Verbose-Mode
            si4703_registers[SI4703Consts.SYSCONFIG1] |= (1 << SI4703Consts.RDSIEN); //Enable RDS-Interrupt
            //si4703_registers[SI4703Consts.SYSCONFIG1] |= (1 << SI4703Consts.STCIEN); //Enable STC-Interrupt
            si4703_registers[SI4703Consts.SYSCONFIG1] |= (1 << SI4703Consts.GPIO2);  //Enable GPIO2 for RDS/STC Interrupt
            if (SI4703Consts.IN_EUROPE) {
                si4703_registers[SI4703Consts.SYSCONFIG1] |= (1 << SI4703Consts.DE); //50kHz Europe setup
                si4703_registers[SI4703Consts.SYSCONFIG2] |= (1 << SI4703Consts.SPACE0); //100kHz channel spacing for Europe
            } else {
                si4703_registers[SI4703Consts.SYSCONFIG2] &= ~(1 << SI4703Consts.SPACE1 | 1 << SI4703Consts.SPACE0); //Force 200kHz channel spacing for USA
            }

            si4703_registers[SI4703Consts.SYSCONFIG2] &= 0xFFF0; //Clear volume bits
            si4703_registers[SI4703Consts.SYSCONFIG2] |= 0x0001; //Set volume to lowest

            si4703_updateRegisters();

            Thread.sleep(110); //Max powerup time, from datasheet page 13
            si4703_readRegisters();
        }

        private void si4703_updateRegisters() throws ConnectionLostException, InterruptedException {
            Log.d("SI4703", "Update registers");
            byte[] request = new byte[32];
            byte[] response = new byte[]{};

            //A write command automatically begins with register 0x02 so no need to send a write-to address
            //First we need to build the request byte array from the si4703_registers buffer
            //In general, we should not write to registers 0x08 and 0x09

            int i = 0;
            for (int regSpot = 0x02; regSpot < 0x08; regSpot++) {
                byte high_byte = (byte) (si4703_registers[regSpot] >> 8);
                byte low_byte = (byte) (si4703_registers[regSpot] & 0x00ff);
                //print out for debugging
                //System.out.println(String.format("0x%2s", Integer.toHexString(regSpot))+String.format(" %8s", Integer.toBinaryString(si4703_registers[regSpot] >> 8)).replace(" ", "0"));
                //System.out.println(String.format("0x%2s", Integer.toHexString(regSpot))+String.format(" %8s", Integer.toBinaryString(si4703_registers[regSpot] & 0x00ff)).replace(" ", "0"));
                request[i] = high_byte; //Upper 8 bits
                i++;
                request[i] = low_byte; //Lower 8 bits
                i++;
            }
            twi.writeRead(0x10, false, request, request.length, response, 0);
        }

        private void si4703_readRegisters() throws ConnectionLostException, InterruptedException {
            Log.d("SI4703", "Read registers");
            byte[] response = new byte[32];
            twi.writeRead(0x10, false, null, 0, response, response.length);

            int i = 0;
            for (int x = 0x0A; ; x++) { //Read in these 32 bytes
                Log.d("SI4703", "Read register " + x);
                if (x == 0x10) x = 0; //Loop back to zero
                //need to glue 2 byte array entries together and prepend a 16 bit 0 to the 32 bit int so we can access it easy later
                si4703_registers[x] = buildRegister(response[i], response[i + 1]);
                i += 2;
                //print out each register for debugging
                //System.out.println(x + " " + String.format("0x%2s", Integer.toHexString(x)) + " " + String.format("0x%2s", Integer.toHexString(si4703_registers[x])) + " " + String.format("%16s", Integer.toBinaryString(si4703_registers[x] & 0x0000FFFF)).replace(" ", "0"));
                if (x == 0x09) break; //We're done!
            }

            dumpRegister();
        }

        private int buildRegister(byte reg1, byte reg2) {
            ByteBuffer bb = ByteBuffer.allocate(4);
            bb.put(SI4703Consts.ZEROBYTE);
            bb.put(SI4703Consts.ZEROBYTE);
            bb.put(reg1);
            bb.put(reg2);
            return (bb.getInt(0));
        }

        public boolean tune(int frequency) {
            int bottomband = 875;
            int spacing = 1;

            int channel = (frequency - bottomband) / spacing;
            try {

                si4703_readRegisters();

                si4703_registers[SI4703Consts.CHANNEL] &= 0x0000; // clear channel bits
                si4703_registers[SI4703Consts.CHANNEL] |= (1 << SI4703Consts.TUNE); // set tuning on
                si4703_registers[SI4703Consts.CHANNEL] |= channel; // set channel

                si4703_updateRegisters();

                Thread.sleep(60);

                // Poll to see if STC is set
                while (!seekTuneComplete()) ;
                Log.i("SI4703", "Tuned to frequency " + (new Float(frequency) / 10.0));

                si4703_readRegisters();
                si4703_registers[SI4703Consts.CHANNEL] = si4703_registers[SI4703Consts.CHANNEL] & ~(1 << SI4703Consts.TUNE); // clear tuning bit
                si4703_updateRegisters();

                si4703_readRegisters();
                boolean stereo = ((si4703_registers[SI4703Consts.STATUSRSSI] >> SI4703Consts.STEREO) & 1) != 0;
                Log.i("SI4703", "finished tuning " + (new Float(frequency) / 10.0) + ", channel is stereo, " + stereo);

            } catch (Exception e) {
                return false;
            }
            resetRDSData();
            return true;
        }

        public void seek(boolean seekUp, boolean bWaitForSTC) {
            try {
                // Seeks out the next available station
                // Set bWaitForSTC to true if you want to wait for the STC bit to be set

                Log.d("SI4703", "start seeking " + (seekUp ? "up" : "down"));
                si4703_readRegisters();
                if (seekUp) {
                    si4703_registers[SI4703Consts.POWERCFG] |= (1 << SI4703Consts.SEEKUP);
                } else {
                    si4703_registers[SI4703Consts.POWERCFG] = si4703_registers[SI4703Consts.POWERCFG] & ~(1 << SI4703Consts.SEEKUP);
                }
                si4703_registers[SI4703Consts.POWERCFG] |= (1 << SI4703Consts.SEEK); // Set the SEEK bit
                si4703_updateRegisters();                                                // Seeking will now start

                resetRDSData();

                if (bWaitForSTC) {
                    // Wait until STC is set.
                    do {
                    } while (!seekTuneComplete());
                    si4703_registers[SI4703Consts.POWERCFG] = si4703_registers[SI4703Consts.POWERCFG] & ~(1 << SI4703Consts.SEEK);
                    si4703_updateRegisters();
             }
                Log.d("SI4703", "finished seeking, tuned frequency " + (new Float(getFrequency()) / 10.0));
            } catch (Exception e) {
                Log.e("SI4703", e.getMessage());
            }
        }

        public int getFrequency() {
            try {
                int intChannel = si4703_registers[SI4703Consts.READCHAN];//& 0x00FF;
                int bottomband = 875;
                int spacing = 1;

                return intChannel * spacing + bottomband;
            } catch (Exception e) {
                Log.e("SI4703", e.getMessage());
            }
            return 0;
        }

        public void setSi4703Volume(int volume) {
            try {
                if (volume < 0 || volume > 15) {
                    return;
                }
                si4703_readRegisters();
                si4703_registers[SI4703Consts.SYSCONFIG2] &= 0xFFF0; //Clear volume bits
                si4703_registers[SI4703Consts.SYSCONFIG2] |= volume;
                si4703_updateRegisters();
            } catch (Exception e) {
                Log.e("SI4703", e.getMessage());
            }
        }

        public String readRDS() {
            try {
                si4703_readRegisters();
            } catch (Exception e) {
                Log.e("SI4703", e.getMessage());
                return null;
            }

            int block1 = si4703_registers[SI4703Consts.RDSA];
            int block2 = si4703_registers[SI4703Consts.RDSB];
            int block3 = si4703_registers[SI4703Consts.RDSC];
            int block4 = si4703_registers[SI4703Consts.RDSD];

            int idx; // index of rdsText
            char c1, c2;


            int mins; ///< RDS time in minutes
            int off;   ///< RDS time offset and sign

            if (block1 == 0) {
                return null;
            }

            // analyzing Block 2
            int rdsGroupType = 0x0A | ((block2 & 0xF000) >> 8) | ((block2 & 0x0800) >> 11);
            int rdsTP = (block2 & 0x0400);
            int rdsPTY = (block2 & 0x0400);

            switch (rdsGroupType) {
                case 0x0A:
                case 0x0B:
                    // The data received is part of the Service Station Name
                    idx = 2 * (block2 & 0x0003);

                    // new data is 2 chars from block 4
                    c1 = (char) (block4 >> 8);
                    c2 = (char) (block4 & 0x00FF);

                    // check that the data was received successfully twice
                    // before publishing the station name

                    if ((_PSName1[idx] == c1) && (_PSName1[idx + 1] == c2)) {
                        // retrieved the text a second time: store to _PSName2
                        _PSName2[idx] = c1;
                        _PSName2[idx + 1] = c2;
                        _PSName2[8] = '\0';

                        if ((idx == 6) && Arrays.equals(_PSName1, _PSName2)) {
                            if (!Arrays.equals(_PSName2, programServiceName) && !String.copyValueOf(programServiceName).isEmpty()) {
                                // publish station name
                                programServiceName = Arrays.copyOf(_PSName2, _PSName2.length);
                            }
                        }
                    }

                    if ((_PSName1[idx] != c1) || (_PSName1[idx + 1] != c2)) {
                        _PSName1[idx] = c1;
                        _PSName1[idx + 1] = c2;
                        _PSName1[8] = '\0';
                    }
                    break;

                case 0x2A:
/*
                    // The data received is part of the RDS Text.
                    _textAB = (block2 & 0x0010);
                    idx = 4 * (block2 & 0x000F);

                    if (idx < _lastTextIDX) {
                        // the existing text might be complete because the index is starting at the beginning again.
                        // now send it to the possible listener.
                        if (_sendText)
                            _sendText(_RDSText);
                    }
                    _lastTextIDX = idx;

                    if (_textAB != _last_textAB) {
                        // when this bit is toggled the whole buffer should be cleared.
                        _last_textAB = _textAB;
                        memset(_RDSText, 0, sizeof(_RDSText));
                        // Serial.println("T>CLEAR");
                    } // if


                    // new data is 2 chars from block 3
                    _RDSText[idx] = (block3 >> 8);     idx++;
                    _RDSText[idx] = (block3 & 0x00FF); idx++;

                    // new data is 2 chars from block 4
                    _RDSText[idx] = (block4 >> 8);     idx++;
                    _RDSText[idx] = (block4 & 0x00FF); idx++;

                    // Serial.print(' '); Serial.println(_RDSText);
                    // Serial.print("T>"); Serial.println(_RDSText);
*/
                    break;

                case 0x4A:
/*
                    // Clock time and date
                    off = (block4)& 0x3F; // 6 bits
                    mins = (block4 >> 6) & 0x3F; // 6 bits
                    mins += 60 * (((block3 & 0x0001) << 4) | ((block4 >> 12) & 0x0F));

                    // adjust offset
                    if (off & 0x20) {
                        mins -= 30 * (off & 0x1F);
                    } else {
                        mins += 30 * (off & 0x1F);
                    }

                    if ((_sendTime) && (mins != _lastRDSMinutes)) {
                        _lastRDSMinutes = mins;
                        _sendTime(mins / 60, mins % 60);
                    } // if
*/
                    break;

                case 0x6A:
                    // IH
                    break;

                case 0x8A:
                    // TMC
                    break;

                case 0xAA:
                    // TMC
                    break;

                case 0xCA:
                    // TMC
                    break;

                case 0xEA:
                    // IH
                    break;

                default:
                    // Serial.print("RDS_GRP:"); Serial.println(rdsGroupType, HEX);
                    break;
            }
            return String.copyValueOf(programServiceName).isEmpty() ? "" : String.copyValueOf(programServiceName);
        }

        private void resetRDSData() {
            programServiceName = "          ".toCharArray();
        }

        private boolean seekTuneComplete() {
            try {
                si4703_readRegisters();
                return ((si4703_registers[SI4703Consts.STATUSRSSI] >> SI4703Consts.STC) & 1) != 0;
            } catch (Exception e) {
                Log.e("SI4703", e.getMessage());
            }
            return true;
        }

        private void dumpRegister() {
            System.out.println("0x00 (DEVICEID)    = " + dumpRegister(si4703_registers[SI4703Consts.DEVICEID]));
            System.out.println("0x01 (CHIPID)      = " + dumpRegister(si4703_registers[SI4703Consts.CHIPID]));
            System.out.println("0x02 (POWERCFG)    = " + dumpRegister(si4703_registers[SI4703Consts.POWERCFG]));
            System.out.println("0x03 (CHANNEL)     = " + dumpRegister(si4703_registers[SI4703Consts.CHANNEL]));
            System.out.println("0x04 (SYSCONFIG1)  = " + dumpRegister(si4703_registers[SI4703Consts.SYSCONFIG1]));
            System.out.println("0x05 (SYSCONFIG2)  = " + dumpRegister(si4703_registers[SI4703Consts.SYSCONFIG2]));
            System.out.println("0x06 (SYSCONFIG3)  = " + dumpRegister(si4703_registers[SI4703Consts.SYSCONFIG3]));
            System.out.println("0x07 (TEST1)       = " + dumpRegister(si4703_registers[SI4703Consts.TEST1]));
            System.out.println("0x08 (TEST2)       = " + dumpRegister(si4703_registers[SI4703Consts.TEST2]));
            System.out.println("0x09 (BOOTCONFIG)  = " + dumpRegister(si4703_registers[SI4703Consts.BOOTCONFIG]));
            System.out.println("0x0A (STATUSRSSI)  = " + dumpRegister(si4703_registers[SI4703Consts.STATUSRSSI]));
            System.out.println("0x0B (READCHAN)    = " + dumpRegister(si4703_registers[SI4703Consts.READCHAN]));

            System.out.println("0x0C (RDSA)        = " + dumpRegister(si4703_registers[SI4703Consts.RDSA]));
            System.out.println("0x0D (RDSB)        = " + dumpRegister(si4703_registers[SI4703Consts.RDSB]));
            System.out.println("0x0E (RDSC)        = " + dumpRegister(si4703_registers[SI4703Consts.RDSC]));
            System.out.println("0x0F (RDSD)        = " + dumpRegister(si4703_registers[SI4703Consts.RDSD]));
        }

        private String dumpRegister(int reg) {
            return String.format("%16s", Integer.toBinaryString(reg & 0x0000FFFF)).replace(" ", "0");
        }

        public void loop() throws ConnectionLostException {
            try {
                //clk_.waitForValue(false);
                //boolean dt_val = dt_.read();
                //setVolume(dt_val);
                //Thread.sleep(60);

                GPIO2_.waitForValue(false);
                Log.d("SI4703", "Stationname: " + readRDS());
                Thread.sleep(1000);

            } catch (InterruptedException e) {
                ioio_.disconnect();
            }
        }
    }

    @Override
    protected IOIOLooper createIOIOLooper() {
        si4703Looper = new SI4703Looper();
        return si4703Looper;
    }

    private void enableUi(final boolean enable) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                preset1.setEnabled(enable);
                preset2.setEnabled(enable);
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