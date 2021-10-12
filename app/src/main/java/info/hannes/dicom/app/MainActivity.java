package info.hannes.dicom.app;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.imebra.CodecFactory;
import com.imebra.ColorTransformsFactory;
import com.imebra.DataSet;
import com.imebra.DrawBitmap;
import com.imebra.Image;
import com.imebra.Memory;
import com.imebra.PatientName;
import com.imebra.PipeStream;
import com.imebra.StreamReader;
import com.imebra.TagId;
import com.imebra.TransformsChain;
import com.imebra.VOILUT;
import com.imebra.drawBitmapType_t;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {
    private ImageView mImageView; // Used to display the image
    private TextView mTextView;  // Used to display the patient name
    private static final int REQUEST_CODE = 6384; // onActivityResult request
    private static final String TAG = "FileChooserExample";

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // First thing: Load the Imebra library
        System.loadLibrary("imebra_lib");

        // We will use the ImageView widget to display the DICOM image
        mImageView = findViewById(R.id.imageView);
        mTextView = findViewById(R.id.textView);

        Button loadButton = findViewById(R.id.choose_file_button);
        loadButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Toast.makeText(MainActivity.this, "choose file", Toast.LENGTH_SHORT).show();
                // get via onActivityResult()
                Intent intent = new Intent().setType("*/*").setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select a DICOM file"), 123);
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 123 && resultCode == RESULT_OK) {
            try {

                CodecFactory.setMaximumImageSize(8000, 8000);

                // Get the URI of selected file.
                Uri selectedfile = data.getData();
                if (selectedfile == null) {
                    return;
                }

                // Then open an InputStream.
                InputStream stream = getContentResolver().openInputStream(selectedfile);

                // The PipeStream allows to use files on Google Drive or other providers as well.
                PipeStream imebraPipe = new PipeStream(32000);

                // Launch a separate thread that reads from the InputStream
                // and push the data to the Pipe.
                Thread pushThread = new Thread(new PushToImebraPipe(imebraPipe, stream));
                pushThread.start();

                // The CodecFactory will read from the Pipe which is fed by the thread launched above.
                // We could just get a file's name to it but this would limit what we can read to only local files.
                DataSet loadDataSet = CodecFactory.load(new StreamReader(imebraPipe.getStreamInput()));

                // Get the first frame from the dataset
                // after the proper modality transforms have been applied.
                Image dicomImage = loadDataSet.getImageApplyModalityTransform(0);

                TransformsChain chain = new TransformsChain();

                if (ColorTransformsFactory.isMonochrome(dicomImage.getColorSpace())) {
                    VOILUT voilut = new VOILUT(VOILUT.getOptimalVOI(dicomImage, 0, 0, dicomImage.getWidth(), dicomImage.getHeight()));
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

                // Update the text with the patient name
                String patientName = loadDataSet.getPatientName(new TagId(0x10,0x10),0,
                        new PatientName("Undefined", "", ""))
                        .getAlphabeticRepresentation();
                if (patientName.length() != 0)
                    mTextView.setText(patientName);

            } catch(IOException e) {
                AlertDialog.Builder dlgAlert  = new AlertDialog.Builder(this);
                dlgAlert.setMessage(e.getMessage());
                dlgAlert.setTitle("Error");
                dlgAlert.setPositiveButton("OK", (dialog, which) -> {
                    //dismiss the dialog
                });
                dlgAlert.setCancelable(true);
                dlgAlert.create().show();
            }
        }

    }
    @Override
    public void onBackPressed() {
        Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
        startActivity(intent);
        finish();
    }
}
