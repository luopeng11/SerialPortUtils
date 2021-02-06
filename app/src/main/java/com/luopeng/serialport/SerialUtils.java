package com.luopeng.serialport;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.util.Log;
import com.luopeng.utils.serialport.DataUtil;
import com.luopeng.utils.serialport.SerialPortManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static android.content.Context.MODE_PRIVATE;

public class SerialUtils implements SerialPortManager.OnSerialPortDataListener {


    public interface MessageListener {
        void onTemperature(float temperature);

        void onMessage(byte[] frameData);

        void onTemperatureBimap(Bitmap bitmap);
    }

    private static final String TAG = SerialUtils.class.getSimpleName();

    private float mTemperature = 0;//温度补偿
    private SerialPortManager serialPortManager;
    private MessageListener mMessageListener;
    private ProgressDataThread mProgressDataThread;

    public void setMessageListener(MessageListener messageListener) {
        mMessageListener = messageListener;
    }


    public void init(Context context) {
        serialPortManager = new SerialPortManager("dev/ttyUSB0",115200).setOnSerialPortDataListener(this);
        mProgressDataThread = new ProgressDataThread();
        mProgressDataThread.start();
        SharedPreferences sp = context.getSharedPreferences(context.getPackageName() + "_preferences", MODE_PRIVATE);
        mTemperature = Float.parseFloat(sp.getString("TEMPERATURE", "0"));
    }

    public void relese() {
        // if (mQueryTemperatureThread != null) mQueryTemperatureThread.interrupt();
        if (serialPortManager != null) serialPortManager.closeSerialPort();
        if (mProgressDataThread != null) mProgressDataThread.interrupt();
    }


    //温度查询启动标志
    private boolean startDetection = false;

    public void switchDetection(boolean detecting) {
        this.startDetection = detecting;
        if (startDetection && !isQueryTemperature) queryTemperature();
    }

    //当前是否正在查询温度 记录状态 防止同时发送俩次查询命令导致数据叠加重写
    private boolean isQueryTemperature = false;

    private void queryTemperature() {
        Log.e(TAG, "发送获取温度命令");
        isQueryTemperature = true;
        sendHex("EEE10155FFFCFDFF");
    }


    /**
     * 发送十六进制字符串数据 无空格  如：AA5501B3FF
     */
    public boolean sendHex(String hex) {
        if (serialPortManager != null) return serialPortManager.sendHex(hex);
        return false;
    }

    public BlockingQueue<byte[]> dataQueue = new LinkedBlockingQueue<>();


    @Override
    public void onDataReceived(byte[] buffer, int size) {
        byte[] bytes = new byte[size];
        System.arraycopy(buffer, 0, bytes, 0, size);
        dataQueue.add(bytes);
    }


    private class ProgressDataThread extends Thread {
        public void run() {
            while (!isInterrupted()) {
                try {
                    byte[] take = dataQueue.take();
                    progressData(take);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private ByteArrayOutputStream baos = new ByteArrayOutputStream();

    private void progressData(byte[] buffer) throws IOException {
        baos.write(buffer);
        //其他数据帧 帧尾0xFF 0xFC 0xFD 0xFF
        boolean isFrameEnd = buffer.length > 4 && buffer[buffer.length - 1] == (byte) 0xFF && buffer[buffer.length - 2] == (byte) 0xFD && buffer[buffer.length - 3] == (byte) 0xFC && buffer[buffer.length - 4] == (byte) 0xFF;

        //温度帧 帧尾 01 00 C6 82(探测器编号)
        boolean isTemperatureFrameEnd = buffer.length > 4 && buffer[buffer.length - 1] == (byte) 0x82 && buffer[buffer.length - 2] == (byte) 0xC6 && buffer[buffer.length - 3] == (byte) 0x00 && buffer[buffer.length - 4] == (byte) 0x01;

        if (isTemperatureFrameEnd) {
            byte[] data = baos.toByteArray();
            Log.d(TAG, "=====获取温度帧：" + data.length);
            if (9927 == data.length) {//不丢失数据才解析
                float temperature = progressTemperature(data);
                if (mMessageListener != null )
                    mMessageListener.onTemperatureBimap(createThermodynamicBitmap(data, Math.max(temperature, 37.3f)));
                if (mMessageListener != null)
                    mMessageListener.onTemperature(temperature + mTemperature);
            } else {
                Log.e(TAG, data.length + "=====异常温度帧： " + DataUtil.ByteArrToHex(buffer));
            }
            baos.reset();

            //是否继续查询温度
            if (startDetection) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                queryTemperature();
            } else {
                isQueryTemperature = false;
            }
        } else if (isFrameEnd) {
            byte[] data = baos.toByteArray();
            if (mMessageListener != null) mMessageListener.onMessage(data);
            baos.reset();
        }
    }

    private Bitmap createThermodynamicBitmap(byte[] data, float max) {

        int width = 80;
        int height = 62;
        int[] mArrayColor = new int[width * height];
        for (int i = 0; i < mArrayColor.length; i++) {
            int dataI = i * 2 + 1;
            float temp = (bytesToInt(data[dataI], data[dataI + 1]) * 1.0f - 2731) / 10;
            float value = temp < 25 ? 0 : (temp / max);
            mArrayColor[i] = getColor(value);
        }
        double scaleRate = 1;
        Bitmap img = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        img.setPixels(mArrayColor, 0, (int) (width), 0, 0, (int) (width * scaleRate), (int) (height * scaleRate));
        return img;
    }

    /**
     * 能量色条值获取
     *
     * @param value 　实际数据值
     * @return　对应能量色条颜色数组[r, g, b]
     */
    public static int getColor(float value) {
        int sr1 = 0x00, sg1 = 0x00, sb1 = 0x00;
        int sr2 = 0x46, sg2 = 0xb7, sb2 = 0xec;
        int sr3 = 0x22, sg3 = 0xfe, sb3 = 0x2c;
        int sr4 = 0xff, sg4 = 0xea, sb4 = 0x00;
        int sr5 = 0xff, sg5 = 0x4e, sb5 = 0x00;
        int r = 0, g = 0, b = 0;

        if (value < 0.15) {
            r = (int) (sr1 + (sr2 - sr1) * (value / 0.15));
            g = (int) (sg1 + (sg2 - sg1) * (value / 0.15));
            b = (int) (sb1 + (sb2 - sb1) * (value / 0.15));
        } else if (value < 0.5) {
            r = (int) (sr2 + (sr3 - sr2) * ((value - 0.15) / 0.35));
            g = (int) (sg2 + (sg3 - sg2) * ((value - 0.15) / 0.35));
            b = (int) (sb2 + (sb3 - sb2) * ((value - 0.15) / 0.35));
        } else if (value < 0.75) {
            r = (int) (sr3 + (sr4 - sr3) * ((value - 0.5) / 0.25));
            g = (int) (sg3 + (sg4 - sg3) * ((value - 0.5) / 0.25));
            b = (int) (sb3 + (sb4 - sb3) * ((value - 0.5) / 0.25));
        } else {
            r = (int) (sr4 + (sr5 - sr4) * ((value - 0.75) / 0.25));
            g = (int) (sg4 + (sg5 - sg4) * ((value - 0.75) / 0.25));
            b = (int) (sb4 + (sb5 - sb4) * ((value - 0.75) / 0.25));
        }
        return 128 << 24 | r << 16 | g << 8 | b;
    }


    private float progressTemperature(byte[] data) {
        float maxTemp = 0;
        for (int i = 0; i < 62 * 80; i++) {
            int dataI = i * 2 + 1;
            float temp = (bytesToInt(data[dataI], data[dataI + 1]) * 1.0f - 2731) / 10;
            if (temp > maxTemp) {
                maxTemp = temp;
            }
        }
        return maxTemp;
    }

    /**
     * 2byte转int
     */
    private int bytesToInt(byte b, byte b2) {
        int ch1 = b & 0xff;
        int ch2 = b2 & 0xff;
        return (ch1 << 8) | ch2;
    }

}
