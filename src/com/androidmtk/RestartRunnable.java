package com.androidmtk;

import java.io.IOException;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

/** Nested class that performs the restart */
public class RestartRunnable implements Runnable 
{
	Handler mHandler;
	private String restartName;
	private String restartCommand;
	private String restartResponse;
	private static String GPS_bluetooth_id;

	RestartRunnable(Handler h, int mode) 
	{
		mHandler = h;
		GPS_bluetooth_id = AndroidMTK.getSharedPreferences().getString("bluetoothListPref", "-1");
		
		String restartName;
		String restartCommand;
		String restartResponse;
		switch (mode) {
		case 1:
			restartName = "Hot start";
			restartCommand = "PMTK101";
			restartResponse = "PMTK010,001";
			break;
		case 2:
			restartName = "Warm start";
			restartCommand = "PMTK102";
			restartResponse = "PMTK010,001";
			break;
		case 3:
			restartName = "Cold start";
			restartCommand = "PMTK103";
			restartResponse = "PMTK010,001";
			break;
		default:
			sendTOAST("Unrecognized restart mode");
			return;
		}

		this.restartName = restartName;
		this.restartCommand = restartCommand;
		this.restartResponse = restartResponse;
	}

	public void run() 
	{	
		Log.v(AndroidMTK.TAG, "+++ ON RestartRunnable.run(" + restartName + ") +++");
			
		GPSrxtx gpsdev = new GPSrxtx(AndroidMTK.getmBluetoothAdapter(), GPS_bluetooth_id);
		if (gpsdev.connect()) {
    		// Send the command to perform restart
        	try {
				gpsdev.sendCommand(restartCommand);
			} catch (IOException e) {
				sendMessageField("Failed");
				gpsdev.close();
                return;
			}
    		// Wait for reply from the device
        	try {
				gpsdev.waitForReply(restartResponse, 60.0);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        	gpsdev.close();

        	sendMessageField(restartName + " succeed");
    	}
    	else {
    		sendMessageField("Error, could not connect to GPS device");
    	}
		sendCloseProgress();
		Log.d(AndroidMTK.TAG, "++++ Done: " + restartName);
	}

	private void sendTOAST(String message) {
		Message msg = mHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putString(AndroidMTK.KEY_TOAST, message);
		msg.setData(b);
		mHandler.sendMessage(msg);
	}

	private void sendMessageField(String message) {
		Message msg = mHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putString(AndroidMTK.MESSAGEFIELD, message);
		msg.setData(b);
		mHandler.sendMessage(msg);
	}
	
	private void sendCloseProgress() {
		Message msg = mHandler.obtainMessage();
		Bundle b = new Bundle();
		b.putInt(AndroidMTK.CLOSE_PROGRESS, 1);
		msg.setData(b);
		mHandler.sendMessage(msg);
	}

}
