package com.androidmtk;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Log;

public class GPSrxtx {
	private static final String TAG = "AndroidMTK-GPSrxtx";
	
	public InputStream in = null;
	public OutputStream out = null;
	
	private BluetoothAdapter mBluetoothAdapter = null;
	private String dev_id;
	private	BluetoothSocket sock = null;
	private StringBuilder buffer = new StringBuilder();
	
	public GPSrxtx(BluetoothAdapter adapter, String gpsdev) {
		mBluetoothAdapter = adapter;
		dev_id = gpsdev;
	}

	public boolean connect() {
		Log.d(TAG, "++++ connect()");
		
    	BluetoothDevice zee = mBluetoothAdapter.getRemoteDevice(dev_id);
    	Method m = null;
		try {
			m = zee.getClass().getMethod("createRfcommSocket", new Class[] { int.class });
		} catch (SecurityException e1) {
			e1.printStackTrace();
			return false;
		} catch (NoSuchMethodException e1) {
			e1.printStackTrace();
			return false;
		}
    	try {
			sock = (BluetoothSocket)m.invoke(zee, Integer.valueOf(1));
		} catch (IllegalArgumentException e1) {
			e1.printStackTrace();
			return false;
		} catch (IllegalAccessException e1) {
			e1.printStackTrace();
			return false;
		} catch (InvocationTargetException e1) {
			e1.printStackTrace();
			return false;
		}
    	try {
			sock.connect();
		} catch (IOException e1) {
			e1.printStackTrace();
			return false;
		}
    	Log.d(TAG, "++++ Connected");
    	try {
			in = sock.getInputStream();
		} catch (IOException e1) {
			e1.printStackTrace();
			return false;
		}
    	try {
			out = sock.getOutputStream();
		} catch (IOException e1) {
			e1.printStackTrace();
			return false;
		}

		return true;
	}

	public void sendCommand(String command) {
		int i = command.length();
		byte checksum = 0;
		while (--i >= 0) {
			checksum ^= (byte) command.charAt(i);
		}
		StringBuilder rec = new StringBuilder(256);
		rec.setLength(0);
		rec.append('$');
		rec.append(command);
		rec.append('*');
		rec.append(Integer.toHexString(checksum));
		rec.append("\r\n");
		//Log.d(TAG, "++++ Writing: " + rec.toString() );

		// Actually send it
		try {
			out.write(rec.toString().getBytes());
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	public String waitForReply(String reply) {
        // Read from the device until we get the reply we are looking for
        byte[] buf = new byte[50];
    	int read = 0;
    	//Log.d(TAG, "++++ Reading from device...");
    	try {
			while (true) {
    			read = in.read(buf);
    			for (int j = 0; j < read; j++) {
    				char b = (char)(buf[j] & 0xff);
    				// Check if this is the start of a new message
    				if (buffer.length() > 0 && b == '$') {
    					// Yep new message started, parse old message (if any)
    					String message = buffer.toString();
    					//Log.d(TAG, "++++ Received a message: "+ message);
    					if (message.charAt(0) == '$') {
    						if (message.indexOf(reply, 0) > 0) {
    							//Log.d(TAG, "++++ Breaking because we received:" + reply);
    							buffer.setLength(0);
    							for (int k = j; k < read; k++) {
    								char c = (char)(buf[k] & 0xff);
    								buffer.append(c);
    							}
    							return message;
    						}
    					}
    					buffer.setLength(0);
    				}
    				buffer.append(b);
    			}
    		}
    	} catch (IOException e) {}

    	return "";
	}
	
	
	public void close() {
		Log.d(TAG, "++++ close()");
		try {
			sock.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
