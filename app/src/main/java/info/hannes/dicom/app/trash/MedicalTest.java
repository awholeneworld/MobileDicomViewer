package info.hannes.dicom.app.trash;

import java.util.HashMap;
import java.util.Map;

public class MedicalTest {
    private String name;
    private Map<String, Series> series = new HashMap<>();

    public MedicalTest(String testName) {
        this.name = testName;
    }

    public void addOneImage(DicomData dicomData) {
        String seriesName = dicomData.getSeriesName();
        String imagePath = dicomData.getPath();
        if (seriesExists(seriesName)) {
            this.series.get(seriesName).addImage(imagePath);
        } else {
            Series s = new Series(seriesName);
            s.addImage(imagePath);
            this.series.put(seriesName, s);
        }
    }

    private boolean seriesExists(String seriesName) {
        return this.series.containsKey(seriesName);
    }

    public String getName() {
        return name;
    }

    public Map<String, Series> getAllSeries() {
        return this.series;
    }

    public Series getSeries(String seriesName) {
        return this.series.get(seriesName);
    }
}
