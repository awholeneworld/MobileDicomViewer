package gachon.dicomviewer;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class HomeActivity extends AppCompatActivity {
    ImageButton diagnose;
    ImageButton search;
    ImageButton chatbot;
    ImageButton finish;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_main);

        diagnose = findViewById(R.id.diagnose);
        search = findViewById(R.id.search);
        chatbot = findViewById(R.id.chatbot);
        finish = findViewById(R.id.exit);

        diagnose.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), FileChooseActivity.class);
            intent.putExtra("isSearch", false);
            startActivity(intent);
        });

        search.setOnClickListener(v -> {
            Intent intent = new Intent(getApplicationContext(), FileChooseActivity.class);
            intent.putExtra("isSearch", true);
            startActivity(intent);
        });

        finish.setOnClickListener(v -> {
            finishAndRemoveTask();
            Toast.makeText(getApplicationContext(), "Thank you for using Dicom Viewer!", Toast.LENGTH_LONG).show();

        });
    }
}

