package com.androidmtk;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.androidmtk.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

public class AndroidMTK extends Activity {
	private static final String TAG = "AndroidMTK";
	private static final int REQUEST_ENABLE_BT = 2;
	private static final int SIZEOF_SECTOR = 0x10000;

	// Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;
    private SharedPreferences sharedPreferences;

    // Preferences 
    // Bluetooth device string
    private String GPS_bluetooth_id;
    // Bluetooth device string
    private boolean create_log_file = false;
    private boolean write_one_trk = true;
	private int SIZEOF_CHUNK = 0x1000;

    private Button buttondel;
    private Button buttonget;
    private Button buttonhelp;
    private ProgressDialog dialog;
    
    // Output date
    private String file_time_stamp;
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.v(TAG, "+++ ON CREATE +++");

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
		// Clear all preferences. FOR TESTING!
        //SharedPreferences.Editor editor = sharedPreferences.edit();
        //editor.clear();
        //editor.commit();
        
        // Create the layout with a couple of buttons
        setContentView(R.layout.main);
        buttondel = (Button) findViewById(R.id.buttondel);
        buttondel.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click
            	delLog();
            }
        });
        buttonget = (Button) findViewById(R.id.buttonget);
        buttonget.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click
            	getLog();
            }
        });
        buttonhelp = (Button) findViewById(R.id.buttonhelp);
        buttonhelp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click
            	startActivity(new Intent(getBaseContext(), Help.class));
            }
        });

        // Check if device has Bluetooth
    	mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (mBluetoothAdapter == null) {
            // Device does not support Bluetooth
        	Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        // Check if Bluetooth is enabled
        if (!mBluetoothAdapter.isEnabled()) {
        	// No, ask user to start it
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        Log.i(TAG, "+++ GPS bluetooth device: "+sharedPreferences.getString("bluetoothListPref","-1"));
/*
    	File bin_file = new File(Environment.getExternalStorageDirectory(), "gpslog2011-07-31_1025_holux.bin");
    	File gpx_file = new File(Environment.getExternalStorageDirectory(), "gpslog2011-07-31_1023_holux.gpx");
		File log_file = new File(Environment.getExternalStorageDirectory(), "gpslog2011-07-31_1023_holux.txt");
		new ParseBinFile(bin_file, gpx_file, log_file);
*/
    }
    
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
        case REQUEST_ENABLE_BT:
            // When the request to enable Bluetooth returns
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth is now enabled
            } else {
                // User did not enable Bluetooth or an error occured
                finish();
            }
        }
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }
    
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.preferences:
        	startActivity(new Intent(getBaseContext(), MyPreferenceActivity.class));
            return true;
        case R.id.help:
        	startActivity(new Intent(getBaseContext(), Help.class));
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    public void delLog() {
    	Log.v(TAG, "+++ ON delLog +++");

    	GPS_bluetooth_id = sharedPreferences.getString("bluetoothListPref","-1");
    	if (GPS_bluetooth_id == "-1" || GPS_bluetooth_id.length() == 0) {
    		// No GPS device selected in the preferences
    		AlertDialog.Builder builder = new AlertDialog.Builder(AndroidMTK.this);
    		builder.setMessage("Please select a GPS device in the preferences first!");
    		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	           	Intent preferenceActivity = new Intent(getBaseContext(),
    	                    MyPreferenceActivity.class);
    	        	startActivity(preferenceActivity);
    	           }
    	       });
    		builder.show();
    		return;
    	}

    	dialog = ProgressDialog.show(this, "Clearing log", 
                "Please wait...", true , false);

    	// Start a thread to do the deleting
    	DelThread thread = new DelThread(DelThreadHandler);
    	thread.start();

    	Log.d(TAG, "++++ Done: delLog()");
    }

    // Define a Handler 
    final Handler DelThreadHandler = new Handler() {
    	public void handleMessage(Message msg) {
        	dialog.dismiss();
        }
    };

    /** Nested class that performs the deleting of the log */
    private class DelThread extends Thread {
    	Handler mHandler;

        DelThread(Handler h) {
        	mHandler = h;
        }
       
        public void run() {
        	GPSrxtx gpsdev = new GPSrxtx(mBluetoothAdapter, GPS_bluetooth_id);
        	if (gpsdev.connect()) {
        		// Send the command to clear the log
            	gpsdev.sendCommand("PMTK182,6,1");
        		// Wait for reply from the device
            	gpsdev.waitForReply("PMTK001,182,6,3");
            	gpsdev.close();
        	}
        	
			Message msg = mHandler.obtainMessage();
			mHandler.sendMessage(msg);
        }
    }

    public void getLog() {
    	Log.v(TAG, "+++ ON getLog +++");

    	// Get some preferences information
    	SIZEOF_CHUNK = Integer.parseInt(sharedPreferences.getString("chunkSizePref", "4096"));
    	create_log_file = sharedPreferences.getBoolean("createDebugPref", false);
    	write_one_trk = sharedPreferences.getBoolean("createOneTrkPref", true);
    	GPS_bluetooth_id = sharedPreferences.getString("bluetoothListPref","-1");
    	if (GPS_bluetooth_id == "-1" || GPS_bluetooth_id.length() == 0) {
    		// No GPS device selected in the preferences
    		AlertDialog.Builder builder = new AlertDialog.Builder(AndroidMTK.this);
    		builder.setMessage("Please select a GPS device in the preferences first!");
    		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	           	Intent preferenceActivity = new Intent(getBaseContext(),
    	                    MyPreferenceActivity.class);
    	        	startActivity(preferenceActivity);
    	           }
    	       });
    		builder.show();
    		return;
    	}
    	
    	// Create a unique file for writing the log files to
    	Time now = new Time();
    	now.setToNow();
    	file_time_stamp = now.format("%Y-%m-%d_%H%M");

    	dialog = new ProgressDialog(this);
    	dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    	dialog.setMessage("Downloading GPS log");
    	dialog.setCancelable(false);
    	dialog.setMax(100);
    	dialog.show();

    	// Start a thread to get the log
    	GetThread thread = new GetThread(GetThreadHandler);
    	thread.start();

    	Log.d(TAG, "++++ Done: getLog()");
    }

    // Define a Handler 
    final Handler GetThreadHandler = new Handler() {
    	public void handleMessage(Message msg) {
    		int percetageComplete = msg.getData().getInt("percetageComplete");
    		dialog.setProgress(percetageComplete);

    		if (percetageComplete >= 100){
    			dialog.dismiss();
    		}
    	}
    };

    /** Nested class that performs the downloading of the log */
    private class GetThread extends Thread {
    	Handler mHandler;
        File bin_file;
        File gpx_file;
        File log_file = null;
        FileWriter log_writer = null;

        GetThread(Handler h) {
        	mHandler = h;
        }
       
        public int getFlashSize (int model) {
            // 8 Mbit = 1 Mb
            if (model == 0x1388) return( 8 * 1024 * 1024 / 8); // 757/ZI v1
            if (model == 0x5202) return( 8 * 1024 * 1024 / 8); // 757/ZI v2
            // 32 Mbit = 4 Mb
            if (model == 0x0000) return(32 * 1024 * 1024 / 8); // Holux M-1200E
            if (model == 0x0004) return(32 * 1024 * 1024 / 8); // 747 A+ GPS Trip Recorder
            if (model == 0x0005) return(32 * 1024 * 1024 / 8); // Qstarz BT-Q1000P
            if (model == 0x0006) return(32 * 1024 * 1024 / 8); // 747 A+ GPS Trip Recorder
            if (model == 0x000F) return(32 * 1024 * 1024 / 8); // 747 A+ GPS Trip Recorder
            if (model == 0x0008) return(32 * 1024 * 1024 / 8); // Pentagram PathFinder P 3106
            if (model == 0x8300) return(32 * 1024 * 1024 / 8); // Qstarz BT-1200
            // 16Mbit -> 2Mb
            // 0x0051    i-Blue 737, Qstarz 810, Polaris iBT-GPS, Holux M1000
            // 0x0002    Qstarz 815
            // 0x001b    i-Blue 747
            // 0x001d    BT-Q1000 / BGL-32
            // 0x0131    EB-85A
            return(16 * 1024 * 1024 / 8);
        }
        
        public void Log(String text) {
        	if (log_writer != null) {
            	// Create a unique file for writing the log files to
            	Time now = new Time();
            	now.setToNow();
            	String time = now.format("%H:%M:%S ");
				try {
					log_writer.append(time);
					log_writer.append(text);
					log_writer.append('\n');
					log_writer.flush();
				} catch (IOException e) {
					e.printStackTrace();
				}
        	}
        }

        public void run() {
        	String reply;
        	Pattern p;
        	Matcher m;
			FileOutputStream output_stream = null;

			// Open log file
        	if (create_log_file) {
				log_file = new File(Environment.getExternalStorageDirectory(), "gpslog"+file_time_stamp+".txt");
				try {
					log_writer = new FileWriter(log_file); 
				}
				catch (IOException e) {
					e.printStackTrace();
			    }
        	}
			Log(String.format("Trying to connect to GPS device: %s", GPS_bluetooth_id));
        	GPSrxtx gpsdev = new GPSrxtx(mBluetoothAdapter, GPS_bluetooth_id);
        	if (gpsdev.connect()) {
    			Log(String.format("Connected to GPS device: %s", GPS_bluetooth_id));

        		// Query recording method when full (OVERLAP/STOP).
    			Log("Sending command: PMTK182,2,6 and waiting for reply: PMTK182,3,6,");
        		gpsdev.sendCommand("PMTK182,2,6");
        		// Wait for reply from the device
            	reply = gpsdev.waitForReply("PMTK182,3,6,");
    			Log(String.format("Got reply: %s", reply));

            	// log_full_method == 1 means overwrite from the beginning
            	// log_full_method == 2 means stop recording
            	int log_full_method = 0;
            	p = Pattern.compile(".*PMTK182,3,6,([0-9]+).*");
            	m = p.matcher(reply);
            	if (m.find()) {
            		log_full_method = Integer.parseInt(m.group(1));
            	}
        		Log(String.format("Recording method on memory full: %d", log_full_method));

            	// Determine how much bytes we need to read from the memory
            	int bytes_to_read = 0;
            	if (log_full_method == 1) {
            		// Device is in OVERLAP mode we don't know where data ends; read the entire memory.
            		int flashManuProdID = 0;
            		// Query memory information
        			Log("Sending command: PMTK605 and waiting for reply: PMTK705,");
                    gpsdev.sendCommand("PMTK605");
                    // Wait for reply from the device
                    reply = gpsdev.waitForReply("PMTK705,");
        			Log(String.format("Got reply: %s", reply));
                    p = Pattern.compile(".*PMTK705,[\\.0-9A-Za-z_-]+,([0-9A-Za-z]+).*");
                    m = p.matcher(reply);
                    if (m.find()) {
                        flashManuProdID = Integer.parseInt(m.group(1), 16);
                        Log(String.format("flashManuProdID: %d (0x%08X)", flashManuProdID, flashManuProdID));
                    }
                    bytes_to_read = getFlashSize(flashManuProdID);
            	}
            	else {
            		int next_write_address = 0;
            		// Query the RCD_ADDR (data log Next Write Address).
        			Log("Sending command: PMTK182,2,8 and waiting for reply: PMTK182,3,8,");
            		gpsdev.sendCommand("PMTK182,2,8");
            		// Wait for reply from the device
            		reply = gpsdev.waitForReply("PMTK182,3,8,");
        			Log(String.format("Got reply: %s", reply));

            		p = Pattern.compile(".*PMTK182,3,8,([0-9A-Za-z]+).*");
            		m = p.matcher(reply);
            		if (m.find()) {
            			next_write_address = Integer.parseInt(m.group(1), 16);  
            			Log(String.format("Next write address: %d (0x%08X)", next_write_address, next_write_address));
            		}
            		int sectors  = (int) Math.floor(next_write_address / SIZEOF_SECTOR);
            		if (next_write_address % SIZEOF_SECTOR != 0) {
            			sectors += 1;
            		}
            		bytes_to_read = sectors * SIZEOF_SECTOR;
            	}
                Log(String.format("Retrieving %d (0x%08X) bytes of log data from device...", bytes_to_read, bytes_to_read));

            	// Open an output stream for writing
            	bin_file = new File(Environment.getExternalStorageDirectory(), "gpslog"+file_time_stamp+".bin");
    			try {
    				output_stream = new FileOutputStream(bin_file);
    			} catch (FileNotFoundException e1) {
    				e1.printStackTrace();
    				return;
    			}

                // To be safe we iterate requesting SIZEOF_CHUNK bytes at time.
                for (int offset = 0; offset < bytes_to_read; offset += SIZEOF_CHUNK) {
                    // Request log data (PMTK_LOG_REQ_DATA) from offset to bytes_to_read.
                	String command = String.format("PMTK182,7,%08X,%08X", offset, SIZEOF_CHUNK);
        			Log(String.format("Sending command: %s", command));
                	gpsdev.sendCommand(command);
                    // Read from the device
                	// It seems the chunk might be split over more than one message
                	// read until all bytes are received
                	int number_of_empty = 0;
                    byte[] tmp_array = new byte[SIZEOF_CHUNK];
                    int bytes_received = 0;
                    int number_of_message = 1;
                    if (SIZEOF_CHUNK > 0x800) {
                        number_of_message = SIZEOF_CHUNK/0x800;
                    }
                    Log(String.format("Waiting for %d PMTK182,8 messages", number_of_message));
                    for (int j=0; j<number_of_message; j++) {
                		reply = gpsdev.waitForReply("PMTK182,8");
            			Log(String.format("Got reply: %s", reply));
                		for (int i = 20; i < (reply.length()-3); i += 2) {
                			String string_byte = reply.substring(i, i+2);
                			if (string_byte.equals("FF")) {
                				number_of_empty++;
                			}
                			try {
                				tmp_array[bytes_received] = (byte) (Integer.parseInt(string_byte, 16) & 0xFF);
                				bytes_received++;
                			} catch (NumberFormatException e) {
                				e.printStackTrace();
                			}
                		}
                	}
                    if (bytes_received != SIZEOF_CHUNK) {
                        Log(String.format("ERROR! bytes_received(%d) != SIZEOF_CHUNK", bytes_received));
                        offset -= SIZEOF_CHUNK;
                        continue;
                    }
                    else {
                        try {
							output_stream.write(tmp_array, 0, SIZEOF_CHUNK);
						} catch (IOException e) {
							e.printStackTrace();
						}
                    }
            		if (number_of_empty == bytes_received) {
            			offset = bytes_to_read;
            			Log(String.format("Found empty SIZEOF_CHUNK, stopping reading any further"));
            		}

                	double percetageComplete = ((offset+SIZEOF_CHUNK) / (double)bytes_to_read) * 100.0;
                	Log(String.format("Saved log data: %6.2f%%", percetageComplete));
                	Message msg = mHandler.obtainMessage();
                    Bundle b = new Bundle();
                    b.putInt("percetageComplete", (int)percetageComplete);
                    msg.setData(b);
                    mHandler.sendMessage(msg);
                }
            	
                Log("Closing GPS device");
            	gpsdev.close();

            	// Close the bin file
            	try {
            		output_stream.flush();
            		output_stream.close();
    			} catch (IOException e) {
    				e.printStackTrace();

    			}
    			
    			// Check if we should also create a GPX file
    			if (sharedPreferences.getBoolean("createGPXPref", true)) {
    				Log("Creating GPX file from bin file");
                	gpx_file = new File(Environment.getExternalStorageDirectory(), "gpslog"+file_time_stamp+".gpx");
    				new ParseBinFile(bin_file, gpx_file, write_one_trk, log_file);
    			}

    			// Close the log file
            	if (log_writer != null) {
	            	try {
	        			log_writer.close();
	    			} catch (IOException e) {
	    				e.printStackTrace();
	
	    			}
            	}
        	}

			Message msg = mHandler.obtainMessage();
            Bundle b = new Bundle();
            b.putInt("percetageComplete", 100);
            msg.setData(b);
            mHandler.sendMessage(msg);
        }
    }
    
    // **** Restarting code ****
    
    public void hotStart(View v)
    {
    	performRestart(1);    
    }
    
    public void warmStart(View v)
    {
    	performRestart(2);    
    }

    public void coldStart(View v)
    {
    	performRestart(3);    
    }
    
    private void performRestart(int mode)
    {
    	String restartName;
    	String restartCommand;
    	String restartResponse;
    	switch (mode)
    	{
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
   				Toast.makeText(this, "Unrecognized restart mode", Toast.LENGTH_LONG).show();
   				return;
    	}
    	Log.v(TAG, "+++ ON " + restartName + " +++");

    	GPS_bluetooth_id = sharedPreferences.getString("bluetoothListPref","-1");
    	if (GPS_bluetooth_id == "-1" || GPS_bluetooth_id.length() == 0) {
    		// No GPS device selected in the preferences
    		AlertDialog.Builder builder = new AlertDialog.Builder(AndroidMTK.this);
    		builder.setMessage("Please select a GPS device in the preferences first!");
    		builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
    	           public void onClick(DialogInterface dialog, int id) {
    	           	Intent preferenceActivity = new Intent(getBaseContext(),
    	                    MyPreferenceActivity.class);
    	        	startActivity(preferenceActivity);
    	           }
    	       });
    		builder.show();
    		return;
    	}

    	dialog = ProgressDialog.show(this, "Performing " + restartName, 
                "Please wait...", true , false);

    	// Start a thread to do the restarting
    	RestartThread thread = new RestartThread(RestartThreadHandler, restartName, restartCommand, restartResponse);
    	thread.start();

    	Log.d(TAG, "++++ Done: " + restartName );
    }

    // Define a Handler 
    final Handler RestartThreadHandler = new Handler() {
    	public void handleMessage(Message msg) {
        	dialog.dismiss();
        	Toast.makeText(AndroidMTK.this, (String)msg.obj, Toast.LENGTH_LONG).show();	
        }
    };
    

    /** Nested class that performs the restart */
    private class RestartThread extends Thread {
    	Handler mHandler;
    	String restartName;
    	String restartCommand;
    	String restartResponse;   	

        RestartThread(Handler h, String name, String command, String response) {
        	mHandler = h;
        	restartName = name;
        	restartCommand = command;
        	restartResponse = response;
        }
       
        public void run() {
        	GPSrxtx gpsdev = new GPSrxtx(mBluetoothAdapter, GPS_bluetooth_id);
			Message msg = mHandler.obtainMessage();
        	if (gpsdev.connect()) {
        		// Send the command to perform restart
            	gpsdev.sendCommand(restartCommand);
        		// Wait for reply from the device
            	gpsdev.waitForReply(restartResponse);
            	gpsdev.close();
    			msg.obj = restartName + " succeed";
        	}
        	else
        		msg.obj = restartName + " failed";
        	
			mHandler.sendMessage(msg);
        }
    }

    public void close(View v)
    {
    	finish();    	
    }
}

