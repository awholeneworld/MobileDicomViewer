package gachon.dicomviewer;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.core.app.ActivityCompat;

import com.imebra.CodecFactory;
import com.imebra.DataSet;
import com.imebra.TagId;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileChooseActivity2 extends ListActivity {

    // Array adapter to display the directory and the files.
    ArrayAdapter<String> mAdapter;

    // Current directory.
    private File mTopDirectory;

    // DICOM file in mTopDirectory count.
    private int mTotal = 0;

    // ID for the onSaveInstanceState.
    private static final String TOP_DIR_ID = "top_directory";

    // Define the progress dialog ID for the caching of DICOM image.
    private static final short PROGRESS_DIALOG_CACHE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }

        // Load Imebra library
        System.loadLibrary("imebra_lib");

        // Set the content view
        setContentView(R.layout.activity_file_choose);

        // Check if the external storage is available
        if (ExternalStorage.checkAvailable()) {

            if (savedInstanceState != null) {
                String topDirectoryString = savedInstanceState.getString(TOP_DIR_ID);

                mTopDirectory = (topDirectoryString == null) ? Environment.getExternalStorageDirectory()
                        : new File(savedInstanceState.getString(TOP_DIR_ID));
            } else {
                // Set the top directory
                mTopDirectory = Environment.getExternalStorageDirectory();

                // Display the disclaimer
                fill();
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {

        super.onSaveInstanceState(outState);

        // Save the top directory absolute path
        outState.putString(TOP_DIR_ID, mTopDirectory.getAbsolutePath());
    }

    @Override
    public void onBackPressed() {

        // If the directory is the external storage directory or there is no parent,
        if (mTopDirectory.getParent() == null
                || mTopDirectory.equals(Environment.getExternalStorageDirectory())) {

            super.onBackPressed();

        }
        // Else go to parent directory.
        else {

            mTopDirectory = mTopDirectory.getParentFile();

            fill();
        }
    }

    @Override
    protected void onResume() {

        // If there is no external storage available, quit the application
        if (!ExternalStorage.checkAvailable()) {

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("There is no external storage.\n"
                    + "1) There is no external storage : add one.\n"
                    + "2) Your external storage is used by a computer:"
                    + " Disconnect the it from the computer.")
                    .setTitle("[ERROR] No External Storage")
                    .setCancelable(false)
                    .setPositiveButton("Exit", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            FileChooseActivity2.this.finish();
                        }
                    });

            AlertDialog alertDialog = builder.create();
            alertDialog.show();
        }
        // Else display data
        else fill();

        super.onResume();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {

        super.onListItemClick(l, v, position, id);

        String itemName = mAdapter.getItem(position);

        // If it is a directory, display its content
        if (itemName.charAt(0) == '/') {

            mTopDirectory = new File(mTopDirectory.getPath() + itemName);

            fill();
        }
        // If itemNam = ".." go to parent directory
        else if (itemName.equals("..")) {

            mTopDirectory = mTopDirectory.getParentFile();

            fill();
        }
        // If it is a file.
        else {

            try {

                // Load the data
                DataSet loadedFile = CodecFactory.load(mTopDirectory.getPath() + "/" + itemName);

                // Get the information of attribute "SOP Class UID"
                String SOPClassUID = loadedFile.getString(new TagId(0x08, 0x16), 0);

                if (SOPClassUID.equals("1.2.840.10008.1.3.10")) {

                    AlertDialog.Builder builder = new AlertDialog.Builder(this);
                    builder.setMessage("Media Storage Directory (DICOMDIR) are not supported yet.")
                            .setTitle("[ERROR] Opening file " + itemName)
                            .setCancelable(false)
                            .setPositiveButton("Close", (dialog, id1) -> {
                                // Do nothing
                            });
                    AlertDialog alertDialog = builder.create();
                    alertDialog.show();

                } else {

                    // Open the DICOM Viewer
                    Intent intent = new Intent(this, DICOMSearch.class);
                    intent.putExtra("DICOMFileName", mTopDirectory.getPath() + "/" + itemName);
                    intent.putExtra("FileCount", mTotal);
                    startActivity(intent);
                }

            } catch (Exception ex) {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage("Error while opening the file " + itemName
                        + ". \n" + ex.getMessage())
                        .setTitle("[ERROR] Opening file " + itemName)
                        .setCancelable(false)
                        .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                // Do nothing
                            }
                        });
                AlertDialog alertDialog = builder.create();
                alertDialog.show();
            }
        }
    }

    // Update the content of the view.
    private void fill() {

        // If the external storage is not available
        if (!ExternalStorage.checkAvailable())
            return;

        // Get the children directories and the files of top directories
        File[] childrenFiles = mTopDirectory.listFiles();

        // Declare the directories and the files array
        List<String> directoryList = new ArrayList<>();
        List<String> fileList = new ArrayList<>();

        // Loop on all children
        for (File child: childrenFiles) {

            // If it is a directory
            if (child.isDirectory()) {

                String directoryName = child.getName();
                if (directoryName.charAt(0) != '.')
                    directoryList.add("/" + child.getName());

            }
            // If it is a file.
            else {

                String[] fileName = child.getName().split("\\.");

                if (!child.isHidden()) {

                    if (fileName.length > 1) {

                        // DICOM files have no extension or dcm extension
                        if (fileName[fileName.length-1].equalsIgnoreCase("dcm"))
                            fileList.add(child.getName());

                    } else
                        fileList.add(child.getName());
                }
            }
        }

        // Sort both lists
        directoryList.sort((o1, o2) -> {
            // Check file numbers
            String strNum1 = o1.replaceAll("[^\\d]", "");
            String strNum2 = o2.replaceAll("[^\\d]", "");

            if (strNum1.equals("") || strNum2.equals(""))
                return o1.toLowerCase().compareTo(o2.toLowerCase());
                // Compare file numbers
            else {
                int num1 = Integer.parseInt(strNum1);
                int num2 = Integer.parseInt(strNum2);

                return num1 - num2;
            }
        });
        fileList.sort((o1, o2) -> {
            // Check file numbers
            String strNum1 = o1.replaceAll("[^\\d]", "");
            String strNum2 = o2.replaceAll("[^\\d]", "");

            if (strNum1.equals("") || strNum2.equals(""))
                return o1.toLowerCase().compareTo(o2.toLowerCase());
                // Compare file numbers
            else {
                int num1 = Integer.parseInt(strNum1);
                int num2 = Integer.parseInt(strNum2);

                return num1 - num2;
            }
        });

        // Set the number of dicom files
        mTotal = fileList.size();

        // Output list will be files before directories
        // then we add the directoryList to the fileList
        fileList.addAll(directoryList);

        if (!mTopDirectory.equals(Environment.getExternalStorageDirectory()))
            fileList.add(0, "..");

        mAdapter = new ArrayAdapter<>(this, R.layout.file_choose_item, R.id.fileName, fileList);

        setListAdapter(mAdapter);

    }
}
