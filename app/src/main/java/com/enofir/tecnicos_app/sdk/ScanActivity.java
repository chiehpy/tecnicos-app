package com.enofir.tecnicos_app.sdk;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;
import com.journeyapps.barcodescanner.DefaultDecoderFactory;

import com.enofir.tecnicos_app.core.SessionManager;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScanActivity extends AppCompatActivity {

    public static final String EXTRA_RESULT = "scan_result";

    private DecoratedBarcodeView barcodeView;
    private final AtomicBoolean finished = new AtomicBoolean(false);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Configurar orientación según preferencia del usuario
        SessionManager session = new SessionManager(this);
        String orientation = session.getScannerOrientation();
        int requestedOrientation = orientation.equals(SessionManager.SCANNER_LANDSCAPE)
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        setRequestedOrientation(requestedOrientation);

        super.onCreate(savedInstanceState);
        setContentView(com.enofir.tecnicos_app.R.layout.activity_scan);

        barcodeView = findViewById(com.enofir.tecnicos_app.R.id.barcode_scanner);

        // SOLO CODE_128
        barcodeView.getBarcodeView().setDecoderFactory(
                new DefaultDecoderFactory(Collections.singletonList(BarcodeFormat.CODE_128))
        );

        // Ocultar texto de estado
        barcodeView.setStatusText("");

        // Decode continuo: apenas detecta, devuelve y cierra
        barcodeView.decodeContinuous(new BarcodeCallback() {
            @Override
            public void barcodeResult(BarcodeResult result) {
                if (result == null) return;

                String text = result.getText();
                if (text == null) return;

                text = text.trim();
                if (text.isEmpty()) return;

                if (!finished.compareAndSet(false, true)) return;

                Intent out = new Intent();
                out.putExtra(EXTRA_RESULT, text);
                setResult(Activity.RESULT_OK, out);

                barcodeView.pause();
                finish();
            }

            @Override
            public void possibleResultPoints(List<ResultPoint> resultPoints) {
                // No usamos puntos intermedios; se deja vacío
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!finished.get()) {
            barcodeView.resume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (barcodeView != null) {
            barcodeView.pause();
        }
    }
}
