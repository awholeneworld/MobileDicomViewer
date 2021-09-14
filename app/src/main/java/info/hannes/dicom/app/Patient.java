package info.hannes.dicom.app;

import java.util.HashMap;
import java.util.Map;

public class Patient {
    private String name;
    private Map<String, MedicalTest> medicalTests = new HashMap<>();

    public Patient(String patientName) {
        this.name = patientName;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void addOneImage(DicomData dicomData) {
        String medicalTestName = dicomData.getMedicalTestName();
        if (medicalTestExist(medicalTestName)) {
            this.medicalTests.get(medicalTestName).addOneImage(dicomData);
        } else {
            MedicalTest medicalTest = new MedicalTest(medicalTestName);
            medicalTest.addOneImage(dicomData);
            this.medicalTests.put(medicalTestName, medicalTest);
        }
    }

    private boolean medicalTestExist(String medicalTestName) {
        return this.medicalTests.containsKey(medicalTestName);
    }

    public Map<String, MedicalTest> getMedicalTests() {
        return medicalTests;
    }


    public Map<String, Series> getAllSeries(String medicalTestName) {
        return medicalTests.get(medicalTestName).getAllSeries();
    }

    public MedicalTest getMedicalTest(String medicalTestName) {
        return this.medicalTests.get(medicalTestName);
    }
}
