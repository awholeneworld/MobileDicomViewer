package info.hannes.dicom.app.trash;

import android.app.ListActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

import info.hannes.dicom.app.R;
import info.hannes.dicom.app.ScreenSlideActivity;

public class SeriesListActivity extends ListActivity{
    private Patient patient;

    /**
     * The collection of all samples in the app. This gets instantiated in {@link
     * #onCreate(android.os.Bundle)} because the {@link Sample} constructor needs access to {@link
     * android.content.res.Resources}.
     */
    private static Sample[] mSamples;
    private String medicalTestName;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            String patientName = extras.getString("patient_name");
            patient = Patients.getInstance().getPatient(patientName);
            medicalTestName = extras.getString("medical_test");
        }

        setContentView(R.layout.series_list);

        // Instantiate the list of samples.

        ArrayList<Sample> samples = new ArrayList<>();
        for (Series s : patient.getAllSeries(medicalTestName).values()) {
            samples.add(new Sample(s.getName(), ScreenSlideActivity.class));
        }

        mSamples = samples.toArray(new Sample[samples.size()]);
        setListAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1,
                android.R.id.text1,
                mSamples));
    }

    @Override
    protected void onListItemClick(ListView listView, View view, int position, long id) {
        // Launch the sample associated with this list position.
        Intent intent = new Intent(SeriesListActivity.this, mSamples[position].getActivityClass());
        intent.putExtra("series_name", mSamples[position].getTitle());
        intent.putExtra("patient_name", patient.getName());
        intent.putExtra("medical_test", medicalTestName);
        startActivity(intent);
    }
}
