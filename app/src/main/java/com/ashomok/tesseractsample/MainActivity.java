package com.ashomok.tesseractsample;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Bundle;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.ashomok.tesseractsample.tools.RequestPermissionsTool;
import com.ashomok.tesseractsample.tools.RequestPermissionsToolImpl;
import com.googlecode.leptonica.android.WriteFile;
import com.googlecode.tesseract.android.TessBaseAPI;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

public class MainActivity extends Activity implements ActivityCompat.OnRequestPermissionsResultCallback {

    int numero1=99999;
    int numero2=9999;

    private static final String TAG = MainActivity.class.getSimpleName();
    static final int PHOTO_REQUEST_CODE = 1;
    private TessBaseAPI tessBaseApi;
    TextView textView;
    Uri outputFileUri;
    private static final String lang = "spa";
    String result = "empty";

    private RequestPermissionsTool requestTool; //for API >=23 only

    private static final String DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/TesseractSample/";
    private static final String TESSDATA = "tessdata";

    private ImageView imagen_original;
    private ImageView imagen_tess;
    private ImageView imagen_volteada;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button captureImg = (Button) findViewById(R.id.action_btn);
        if (captureImg != null) {
            captureImg.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    startCameraActivity();
                }
            });
        }
        imagen_original = findViewById(R.id.imagen_original);
        imagen_tess = findViewById(R.id.imagen_tess);
        imagen_volteada = findViewById(R.id.imagen_volteada);
        textView = (TextView) findViewById(R.id.textResult);

        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions();
        }
    }


    /**
     * to get high resolution image from camera
     */
    private void startCameraActivity() {
        try {

            StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
            StrictMode.setVmPolicy(builder.build());

            String IMGS_PATH = Environment.getExternalStorageDirectory().toString() + "/TesseractSample/imgs";
            prepareDirectory(IMGS_PATH);

            String img_path = IMGS_PATH + "/ocr.jpg";

            outputFileUri = Uri.fromFile(new File(img_path));
            //outputFileUri = FileProvider.getUriForFile(this, "com.ashomok.tesseractsample.provider", new File(img_path));

            final Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);

            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                startActivityForResult(takePictureIntent, PHOTO_REQUEST_CODE);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        //making photo
        if (requestCode == PHOTO_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            doOCR();
        } else {
            Toast.makeText(this, "ERROR: Image was not obtained.", Toast.LENGTH_SHORT).show();
        }
    }

    private void doOCR() {
        prepareTesseract();
        startOCR(outputFileUri);
    }

    /**
     * Prepare directory on external storage
     *
     * @param path
     * @throws Exception
     */
    private void prepareDirectory(String path) {

        File dir = new File(path);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                Log.e(TAG, "ERROR: Creation of directory " + path + " failed, check does Android Manifest have permission to write to external storage.");
            }
        } else {
            Log.i(TAG, "Created directory " + path);
        }
    }


    private void prepareTesseract() {
        try {
            prepareDirectory(DATA_PATH + TESSDATA);
        } catch (Exception e) {
            e.printStackTrace();
        }

        copyTessDataFiles(TESSDATA);
    }

    /**
     * Copy tessdata files (located on assets/tessdata) to destination directory
     *
     * @param path - name of directory with .traineddata files
     */
    private void copyTessDataFiles(String path) {

        System.out.println("------------->111111:"+path);

        try {
            String fileList[] = getAssets().list(path);

            for (String fileName : fileList) {

                // open file within the assets folder
                // if it is not already there copy it to the sdcard
                String pathToDataFile = DATA_PATH + path + "/" + fileName;
                if (!(new File(pathToDataFile)).exists()) {

                    InputStream in = getAssets().open(path + "/" + fileName);

                    OutputStream out = new FileOutputStream(pathToDataFile);

                    // Transfer bytes from in to out
                    byte[] buf = new byte[1024];
                    int len;

                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    in.close();
                    out.close();

                    Log.d(TAG, "Copied " + fileName + "to tessdata");
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Unable to copy files to tessdata " + e.toString());
        }
    }


    /**
     * don't run this code in main thread - it stops UI thread. Create AsyncTask instead.
     * http://developer.android.com/intl/ru/reference/android/os/AsyncTask.html
     *
     * @param imgUri
     */
    private void startOCR(Uri imgUri) {
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 5; // 1 - means max size. 4 - means maxsize/4 size. Don't use value <4, because you need more memory in the heap to store your data.
            Bitmap bitmap = BitmapFactory.decodeFile(imgUri.getPath(), options);

            imagen_original.setImageBitmap(bitmap);

            result = extractText(rotarImagen(bitmap, imgUri));

            textView.setText(result + "\n" + outputFileUri + "\n" + DATA_PATH + "\n" + TESSDATA);

        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
    }

    private Bitmap rotarImagen(Bitmap bitmap, Uri imagen_path) {
        try {
            ExifInterface exif = new ExifInterface(imagen_path.getPath().toString());
            int exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            int rotate = 0;
            switch (exifOrientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    rotate = 90;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_180:
                    rotate = 180;
                    break;
                case ExifInterface.ORIENTATION_ROTATE_270:
                    rotate = 270;
                    break;
            }
            Log.v(TAG, "Rotation: " + rotate);
            if (rotate != 0) {
                // Getting width & height of the given image.
                int w = bitmap.getWidth();
                int h = bitmap.getHeight();
                // Setting pre rotate
                Matrix mtx = new Matrix();
                mtx.postRotate(rotate);
                // Rotating Bitmap
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, true);

            }
        } catch (Exception e) {
            System.out.println("------------------------>Error en rotarImagen:" + e.getMessage());
        }
        return bitmap;
    }


    private String extractText(Bitmap bitmap) {
        try {
            tessBaseApi = new TessBaseAPI();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
            if (tessBaseApi == null) {
                Log.e(TAG, "TessBaseAPI is null. TessFactory not returning tess object.");
            }
        }

        tessBaseApi.init(DATA_PATH, lang);

//       //EXTRA SETTINGS
//        //For example if we only want to detect numbers
//        tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "1234567890");
//
//        //blackList Example
        //tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_BLACKLIST, "!@#$%^&*()_+=-qwertyuiop[]}{POIU" + "YTRWQasdASDfghFGHjklJKLl;L:'\"\\|~`xcvXCVbnmBNM,./<>?");
        tessBaseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "aAbBcCdDeEfFgGhHiIjJkKlLmMnNoOpPqQrRsStTuUvVwWxXyYzZ1234567890',.?;/ ");

        Log.d(TAG, "Training file loaded");
        tessBaseApi.setImage(bitmap);

        String extractedText = "empty result";
        try {
            extractedText = tessBaseApi.getUTF8Text();
            Bitmap bitmap1 = WriteFile.writeBitmap(tessBaseApi.getThresholdedImage());
            imagen_tess.setImageBitmap(bitmap1);

        } catch (Exception e) {
            Log.e(TAG, "Error in recognizing text.");
        }
        tessBaseApi.end();
        return extractedText;
    }


    private void requestPermissions() {
        String[] permissions = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA};
        requestTool = new RequestPermissionsToolImpl();
        requestTool.requestPermissions(this, permissions);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        boolean grantedAllPermissions = true;
        for (int grantResult : grantResults) {
            if (grantResult != PackageManager.PERMISSION_GRANTED) {
                grantedAllPermissions = false;
            }
        }

        if (grantResults.length != permissions.length || (!grantedAllPermissions)) {
            Toast.makeText(this, "Permisos no dados", Toast.LENGTH_SHORT).show();
            requestTool.onPermissionDenied();
        } else {
            //System.out.println("---------PERMISOS DADOS:" + checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE));
            //System.out.println("---------PERMISOS DADOS222:" + checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE));
            //System.out.println("---------PERMISOS DADOScamera:" + checkSelfPermission(Manifest.permission.CAMERA));
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }

    }
}




