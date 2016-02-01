// IIoioService.aidl
package org.genas.headunit;

interface IIoioService {

   void tune(int frequency);
   void mute(boolean muted);
   void seek(boolean seekUp, boolean bWaitForSTC);
   void setVolume(int volume);
   void setPower(boolean powerOn);
   void sendCommand();
}
