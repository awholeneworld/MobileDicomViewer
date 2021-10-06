package info.hannes.dicom.app.trash;

import android.app.Activity;

public class Sample {
    private CharSequence title;

    private Class<? extends Activity> activityClass;

    public Sample(String string, Class<? extends Activity> activityClass) {
        this.activityClass = activityClass;
        this.title = string;
    }

    @Override
    public String toString()
    {
        return title.toString();
    }

    public String getTitle() {
        return this.title.toString();
    }

    public Class<? extends Activity> getActivityClass() {
        return activityClass;
    }
}
