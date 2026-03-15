package com.example.fclient;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.DecoderException;

public class MainActivity extends AppCompatActivity implements TransactionEvents {

    private static final String TAG = "fclient";
    private static final String CORRECT_PIN = "1234";
    private boolean isPinVerified = false;
    private final Object lock = new Object();
    private String pin;
    private boolean waitingForPin = false;  // ✅ Флаг: ждём ли мы ввод PIN

    // ✅ ActivityResultLauncher для PinPad
    private final ActivityResultLauncher<Intent> pinLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        Log.d(TAG, "ActivityResult: resultCode=" + result.getResultCode());

                        String enteredPin = "";
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            enteredPin = result.getData().getStringExtra("PIN_CODE");
                            if (enteredPin == null) enteredPin = "";
                        }
                        Log.d(TAG, "ActivityResult: received pin='" + enteredPin + "'");

                        // ✅ Уведомляем только если действительно ждём ввод (для транзакции)
                        synchronized (lock) {
                            pin = enteredPin;
                            if (waitingForPin) {
                                waitingForPin = false;
                                lock.notifyAll();  // Разблокируем C++ поток
                                Log.d(TAG, "ActivityResult: notified C++ thread");
                            }
                        }
                        // ❗ verifyPin() НЕ вызываем здесь — проверка в C++!
                    }
            );

    static {
        System.loadLibrary("fclient");
        Log.d(TAG, "Native library loaded");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate");

        Button btnPinPad = findViewById(R.id.btnPinPad);
        if (btnPinPad != null) {
            btnPinPad.setOnClickListener(v -> openPinPad(v));
        }

        Button btnTransaction = findViewById(R.id.btnTransaction);
        if (btnTransaction != null) {
            btnTransaction.setOnClickListener(v -> onTransaction(v));
        }
    }

    // Кнопка "Ввод PIN" — независимый режим
    public void openPinPad(View v) {
        Log.d(TAG, "openPinPad: independent mode");
        Intent intent = new Intent(MainActivity.this, PinPadActivity.class);
        intent.putExtra("ptc", 3);
        intent.putExtra("amount", "");
        pinLauncher.launch(intent);  // ✅ Используем launcher, НЕ startActivityForResult
    }

    // Кнопка "Транзакция"
    public void onTransaction(View v) {
        Log.d(TAG, "onTransaction clicked, isPinVerified=" + isPinVerified);

        // Для демо: разрешаем транзакцию без предварительного ввода PIN
        // if (!isPinVerified) {
        //     Toast.makeText(this, "Сначала введите PIN!", Toast.LENGTH_SHORT).show();
        //     openPinPad(null);
        //     return;
        // }

        byte[] trd = stringToHex("9F0206000000000100");
        if (trd != null) {
            Log.d(TAG, "Starting transaction with TRD: " + hexToString(trd));
            boolean result = transaction(trd);
            Log.d(TAG, "transaction() returned: " + result);
            Toast.makeText(this, "Транзакция запущена", Toast.LENGTH_SHORT).show();
        }
    }

    // ✅ JNI методы
    public static native int initRng();
    public static native byte[] randomBytes(int count);
    public static native byte[] encrypt(byte[] key, byte[] data);
    public static native byte[] decrypt(byte[] key, byte[] data);
    public native boolean transaction(byte[] trd);

    // ✅ Вызывается из C++ для запроса PIN
    @Override
    public String enterPin(int ptc, String amount) {
        Log.d(TAG, "enterPin called from C++: ptc=" + ptc + ", amount=" + amount);

        pin = "";
        waitingForPin = true;  // ✅ Помечаем, что ждём ввод для транзакции

        Intent intent = new Intent(MainActivity.this, PinPadActivity.class);
        intent.putExtra("ptc", ptc);
        intent.putExtra("amount", amount);

        // ✅ Запускаем через launcher (в том же потоке UI)
        runOnUiThread(() -> {
            Log.d(TAG, "enterPin: launching PinPadActivity");
            pinLauncher.launch(intent);
        });

        // ✅ Ждём результат в синхронизированном блоке
        synchronized (lock) {
            while (waitingForPin) {  // ✅ while, а не if — защита от spurious wakeup
                try {
                    Log.d(TAG, "enterPin: waiting for user input...");
                    lock.wait(10000);  // ✅ Таймаут 10 сек на всякий случай
                    if (waitingForPin) {
                        Log.w(TAG, "enterPin: timeout waiting for PIN");
                        pin = "";
                        waitingForPin = false;
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "enterPin: interrupted", e);
                    Thread.currentThread().interrupt();
                    pin = "";
                    waitingForPin = false;
                }
            }
        }

        Log.d(TAG, "enterPin returning: '" + pin + "'");
        return pin;
    }

    // ✅ Вызывается из C++ с результатом транзакции
    @Override
    public void transactionResult(boolean success) {
        Log.d(TAG, "transactionResult: success=" + success);
        runOnUiThread(() -> {
            if (success) {
                Toast.makeText(this, "✓ Транзакция успешна!", Toast.LENGTH_SHORT).show();
                isPinVerified = true;
            } else {
                Toast.makeText(this, "✗ Транзакция отклонена", Toast.LENGTH_SHORT).show();
                isPinVerified = false;
            }
        });
    }

    // ✅ Вспомогательные методы
    private void verifyPin(String enteredPin) {
        // Используется ТОЛЬКО для независимого ввода через кнопку "Ввод PIN"
        if (enteredPin != null && enteredPin.equals(CORRECT_PIN)) {
            Toast.makeText(this, "✓ PIN верный!", Toast.LENGTH_SHORT).show();
            isPinVerified = true;
        } else {
            Toast.makeText(this, "✗ Неверный PIN", Toast.LENGTH_SHORT).show();
            isPinVerified = false;
        }
    }

    // ❌ УДАЛИТЕ этот метод — он больше не нужен!
    // @Override
    // protected void onActivityResult(int requestCode, int resultCode, Intent data) { ... }

    public static byte[] stringToHex(String s) {
        try {
            return Hex.decodeHex(s.toCharArray());
        } catch (DecoderException ex) {
            Log.e(TAG, "stringToHex error", ex);
            return null;
        }
    }

    public static String hexToString(byte[] data) {
        return new String(Hex.encodeHex(data)).toUpperCase();
    }
}