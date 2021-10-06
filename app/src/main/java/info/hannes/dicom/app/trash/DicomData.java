package info.hannes.dicom.app.trash;

import com.imebra.*;

public class DicomData {
    private String patientName;
    private String medicalTestName;
    private String seriesName;
    private String path;

    public DicomData(String path) {
        this.path = path;

        FileStreamInput stream = new FileStreamInput(path);
        // Build an internal representation of the Dicom file. Tags larger than 256 bytes
        //  will be loaded on demand from the file
        DataSet dataSet = CodecFactory.load(new StreamReader(stream), 256);

        this.patientName = dataSet.getString(dataSet.getTags().get(0), 0, "0x0010");
        this.medicalTestName = dataSet.getString(dataSet.getTags().get(1), 0, "0x1030");
        this.seriesName = dataSet.getString(dataSet.getTags().get(2), 0, "0x103E");
    }

    public String getPatientName() {
        return patientName;
    }

    public String getMedicalTestName() {
        return medicalTestName;
    }

    public String getSeriesName() {
        return seriesName;
    }

    public String getPath() {
        return path;
    }
}
