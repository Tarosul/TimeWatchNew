package de.lukas.timewatch;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.api.client.extensions.android.json.AndroidJsonFactory;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.services.vision.v1.Vision;
import com.google.api.services.vision.v1.VisionRequestInitializer;
import com.google.api.services.vision.v1.model.AnnotateImageRequest;
import com.google.api.services.vision.v1.model.AnnotateImageResponse;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesRequest;
import com.google.api.services.vision.v1.model.BatchAnnotateImagesResponse;
import com.google.api.services.vision.v1.model.EntityAnnotation;
import com.google.api.services.vision.v1.model.Feature;
import com.google.api.services.vision.v1.model.Image;
import com.google.api.services.vision.v1.model.TextAnnotation;
import com.gun0912.tedpermission.PermissionListener;
import com.gun0912.tedpermission.normal.TedPermission;

import org.apache.commons.io.IOUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CameraActivity extends AppCompatActivity implements TextInterfaces {

    Button camera;
    Button gallery;
    ImageView imageView;
    private Vision vision;
    private String time;
    private static final int REQUEST_WRITE_STORAGE = 112;
    private ProgressBar dialog;

    //private ProgressDialog dialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        dialog = findViewById(R.id.progressBar);
        //dialog = new ProgressDialog(this);

        Vision.Builder visionBuilder = new Vision.Builder(
                new NetHttpTransport(),
                new AndroidJsonFactory(),
                null);
        visionBuilder.setVisionRequestInitializer(
                new VisionRequestInitializer("AIzaSyD_nXRlK-ZnOGlMuMPfPMRFLmeH3mwchww"));
        visionBuilder.setApplicationName("TimeWatch");
        vision = visionBuilder.build();

        imageView = findViewById(R.id.iv_image);
        camera = findViewById(R.id.tv_cta_camera);

        if (ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(CameraActivity.this, new String[]{
                    Manifest.permission.CAMERA
            }, 100);
        }

        camera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                startActivityForResult(intent, 100);

            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 100) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            imageView.setImageBitmap(photo);
            textDetection(photo);
        } else if (requestCode == 122) {
            if (resultCode == RESULT_OK) {
                Bitmap photo = (Bitmap) data.getExtras().get("data");
                imageView.setImageBitmap(photo);
            }
        }
    }

    private void callGallery() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(intent, 122);
    }

    private void textDetection(Bitmap bitmap) {
        dialog.setVisibility(View.VISIBLE);

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream);
                    InputStream inputStream = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
                    byte[] photoData = IOUtils.toByteArray(inputStream);

                    Image inputImage = new Image();
                    inputImage.encodeContent(photoData);

                    Feature desiredFeature = new Feature();
                    desiredFeature.setType("TEXT_DETECTION");
                    AnnotateImageRequest request = new AnnotateImageRequest();
                    request.setImage(inputImage);
                    request.setFeatures(Arrays.asList(desiredFeature));

                    BatchAnnotateImagesRequest batchRequest = new BatchAnnotateImagesRequest();
                    batchRequest.setRequests(Arrays.asList(request));

                    BatchAnnotateImagesResponse batchResponse =
                            vision.images().annotate(batchRequest).execute();

                    final TextAnnotation text = batchResponse.getResponses()
                            .get(0).getFullTextAnnotation();

                    processFinish(parseResponses(batchResponse.getResponses()));

                } catch (Exception e) {
                    ArrayList<String> error = new ArrayList<>();
                    error.add("error");
                    processFinish(error);
                    Log.d("ERROR", e.getMessage());
                }
            }

        });
    }


    /**
     * This method filters the detected text and creates a formatted time string like (00:00:00.00 | 00:00.00 | 00:00)
     *
     * @param detectedText a list of the detected text entities
     */
    private String createStopwatchTime(ArrayList<String> detectedText) {

        // filter texts by time string, number or colon
        ArrayList<String> filteredTexts = this.filterDetectedText(detectedText);

        if (filteredTexts.size() > 1) {
            // get sublist of needed elements to create time string
            ArrayList<String> timeElements = this.getTimeElements(filteredTexts);

            // join dot between seconds and milliseconds
            String seconds = timeElements.get(timeElements.size() - 1);
            if (seconds.length() == 4) {
                timeElements.set(
                        timeElements.indexOf(seconds),
                        String.join(".", seconds.substring(0, 2), seconds.substring(2))
                );
            }

            // append 0 to number < 10
            for (int i = 0; i < timeElements.size(); i++) {
                String el = timeElements.get(i);
                if (this.isNumber(el) && !el.contains(".") && (Integer.parseInt(el) < 10)) {
                    timeElements.set(i, "0" + el);
                }
            }

            this.time = String.join("", timeElements);

            return this.time;
        } else {
            this.time = filteredTexts.get(0);
            return this.time;

        }
    }

    /**
     * This method filters the text which is inside the responses
     *
     * @param responses The responses we got from our request to cloud vision api
     * @return ArrayList of the detected texts
     */
    private ArrayList<String> parseResponses(List<AnnotateImageResponse> responses) {
        ArrayList<String> detectedText = new ArrayList<>();
        // loop responses
        for (AnnotateImageResponse res : responses) {
           /* if (res.getError() != null) {
                System.out.format("Error: %s%n", res.getError().getMessage());
                return new ArrayList<>();
            }*/

            // filter text entities from response
            //System.out.println(res.getFullTextAnnotation().getText());
            for (EntityAnnotation annotation : res.getTextAnnotations()) {
                detectedText.add(annotation.getDescription());
            }
        }

        return detectedText;
    }

    /**
     * This method filters the detected text by a time string, number inside a string and a colon
     *
     * @param detectedText The detected text from given image
     * @return ArrayList with the filtered entities
     */
    private ArrayList<String> filterDetectedText(ArrayList<String> detectedText) {
        ArrayList<String> filteredTexts = new ArrayList<>();

        for (String text : detectedText) {
            // time string
            if (this.isTimeString(text)) {
                filteredTexts.add(text);
            }
            // number string
            else if (this.isNumber(text)) {
                filteredTexts.add(text);
            }
            // colon
            else if (text.equals(":")) {
                filteredTexts.add(text);
            }
        }

        return filteredTexts;
    }

    /**
     * This method extracts the needed elements to create the time string from the filtered texts
     *
     * @param filteredTexts ArrayList of filtered numbers, colons
     * @return ArrayList Returns the elements to create the final time string
     */
    private ArrayList<String> getTimeElements(ArrayList<String> filteredTexts) {
        // get indices of colons
        ArrayList<Integer> colonIdx = new ArrayList<>();
        for (int i = 0; i < filteredTexts.size(); i++) {
            if (filteredTexts.get(i).equals(":")) {
                colonIdx.add(i);
            }
        }

        // get min,max position of colons
        int minColonIndex = Collections.min(colonIdx);
        int maxColonIndex = Collections.max(colonIdx);

        // get sublist of needed elements to create time string
        return new ArrayList<>(filteredTexts.subList(minColonIndex - 1, maxColonIndex + 2));
    }

    /**
     * Validates the String whether it matches the Time RegEx or not
     *
     * @param text String Text that will be validated by regex
     * @return boolean
     */
    private boolean isTimeString(String text) {
        final Pattern pattern = Pattern.compile("^([0-9]?[0-9]|2[0-9]):[0-9][0-9]$");
        Matcher matcher = pattern.matcher(text);

        return matcher.find();
    }

    /**
     * Validates the String whether it is a number or not
     *
     * @param text String Text that will be validated by regex
     * @return boolean
     */
    private boolean isNumber(String text) {
        final Pattern pattern = Pattern.compile("-?\\d+(\\.\\d+)?");
        if (text == null) {
            return false;
        }

        return pattern.matcher(text).matches();
    }

    /**
     * This method returns the final stopwatch time string
     *
     * @return String Return the filtered and prepared time
     */
    public String getTime() {
        return this.time;
    }

    @Override
    public void processFinish(ArrayList<String> output) {
        runOnUiThread(new Runnable() {
            public void run() {
                //dialog.hide();
                dialog.setVisibility(View.INVISIBLE);
                String[] colors = {"Bestätigen"};

                AlertDialog.Builder builder = new AlertDialog.Builder(CameraActivity.this);
                System.out.println(output.get(0));
                if (output.get(0).equalsIgnoreCase("error")) {
                    builder.setTitle("Beim Scannen ist ein Fehler aufgetreten");
                } else {
                    builder.setTitle("Extrahierte Zeit: " + createStopwatchTime(output));
                }
                builder.setItems(colors, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // the user clicked on colors[which]
                        switch (which) {
                            case 0:
                                dialog.dismiss();
                                break;
                        }
                    }
                });
                builder.show();
                //Toast.makeText(getApplicationContext(), createStopwatchTime(output), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void checkCameraPermission(ImageType imageType) {
        TedPermission.create()
                .setPermissionListener(new PermissionListener() {
                    @Override
                    public void onPermissionGranted() {
                        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

                        switch (imageType) {
                            case CAMERA:
                                startActivityForResult(intent, 100);
                                break;
                            case GALLERY:
                                callGallery();
                                break;
                        }
                    }

                    @Override
                    public void onPermissionDenied(List<String> deniedPermissions) {

                    }
                }).setDeniedMessage("Please allow permissions to use this app. \uD83D\uDE2D\uD83D\uDE2D").check();
    }
}
