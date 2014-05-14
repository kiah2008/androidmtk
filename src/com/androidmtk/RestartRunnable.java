package com.androidmtk;

import java.io.IOException;

import android.os.Handler;
import android.util.Log;

/** Nested class that performs the restart */
public class RestartRunnable extends GeneralRunnable 
{
	private String restartName;
	private String restartCommand;
	private String restartResponse;

	RestartRunnable(Handler h, int mode) 
	{
		super(h);
		
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
}
