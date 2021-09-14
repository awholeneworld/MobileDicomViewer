package info.hannes.dicom.app;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class Images {
    private int currentImage = 0;
    private List<String> paths = new ArrayList<>();

    public int getImagesSize() {
        return paths.size();
    }

    public String getImage(int index) {
        Log.i("Image", paths.get(index));
        return paths.get(index);
    }

    public void nextImageToDisplay() {
        if (currentImage >= paths.size() - 1) {
            currentImage = 0;
        } else {
            currentImage++;
        }
    }

    public void addImage(String path) {
        this.paths.add(path);
    }

    public String getCurrentImage() {
        return paths.get(currentImage);
    }

    public void setPathsToImages(List<String> paths) {
        this.paths = new ArrayList<>(paths);
    }

    public boolean contains(String imagePath) {
        return this.paths.contains(imagePath);
    }
}
