package com.android.simple.mrz;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.googlecode.tesseract.android.TessBaseAPI;
import com.innovatrics.mrz.MrzParser;
import com.innovatrics.mrz.MrzRecord;
import com.theartofdev.edmodo.cropper.CropImageView;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity implements TextureView.SurfaceTextureListener {

    private Camera mCamera;
    private TextureView mTextureView;
    private int orgPreviewWidth = 80;
    private int orgPreviewHeight = 20;
    private CropImageView cropImageView;
    private ImageView imagePreview;
    private Button retake;
    private TextView output;
    public static final String DATA_PATH = Environment
            .getExternalStorageDirectory().toString() + "/SimpleAndroidMRZ/";
    public static final String lang = "ocrb";
    private ExecutorService e = Executors.newSingleThreadExecutor();
    private boolean isProcessing;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.preview);
        mTextureView = findViewById(R.id.textureView);
        mTextureView.setSurfaceTextureListener(this);
        cropImageView = findViewById(R.id.cropImageView);
        imagePreview = findViewById(R.id.imagePreview);
        output = findViewById(R.id.output);
        retake = findViewById(R.id.retake);
        retake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startProcessing();
            }
        });
    }

    private void startProcessing() {
        e = Executors.newFixedThreadPool(10);
        mTextureView.setVisibility(View.VISIBLE);
        cropImageView.setVisibility(View.VISIBLE);
        imagePreview.setVisibility(View.VISIBLE);
        mCamera.startPreview();
        output.setText("");
        output.setVisibility(View.GONE);
        retake.setVisibility(View.GONE);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mCamera = Camera.open();

        Camera.Parameters parameters = mCamera.getParameters();

        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
        Pair<Integer, Integer> size = getMaxSize(parameters.getSupportedPreviewSizes());
        parameters.setPreviewSize(size.first, size.second);
        orgPreviewWidth = size.first;
        orgPreviewHeight = size.second;
        mCamera.setParameters(parameters);

        try {
            mCamera.setPreviewTexture(surface);
        } catch (IOException t) {
        }
        updateTextureMatrix(width, height);
        setCameraDisplayOrientation(this, 0, mCamera);
        mCamera.startPreview();

    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        // Ignored, the Camera does all the work for us
        updateTextureMatrix(width, height);
        Bitmap croppedImage = cropImageView.getCroppedImage(200, 200);
        if (croppedImage != null)
            Log.d("Ankur", croppedImage.toString());
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mCamera.stopPreview();
        mCamera.release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // Update your view here!
//        Bitmap croppedImage = cropImageView.getCroppedImage(200,200);
        final Bitmap croppedBmp = Bitmap.createBitmap(mTextureView.getBitmap(),
                (int) cropImageView.getX(), (int) cropImageView.getY(),
                cropImageView.getWidth(),
                cropImageView.getHeight());

        if (croppedBmp != null) {
//            Log.d("Ankur", croppedBmp.toString());
            imagePreview.setImageBitmap(croppedBmp);
            e.execute(new Runnable() {
                @Override
                public void run() {
                    processBitMap(croppedBmp);
                }
            });

        }
    }

    private void updateTextureMatrix(int width, int height) {
        boolean isPortrait = false;

        Display display = getWindowManager().getDefaultDisplay();
        if (display.getRotation() == Surface.ROTATION_0 || display.getRotation() == Surface.ROTATION_180)
            isPortrait = true;
        else if (display.getRotation() == Surface.ROTATION_90 || display.getRotation() == Surface.ROTATION_270)
            isPortrait = false;

        int previewWidth = orgPreviewWidth;
        int previewHeight = orgPreviewHeight;

        if (isPortrait) {
            previewWidth = orgPreviewHeight;
            previewHeight = orgPreviewWidth;
        }

        float ratioSurface = (float) width / height;
        float ratioPreview = (float) previewWidth / previewHeight;

        float scaleX;
        float scaleY;

        if (ratioSurface > ratioPreview) {
            scaleX = (float) height / previewHeight;
            scaleY = 1;
        } else {
            scaleX = 1;
            scaleY = (float) width / previewWidth;
        }

        Matrix matrix = new Matrix();

        matrix.setScale(scaleX, scaleY);
        mTextureView.setTransform(matrix);

        float scaledWidth = width * scaleX;
        float scaledHeight = height * scaleY;

        float dx = (width - scaledWidth) / 2;
        float dy = (height - scaledHeight) / 2;
        mTextureView.setTranslationX(dx);
        mTextureView.setTranslationY(dy);
    }

    private static Pair<Integer, Integer> getMaxSize(List<Camera.Size> list) {
        int width = 0;
        int height = 0;

        for (Camera.Size size : list) {
            if (size.width * size.height > width * height) {
                width = size.width;
                height = size.height;
            }
        }

        return new Pair<Integer, Integer>(width, height);
    }

    public static void setCameraDisplayOrientation(Activity activity, int cameraId, Camera camera) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);

        Camera.Parameters params = camera.getParameters();
        params.setRotation(result);
        camera.setParameters(params);
    }

    private void processBitMap(Bitmap bitmap) {
        if(!isProcessing) {
            isProcessing = true;
            TessBaseAPI baseApi = new TessBaseAPI();
            baseApi.setDebug(true);
            baseApi.init(DATA_PATH, lang);
            baseApi.setImage(bitmap);

            String recognizedText = baseApi.getUTF8Text();

            baseApi.end();

            // You now have the text in recognizedText var, you can do anything with it.
            // We will display a stripped out trimmed alpha-numeric version of it (if lang is eng)
            // so that garbage doesn't make it to the display.
            try {
                final MrzRecord record = MrzParser.parse(recognizedText);
                if (record != null) {
                    final StringBuffer sb = new StringBuffer();
                    sb.append("issuingCountry := " + record.issuingCountry + "\n");
                    sb.append("givenNames := " + record.givenNames + "\n");
                    sb.append("nationality := " + record.nationality + "\n");
                    sb.append("surname := " + record.surname + "\n");

                    sb.append("code := " + record.code + "\n");
                    sb.append("code1 := " + record.code1 + "\n");
                    sb.append("code2 := " + record.code2 + "\n");

                    sb.append("sex := " + record.sex + "\n");
                    sb.append("format := " + record.format + "\n");


                    sb.append("documentNumber := " + record.documentNumber + "\n");
                    sb.append("validDocumentNumber := " + record.validDocumentNumber + "\n");

                    sb.append("expirationDate := " + record.expirationDate + "\n");
                    sb.append("validExpirationDate := " + record.validExpirationDate + "\n");

                    sb.append("dateOfBirth := " + record.dateOfBirth + "\n");
                    sb.append("validDateOfBirth := " + record.validDateOfBirth + "\n");

                    sb.append("validComposite := " + record.validComposite + "\n");
                    output.post(new Runnable() {
                        @Override
                        public void run() {
                            output.setText(sb.toString());
                            stopProcessingImage();
                            isProcessing = false;
                        }
                    });

                }

//            mCamera.release();
            } catch (Exception e) {
                isProcessing = false;
                e.printStackTrace();
            }
        }else{
            Log.e("Ank","Rejecting bitmap");
        }
    }

    private void stopProcessingImage() {
        retake.setVisibility(View.VISIBLE);
        output.setVisibility(View.VISIBLE);
        mTextureView.setVisibility(View.GONE);
        cropImageView.setVisibility(View.GONE);
        imagePreview.setVisibility(View.GONE);
        mCamera.stopPreview();
        List<Runnable> runnables = e.shutdownNow();
        Log.d("ank",runnables.toString() + runnables.size());
    }

}