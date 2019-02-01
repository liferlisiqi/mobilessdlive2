package com.lsq.app;

import android.graphics.Bitmap;

public class MobileNetssd {
    public native boolean Init(byte[] param, byte[] bin, byte[] words); // 初始化函数
    public native float[] Detect(Bitmap bitmap); // 检测函数
    // Used to load the 'native-lib' library on application startup.
    static {
        System.loadLibrary("mobilessd1");
    }
}
