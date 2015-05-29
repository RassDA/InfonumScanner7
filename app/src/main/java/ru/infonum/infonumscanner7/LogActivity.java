package ru.infonum.infonumscanner7;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;

/**
 * Created by d1i on 29.05.15.
 */
public class LogActivity extends Activity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.log_activity);
        EditText editText = (EditText) findViewById(R.id.textViewLog);
        //tw.setGravity(Gravity.CENTER);
        //tw.setTextSize(20);

        Intent intent = getIntent();

        String logStr = intent.getStringExtra(ResultActivity.LOG); // Результат распознавания

        editText.setText(logStr);

    }
}
