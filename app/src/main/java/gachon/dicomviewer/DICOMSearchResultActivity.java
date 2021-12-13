package gachon.dicomviewer;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.imebra.CodecFactory;
import com.imebra.ColorTransformsFactory;
import com.imebra.DataSet;
import com.imebra.DrawBitmap;
import com.imebra.Image;
import com.imebra.Memory;
import com.imebra.TransformsChain;
import com.imebra.VOILUT;
import com.imebra.drawBitmapType_t;

import java.nio.ByteBuffer;
import java.util.ArrayList;

public class DICOMSearchResultActivity extends AppCompatActivity implements SeekBar.OnSeekBarChangeListener {

    private ImageView mQueryImageView; // Used to display the query image
    private ImageView mRetrievedImageView; // Used to display the retrieved image
    private LinearLayout mNavigationBar; // Layout representing the series navigation bar
    private SeekBar mIndexSeekBar; // Series seek bar.
    private Button mPreviousButton; // Previous button.
    private Button mNextButton; // Next button.
    private TextView mIndexTextView; // Image index TextView.
    private DataSet retrievedData; // A single retrieved DICOM data container
    private final DataSet searchedData = DICOMViewActivity.loadedData; // A single DICOM Parcelable
    private final ArrayList<DataSet> retrievedDataList = DICOMViewActivity.retrievedDataList; // A multiple retrieved DICOM data container
    private DICOMFileLoader mDICOMFileLoader = null; // DICOM file loader thread.
    private int mDataSize; // Length of retrieved DICOM data list
    private int mCurrentFileIndex = DICOMViewActivity.retrievedInstanceNumber; // The index of the current file.
    private boolean mBusy = false; // Set if the activity is busy (true) or not (false).

    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_dicom_search_result);

        // We will use the ImageView widget to display the DICOM image
        mQueryImageView = findViewById(R.id.queryImage);
        mRetrievedImageView = findViewById(R.id.retrievedImage);
        mNavigationBar = findViewById(R.id.navigationToolbar);
        mPreviousButton = findViewById(R.id.previousImageButton);
        mNextButton = findViewById(R.id.nextImageButton);
        mIndexTextView = findViewById(R.id.imageIndexView);
        mIndexSeekBar = findViewById(R.id.seriesSeekBar);
        mNavigationBar.setVisibility(View.INVISIBLE);

        // Set the seek bar change index listener
        mIndexSeekBar.setOnSeekBarChangeListener(this);

        // First thing: Load the Imebra library
        System.loadLibrary("imebra_lib");

        // Set the size of image to draw
        CodecFactory.setMaximumImageSize(8000, 8000);

        // Load the query image
        if (searchedData != null) loadQueryImage();

        // If the retrieved data list is null, alert the user and close the activity
        if (retrievedDataList == null)
            Toast.makeText(getApplicationContext(),"[ERROR] Loading files: Files cannot be loaded.\n\n" +
                    "Cannot retrieve files.", Toast.LENGTH_LONG).show();
        // Else load the data
        else loadRetrievedImages();
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
                            DICOMSearchResultActivity.this.finish();
                        }
                    });

            AlertDialog alertDialog = builder.create();
            alertDialog.show();

        }

        super.onResume();

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
                    retrievedDataList.get(mCurrentFileIndex));

            mDICOMFileLoader.start();

            // Update the UI
            mIndexTextView.setText(String.valueOf(mCurrentFileIndex + 1));

            // Set the visibility of the previous button
            if (mCurrentFileIndex == 0) {

                mPreviousButton.setVisibility(View.INVISIBLE);
                mNextButton.setVisibility(View.VISIBLE);

            } else if (mCurrentFileIndex == (mDataSize - 1)) {

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

    private void loadQueryImage() {
        Image dicomImage = searchedData.getImageApplyModalityTransform(0);

        TransformsChain chain = new TransformsChain();

        if (ColorTransformsFactory.isMonochrome(dicomImage.getColorSpace())) {
            VOILUT voilut = new VOILUT(VOILUT.getOptimalVOI(dicomImage, 0, 0, dicomImage.getWidth(), dicomImage.getHeight()));
            chain.addTransform(voilut);
        }

        DrawBitmap drawBitmap = new DrawBitmap(chain);
        Memory memory = drawBitmap.getBitmap(dicomImage, drawBitmapType_t.drawBitmapRGBA, 4);

        byte[] memoryByte = new byte[(int)memory.size()];
        memory.data(memoryByte);

        Bitmap renderBitmap = Bitmap.createBitmap((int)dicomImage.getWidth(), (int)dicomImage.getHeight(), Bitmap.Config.ARGB_8888);
        ByteBuffer byteBuffer = ByteBuffer.wrap(memoryByte);
        renderBitmap.copyPixelsFromBuffer(byteBuffer);

        mQueryImageView.setImageBitmap(renderBitmap);
        mQueryImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
    }

    private void loadRetrievedImages() {
        mDataSize = retrievedDataList.size();

        // Start the loading thread to load the DICOM image
        mDICOMFileLoader = new DICOMFileLoader(loadingHandler, retrievedDataList.get(mCurrentFileIndex));
        mDICOMFileLoader.start();
        mBusy = true;

        // If the current file index is negative
        // or greater or equal to the files array length there is an error.
        if (mCurrentFileIndex < 0 || mCurrentFileIndex >= mDataSize)
            Toast.makeText(getApplicationContext(), "[ERROR] Loading file: The file cannot be loaded.\n\n" +
                    "The file is not in the directory.", Toast.LENGTH_LONG).show();
            // Else initialize views and navigation bar
        else {

            // Check if the seek bar must be shown or not
            if (mDataSize == 1)
                mNavigationBar.setVisibility(View.INVISIBLE);
            else {

                // Display the seek bar and the current file index
                mNavigationBar.setVisibility(View.VISIBLE);
                mIndexTextView.setText(String.valueOf(mCurrentFileIndex + 1));

                // Display the files count and set the seek bar maximum
                TextView countTextView = findViewById(R.id.imageCountView);
                countTextView.setText(String.valueOf(mDataSize));
                mIndexSeekBar.setMax(mDataSize - 1);
                mIndexSeekBar.setProgress(mCurrentFileIndex);

                // Set the visibility of the previous button
                if (mCurrentFileIndex == 0)
                    mPreviousButton.setVisibility(View.INVISIBLE);

                else if (mCurrentFileIndex == (mDataSize - 1))
                    mNextButton.setVisibility(View.INVISIBLE);
            }
        }
    }

    private final class DICOMFileLoader extends Thread {

        private final Handler mHandler; // Send message to the thread
        private final DataSet mData; // The data to load

        public DICOMFileLoader(Handler handler, DataSet data) {

            if (handler == null)
                throw new NullPointerException("The handler is null while calling the loading thread.");

            mHandler = handler;

            if (data == null)
                throw new NullPointerException("The file is null while calling the loading thread.");

            mData = data;

        }

        public void run() {

            Message message = mHandler.obtainMessage();
            message.what = ThreadState.FINISHED;
            retrievedData = mData;
            mHandler.sendMessage(message);

        }
    }

    @SuppressLint("HandlerLeak")
    private final Handler loadingHandler = new Handler() {

        public void handleMessage(Message message) {

            switch (message.what) {

                case ThreadState.STARTED:
                    break;

                case ThreadState.FINISHED:

                    // Set the loaded image
                    if (retrievedData == null) {
                        return;
                    }

                    DataSet data = retrievedData;

                    // Get the first frame from the data
                    // after the proper modality transforms have been applied.
                    Image dicomImage = data.getImageApplyModalityTransform(0);

                    TransformsChain chain = new TransformsChain();

                    if (ColorTransformsFactory.isMonochrome(dicomImage.getColorSpace())) {
                        VOILUT voilut = new VOILUT(VOILUT.getOptimalVOI(dicomImage, 0, 0, dicomImage.getWidth(), dicomImage.getHeight()));
                        chain.addTransform(voilut);
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
                    mRetrievedImageView.setImageBitmap(renderBitmap);
                    mRetrievedImageView.setScaleType(ImageView.ScaleType.FIT_CENTER);

                    mBusy = false;

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
                            + retrievedDataList.get(mCurrentFileIndex).toString()
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
                retrievedDataList.get(mCurrentFileIndex));

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
        if (mCurrentFileIndex >= (mDataSize - 1)) {

            // Not necessary but safer, because we don't know
            // how the code will be used in the future
            mCurrentFileIndex = (mDataSize - 1);

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
                retrievedDataList.get(mCurrentFileIndex));

        mDICOMFileLoader.start();

        // Update the UI
        mIndexTextView.setText(String.valueOf(mCurrentFileIndex + 1));
        mIndexSeekBar.setProgress(mCurrentFileIndex);

        if (mCurrentFileIndex == (mDataSize - 1))
            mNextButton.setVisibility(View.INVISIBLE);

        // The previous button is automatically set to visible
        // because if there is a next image, there is
        // a previous image
        mPreviousButton.setVisibility(View.VISIBLE);

    }
}
