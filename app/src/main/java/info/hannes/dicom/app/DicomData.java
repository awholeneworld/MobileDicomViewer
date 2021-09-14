package info.hannes.dicom.app;

import com.imebra.dicom.CodecFactory;
import com.imebra.dicom.DataSet;
import com.imebra.dicom.Stream;
import com.imebra.dicom.StreamReader;

public class DicomData {
    private String patientName;
    private String medicalTestName;
    private String seriesName;
    private String path;

    public DicomData(String path) {
        this.path = path;

        Stream stream = new Stream();
        stream.openFileRead(path);
        // Build an internal representation of the Dicom file. Tags larger than 256 bytes
        //  will be loaded on demand from the file
        DataSet dataSet = CodecFactory.load(new StreamReader(stream), 256);

        this.patientName = dataSet.getString(0x0010, 0, 0x0010, 0);
        this.medicalTestName = dataSet.getString(0x0008, 0, 0x1030, 0);
        this.seriesName = dataSet.getString(0x0008,0,0x103E,0);
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
