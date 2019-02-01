package com.lsq.app;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.RectF;
import android.hardware.Camera;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CameraActivity extends Activity implements  SurfaceHolder.Callback, Camera.PreviewCallback {

    public static final String TAG = CameraActivity.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST = 1;
    private static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    private static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final int DESIRED_PREVIEW_WIDTH = 320;
    private static final int DESIRED_PREVIEW_HEIGHT = 240;
    private static final int NN_INPUT_SIZE = 227;

    private Camera mCamera;

    // We need the phone orientation to correctly draw the overlay:
    private int mOrientation;
    private int mOrientationCompensation;
    private OrientationEventListener mOrientationEventListener;

    // Let's keep track of the display rotation and orientation also:
    private int mDisplayRotation;
    private int mDisplayOrientation;

    // The surface view for the camera data
    private SurfaceView mSurfaceView;
    private OverlayView mOverlayView;
    Camera.Size previewSize = null;

    //
    private Runnable postInferenceCallback;
    private Runnable imageConverter;
    private Handler handler;
    private HandlerThread handlerThread;

    // captured frame
    private int[] rgbBytes = null;
    private byte[] yuvBytes = null;
    private Bitmap originBitmap = null;
    private Bitmap croppedBitmap = null;

    // mobilessd
    private List<String> synset_words = new ArrayList<>();
    private MobileNetssd mobileNetssd = new MobileNetssd();

    // detected results
    private float[] results = null;
    private List<DetectObject> objects = new ArrayList<>();
    private float minimumConfidence = 0.5f;

    //
    private boolean isProcessingFrame = false;
    private boolean isComputingDetection = false;

    // to compute detect time
    private long startTime;
    private long endTime;
    private long processingTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSurfaceView = new SurfaceView(this);
        setContentView(mSurfaceView);

        requestPermission();

        mOverlayView = new OverlayView(this);
        addContentView(mOverlayView, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Create and Start the OrientationListener:
        mOrientationEventListener = new SimpleOrientationEventListener(this);
        mOrientationEventListener.enable();

        try {
            loadMobileSSD();
            loadLabel();
        } catch (IOException e) {
            Log.e("DetectActivity", "loadMobileSSD error");
        }
    }

    @Override
    public void onPreviewFrame(final byte[] bytes, final Camera camera) {

        if(isProcessingFrame){
            Log.e(TAG, "Dropping frame!");
            return;
        }

        try{
            if(rgbBytes == null){
                rgbBytes = new int[previewSize.width * previewSize.height];
                originBitmap = Bitmap.createBitmap(previewSize.width, previewSize.height, Bitmap.Config.ARGB_8888);
                croppedBitmap = Bitmap.createBitmap(NN_INPUT_SIZE, NN_INPUT_SIZE, Bitmap.Config.ARGB_8888);
            }
        } catch (final Exception e){
            Log.e(TAG, "Exception!");
            return;
        }

        isProcessingFrame = true;
        yuvBytes = bytes;

        imageConverter = new Runnable() {
                    @Override
                    public void run() {
                        Util.convertYUV420SPToARGB8888(bytes, previewSize.width, previewSize.height, rgbBytes);
                        //Util.convertYUV420SPToARGB8888(bytes, previewSize.height, previewSize.width, rgbBytes);
                        //Util.decodeYUV420SP(bytes, previewSize.width, previewSize.height, rgbBytes);
                    }};

        postInferenceCallback =
                new Runnable() {
                    @Override
                    public void run() {
                        camera.addCallbackBuffer(bytes);
                        isProcessingFrame = false;
                    }
                };

        processImage();
    }

    protected void processImage(){
        if(yuvBytes.length == 0) return;

        // No mutex needed as this method is not reentrant.
        if (isComputingDetection) {
            readyForNextImage();
            return;
        }
        isComputingDetection = true;

        originBitmap.setPixels(getRgbBytes(), 0, previewSize.width, 0, 0, previewSize.width, previewSize.height);
        croppedBitmap = Bitmap.createScaledBitmap(originBitmap, 227, 227, false);

        String path = Environment.getExternalStorageDirectory().toString();
        File file = new File(path, "origin.png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            originBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            e.printStackTrace();
        }

        file = new File(path, "cropped.png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            croppedBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (IOException e) {
            e.printStackTrace();
        }

        /*if (luminanceCopy == null) {
            luminanceCopy = new byte[originalLuminance.length];
        }
        System.arraycopy(originalLuminance, 0, luminanceCopy, 0, originalLuminance.length);*/
        readyForNextImage();

        runInBackground(
                new Runnable() {
                    @Override
                    public void run() {
                        startTime = SystemClock.uptimeMillis();
                        results = mobileNetssd.Detect(croppedBitmap);
                        endTime = SystemClock.uptimeMillis();
                        processingTime = endTime - startTime;

                        for (int i = 0; i < results.length / 6; i++) {

                            if(results[i * 6 + 1] < minimumConfidence)
                                continue;

                            for (int j = 2; j < 6; j++) {
                                if (results[i * 6 + j] < 0) results[i * 6 + j] = 0;
                                if (results[i * 6 + j] > 1) results[i * 6 + j] = 1;
                            }

                            String ID = results[i * 6] + "";
                            String title = synset_words.get((int) results[i * 6]);
                            Float confidence = results[i * 6 + 1];
                            RectF location = new RectF(results[i * 6 + 2] ,
                                    results[i * 6 + 3] ,
                                    results[i * 6 + 4],
                                    results[i * 6 + 5]);
                            objects.add(new DetectObject(ID, title, confidence, location));
                        }
                        mOverlayView.drawResults(objects, processingTime);
                        mOverlayView.postInvalidate();
                        isComputingDetection = false;
                    }
                });
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        SurfaceHolder holder = mSurfaceView.getHolder();
        holder.addCallback(this);
    }

    @Override
    protected synchronized void onPause() {
        mOrientationEventListener.disable();

        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            Log.e(TAG, "Exception!");
        }

        super.onPause();
    }

    @Override
    protected synchronized void onResume() {
        mOrientationEventListener.enable();
        super.onResume();

        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    private static Camera getCameraInstance() {
        Camera camera = null;
        try {
            camera = Camera.open();
        } catch (Exception e) {
            Log.d(TAG, "camera is not available");
        }
        return camera;
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        mCamera = getCameraInstance();
        try {
            mCamera.setPreviewDisplay(surfaceHolder);
            mCamera.startPreview();
        } catch (Exception e) {
            Log.e(TAG, "Could not preview the image.", e);
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        if (surfaceHolder.getSurface() == null) {
            return;
        }
        // Try to stop the current preview:
        try {
            mCamera.stopPreview();
            configureCamera();
            setDisplayOrientation();
            mCamera.setPreviewDisplay(surfaceHolder);
            mCamera.setPreviewCallback(this);
            int bufferBytes = Util.getYUVByteSize(height, width);
            byte[] bytes = new byte[bufferBytes];
            // why is 3110400?
            //byte[] bytes = new byte[3110400];
            mCamera.addCallbackBuffer(bytes);
            mCamera.setPreviewCallbackWithBuffer(this);
            mCamera.startPreview();
        } catch (Exception e) {
            Log.e(TAG, "Could not preview the image.", e);
        }
    }

    private void setDisplayOrientation() {
        // Now set the display orientation:
        mDisplayRotation = Util.getDisplayRotation(CameraActivity.this);
        mDisplayOrientation = Util.getDisplayOrientation(mDisplayRotation, 0);

        mCamera.setDisplayOrientation(mDisplayOrientation);

        if (mOverlayView != null) {
            //mOverlayView.setDisplayOrientation(mDisplayOrientation);
            mOverlayView.setDisplayOrientation(90);
        }
    }

    private void configureCamera() {
        Camera.Parameters parameters = mCamera.getParameters();
        // Set the PreviewSize and AutoFocus:
        setOptimalPreviewSize(parameters);
        setAutoFocus(parameters);
        parameters.setPreviewFormat(ImageFormat.NV21);
        // And set the parameters:
        mCamera.setParameters(parameters);
    }

    private void setOptimalPreviewSize(Camera.Parameters cameraParameters) {
        List<Camera.Size> previewSizes = cameraParameters.getSupportedPreviewSizes();
        previewSize = Util.chooseOptimalSize(previewSizes, DESIRED_PREVIEW_WIDTH, DESIRED_PREVIEW_HEIGHT);
        cameraParameters.setPreviewSize(previewSize.width, previewSize.height);
    }

    private void setAutoFocus(Camera.Parameters cameraParameters) {
        cameraParameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        mCamera.setPreviewCallback(null);
        mCamera.setErrorCallback(null);
        mCamera.release();
        mCamera = null;
    }

    private class SimpleOrientationEventListener extends OrientationEventListener {

        public SimpleOrientationEventListener(Context context) {
            super(context, SensorManager.SENSOR_DELAY_NORMAL);
        }

        @Override
        public void onOrientationChanged(int orientation) {
            // We keep the last known orientation. So if the user first orient
            // the camera then point the camera to floor or sky, we still have
            // the correct orientation.
            if (orientation == ORIENTATION_UNKNOWN) return;
            mOrientation = Util.roundOrientation(orientation, mOrientation);
            // When the screen is unlocked, display rotation may change. Always
            // calculate the up-to-date orientationCompensation.
            int orientationCompensation = mOrientation
                    + Util.getDisplayRotation(CameraActivity.this);
            if (mOrientationCompensation != orientationCompensation) {
                mOrientationCompensation = orientationCompensation;
                mOverlayView.setOrientation(mOrientationCompensation);
            }
        }
    }

    private void requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (shouldShowRequestPermissionRationale(PERMISSION_CAMERA) ||
                    shouldShowRequestPermissionRationale(PERMISSION_STORAGE)) {
                Toast.makeText(CameraActivity.this,
                        "Camera AND storage permission are required for this demo", Toast.LENGTH_LONG).show();
            }
            requestPermissions(new String[]{PERMISSION_CAMERA, PERMISSION_STORAGE}, PERMISSIONS_REQUEST);
        }
    }

    private void loadMobileSSD() throws IOException {
        byte[] param = null;
        byte[] bin = null;
        byte[] words = null;

        {
            InputStream assetsInputStream = getAssets().open("MobileNetSSD_deploy.param.bin");
            int available = assetsInputStream.available();
            param = new byte[available];
            int byteCode = assetsInputStream.read(param);
            assetsInputStream.close();
        }
        {
            InputStream assetsInputStream = getAssets().open("MobileNetSSD_deploy.bin");
            int available = assetsInputStream.available();
            bin = new byte[available];
            int byteCode = assetsInputStream.read(bin);
            assetsInputStream.close();
        }
        {
            InputStream assetsInputStream = getAssets().open("words.txt");
            int available = assetsInputStream.available();
            words = new byte[available];
            int byteCode = assetsInputStream.read(words);
            assetsInputStream.close();
        }
        Log.e("DetectActivity", "initMobileSSD ok");
        mobileNetssd.Init(param, bin, words);
    }

    private void loadLabel() {
        try {
            AssetManager assetManager = getApplicationContext().getAssets();
            BufferedReader reader = new BufferedReader(new InputStreamReader(assetManager.open("words.txt")));//这里是label的文件
            String readLine = null;
            while ((readLine = reader.readLine()) != null) {
                synset_words.add(readLine);
            }
            reader.close();
        } catch (Exception e) {
            Log.e("labelCache", "error " + e);
        }
    }

    protected int[] getRgbBytes() {
        imageConverter.run();
        return rgbBytes;
    }

    protected void readyForNextImage() {
        if (postInferenceCallback != null) {
            postInferenceCallback.run();
        }
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

}
