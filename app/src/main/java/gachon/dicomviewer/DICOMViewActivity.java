package gachon.dicomviewer;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.imebra.*;
import com.jsibbold.zoomage.ZoomageView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

public class DICOMViewActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener, CompoundButton.OnCheckedChangeListener {

    private TextView mPatientName; // Used to display the patient name
    private TextView mWindowLevel; // Used to display the window level
    private TextView mWindowWidth; // Used to display the window width
    private ZoomageView mImageView; // Used to display the image
    private LinearLayout mNavigationBar; // Layout representing the series navigation bar
    private SeekBar mIndexSeekBar; // Series seek bar.
    private TextView mIndexTextView; // Image index TextView.
    private RadioButton radio_windowing;
    private RadioButton radio_zoom;
    private RadioGroup radioGroup;
    private Button mPreviousButton; // Previous button.
    private Button mNextButton; // Next button.
    private Button sendButton; // Send button for transmitting the DICOM file to PACS.
    private Uri mCurrentUri = null; // Current uri of DICOM file to process
    private File mCurrentFile = null; // Current DICOM file to process
    private File[] mFileArray = null; // The array of DICOM images in the folder.
    private int mFileArrayLength; // Length of loaded DICOM files list
    private int mCurrentFileIndex; // The index of the current file.
    static DataSet loadedData; // A single DICOM data container
    static DataSet retrievedData;
    static ArrayList<DataSet> retrievedDataList;
    static int retrievedInstanceNumber;
    private DICOMFileLoader mDICOMFileLoader = null; // DICOM file loader thread.
    private boolean isPatientNameLoaded = false;
    private boolean isSearch;
    private boolean mBusy = false; // Set if the activity is busy (true) or not (false).
    private int windowCenter;
    private int windowWidth;
    private int tmpCenter = 0;
    private int tmpWidth = 0;
    private final Matrix matrix = new Matrix();
    private final Matrix savedMatrix = new Matrix();
    private final PointF start = new PointF();

    @SuppressLint("ClickableViewAccessibility")
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dicom_view);

        mPatientName = findViewById(R.id.patientName);
        mWindowLevel = findViewById(R.id.windowLevel);
        mWindowWidth = findViewById(R.id.windowWidth);
        mImageView = findViewById(R.id.imageView);
        mNavigationBar = findViewById(R.id.navigationToolbar);
        mPreviousButton = findViewById(R.id.previousImageButton);
        mNextButton = findViewById(R.id.nextImageButton);
        mIndexTextView = findViewById(R.id.imageIndexView);
        mIndexSeekBar = findViewById(R.id.seriesSeekBar);
        sendButton = findViewById(R.id.send_file_button);
        radioGroup = findViewById(R.id.radioGroup);
        radio_windowing = findViewById(R.id.radio_windowing);
        radio_zoom = findViewById(R.id.radio_zoom);

        mNavigationBar.setVisibility(View.INVISIBLE);
        mIndexSeekBar.setOnSeekBarChangeListener(this); // Set the seek bar change index listener
        mImageView.setOnTouchListener(windowingListener); // Set the touch listener for windowing

        radio_windowing.setOnCheckedChangeListener((CompoundButton.OnCheckedChangeListener) this);
        radio_zoom.setOnCheckedChangeListener((CompoundButton.OnCheckedChangeListener) this);

        // First thing: Load the Imebra library
        System.loadLibrary("imebra_lib");

        // Set the size of image to draw
        CodecFactory.setMaximumImageSize(8000, 8000);

        // Get the file name from the savedInstanceState or from the intent
        String fileName = null;

        // If the saved instance state is not null get the file name
        if (savedInstanceState != null)
            fileName = savedInstanceState.getString("file_name");
        // Else get from the intent
        else {
            Intent intent = getIntent();

            if (intent != null && intent.getExtras() != null) {
                fileName = intent.getStringExtra("DICOMFileName");
                isSearch = intent.getBooleanExtra("isSearch", false);

                // If the selected menu is not search, hide the send button.
                if (!isSearch) sendButton.setVisibility(View.INVISIBLE);
                else sendButton.setOnClickListener(search);
            }
        }

        // If the file name is null, alert the user and close the activity
        if (fileName == null)
            Toast.makeText(getApplicationContext(),"[ERROR] Loading file: The file cannot be loaded.\n\n" +
                            "Cannot retrieve its name.", Toast.LENGTH_LONG).show();
        // Else load the file
        else
            loadFiles(fileName);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        // Save the current file name
        String currentFileName = mFileArray[mCurrentFileIndex].getAbsolutePath();
        outState.putString("file_name", currentFileName);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finish();
    }

    @Override
    protected void onPause() {

        // We wait until the end of the loading thread
        // before putting the activity in pause mode
        if (mDICOMFileLoader != null) {

            // Wait until the loading thread die
            while (mDICOMFileLoader.isAlive()) {
                try {
                    synchronized(this) {
                        wait(10);
                    }
                } catch (InterruptedException e) {
                    // Do nothing
                }
            }

        }

        super.onPause();

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
                            DICOMViewActivity.this.finish();
                        }
                    });

            AlertDialog alertDialog = builder.create();
            alertDialog.show();

        }

        super.onResume();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mFileArray = null;

        // Free the drawable callback
        if (mImageView != null) {
            Drawable drawable = mImageView.getDrawable();

            if (drawable != null)
                drawable.setCallback(null);
        }
    }

    @Override
    public synchronized void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

        try {

            // If it is busy, do nothing
            if (mBusy)
                return;

            // It is busy now
            mBusy = true;

            // Wait until the loading thread die
            while (mDICOMFileLoader.isAlive()) {
                try {
                    synchronized(this) {
                        wait(10);
                    }
                } catch (InterruptedException e) {
                    // Do nothing
                }
            }

            // Set the current file index
            mCurrentFileIndex = progress;

            // Start the loading thread to load the DICOM image
            mDICOMFileLoader = new DICOMFileLoader(loadingHandler,
                    mFileArray[mCurrentFileIndex], false);

            mDICOMFileLoader.start();

            // Update the UI
            mIndexTextView.setText(String.valueOf(mCurrentFileIndex + 1));

            // Set the visibility of the previous button
            if (mCurrentFileIndex == 0) {

                mPreviousButton.setVisibility(View.INVISIBLE);
                mNextButton.setVisibility(View.VISIBLE);

            } else if (mCurrentFileIndex == (mFileArrayLength - 1)) {

                mNextButton.setVisibility(View.INVISIBLE);
                mPreviousButton.setVisibility(View.VISIBLE);

            } else {

                mPreviousButton.setVisibility(View.VISIBLE);
                mNextButton.setVisibility(View.VISIBLE);

            }

        } catch (OutOfMemoryError ex) {
            System.gc();

            Toast.makeText(getApplicationContext(), "[ERROR] Out Of Memory: This series contains images that are too big" +
                    " and that cause out of memory error. The best is to don't" +
                    " use the series seek bar. If the error occurs again" +
                    " it is because this series is not adapted to your" +
                    " Android(TM) device.", Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }

    @Override
    @SuppressLint({"NonConstantResourceId", "ClickableViewAccessibility"})
    public void onCheckedChanged(CompoundButton compoundButton, boolean b){
        radioGroup.setOnCheckedChangeListener((radioGroup, i) -> {
            switch (i) {
                case R.id.radio_windowing:
                    radio_windowing.setTextColor(0xFF2196F3);
                    radio_zoom.setTextColor(Color.WHITE);
                    mImageView.setOnTouchListener(windowingListener);
                    break;
                case R.id.radio_zoom:
                    radio_zoom.setTextColor(0xFF2196F3);
                    radio_windowing.setTextColor(Color.WHITE);
                    mImageView.setOnTouchListener(zoomingListener);
                    break;
            }
        });
    }

    /**
     * Get the index of the file in the files array.
     * @param file
     * @return Index of the file in the files array
     * or -1 if the files is not in the list.
     */
    private int getIndex(File file) {

        if (mFileArray == null)
            throw new NullPointerException("The files array is null.");

        for (int i = 0; i < mFileArrayLength; i++)
            if (mFileArray[i].getName().equals(file.getName()))
                return i;

        return -1;

    }

    private void loadFiles(String fileName) {
        // Get the File object for the current file
        mCurrentFile = new File(fileName);

        // Get the files in the parent of the current file
        mFileArray = mCurrentFile.getParentFile().listFiles(new DICOMFileFilter());

        // Get the length of the files list
        mFileArrayLength = mFileArray.length;

        // Sort the files array
        Arrays.sort(mFileArray, (o1, o2) -> {
            // Check file numbers
            String str1 = o1.getName();
            String str2 = o2.getName();
            String strNum1 = str1.replaceAll("[^\\d]", "");
            String strNum2 = str2.replaceAll("[^\\d]", "");

            if (strNum1.equals("") || strNum2.equals(""))
                return str1.toLowerCase().compareTo(str2.toLowerCase());
                // Compare file numbers
            else {
                long num1 = Long.parseLong(strNum1);
                long num2 = Long.parseLong(strNum2);

                return (int)(num1 - num2);
            }
        });

        // Start the loading thread to load the DICOM image
        mDICOMFileLoader = new DICOMFileLoader(loadingHandler, mCurrentFile, false);
        mDICOMFileLoader.start();
        mBusy = true;

        // If the files array is null or its length is less than 1,
        // there is an error because it must contain at least 1 file (the current file).
        if (mFileArray == null || mFileArrayLength < 1)
            Toast.makeText(getApplicationContext(), "[ERROR] Loading file: The file cannot be loaded.\n\n" +
                    "The directory contains no DICOM files.", Toast.LENGTH_LONG).show();
        else {

            // Get the file index in the array
            mCurrentFileIndex = getIndex(mCurrentFile);

            // If the current file index is negative
            // or greater or equal to the files array length there is an error.
            if (mCurrentFileIndex < 0 || mCurrentFileIndex >= mFileArrayLength)
                Toast.makeText(getApplicationContext(), "[ERROR] Loading file: The file cannot be loaded.\n\n" +
                        "The file is not in the directory.", Toast.LENGTH_LONG).show();
                // Else initialize views and navigation bar
            else {

                // Check if the seek bar must be shown or not
                if (mFileArrayLength == 1)
                    mNavigationBar.setVisibility(View.INVISIBLE);
                else {

                    // Display the seek bar and the current file index
                    mNavigationBar.setVisibility(View.VISIBLE);
                    mIndexTextView.setText(String.valueOf(mCurrentFileIndex + 1));

                    // Display the files count and set the seek bar maximum
                    TextView countTextView = findViewById(R.id.imageCountView);
                    countTextView.setText(String.valueOf(mFileArrayLength));
                    mIndexSeekBar.setMax(mFileArrayLength - 1);
                    mIndexSeekBar.setProgress(mCurrentFileIndex);

                    // Set the visibility of the previous button
                    if (mCurrentFileIndex == 0)
                        mPreviousButton.setVisibility(View.INVISIBLE);

                    else if (mCurrentFileIndex == (mFileArrayLength - 1))
                        mNextButton.setVisibility(View.INVISIBLE);
                }
            }
        }
    }

    private final class DICOMFileLoader extends Thread {

        private final Handler mHandler; // Send message to the thread
        private final File mFile; // The file to load
        private boolean mWindowing;

        public DICOMFileLoader(Handler handler, File file, boolean windowing) {

            if (handler == null)
                throw new NullPointerException("The handler is null while calling the loading thread.");

            mHandler = handler;

            if (file == null)
                throw new NullPointerException("The file is null while calling the loading thread.");

            mFile = file;
            mWindowing = windowing;

        }

        public void run() {

            // If the image data is null, do nothing.
            if (!mFile.exists()) {
                Message message = mHandler.obtainMessage();
                message.what = ThreadState.UNCATCHABLE_ERROR_OCCURRED;
                message.obj = "The file doesn't exist.";
                mHandler.sendMessage(message);

            } else if (!mWindowing) {
                Message message = mHandler.obtainMessage();
                message.what = ThreadState.FINISHED;
                mCurrentUri = Uri.fromFile(mFile);
                mHandler.sendMessage(message);

            } else {
                Message message = mHandler.obtainMessage();
                message.what = ThreadState.PROGRESSION_UPDATE;
                mCurrentUri = Uri.fromFile(mFile);
                mHandler.sendMessage(message);
            }
        }
    }

    @SuppressLint("HandlerLeak")
    private final Handler loadingHandler = new Handler() {

        public void handleMessage(Message message) {

            switch (message.what) {

                case ThreadState.STARTED:
                    break;

                // Set the loaded image
                case ThreadState.FINISHED:

                    // Set the loaded image
                    try {

                        if (mCurrentUri == null) {
                            return;
                        }

                        // Then open an InputStream.
                        InputStream stream = getContentResolver().openInputStream(mCurrentUri);

                        // The PipeStream allows to use files on Google Drive or other providers as well.
                        PipeStream imebraPipe = new PipeStream(32000);

                        // Launch a separate thread that reads from the InputStream
                        // and push the data to the Pipe.
                        Thread pushThread = new Thread(new PushToImebraPipe(imebraPipe, stream));
                        pushThread.start();

                        // The CodecFactory will read from the Pipe which is fed by the thread launched above.
                        // We could just get a file's name to it but this would limit what we can read to only local files.
                        loadedData = CodecFactory.load(new StreamReader(imebraPipe.getStreamInput()));

                        // Get the first frame from the data
                        // after the proper modality transforms have been applied.
                        Image dicomImage = loadedData.getImageApplyModalityTransform(0);

                        TransformsChain chain = new TransformsChain();

                        if (ColorTransformsFactory.isMonochrome(dicomImage.getColorSpace())) {
                            VOIDescription description = VOILUT.getOptimalVOI(dicomImage, 0, 0, dicomImage.getWidth(), dicomImage.getHeight());
                            VOILUT voilut = new VOILUT(description);
                            chain.addTransform(voilut);

                            // Update the initial values of window and patient name text view if not updated
                            if (!isPatientNameLoaded) {
                                windowCenter = (int)description.getCenter();
                                windowWidth = (int)description.getWidth();

                                String patientName = loadedData.getPatientName(new TagId(0x10,0x10),0,
                                        new PatientName("Undefined", "", ""))
                                        .getAlphabeticRepresentation();

                                if (patientName.length() != 0)
                                    mPatientName.setText(patientName);

                                mWindowLevel.setText("L: " + windowCenter);
                                mWindowWidth.setText("W: " + windowWidth);

                                isPatientNameLoaded = true;
                            }
                        }

                        // Build a stream of bytes that can be handled by android's Bitmap class using a DrawBitmap
                        DrawBitmap drawBitmap = new DrawBitmap(chain);
                        Memory memory = drawBitmap.getBitmap(dicomImage, drawBitmapType_t.drawBitmapRGBA, 4);

                        byte[] memoryByte = new byte[(int)memory.size()];
                        memory.data(memoryByte);

                        // Build the android's Bitmap object from the raw bytes returned by DrawBitmap.
                        Bitmap renderBitmap = Bitmap.createBitmap((int)dicomImage.getWidth(), (int)dicomImage.getHeight(), Bitmap.Config.ARGB_8888);
                        ByteBuffer byteBuffer = ByteBuffer.wrap(memoryByte);
                        renderBitmap.copyPixelsFromBuffer(byteBuffer);

                        // Update the image
                        mImageView.setImageBitmap(renderBitmap);
                        mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

                        mBusy = false;

                    } catch (IOException e) {
                        AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(getApplicationContext());
                        dlgAlert.setMessage(e.getMessage());
                        dlgAlert.setTitle("Error");
                        dlgAlert.setPositiveButton("OK", (dialog, which) -> {
                            //dismiss the dialog
                        });
                        dlgAlert.setCancelable(true);
                        dlgAlert.create().show();
                    }

                    break;

                case ThreadState.PROGRESSION_UPDATE:

                    // Set the loaded image
                    try {

                        if (mCurrentUri == null) {
                            return;
                        }

                        // Then open an InputStream.
                        InputStream stream = getContentResolver().openInputStream(mCurrentUri);

                        // The PipeStream allows to use files on Google Drive or other providers as well.
                        PipeStream imebraPipe = new PipeStream(32000);

                        // Launch a separate thread that reads from the InputStream
                        // and push the data to the Pipe.
                        Thread pushThread = new Thread(new PushToImebraPipe(imebraPipe, stream));
                        pushThread.start();

                        // The CodecFactory will read from the Pipe which is fed by the thread launched above.
                        // We could just get a file's name to it but this would limit what we can read to only local files.
                        loadedData = CodecFactory.load(new StreamReader(imebraPipe.getStreamInput()));

                        // Get the first frame from the data
                        // after the proper modality transforms have been applied.
                        Image dicomImage = loadedData.getImageApplyModalityTransform(0);

                        TransformsChain chain = new TransformsChain();

                        if (ColorTransformsFactory.isMonochrome(dicomImage.getColorSpace())) {
                            VOIDescription description = VOILUT.getOptimalVOI(dicomImage, 0, 0, dicomImage.getWidth(), dicomImage.getHeight());
                            description = new VOIDescription(windowCenter, windowWidth, description.getFunction(), description.getDescription());
                            VOILUT voilut = new VOILUT(description);
                            chain.addTransform(voilut);
                        }

                        // Update windowing values
                        mWindowLevel.setText("L: " + windowCenter);
                        mWindowWidth.setText("W: " + windowWidth);

                        // Build a stream of bytes that can be handled by android's Bitmap class using a DrawBitmap
                        DrawBitmap drawBitmap = new DrawBitmap(chain);
                        Memory memory = drawBitmap.getBitmap(dicomImage, drawBitmapType_t.drawBitmapRGBA, 4);

                        byte[] memoryByte = new byte[(int)memory.size()];
                        memory.data(memoryByte);

                        // Build the android's Bitmap object from the raw bytes returned by DrawBitmap.
                        Bitmap renderBitmap = Bitmap.createBitmap((int)dicomImage.getWidth(), (int)dicomImage.getHeight(), Bitmap.Config.ARGB_8888);
                        ByteBuffer byteBuffer = ByteBuffer.wrap(memoryByte);
                        renderBitmap.copyPixelsFromBuffer(byteBuffer);

                        // Update the image
                        mImageView.setImageBitmap(renderBitmap);
                        mImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

                        mBusy = false;

                    } catch (IOException e) {
                        AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(getApplicationContext());
                        dlgAlert.setMessage(e.getMessage());
                        dlgAlert.setTitle("Error");
                        dlgAlert.setPositiveButton("OK", (dialog, which) -> {
                            //dismiss the dialog
                        });
                        dlgAlert.setCancelable(true);
                        dlgAlert.create().show();
                    }

                    break;

                case ThreadState.UNCATCHABLE_ERROR_OCCURRED:

                    // Get the error message
                    String errorMessage;

                    if (message.obj instanceof String)
                        errorMessage = (String) message.obj;
                    else
                        errorMessage = "Unknown error";

                    // Show an alert dialog
                    Toast.makeText(getApplicationContext(), "[ERROR] Loading file: " +
                            "An error occured during the file loading.\n\n" + errorMessage, Toast.LENGTH_LONG).show();

                    break;

                case ThreadState.OUT_OF_MEMORY:

                    // Show an alert dialog
                    Toast.makeText(getApplicationContext(), "[ERROR] Loading file: " +
                            "OutOfMemoryError: During the loading of image ("
                            + mFileArray[mCurrentFileIndex].getName()
                            + "), an out of memory error occurred.\n\n"
                            + "Your file is too large for your Android system. You can"
                            + " try to cache the image in the file chooser."
                            + " If the error occured again, then the image cannot be displayed"
                            + " on your device.\n"
                            + "Try to use the Droid Dicom Viewer desktop file cacher software"
                            + " (not available yet).", Toast.LENGTH_LONG).show();

                    break;
            }
        }
    };

    /**
     * Handle touch on the previousButton.
     * @param view
     */
    public synchronized void previousImage(View view) {

        // If it is busy, do nothing
        if (mBusy)
            return;

        // It is busy now
        mBusy = true;

        // Wait until the loading thread die
        while (mDICOMFileLoader.isAlive()) {
            try {
                synchronized(this) {
                    wait(10);
                }
            } catch (InterruptedException e) {
                // Do nothing
            }
        }

        // If the current file index is 0, there is
        // no previous file in the files array
        // We add the less or equal to zero because it is
        // safer
        if (mCurrentFileIndex <= 0) {

            // Not necessary but safer, because we don't know
            // how the code will be used in the future
            mCurrentFileIndex = 0;

            // If for a unknown reason the previous button is
            // visible => hide it
            if (mPreviousButton.getVisibility() == View.VISIBLE)
                mPreviousButton.setVisibility(View.INVISIBLE);

            mBusy = false;

            return;

        }

        //  Decrease the file index
        mCurrentFileIndex--;

        // Start the loading thread to load the DICOM image
        mDICOMFileLoader = new DICOMFileLoader(loadingHandler,
                mFileArray[mCurrentFileIndex], false);

        mDICOMFileLoader.start();

        // Update the UI
        mIndexTextView.setText(String.valueOf(mCurrentFileIndex + 1));
        mIndexSeekBar.setProgress(mCurrentFileIndex);

        if (mCurrentFileIndex == 0)
            mPreviousButton.setVisibility(View.INVISIBLE);

        // The next button is automatically set to visible
        // because if there is a previous image, there is
        // a next image
        mNextButton.setVisibility(View.VISIBLE);

    }

    /**
     * Handle touch on next button.
     * @param view
     */
    public synchronized void nextImage(View view) {

        // If it is busy, do nothing
        if (mBusy)
            return;

        // It is busy now
        mBusy = true;

        // Wait until the loading thread die
        while (mDICOMFileLoader.isAlive()) {
            try {
                synchronized(this) {
                    wait(10);
                }
            } catch (InterruptedException e) {
                // Do nothing
            }
        }

        // If the current file index is the last file index,
        // there is no next file in the files array
        // We add the greater or equal to (mFileArrayLength - 1)
        // because it is safer
        if (mCurrentFileIndex >= (mFileArrayLength - 1)) {

            // Not necessary but safer, because we don't know
            // how the code will be used in the future
            mCurrentFileIndex = (mFileArrayLength - 1);

            // If for a unknown reason the previous button is
            // visible => hide it
            if (mNextButton.getVisibility() == View.VISIBLE)
                mNextButton.setVisibility(View.INVISIBLE);

            mBusy = false;
            return;

        }

        //  Increase the file index
        mCurrentFileIndex++;

        // Start the loading thread to load the DICOM image
        mDICOMFileLoader = new DICOMFileLoader(loadingHandler,
                mFileArray[mCurrentFileIndex], false);

        mDICOMFileLoader.start();

        // Update the UI
        mIndexTextView.setText(String.valueOf(mCurrentFileIndex + 1));
        mIndexSeekBar.setProgress(mCurrentFileIndex);

        if (mCurrentFileIndex == (mFileArrayLength - 1))
            mNextButton.setVisibility(View.INVISIBLE);

        // The previous button is automatically set to visible
        // because if there is a next image, there is
        // a previous image
        mPreviousButton.setVisibility(View.VISIBLE);

    }

    /**
     * Hide navigation tool bar if it is visible.
     * @param view
     */
    public void hideNavigationBar(View view) {

        if (mNavigationBar.getVisibility() == View.VISIBLE)
            mNavigationBar.setVisibility(View.INVISIBLE);
    }

    @SuppressLint("ClickableViewAccessibility")
    View.OnTouchListener windowingListener = (view, motionEvent) -> {
        int action = motionEvent.getAction();

        int curX = (int)motionEvent.getX();
        int curY = (int)motionEvent.getY();

        if (action == MotionEvent.ACTION_DOWN){
            tmpCenter = curX;
            tmpWidth = curY;
        }
        else if (action == MotionEvent.ACTION_MOVE){
            windowCenter += curX - tmpCenter;
            windowWidth += curY - tmpWidth;

            mBusy = true;

            mDICOMFileLoader = new DICOMFileLoader(loadingHandler, mFileArray[mCurrentFileIndex], true);
            mDICOMFileLoader.start();
        }
        else if (action == MotionEvent.ACTION_UP){
            tmpCenter = 0;
            tmpWidth = 0;
        }

        return true;
    };

    @SuppressLint("ClickableViewAccessibility")
    View.OnTouchListener zoomingListener = (view, event) -> {
        int action = event.getAction();

        if (action == MotionEvent.ACTION_DOWN) {
            savedMatrix.set(matrix);
            start.set(event.getX(), event.getY());
        }
        else if (action == MotionEvent.ACTION_MOVE) {
            matrix.set(savedMatrix);
            matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
        }

        return false;
    };

    View.OnClickListener search = v -> {
        // TODO: Put your PC IP address which is connected to WiFi.
        // TODO: Do not commit when your PC IP address is written.
        try {
            // Start SCP service
            // Bind the port 1024 to a listening socket
            TCPListener tcpListener = new TCPListener(new TCPPassiveAddress("", "1024"));

            // Wait until a connection arrives or terminate() is called on the tcpListener
            // TODO: Call tcpListener.terminate(); when 8 seconds pass.
            TCPStream tcpStream = new TCPStream(tcpListener.waitForConnection());

            // tcpStream now represents the connected socket
            // Allocate a stream reader and a writer to read and write on the connected socket
            StreamReader readSCU = new StreamReader(tcpStream.getStreamInput());
            StreamWriter writeSCU = new StreamWriter(tcpStream.getStreamOutput());

            // Specify which presentation contexts we accept
            PresentationContext context = new PresentationContext(imebra.getUidCTImageStorage_1_2_840_10008_5_1_4_1_1_2());
            context.addTransferSyntax(imebra.getUidExplicitVRLittleEndian_1_2_840_10008_1_2_1());
            PresentationContexts presentationContexts = new PresentationContexts();
            presentationContexts.addPresentationContext(context);

            // The AssociationSCP constructor will negotiate the assocation
            AssociationSCP scp = new AssociationSCP("SCP", 1, 1, presentationContexts, readSCU, writeSCU, 0, 10);

            // Receive commands via the dimse service
            DimseService dimse = new DimseService(scp);

            // Initialize the list to get data
            retrievedDataList = new ArrayList<>();

            // Receive commands until the association is closed
            for(;;) {

                // We assume we are going to receive a C-Store. Normally you should check the command type
                // (using DimseCommand::getCommandType()) and then cast to the proper class.
                CStoreCommand command = new CStoreCommand(dimse.getCommand().getAsCStoreCommand());

                // The store command has a payload. We can do something with it, or we can
                // use the methods in CStoreCommand to get other data sent by the peer
                DataSet payload = command.getPayloadDataSet();

                // Do something with the payload
                retrievedDataList.add(payload);

                // Send a response
                dimse.sendCommandOrResponse(new CStoreResponse(command, dimseStatusCode_t.success));
            }

        } catch (Exception e){
            // The association has been closed
            Log.d("TCP Error", e.toString());
            Toast.makeText(this, "Receive Success: The input stream now has been closed", Toast.LENGTH_LONG).show();

            retrievedData = retrievedDataList.get(0);
            retrievedDataList.remove(0);
            retrievedInstanceNumber = Integer.parseInt(retrievedData.getString(new TagId(0x20, 0x13), 0));

            Intent intent = new Intent(getApplicationContext(), DICOMSearchResultActivity.class);
            startActivity(intent);
        }
        /*
        try {
            // Allocate a TCP stream that connects to the DICOM SCP
            TCPStream tcpStream = new TCPStream(new TCPActiveAddress("IP Address of PC", "8104"));

            // Allocate a stream reader and a writer that use the TCP stream
            StreamReader readSCU = new StreamReader(tcpStream.getStreamInput());
            StreamWriter writeSCU = new StreamWriter(tcpStream.getStreamOutput());

            // Add all the abstract syntaxes and the supported transfer syntaxes for each abstract syntax
            // +) The pair abstract/transfer syntax is called "presentation context"
            PresentationContext context = new PresentationContext(imebra.getUidCTImageStorage_1_2_840_10008_5_1_4_1_1_2());
            context.addTransferSyntax(imebra.getUidExplicitVRLittleEndian_1_2_840_10008_1_2_1());
            PresentationContexts presentationContexts = new PresentationContexts();
            presentationContexts.addPresentationContext(context);

            // The AssociationSCU constructor will negotiate a connection
            // through the readSCU and writeSCU stream reader and writer
            AssociationSCU scu = new AssociationSCU("SCU", "XNAT", 1, 1, presentationContexts, readSCU, writeSCU, 0);

            // The DIMSE service will use the negotiated association to send and receive DICOM commands
            DimseService dimse = new DimseService(scu);

            // Prepare a dataset to store on the SCP and fill appropriately all the DataSet tag
            CStoreCommand command = new CStoreCommand(imebra.getUidCTImageStorage_1_2_840_10008_5_1_4_1_1_2(), dimse.getNextCommandID(), dimseCommandPriority_t.medium,
                    loadedData.getString(new TagId(0x08, 0x16), 0),
                    loadedData.getString(new TagId(0x08, 0x18), 0),
                    "", 0, loadedData);

            // Send the command
            dimse.sendCommandOrResponse(command);

            // Get the response of it
            DimseResponse response = new DimseResponse(dimse.getCStoreResponse(command));

            if (response.getStatus() == dimseStatus_t.success) {
                Toast.makeText(this, "Success: File has been sent!", Toast.LENGTH_LONG).show();

                try {
                    // Start SCP service
                    // Bind the port 1024 to a listening socket
                    TCPListener tcpListener = new TCPListener(new TCPPassiveAddress("", "1024"));

                    // Wait until a connection arrives or terminate() is called on the tcpListener
                    // TODO: Call tcpListener.terminate(); when 8 seconds pass.
                    tcpStream = new TCPStream(tcpListener.waitForConnection());

                    // tcpStream now represents the connected socket
                    // Allocate a stream reader and a writer to read and write on the connected socket
                    readSCU = new StreamReader(tcpStream.getStreamInput());
                    writeSCU = new StreamWriter(tcpStream.getStreamOutput());

                    // The AssociationSCP constructor will negotiate the assocation
                    AssociationSCP scp = new AssociationSCP("SCP", 1, 1, presentationContexts, readSCU, writeSCU, 0, 10);

                    // Receive commands via the dimse service
                    dimse = new DimseService(scp);

                    // Initialize the list to get data
                    retrievedDataList = new ArrayList<>();

                    // Receive commands until the association is closed
                    for(;;) {

                        // We assume we are going to receive a C-Store. Normally you should check the command type
                        // (using DimseCommand::getCommandType()) and then cast to the proper class.
                        command = new CStoreCommand(dimse.getCommand().getAsCStoreCommand());

                        // The store command has a payload. We can do something with it, or we can
                        // use the methods in CStoreCommand to get other data sent by the peer
                        DataSet payload = command.getPayloadDataSet();

                        // Do something with the payload
                        retrievedDataList.add(payload);

                        // Send a response
                        dimse.sendCommandOrResponse(new CStoreResponse(command, dimseStatusCode_t.success));
                    }

                } catch (Exception e) {
                    // The association has been closed
                    Log.d("TCP Error", e.toString());
                    Toast.makeText(this, "Receive Success: The input stream now has been closed", Toast.LENGTH_LONG).show();

                    retrievedData = retrievedDataList.get(0);
                    retrievedDataList.remove(0);
                    retrievedInstanceNumber = Integer.parseInt(retrievedData.getString(new TagId(0x20, 0x13), 0));

                    Intent intent = new Intent(getApplicationContext(), DICOMSearchResultActivity.class);
                    startActivity(intent);
                }
            }

        } catch (Exception e) {
            Toast.makeText(this, "Exception: Sending file failed!", Toast.LENGTH_LONG).show();
        }
         */
    };
}
