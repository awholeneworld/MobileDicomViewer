package gachon.dicomviewer;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {
    ImageButton diagnose;
    ImageButton search;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_main);

        diagnose = findViewById(R.id.diagnose);
        search = findViewById(R.id.search);

        diagnose.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), FileChooseActivity.class);
            startActivity(intent);
        });

        search.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), FileChooseActivity.class);
            startActivity(intent);
        });
    }
}

