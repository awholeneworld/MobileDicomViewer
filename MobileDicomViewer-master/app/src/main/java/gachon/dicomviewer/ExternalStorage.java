package gachon.dicomviewer;

import android.os.Environment;

public class ExternalStorage {
    /**
     * @return True if the external storage is available.
     * False otherwise.
     */
    public static boolean checkAvailable() {

        // Retrieving the external storage state
        String state = Environment.getExternalStorageState();

        // Check if available
        if (Environment.MEDIA_MOUNTED.equals(state)
                || Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }

        return false;
    }

    /**
     * @return True if the external storage is writable.
     * False otherwise.
     */
    public static boolean checkWritable() {

        // Retrieving the external storage state
        String state = Environment.getExternalStorageState();

        // Check if writable
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }

        return false;

    }
}
