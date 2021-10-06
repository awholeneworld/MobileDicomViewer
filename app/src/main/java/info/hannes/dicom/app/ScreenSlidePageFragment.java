package info.hannes.dicom.app;

import android.app.Fragment;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;

import com.imebra.*;

import java.util.Timer;
import java.util.TimerTask;

import info.hannes.dicom.app.trash.Patients;
import info.hannes.dicom.app.trash.Series;

public class ScreenSlidePageFragment extends Fragment {
    /**
     * The argument key for the page number this fragment represents.
     */
    public static final String ARG_PAGE              = "page";
    public static final String ARG_PATIENT_NAME      = "patient_name";
    public static final String ARG_MEDICAL_TEST_NAME = "medical_test_name";
    public static final String ARG_SERIES_NAME       = "series_name";

    /**
     * The fragment's page number, which is set to the argument value for {@link #ARG_PAGE}.
     */
    private int    mPageNumber;
    private String mPatientName;
    private String mMedicalTestName;
    private String mSeriesName;

    private Timer     timer;
    private MyHandler handler;
    private Images    images;

    private ImageView imageView;
    private boolean animationRunning = false;
    private Series series;

    /**
     * Factory method for this fragment class. Constructs a new fragment for the given page number.
     */
    public static ScreenSlidePageFragment create(int pageNumber, String patientName, String medicalTestName, String seriesName) {
        ScreenSlidePageFragment fragment = new ScreenSlidePageFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_PAGE, pageNumber);
        args.putString(ARG_PATIENT_NAME, patientName);
        args.putString(ARG_MEDICAL_TEST_NAME, medicalTestName);
        args.putString(ARG_SERIES_NAME, seriesName);
        fragment.setArguments(args);
        return fragment;
    }

    public ScreenSlidePageFragment() {
    }

    @Override
    public void onStop() {
        super.onStop();
        this.timer.cancel();
        this.timer.purge();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPageNumber = getArguments().getInt(ARG_PAGE);
        mMedicalTestName = getArguments().getString(ARG_MEDICAL_TEST_NAME);
        mPatientName = getArguments().getString(ARG_PATIENT_NAME);
        mSeriesName = getArguments().getString(ARG_SERIES_NAME);
        Log.i("mPageNumber: ", "" + mPageNumber);
        Log.i("mPatientName: ", "" + mPatientName);
        Log.i("mMedicalTestName: ", "" + mMedicalTestName);
        Log.i("mSeriesName: ", "" + mSeriesName);
        this.series = Patients.getInstance().getPatient(mPatientName).getMedicalTest(mMedicalTestName).getSeries(mSeriesName);
        this.images = series.getImages();

        this.handler = new MyHandler();
        this.timer = new Timer();
//        this.timer.schedule(new TickClass(), 500, 500);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout containing a title and body text.
        ViewGroup rootView = (ViewGroup) inflater.inflate(R.layout.fragment_screen_slide_page, container, false);
        this.imageView = rootView.findViewById(R.id.imageView);

//        // Set the title view to show the page number.
//        ((TextView) rootView.findViewById(android.R.id.text1)).setText(
//                getString(R.string.title_template_step, mPageNumber + 1));

        final Button button = rootView.findViewById(R.id.play_animation);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Perform action on click
                if (animationRunning) {
                    timer.cancel();
                    timer.purge();
                } else {
                    timer = new Timer();
                    timer.schedule(new TickClass(), 500, 500);
                }
                animationRunning = !animationRunning;
            }
        });

        displayDicomImage(series.getImages().getImage(mPageNumber));

        return rootView;
    }

    private void displayDicomImage(String path) {
        // Open the dicom file from sdcard
        FileStreamInput stream = new FileStreamInput(path);
        // Build an internal representation of the Dicom file. Tags larger than 256 bytes
        //  will be loaded on demand from the file
        DataSet dataSet = CodecFactory.load(new StreamReader(stream), 256);
        // Get the first image
        Image image = dataSet.getImage(0);
        // Monochrome images may have a modality transform
        if (ColorTransformsFactory.isMonochrome(image.getColorSpace())) {
            ModalityVOILUT modalityVOILUT = new ModalityVOILUT(dataSet);
            if (!modalityVOILUT.isEmpty()) {
                MutableImage modalityImage = modalityVOILUT.allocateOutputImage(image, image.getWidth(), image.getHeight());
                modalityVOILUT.runTransform(image, 0, 0, image.getWidth(), image.getHeight(), modalityImage, 0, 0);
                image = modalityImage;
            }
        }

        //String patientName = dataSet.getString(0x0010, 0, 0x0010);
        //Log.i("patient name from dcm", patientName);
        //String studyDesc = dataSet.getString(0x0008, 0, 0x1030);
        //String seriesDesc = dataSet.getString(0x0008, 0, 0x103E);

        // Allocate a transforms chain: contains all the transforms to execute before displaying
        //  an image
        TransformsChain transformsChain = new TransformsChain();
        // Monochromatic image may require a presentation transform to display interesting data
        /*
        if (ColorTransformsFactory.isMonochrome(image.getColorSpace())) {
            VOILUT voilut = new VOILUT(dataSet);
            int voilutId = voilut.getVOILUTId(0);
            if (voilutId != 0) {
                voilut.setVOILUT(voilutId);
            } else {
                // No presentation transform is present: here we calculate the optimal window/width (brightness,
                //  contrast) and we will use that
                voilut.getOptimalVOI(image, 0, 0, image.getWidth(), image.getHeight());
            }
            transformsChain.addTransform(voilut);
        }
         */
        // Let's find the DicomView and set the image
        DrawBitmap bitmap = new DrawBitmap(transformsChain);
        //imageView.setImageDrawable(bitmap.getBitmap(image, drawBitmapType_t.drawBitmapRGB, 4));
    }

    /**
     * Returns the page number represented by this fragment object.
     */
    public int getPageNumber() {
        return mPageNumber;
    }

    private class TickClass extends TimerTask {
        @Override
        public void run() {
            int dummyMessage = 0;
            handler.sendEmptyMessage(dummyMessage);
            images.nextImageToDisplay();
        }
    }

    private class MyHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Log.i("Displaying Image: ", images.getCurrentImage());
            displayDicomImage(images.getCurrentImage());
        }
    }
}
