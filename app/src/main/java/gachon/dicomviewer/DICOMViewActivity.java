package gachon.dicomviewer;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.ablanco.zoomy.Zoomy;
import com.imebra.AssociationSCU;
import com.imebra.CStoreCommand;
import com.imebra.CodecFactory;
import com.imebra.ColorTransformsFactory;
import com.imebra.DataSet;
import com.imebra.DimseResponse;
import com.imebra.DimseService;
import com.imebra.DrawBitmap;
import com.imebra.Image;
import com.imebra.Memory;
import com.imebra.PatientName;
import com.imebra.PipeStream;
import com.imebra.PresentationContext;
import com.imebra.PresentationContexts;
import com.imebra.StreamReader;
import com.imebra.StreamWriter;
import com.imebra.TCPActiveAddress;
import com.imebra.TCPStream;
import com.imebra.TagId;
import com.imebra.TransformsChain;
import com.imebra.VOIDescription;
import com.imebra.VOILUT;
import com.imebra.dimseCommandPriority_t;
import com.imebra.dimseStatus_t;
import com.imebra.drawBitmapType_t;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class DICOMViewActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener {

    private TextView mTextView; // Used to display the patient name
    private ImageView mImageView; // Used to display the image
    private LinearLayout mNavigationBar; // Layout representing the series navigation bar
    private SeekBar mIndexSeekBar; // Series seek bar.
    private TextView mIndexTextView; // Image index TextView.
    private Button mPreviousButton; // Previous button.
    private Button mNextButton; // Next button.
    private Button sendButton; // Send button for transmitting the DICOM file to PACS.
    private Uri mCurrentUri = null; // Current uri of DICOM file to process
    private File mCurrentFile = null; // Current DICOM file to process
    private File[] mFileArray = null; // The array of DICOM images in the folder.
    private int mCurrentFileIndex; // The index of the current file.
    private DataSet loadedData; // A single DICOM file container
    private DICOMFileLoader mDICOMFileLoader = null; // DICOM file loader thread.
    private boolean isPatientNameLoaded = false;
    private boolean isSearch;
    private boolean mBusy = false; // Set if the activity is busy (true) or not (false).
    private int windowCenter;
    private int windowWidth;
    private int tmpCenter = 0;
    private int tmpWidth = 0;

    @SuppressLint("ClickableViewAccessibility")
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dicom_view);

        // We will use the ImageView widget to display the DICOM image
        mTextView = findViewById(R.id.textView);
        mImageView = findViewById(R.id.imageView);
        mNavigationBar = findViewById(R.id.navigationToolbar);
        mPreviousButton = findViewById(R.id.previousImageButton);
        mNextButton = findViewById(R.id.nextImageButton);
        mIndexTextView = findViewById(R.id.imageIndexView);
        mIndexSeekBar = findViewById(R.id.seriesSeekBar);
        sendButton = findViewById(R.id.send_file_button);
        mNavigationBar.setVisibility(View.INVISIBLE);

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

            if (intent != null) {
                isSearch = intent.getBooleanExtra("isSearch", false);
                Bundle extras = intent.getExtras();
                fileName = extras == null ? null : extras.getString("DICOMFileName");
            }
        }

        // If the file name is null, alert the user and close the activity
        if (fileName == null)
            Toast.makeText(getApplicationContext(),"[ERROR] Loading file: The file cannot be loaded.\n\n" +
                            "Cannot retrieve its name.", Toast.LENGTH_LONG).show();
        // Else load the file
        else {
            // Get the File object for the current file
            mCurrentFile = new File(fileName);

            // Get the files in the parent of the current file
            mFileArray = mCurrentFile.getParentFile().listFiles(new DICOMFileFilter());

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
            if (mFileArray == null || mFileArray.length < 1)
                Toast.makeText(getApplicationContext(), "[ERROR] Loading file: The file cannot be loaded.\n\n" +
                        "The directory contains no DICOM files.", Toast.LENGTH_LONG).show();
            else {

                // Get the file index in the array
                mCurrentFileIndex = getIndex(mCurrentFile);

                // If the current file index is negative
                // or greater or equal to the files array length there is an error.
                if (mCurrentFileIndex < 0 || mCurrentFileIndex >= mFileArray.length)
                    Toast.makeText(getApplicationContext(), "[ERROR] Loading file: The file cannot be loaded.\n\n" +
                            "The file is not in the directory.", Toast.LENGTH_LONG).show();
                // Else initialize views and navigation bar
                else {

                    // Check if the seek bar must be shown or not
                    if (mFileArray.length == 1)
                        mNavigationBar.setVisibility(View.INVISIBLE);
                    else {

                        // Display the seek bar and the current file index
                        mNavigationBar.setVisibility(View.VISIBLE);
                        mIndexTextView.setText(String.valueOf(mCurrentFileIndex + 1));

                        // Display the files count and set the seek bar maximum
                        TextView countTextView = findViewById(R.id.imageCountView);
                        countTextView.setText(String.valueOf(mFileArray.length));
                        mIndexSeekBar.setMax(mFileArray.length - 1);
                        mIndexSeekBar.setProgress(mCurrentFileIndex);

                        // Set the visibility of the previous button
                        if (mCurrentFileIndex == 0)
                            mPreviousButton.setVisibility(View.INVISIBLE);

                        else if (mCurrentFileIndex == (mFileArray.length - 1))
                            mNextButton.setVisibility(View.INVISIBLE);
                    }
                }
            }
        }

        // Set the seek bar change index listener
        mIndexSeekBar.setOnSeekBarChangeListener(this);

        // Set the touch listener for windowing
        mImageView.setOnTouchListener((view, motionEvent) -> {
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
        });

        // If the selected menu is not search, hide the send button.
        if (!isSearch) sendButton.setVisibility(View.VISIBLE);
        else sendButton.setOnClickListener(v -> {
                // TODO: Put your PC IP address which is connected to WiFi.
                // TODO: Do not commit when your PC IP address is written.
                TCPStream tcpStream = new TCPStream(new TCPActiveAddress("PC IP ADDRESS", "8104"));
                StreamReader readSCU = new StreamReader(tcpStream.getStreamInput());
                StreamWriter writeSCU = new StreamWriter(tcpStream.getStreamOutput());

                PresentationContext context = new PresentationContext("1.2.840.10008.5.1.4.1.1.2");
                context.addTransferSyntax("1.2.840.10008.1.2.1");
                PresentationContexts presentationContexts = new PresentationContexts();
                presentationContexts.addPresentationContext(context);

                AssociationSCU scu = new AssociationSCU("SCU", "XNAT", 1, 1, presentationContexts, readSCU, writeSCU, 0);
                DimseService dimse = new DimseService(scu);

                CStoreCommand command = new CStoreCommand("1.2.840.10008.5.1.4.1.1.2", dimse.getNextCommandID(), dimseCommandPriority_t.medium,
                        loadedData.getString(new TagId(0x08, 0x16), 0),
                        loadedData.getString(new TagId(0x08, 0x18), 0),
                        "", 0, loadedData);

                dimse.sendCommandOrResponse(command);
                DimseResponse response = new DimseResponse(dimse.getCStoreResponse(command));

                if (response.getStatus() == dimseStatus_t.success)
                    Toast.makeText(DICOMViewActivity.this, "Success: File is sent!", Toast.LENGTH_LONG).show();
            });
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
        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
        intent.putExtra("isSearch", true);
        startActivity(intent);
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

            } else if (mCurrentFileIndex == (mFileArray.length - 1)) {

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
                            windowCenter = (int)description.getCenter();
                            windowWidth = (int)description.getWidth();

                            VOILUT voilut = new VOILUT(description);
                            chain.addTransform(voilut);
                        }

                        // Build a stream of bytes that can be handled by android's Bitmap class using a DrawBitmap
                        DrawBitmap drawBitmap = new DrawBitmap(chain);
                        Memory memory = drawBitmap.getBitmap(dicomImage, drawBitmapType_t.drawBitmapRGBA, 4);

                        // Build the android's Bitmap object from the raw bytes returned by DrawBitmap.
                        Bitmap renderBitmap = Bitmap.createBitmap((int)dicomImage.getWidth(), (int)dicomImage.getHeight(), Bitmap.Config.ARGB_8888);
                        byte[] memoryByte = new byte[(int)memory.size()];
                        memory.data(memoryByte);
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

                        // Build a stream of bytes that can be handled by android's Bitmap class using a DrawBitmap
                        DrawBitmap drawBitmap = new DrawBitmap(chain);
                        Memory memory = drawBitmap.getBitmap(dicomImage, drawBitmapType_t.drawBitmapRGBA, 4);

                        // Build the android's Bitmap object from the raw bytes returned by DrawBitmap.
                        Bitmap renderBitmap = Bitmap.createBitmap((int)dicomImage.getWidth(), (int)dicomImage.getHeight(), Bitmap.Config.ARGB_8888);
                        byte[] memoryByte = new byte[(int)memory.size()];
                        memory.data(memoryByte);
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
        // We add the greater or equal to (mFileArray.length - 1)
        // because it is safer
        if (mCurrentFileIndex >= (mFileArray.length - 1)) {

            // Not necessary but safer, because we don't know
            // how the code will be used in the future
            mCurrentFileIndex = (mFileArray.length - 1);

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

        if (mCurrentFileIndex == (mFileArray.length - 1))
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

    /**
     * Get the index of the file in the files array.
     * @param file
     * @return Index of the file in the files array
     * or -1 if the files is not in the list.
     */
    private int getIndex(File file) {

        if (mFileArray == null)
            throw new NullPointerException("The files array is null.");

        for (int i = 0; i < mFileArray.length; i++)
            if (mFileArray[i].getName().equals(file.getName()))
                return i;

        return -1;

    }
}
