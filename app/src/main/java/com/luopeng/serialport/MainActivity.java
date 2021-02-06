package com.luopeng.serialport;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Bitmap;
import android.os.Bundle;

public class MainActivity extends AppCompatActivity implements SerialUtils.MessageListener {

    private SerialUtils mSerialUtils;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSerialUtils = new SerialUtils();
        mSerialUtils.setMessageListener(this);
        mSerialUtils.init(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mSerialUtils.relese();
    }

    @Override
    public void onTemperature(float temperature) {

    }

    @Override
    public void onMessage(byte[] frameData) {

    }

    @Override
    public void onTemperatureBimap(Bitmap bitmap) {

    }
}