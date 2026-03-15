package com.example.fclient;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PinPadActivity extends AppCompatActivity {

    private TextView tvPinDisplay;
    private TextView tvAmount;
    private TextView tvAttempts;
    private StringBuilder pinInput;
    private static final int MAX_PIN_LENGTH = 4;
    private int attemptsLeft = 3;
    private String amount = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pin_pad);

        tvPinDisplay = findViewById(R.id.tvPinDisplay);
        tvAmount = findViewById(R.id.tvAmount);
        tvAttempts = findViewById(R.id.tvAttempts);
        pinInput = new StringBuilder();

        attemptsLeft = getIntent().getIntExtra("ptc", 3);
        amount = getIntent().getStringExtra("amount");

        updateAttemptsDisplay();
        setupButtons();
        shuffleKeys();
    }


    private void updateAttemptsDisplay() {
        if (tvAttempts != null) {
            String attemptsText = "Осталось попыток: " + attemptsLeft;
            if (attemptsLeft <= 1) {
                attemptsText += " ⚠️";  // Визуальное предупреждение
                tvAttempts.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }
            tvAttempts.setText(attemptsText);
        }

    }

    private String formatAmount(String amountBcd) {
        if (amountBcd == null || amountBcd.length() != 12) {
            return "0.00";
        }
        try {
            long kopecks = Long.parseLong(amountBcd);
            long rubles = kopecks / 100;
            long kopecksPart = kopecks % 100;
            return String.format("%d.%02d руб.", rubles, kopecksPart);
        } catch (NumberFormatException e) {
            return "0.00";
        }
    }

    private void setupButtons() {
        int[] buttonIds = {
                R.id.btn1, R.id.btn2, R.id.btn3,
                R.id.btn4, R.id.btn5, R.id.btn6,
                R.id.btn7, R.id.btn8, R.id.btn9,
                R.id.btn0
        };

        for (int id : buttonIds) {
            Button btn = findViewById(id);
            if (btn != null) {
                btn.setOnClickListener(this::onNumberClick);
            }
        }

        Button btnClear = findViewById(R.id.btnClear);
        if (btnClear != null) {
            btnClear.setOnClickListener(v -> clearPin());
        }

        Button btnDelete = findViewById(R.id.btnDelete);
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> deleteLastDigit());
        }

        Button btnConfirm = findViewById(R.id.btnConfirm);
        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> confirmPin());
        }
    }

    private void onNumberClick(View v) {
        Button btn = (Button) v;
        String digit = btn.getText().toString();

        if (pinInput.length() < MAX_PIN_LENGTH) {
            pinInput.append(digit);
            updatePinDisplay();

            if (pinInput.length() == MAX_PIN_LENGTH) {
                v.postDelayed(this::confirmPin, 200);
            }
        }
    }

    private void updatePinDisplay() {
        if (tvPinDisplay == null) return;
        StringBuilder masked = new StringBuilder();
        for (int i = 0; i < pinInput.length(); i++) {
            masked.append("●");
        }
        tvPinDisplay.setText(masked.toString());
    }

    private void clearPin() {
        pinInput.setLength(0);
        updatePinDisplay();
    }

    private void deleteLastDigit() {
        if (pinInput.length() > 0) {
            pinInput.deleteCharAt(pinInput.length() - 1);
            updatePinDisplay();
        }
    }

    private void confirmPin() {
        Log.d("PinPad", "confirmPin: length=" + pinInput.length() + ", attempts=" + attemptsLeft);

        if (pinInput.length() == MAX_PIN_LENGTH) {
            String enteredPin = pinInput.toString();

            if (attemptsLeft <= 0) {
                Toast.makeText(this, "Попытки исчерпаны!", Toast.LENGTH_SHORT).show();
                Intent result = new Intent();
                result.putExtra("PIN_CODE", "");
                setResult(RESULT_OK, result);
                Log.d("PinPad", "confirmPin: attempts exhausted, returning empty PIN");
            } else {
                Intent result = new Intent();
                result.putExtra("PIN_CODE", enteredPin);
                setResult(RESULT_OK, result);
                Log.d("PinPad", "confirmPin: returning PIN='" + enteredPin + "'");
            }
            finish();  // ✅ Обязательно закрываем активность
            Log.d("PinPad", "confirmPin: finished");
        } else {
            Toast.makeText(this, "Введите 4 цифры", Toast.LENGTH_SHORT).show();
            Log.d("PinPad", "confirmPin: PIN too short");
        }
    }

    private void shuffleKeys() {
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i <= 9; i++) {
            numbers.add(i);
        }
        Collections.shuffle(numbers);

        int[] buttonIds = {
                R.id.btn1, R.id.btn2, R.id.btn3,
                R.id.btn4, R.id.btn5, R.id.btn6,
                R.id.btn7, R.id.btn8, R.id.btn9,
                R.id.btn0
        };

        for (int i = 0; i < buttonIds.length; i++) {
            Button btn = findViewById(buttonIds[i]);
            if (btn != null) {
                btn.setText(String.valueOf(numbers.get(i)));
            }
        }
    }

    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        finish();
    }
}