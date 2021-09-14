package info.hannes.dicom.app;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.imebra.dicom.*;

import java.util.Timer;
import java.util.TimerTask;

public class Animation extends Activity {
    private Timer timer;
    private MyHandler handler;

    public static final String ARG_PATIENT_NAME= "patient_name";
    public static final String ARG_MEDICAL_TEST_NAME= "medical_test_name";
    public static final String ARG_SERIES_NAME= "series_name";

    private String mPatientName;
    private String mMedicalTestName;
    private Images images;
    private String mSeriesName;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.animation_activity);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            mPatientName = extras.getString(ARG_PATIENT_NAME);
            Log.i("ARG_PATIENT_NAME", mPatientName);
            mMedicalTestName = extras.getString(ARG_MEDICAL_TEST_NAME);
            Log.i("ARG_MEDICAL_TEST_NAME", mMedicalTestName);
            mSeriesName = extras.getString(ARG_SERIES_NAME);

            Patient patient = Patients.getInstance().getPatient(mPatientName);
            Series series = patient.getMedicalTest(mMedicalTestName).getSeries(mSeriesName);
            this.images = series.getImages();
        }

        this.handler = new MyHandler();
        this.timer= new Timer();
        this.timer.schedule(new TickClass(), 500, 500);
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

    public void onStart() {
        super.onStart();
        displayDicomImage(images.getCurrentImage());
    }

    @Override
    protected void onStop() {
        super.onStop();
        this.timer.cancel();
        this.timer.purge();
    }

    private void displayDicomImage(String path) {
        // Open the dicom file from sdcard
        Stream stream = new Stream();
        stream.openFileRead(path);
        // Build an internal representation of the Dicom file. Tags larger than 256 bytes
        //  will be loaded on demand from the file
        DataSet dataSet = CodecFactory.load(new StreamReader(stream), 256);
        // Get the first image
        Image image = dataSet.getImage(0);
        // Monochrome images may have a modality transform
        if(ColorTransformsFactory.isMonochrome(image.getColorSpace()))
        {
            ModalityVOILUT modalityVOILUT = new ModalityVOILUT(dataSet);
            if(!modalityVOILUT.isEmpty())
            {
                Image modalityImage = modalityVOILUT.allocateOutputImage(image, image.getSizeX(), image.getSizeY());
                modalityVOILUT.runTransform(image, 0, 0, image.getSizeX(), image.getSizeY(), modalityImage, 0, 0);
                image = modalityImage;
            }
        }
        // Allocate a transforms chain: contains all the transforms to execute before displaying
        //  an image
        TransformsChain transformsChain = new TransformsChain();
        // Monochromatic image may require a presentation transform to display interesting data
        if(ColorTransformsFactory.isMonochrome(image.getColorSpace()))
        {
            VOILUT voilut = new VOILUT(dataSet);
            int voilutId = voilut.getVOILUTId(0);
            if(voilutId != 0)
            {
                voilut.setVOILUT(voilutId);
            }
            else
            {
                // No presentation transform is present: here we calculate the optimal window/width (brightness,
                //  contrast) and we will use that
                voilut.applyOptimalVOI(image, 0, 0, image.getSizeX(), image.getSizeY());
            }
            transformsChain.addTransform(voilut);
        }
        // Let's find the DicomView and se the image
        DicomView imageView = (DicomView)findViewById(R.id.dicomView);
        imageView.setImage(image, transformsChain);
    }

}
