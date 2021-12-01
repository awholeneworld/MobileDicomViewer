package gachon.dicomviewer;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {
    ImageButton diagnose;
    ImageButton logout;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_main);

        diagnose = findViewById(R.id.diagnose);
        logout = findViewById(R.id.logout);

        diagnose.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), FileChooseActivity.class);
            startActivity(intent);
        });
        logout.setOnClickListener(v -> {
            finishAffinity();
            Toast.makeText(HomeActivity.this,"Thank you for using Dicom Viewer Mobile.",Toast.LENGTH_SHORT).show();
        });
    }
}
