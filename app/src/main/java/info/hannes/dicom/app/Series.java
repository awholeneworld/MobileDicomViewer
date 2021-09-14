package info.hannes.dicom.app;

public class Series {
    private String name;
    private Images images = new Images();

    public Series(String testName) {
        this.name = testName;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Images getImages() {
        return images;
    }

    public void addImage(String imagePath) {
        if (!images.contains(imagePath)) {
            this.images.addImage(imagePath);
        }
    }
}
