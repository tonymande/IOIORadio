package ioio.examples.simple;

/**
 * Created by dgey on 24.01.16.
 */
public interface Si4703Consts {
    byte ZEROBYTE = (byte) 00000000;
    int STATUS_LED = 13;
    int RESETPIN = 7; //3;
    int GPIO2PIN = 8; //3;
    int TWINUM = 1; //1;
    int SCLKPIN = 2; //48;
    int SDIOPIN = 6; //47;

    int FAIL = 0;
    int SUCCESS = 1;

    int SI4703 = 0x10; //0b._001.0000 = I2C address of Si4703 - note that the Wire function assumes non-left-shifted I2C address, not 0b.0010.000W
    int I2C_FAIL_MAX = 10; //This is the number of attempts we will try to contact the device before erroring out

    boolean IN_EUROPE = true; //Use this define to setup European FM reception. I wuz there for a day during testing (TEI 2011).
    int SEEK_DOWN = 0; //Direction used for seeking. Default is down
    int SEEK_UP = 1;

    //Define the register names
    int DEVICEID = 0x00;
    int CHIPID = 0x01;
    int POWERCFG = 0x02;
    int CHANNEL = 0x03;
    int SYSCONFIG1 = 0x04;
    int SYSCONFIG2 = 0x05;
    int SYSCONFIG3 = 0x06;
    int TEST1 = 0x07;
    int TEST2 = 0x08;
    int BOOTCONFIG = 0x09;
    int STATUSRSSI = 0x0A;
    int READCHAN = 0x0B;
    int RDSA = 0x0C;
    int RDSB = 0x0D;
    int RDSC = 0x0E;
    int RDSD = 0x0F;

    //Register 0x02 - POWERCFG
    int SMUTE = 15;
    int DMUTE = 14;
    int SKMODE = 10;
    int SEEKUP = 9;
    int SEEK = 8;

    //Register 0x03 - CHANNEL
    int TUNE = 15;

    //Register 0x04 - SYSCONFIG1
    int RDSIEN = 15;
    int STCIEN = 14;
    int RDS = 12;
    int DE = 11;
    int GPIO2 = 2;

    //Register 0x05 - SYSCONFIG2
    int SPACE1 = 5;
    int SPACE0 = 4;

    //Register 0x0A - STATUSRSSI
    int RDSR = 15;
    int STC = 14;
    int SFBL = 13;
    int AFCRL = 12;
    int RDSS = 11;
    int STEREO = 8;
}
