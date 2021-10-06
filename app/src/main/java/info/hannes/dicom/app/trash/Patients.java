package info.hannes.dicom.app.trash;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Map;


public class Patients {
    private static Patients ourInstance = new Patients();
    private Map<String,Patient> patients;

    public static Patients getInstance() {
        return ourInstance;
    }

    private Patients() {
        this.patients = new HashMap<>();
    }

    public Map<String, Patient> getAllPatients() {
        return this.patients;
    }

    public Patient getPatient(String name) {
        return this.patients.get(name);
    }

    private boolean patientExists(String name) {
        return this.patients.containsKey(name);
    }

    public void addOneImage(String path) {
        Log.i("ADDING IMAGE", path);
        DicomData dicomData = new DicomData(path);

        String patientName = dicomData.getPatientName();
        if (this.patientExists(patientName)) {
            this.patients.get(patientName).addOneImage(dicomData);
        } else {
            Patient patient = new Patient(patientName);
            patient.addOneImage(dicomData);
            this.patients.put(patientName, patient);
        }
    }

    public void addPatient(String path, Context context) {
        File file = new File(path);

        if (file.isDirectory()) {
            addRecursivelyImagesInDir(context, file);
        } else {
            addOneImage(file.getAbsolutePath());
        }
    }

    private void addRecursivelyImagesInDir(Context context, File file) {
        String[] extensions = {"dcm"};
        boolean recursive = true;
        /* Collection<File> iterator = FileUtils.listFiles(file, extensions, recursive);

        if (iterator.isEmpty()) {
            Toast.makeText(context, "Cannot find any files with .dcm extension!", Toast.LENGTH_SHORT);
            return;
        }

        for (File f : iterator) {
            addOneImage(f.getAbsolutePath());
        }
         */
    }
}
