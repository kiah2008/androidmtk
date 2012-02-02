package com.androidmtk;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Locale;
import java.util.TimeZone;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class ParseBinFile {
    private static final int SIZEOF_SECTOR = 0x10000;

	// Log format is stored as a bitmask field.
	private static final int LOG_FORMAT_UTC         = 0x00000001;
	private static final int LOG_FORMAT_VALID       = 0x00000002;
	private static final int LOG_FORMAT_LATITUDE    = 0x00000004;
	private static final int LOG_FORMAT_LONGITUDE   = 0x00000005;
	private static final int LOG_FORMAT_HEIGHT      = 0x00000010;
	private static final int LOG_FORMAT_SPEED       = 0x00000020;
	private static final int LOG_FORMAT_HEADING     = 0x00000040;
	private static final int LOG_FORMAT_DSTA        = 0x00000080;
	private static final int LOG_FORMAT_DAGE        = 0x00000100;
	private static final int LOG_FORMAT_PDOP        = 0x00000200;
	private static final int LOG_FORMAT_HDOP        = 0x00000400;
	private static final int LOG_FORMAT_VDOP        = 0x00000800;
	private static final int LOG_FORMAT_NSAT        = 0x00001000;
	private static final int LOG_FORMAT_SID         = 0x00002000;
	private static final int LOG_FORMAT_ELEVATION   = 0x00004000;
	private static final int LOG_FORMAT_AZIMUTH     = 0x00008000;
	private static final int LOG_FORMAT_SNR         = 0x00010000;
	private static final int LOG_FORMAT_RCR         = 0x00020000;
	private static final int LOG_FORMAT_MILLISECOND = 0x00040000;
	private static final int LOG_FORMAT_DISTANCE    = 0x00080000;
	
	private static final int VALID_NOFIX            = 0x0001;

	
	private boolean LOG_IS_HOLUX_M241 = false;

	private int gpx_trk_number = 0;
	
	private byte[] buffer = new byte[SIZEOF_SECTOR];
	private byte[] emptyseparator = new byte[0x10];
	
	FileWriter log_writer = null;
	
    public void Log(String text) {
    	if (log_writer != null) {
			try {
				log_writer.append(text);
				log_writer.append('\n');
				log_writer.flush();
			} catch (IOException e) {
				e.printStackTrace();
			}
    	}
    }
    
    public byte packet_checksum(byte[] array, int length) {
        byte check = 0;
        int i;

        for (i = 0; i < length; i++) {
        	check ^= array[i];
        }
        return check;
    }
	
	public ParseBinFile(File bin_file, File gpx_file, boolean write_one_trk, File log_file) {
		boolean gpx_in_trk = false;
		
		for(int i=0; i<0x10;i++) {
			emptyseparator[i] = (byte)0xFF;
		}

		if (log_file != null) {
			try {
				log_writer = new FileWriter(log_file, true); 
			}
			catch (IOException e) {
				e.printStackTrace();
		    }
		}

		// Open an output for writing the gpx file
		FileWriter writer = null;
		try {
			writer = new FileWriter(gpx_file);
			WriteHeader(writer);
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
		
		// Open an input stream for reading from the binary log
		FileInputStream reader = null;
		try {
			Log(String.format("Reading bin file: %s", bin_file.toString()));
			reader = new FileInputStream(bin_file);
		} catch (FileNotFoundException e1) {
			e1.printStackTrace();
			return;
		}

		while(true) {
			int bytes_in_sector = 0;
			try {
				bytes_in_sector = reader.read(buffer, 0, SIZEOF_SECTOR);
				Log(String.format("Read %d bytes from bin file", bytes_in_sector));
				if (bytes_in_sector == 0) {
					// Reached the end of the file or something is wrong
					Log(String.format("End of file!"));
					break;
				}
			} catch (IOException e1) {
				e1.printStackTrace();
			}

			Log(String.format("Reading sector"));

			ByteBuffer buf = ByteBuffer.wrap(buffer);
			buf.order(ByteOrder.LITTLE_ENDIAN);

			short log_count = buf.getShort(0);
			// -1 is used if a sector is not fully written
			if (log_count == -1) {
				log_count = 5000;
			}
			int log_format = buf.getInt(2);
			Log(String.format("Log format %x", log_format));
			
			// Skip the header (which is 0x200 bytes long)
			buf.position(0x200);

			Log(String.format("Nr of sector records: %d", log_count));

			int record_count_sector = 0;
			while (record_count_sector < log_count) {
				byte[] tmp = new byte[0x10];
				// Test for record separators
				int seperator_length = 0x10;
				buf.get(tmp);
				String value = new String(tmp);
				if (value.compareTo("HOLUXGR241LOGGER") == 0) {
					LOG_IS_HOLUX_M241 = true;
					Log(String.format("Found a HOLUX M241 separator!"));
					byte[] tmp4 = new byte[4];
					buf.get(tmp4);
					if (tmp4[0]  == (byte)0x20 && tmp4[1]  == (byte)0x20 &&
				        tmp4[2]  == (byte)0x20 && tmp4[3]  == (byte)0x20) 
					{
						Log(String.format("Found a HOLUX M241 1.3 firmware!"));
					}
					else {
						buf.position(buf.position()-4);
					}
					continue;
				}
				else if (tmp[0]  == (byte)0xAA && tmp[1]  == (byte)0xAA &&
					     tmp[2]  == (byte)0xAA && tmp[3]  == (byte)0xAA &&
					     tmp[4]  == (byte)0xAA && tmp[5]  == (byte)0xAA &&
					     tmp[6]  == (byte)0xAA &&
					     tmp[15] == (byte)0xBB && tmp[14] == (byte)0xBB && 
					     tmp[13] == (byte)0xBB && tmp[12] == (byte)0xBB )
				{
					// So we found a record separator..
					Log(String.format("Found a record separator"));
					// Close the current trk section
                	try {
	                    if (!write_one_trk && gpx_in_trk) {
							WriteTrackEnd(writer);
	                    	gpx_in_trk = false;
	                    }
					} catch (IOException e) {
						e.printStackTrace();
					}
					// It is possible that the log_format have changed, parse out the
					// new log conditions
					buf.position(buf.position()-9);
					byte seperator_type = buf.get();
					if (seperator_type == 0x02) {
						log_format = buf.getInt();
						buf.position(buf.position()+4);
						Log(String.format("Log format has changed to %x", log_format));
					}
					else {
						buf.position(buf.position()+8);
					}
					continue;
				}
				else if ( Arrays.equals(tmp, emptyseparator) ) {
					Log(String.format("Empty space, assume end of sector"));
					break;
				}
				else {
					buf.position(buf.position()-seperator_length);
				}
				// So this is not a separator but it is an actual record, read it!
				record_count_sector++;
				Log(String.format("Reading record: %d of %d position %x", record_count_sector, log_count, buf.position()));
				int bytes_read = 0;
				int utc_time = 0;
				short valid = 0; 
				double lat = 0;
				double lon = 0;
				float height = 0;
				if ((log_format & LOG_FORMAT_UTC) == LOG_FORMAT_UTC) {
					bytes_read += 4;
					utc_time = buf.getInt();
					Log(String.format("UTC time %d", utc_time));
				}
				if ((log_format & LOG_FORMAT_VALID) == LOG_FORMAT_VALID) {
					bytes_read += 2;
					valid = buf.getShort();
					Log(String.format("Valid %d", valid));
				}
				if ((log_format & LOG_FORMAT_LATITUDE) == LOG_FORMAT_LATITUDE) {
					if (LOG_IS_HOLUX_M241) {
						bytes_read += 4;
						lat = buf.getFloat();
					}
					else {
						bytes_read += 8;
						lat = buf.getDouble();
					}
					Log(String.format("Latitude %f", lat));
					
				}
				if ((log_format & LOG_FORMAT_LONGITUDE) == LOG_FORMAT_LONGITUDE) {
					if (LOG_IS_HOLUX_M241) {
						bytes_read += 4;
						lon = buf.getFloat();
					}
					else {
						bytes_read += 8;
						lon = buf.getDouble();
					}
					Log(String.format("Longitude %f", lon));
				}
				if ((log_format & LOG_FORMAT_HEIGHT) == LOG_FORMAT_HEIGHT) {
					if (LOG_IS_HOLUX_M241) {
						bytes_read += 3;
						byte[] tmp4 = new byte[4];
						buf.get(tmp4, 1, 3);
						ByteBuffer b = ByteBuffer.wrap(tmp4);
						b.order(ByteOrder.LITTLE_ENDIAN);
						height = b.getFloat();
					}
					else {
						bytes_read += 4;
						height = buf.getFloat();
					}
					Log(String.format("Height %f", height));
				}
				if ((log_format & LOG_FORMAT_SPEED) == LOG_FORMAT_SPEED) {
					bytes_read += 4;
					float speed = buf.getFloat();
					Log(String.format("Speed %f", speed));
				}
				if ((log_format & LOG_FORMAT_HEADING) == LOG_FORMAT_HEADING) {
					bytes_read += 4;
					float heading = buf.getFloat();
					Log(String.format("Heading %f", heading));
				}
				if ((log_format & LOG_FORMAT_DSTA) == LOG_FORMAT_DSTA) {
					bytes_read += 2;
					short dsta = buf.getShort();
					Log(String.format("DSTA %d", dsta));
				}
				if ((log_format & LOG_FORMAT_DAGE) == LOG_FORMAT_DAGE) {
					bytes_read += 4;
					int dage = buf.getInt();
					Log(String.format("DAGE %d", dage));
				}
				if ((log_format & LOG_FORMAT_PDOP) == LOG_FORMAT_PDOP) {
					bytes_read += 2;
					short pdop = buf.getShort();
					Log(String.format("PDOP %d", pdop));
				}
				if ((log_format & LOG_FORMAT_HDOP) == LOG_FORMAT_HDOP) {
					bytes_read += 2;
					short hdop = buf.getShort();
					Log(String.format("HDOP %d", hdop));
				}
				if ((log_format & LOG_FORMAT_VDOP) == LOG_FORMAT_VDOP) {
					bytes_read += 2;
					short vdop = buf.getShort();
					Log(String.format("VDOP %d", vdop));
				}
				if ((log_format & LOG_FORMAT_NSAT) == LOG_FORMAT_NSAT) {
					bytes_read += 2;
					byte nsat = buf.get();
					byte nsat_in_use = buf.get();
					Log(String.format("NSAT %d %d", (int)nsat, (int)nsat_in_use));
				}
				if ((log_format & LOG_FORMAT_SID) == LOG_FORMAT_SID) {
					// Large section to parse
					int satdata_count = 0;
					while(true) {
						bytes_read += 1;
						byte satdata_sid = buf.get();
						Log(String.format("SID %d", (int)satdata_sid));
						bytes_read += 2;
						byte satdata_inuse = buf.get();
						Log(String.format("SID in use %d", (int)satdata_inuse));
						bytes_read += 2;
						short satdata_inview = buf.getShort();
						Log(String.format("SID in view %d", (int)satdata_inview));
						if (satdata_inview > 0) {
							if ((log_format & LOG_FORMAT_ELEVATION) == LOG_FORMAT_ELEVATION) {
								bytes_read += 2;
								short sat_elevation = buf.getShort();
								Log(String.format("Satellite ELEVATION %d", (int)sat_elevation));
							}
							if ((log_format & LOG_FORMAT_AZIMUTH) == LOG_FORMAT_AZIMUTH) {
								bytes_read += 2;
								short sat_azimuth = buf.getShort();
								Log(String.format("Satellite AZIMUTH %d", (int)sat_azimuth));
							}
							if ((log_format & LOG_FORMAT_SNR) == LOG_FORMAT_SNR) {
								bytes_read += 2;
								short sat_snr = buf.getShort();
								Log(String.format("Satellite SNR %d", (int)sat_snr));
							}
							satdata_count++;
						}
						if (satdata_count >= satdata_inview) {
							break;
						}
					}
				}
				if ((log_format & LOG_FORMAT_RCR) == LOG_FORMAT_RCR) {
					bytes_read += 2;
					short rcr = buf.getShort();
					Log(String.format("RCR %d", rcr));
				}
				if ((log_format & LOG_FORMAT_MILLISECOND) == LOG_FORMAT_MILLISECOND) {
					bytes_read += 2;
					short millisecond = buf.getShort();
					Log(String.format("Millisecond %d", millisecond));
				}
				if ((log_format & LOG_FORMAT_DISTANCE) == LOG_FORMAT_DISTANCE) {
					bytes_read += 8;
					double distance = buf.getDouble();
					Log(String.format("Distance %f", distance));
				}

				buf.position((buf.position()-bytes_read));
				byte[] tmp2 = new byte[bytes_read];
				buf.get(tmp2, 0, bytes_read);
				byte checksum = packet_checksum(tmp2, bytes_read);
				
				if (!LOG_IS_HOLUX_M241) {
					// Read the "*"
					buf.get();
				}
				// And the final character is the checksum count
				byte read_checksum = buf.get();
				Log(String.format("bytes_read %d Checksum %x read checksum %x", bytes_read, checksum, read_checksum));
				
				try {
                    if (!gpx_in_trk) {
                    	WriteTrackBegin(writer, utc_time);
                    	gpx_in_trk = true;
                    }
                    if (valid != VALID_NOFIX && checksum == read_checksum) {
                    	WriteTrackPoint(writer, lat, lon, height, utc_time);
                    }
				} catch (IOException e) {
					e.printStackTrace();
				}
				
			}
			if (bytes_in_sector < SIZEOF_SECTOR) {
				// Reached the end of the file or something is wrong
				Log(String.format("End of file!"));
				break;
			}
		};

		// Close GPX file
		try {
            if (gpx_in_trk) {
            	WriteTrackEnd(writer);
            	gpx_in_trk = false;
            }
			WriteFooter(writer);
			writer.flush();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}
	}

	public void WriteHeader(FileWriter writer) throws IOException {
		writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\" ?>" +
				 "<gpx\n" +
				 "    version=\"1.1\"\n" +
				 "    creator=\"AndroidMTK - http://www.bastiaannaber.com\"\n" +
				 "    xmlns=\"http://www.topografix.com/GPX/1/1\"\n" +
				 "    xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"\n" +
				 "    xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n");
	}

	public void WriteFooter(FileWriter writer) throws IOException {
		writer.write("</gpx>\n");
	}
	
	public void WriteTrackBegin(FileWriter writer, int time) throws IOException {
		long timestamp = (long)time * 1000;  // msec  
		java.util.Date date = new java.util.Date(timestamp);
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));

		writer.write("<trk>\n" +
					 "  <name>" + formatter.format(date) + "</name>\n" +
					 "  <number>" + gpx_trk_number + "</number>\n" + 
					 "<trkseg>\n");
	    gpx_trk_number++;
	}

	public void WriteTrackPoint(FileWriter writer, double lat, double lon, double height, int time) throws IOException {
		long timestamp = (long)time * 1000;  // msec  
		java.util.Date date = new java.util.Date(timestamp);
		SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		formatter.setTimeZone(TimeZone.getTimeZone("UTC"));
		writer.write(String.format(Locale.US, 
				"<trkpt lat=\"%.9f\" lon=\"%.9f\">\n" +
				"  <ele>%.6f</ele>\n" +
				"  <time>%s</time>\n" +
				"</trkpt>\n", lat, lon, height, formatter.format(date)));
	}

	public void WriteTrackEnd(FileWriter writer) throws IOException {
		writer.write("</trkseg>\n" +
				 	 "</trk>\n");
	}

}
