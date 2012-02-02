package com.androidmtk;

import java.util.Set;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;

public class MyPreferenceActivity extends PreferenceActivity {
	// Local Bluetooth adapter
    private BluetoothAdapter mBluetoothAdapter = null;

	protected void onCreate(Bundle savedInstanceState) {        
		super.onCreate(savedInstanceState);

		addPreferencesFromResource(R.xml.preferences);    

		// Populate the listPreference with all the bluetooth devices
		ListPreference customPref = (ListPreference) findPreference("bluetoothListPref");

		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    	Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
    	// If there are paired devices
    	if (pairedDevices.size() > 0) {
    	    // Loop through paired devices
    		CharSequence[] entries = new CharSequence[pairedDevices.size()];
    		CharSequence[] entrieValues = new CharSequence[pairedDevices.size()];
    		int i = 0;
    	    for (BluetoothDevice device : pairedDevices) {
    	        // Add the name and address to an array adapter to show in a ListView
        		entries[i] = device.getName();
        		entrieValues[i] = device.getAddress();
        		i++;
    	    }
    		customPref.setEntries(entries);
    		customPref.setEntryValues(entrieValues);
    	}
    	else {
    		customPref.setEnabled(false);
    	}
	}

}
