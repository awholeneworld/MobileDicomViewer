package info.hannes.dicom.app;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {
    ImageButton Diagnose;
    ImageButton Logout;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_main);

        Diagnose = (ImageButton)findViewById(R.id.diagnose);
        Logout = (ImageButton)findViewById(R.id.logout);


        Logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishAffinity();
                Toast.makeText(HomeActivity.this,"Thank you for using Dicom Viewer Mobile.",Toast.LENGTH_LONG).show();
            }
        });
    }
}
