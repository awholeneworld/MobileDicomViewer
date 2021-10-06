package info.hannes.dicom.app;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

public class InitialActivity extends AppCompatActivity {
    ImageButton start;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.initial_main);

        start = findViewById(R.id.btn_get_started);

        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), HomeActivity.class);
                startActivity(intent);
                Toast.makeText(info.hannes.dicom.app.InitialActivity.this,"Welcome to Dicom Viewer Mobile.",Toast.LENGTH_LONG).show();
                finish();
            }
        });
    }
}
