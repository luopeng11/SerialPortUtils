package com.luopeng.utils.serialport;

import android.serialport.SerialPort;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SerialPortManager {
    private static final String TAG = SerialPortManager.class.getSimpleName();

    /**
     * 串口，默认的8n1
     *
     * @param devicePath 串口设备文件
     * @param baudrate 波特率
     * @throws SecurityException
     * @throws IOException
     */
    public SerialPortManager(String devicePath, int baudrate) {
        this(devicePath, baudrate, 8, 0, 1, 0);
    }

    /**
     * 串口
     *
     * @param devicePath 串口设备文件
     * @param baudrate 波特率
     * @param dataBits 数据位；默认8,可选值为5~8
     * @param parity 奇偶校验；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
     * @param stopBits 停止位；默认1；1:1位停止位；2:2位停止位
     * @throws SecurityException
     * @throws IOException
     */
    public SerialPortManager(String devicePath, int baudrate, int dataBits, int parity, int stopBits) {
        this(devicePath, baudrate, dataBits, parity, stopBits, 0);
    }
    /**
     * 串口
     *
     * @param devicePath 串口设备文件
     * @param baudrate 波特率
     * @param dataBits 数据位；默认8,可选值为5~8
     * @param parity 奇偶校验；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
     * @param stopBits 停止位；默认1；1:1位停止位；2:2位停止位
     * @param flags 默认0
     * @throws SecurityException
     * @throws IOException
     */
    public SerialPortManager(String devicePath, int baudrate, int dataBits, int parity, int stopBits, int flags) {
        try {
            SerialPort serialPort = SerialPort //
                    .newBuilder(devicePath, baudrate) // 串口地址地址，波特率
                    .parity(parity) // 校验位；0:无校验位(NONE，默认)；1:奇校验位(ODD);2:偶校验位(EVEN)
                    .dataBits(dataBits) // 数据位,默认8；可选值为5~8
                    .stopBits(stopBits) // 停止位，默认1；1:1位停止位；2:2位停止位
                    .flags(flags) //标志位
                    .build();
            mSerialPort = serialPort;
            mOutputStream = mSerialPort.getOutputStream();
            mInputStream = mSerialPort.getInputStream();

            /* Create a receiving thread */
            mReadThread = new ReadThread();
            mReadThread.start();
        } catch (Exception e) {
            Log.e(TAG, "SerialPortManager初始化出错：" + e.toString());
            e.printStackTrace();
        }

    }

    private SerialPort mSerialPort = null;
    protected OutputStream mOutputStream;
    private InputStream mInputStream;
    private ReadThread mReadThread;



    /**
     * 关闭串口
     */
    public void closeSerialPort() {
        if (mReadThread != null) mReadThread.interrupt();
        if (mSerialPort != null) {
            mSerialPort.close();
            mSerialPort = null;
        }
    }

    /**
     * 发送16进制字符串 中间无空格
     *
     * @param hex
     * @return
     */
    public boolean sendHex(String hex) {
        send(DataUtil.HexToByteArr(hex));
        return false;
    }

    /**
     * 发送二进制数组
     *
     * @param data
     * @return
     */
    public boolean send(byte[] data) {
        try {
            if (mOutputStream != null) {
                mOutputStream.write(data);
                return true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }


    OnSerialPortDataListener mOnSerialPortDataListener;

    public SerialPortManager setOnSerialPortDataListener(OnSerialPortDataListener onSerialPortDataListener) {
        this.mOnSerialPortDataListener = onSerialPortDataListener;
        return this;
    }

    /**
     * 数据监听
     */
    public interface OnSerialPortDataListener {
        void onDataReceived(final byte[] buffer, final int size);
    }

    /**
     * 数据读取线程
     */
    private class ReadThread extends Thread {
        @Override
        public void run() {
            super.run();
            while (!isInterrupted()) {
                int size;
                try {
                    byte[] buffer = new byte[1024 * 2];
                    if (mInputStream == null) return;
                    size = mInputStream.read(buffer);
                    if (size > 0 && mOnSerialPortDataListener != null) {
                        mOnSerialPortDataListener.onDataReceived(buffer, size);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                    return;
                }
            }
        }
    }
}
