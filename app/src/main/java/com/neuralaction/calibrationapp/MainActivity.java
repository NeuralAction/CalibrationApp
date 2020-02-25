package com.neuralaction.calibrationapp;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.graphics.Point;
import android.media.CamcorderProfile;
import android.media.MediaMetadataRetriever;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.SystemClock;

import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;

import android.view.TextureView;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.error.VolleyError;
import com.android.volley.request.SimpleMultiPartRequest;
import com.android.volley.request.StringRequest;
import com.android.volley.toolbox.Volley;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private Camera mCamera;
    private TextureView mPreview;
    private MediaRecorder mMediaRecorder;
    private File mOutputFile;

    private boolean isRecording = false;
    private static final String TAG = "Recorder";
    private Button captureButton;

    LinearLayout inst;
    LinearLayout calibrationarea;
    SeekBar camera_x;

    int PERMISSION_ALL = 1;
    String[] PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.INTERNET
    };


    JSONObject result;
    JSONArray CalibPoints;

    Date startRecord;

    private long startHTime = 0L;

    SimpleDateFormat df;

    Point size;

    EditText username;
    EditText serverinfo;

    String videofile;

    String serverurl;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Display display = getWindowManager().getDefaultDisplay();
        size = new Point();
        display.getSize(size);
        inst = findViewById(R.id.inst);
        calibrationarea = findViewById(R.id.calibraionarea);
        camera_x = findViewById(R.id.seekBar);
        mPreview = findViewById(R.id.CameraView);
        captureButton = findViewById(R.id.btn_record);
        CalibPoints = new JSONArray();
        username = findViewById(R.id.username);
        serverinfo = findViewById(R.id.serverinfo);
        df = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        camera_x.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onStopTrackingTouch(SeekBar seekBar) {
                captureButton.setEnabled(true);
            }

            public void onStartTrackingTouch(SeekBar seekBar) {
                captureButton.setEnabled(true);
            }

            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                captureButton.setEnabled(true);
            }
        });

        if (!hasPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_ALL);
        } else {

            try {
                result = getInfoDevice();
            } catch (JSONException e) {
                e.printStackTrace();
            }

            getWindow().setFlags(LayoutParams.FLAG_FULLSCREEN,
                    LayoutParams.FLAG_FULLSCREEN);
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        }

    }


    public static boolean hasPermissions(Context context, String... permissions) {
        if (context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }


    public JSONObject getInfoDevice() throws JSONException {

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);

        double mWidthPixels = dm.widthPixels;
        double mHeightPixels = dm.heightPixels;

        // includes window decorations (statusbar bar/menu bar)
        if (Build.VERSION.SDK_INT >= 14 && Build.VERSION.SDK_INT < 17) {
            try {
                mWidthPixels = (Integer) Display.class.getMethod("getRawWidth").invoke(display);
                mHeightPixels = (Integer) Display.class.getMethod("getRawHeight").invoke(display);
            } catch (Exception ignored) {
            }
        }

        // includes window decorations (statusbar bar/menu bar)
        if (Build.VERSION.SDK_INT >= 17) {
            try {
                Point realSize = new Point();
                Display.class.getMethod("getRealSize", Point.class).invoke(display, realSize);
                mWidthPixels = realSize.x;
                mHeightPixels = realSize.y;
            } catch (Exception ignored) {
            }
        }

        double x = Math.pow(mWidthPixels / dm.xdpi, 2);
        double y = Math.pow(mHeightPixels / dm.ydpi, 2);

        JSONObject deviceInfo = new JSONObject();

        deviceInfo.accumulate("ScrMilliWidth", x*10);
        deviceInfo.accumulate("ScrMilliHeight", (size.y / (size.x / x))*10);
        deviceInfo.accumulate("ScrPixelWidth", size.x);
        deviceInfo.accumulate("ScrPixelHeight", size.y);
        deviceInfo.accumulate("ScrOriginX", 1);
        deviceInfo.accumulate("ScrOriginY", 0);
        deviceInfo.accumulate("ScrOriginZ", 0);

        return deviceInfo;
    }

    public void onServerTest(View view) {

        serverurl = ((EditText) findViewById(R.id.serverinfo)).getText().toString();

        RequestQueue mRequestQueue = Volley.newRequestQueue(getApplicationContext());

        StringRequest stringRequest = new StringRequest(Request.Method.POST, serverurl+"/test", new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle("ServerTest");
                try {
                    alertDialog.setMessage(new JSONObject(response).getString("message"));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Complete",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });
                alertDialog.show();
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                alertDialog.setTitle("ServerTest");
                alertDialog.setMessage("SERVER CONNECTION ERROR\n"+ error.toString());
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Complete",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        });
                alertDialog.show();
            }
        });

        mRequestQueue.add(stringRequest);
    }

    /**
     * The capture button controls all user interaction. When recording, the button click
     * stops recording, releases {@link android.media.MediaRecorder} and
     * {@link android.hardware.Camera}. When not recording, it prepares the
     * {@link android.media.MediaRecorder} and starts recording.
     *
     * @param view the view generating the event.
     */
    public void onCaptureClick(View view) {
        if (isRecording) {
            // BEGIN_INCLUDE(stop_release_media_recorder)

            // stop recording and release camera
            try {
                mMediaRecorder.stop();  // stop the recording
            } catch (RuntimeException e) {
                // RuntimeException is thrown when stop() is called immediately after start().
                // In this case the output file is not properly constructed ans should be deleted.
                Log.d(TAG, "RuntimeException: stop() is called immediately after start()");
                //noinspection ResultOfMethodCallIgnored
                mOutputFile.delete();
            }
            releaseMediaRecorder(); // release the MediaRecorder object
            mCamera.lock();         // take camera access back from MediaRecorder

            // inform the user that recording has stopped
            setCaptureButtonText("Capture");
            isRecording = false;
            releaseCamera();
            // END_INCLUDE(stop_release_media_recorder)

        } else {

            // BEGIN_INCLUDE(prepare_start_media_recorder)
            inst.setVisibility(View.INVISIBLE);
            captureButton.setVisibility(View.INVISIBLE);
            calibrationarea.setVisibility(View.VISIBLE);

            new MediaPrepareTask().execute(null, null, null);
            startRecord = new Date();
            calibraionRun();
            // END_INCLUDE(prepare_start_media_recorder)

        }
    }

    private void setCaptureButtonText(String title) {
        captureButton.setText(title);
    }

    @Override
    protected void onPause() {
        super.onPause();
        // if we are using MediaRecorder, release it first
        releaseMediaRecorder();
        // release the camera immediately on pause event
        releaseCamera();
    }

    private void releaseMediaRecorder() {
        if (mMediaRecorder != null) {
            // clear recorder configuration
            mMediaRecorder.reset();
            // release the recorder object
            mMediaRecorder.release();
            mMediaRecorder = null;
            // Lock camera for later use i.e taking it back from MediaRecorder.
            // MediaRecorder doesn't need it anymore and we will release it if the activity pauses.
            mCamera.lock();
        }
    }

    private void releaseCamera() {
        if (mCamera != null) {
            // release the camera for other applications
            mCamera.release();
            mCamera = null;
        }
    }

    private boolean prepareVideoRecorder() {


        // BEGIN_INCLUDE (configure_preview)
        mCamera = CameraHelper.getDefaultFrontFacingCameraInstance();
        mCamera.setDisplayOrientation(90);
        // We need to make sure that our preview and recording video size are supported by the
        // camera. Query camera to find all the sizes and choose the optimal size given the
        // dimensions of our preview surface.
        Camera.Parameters parameters = mCamera.getParameters();
        List<Camera.Size> mSupportedPreviewSizes = parameters.getSupportedPreviewSizes();
        List<Camera.Size> mSupportedVideoSizes = parameters.getSupportedVideoSizes();
        Camera.Size optimalSize = CameraHelper.getOptimalVideoSize(mSupportedVideoSizes,
                mSupportedPreviewSizes, mPreview.getWidth(), mPreview.getHeight());

        // Use the same size for recording profile.
        CamcorderProfile profile = CamcorderProfile.get(CamcorderProfile.QUALITY_1080P);
//        profile.videoFrameWidth = optimalSize.width;
//        profile.videoFrameHeight = optimalSize.height;

        // likewise for the camera object itself.
        parameters.setPreviewSize(profile.videoFrameWidth, profile.videoFrameHeight);
        mCamera.setParameters(parameters);
        try {
            // Requires API level 11+, For backward compatibility use {@link setPreviewDisplay}
            // with {@link SurfaceView}
            mCamera.setPreviewTexture(mPreview.getSurfaceTexture());
        } catch (IOException e) {
            Log.e(TAG, "Surface texture is unavailable or unsuitable" + e.getMessage());
            return false;
        }
        // END_INCLUDE (configure_preview)


        // BEGIN_INCLUDE (configure_media_recorder)
        mMediaRecorder = new MediaRecorder();

        // Step 1: Unlock and set camera to MediaRecorder
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);

        // Step 2: Set sources
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

        // Step 3: Set a CamcorderProfile (requires API Level 8 or higher)
        mMediaRecorder.setProfile(profile);
        mMediaRecorder.setVideoFrameRate(30);

        // Step 4: Set output file
        mOutputFile = CameraHelper.getOutputMediaFile(CameraHelper.MEDIA_TYPE_VIDEO);
        if (mOutputFile == null) {
            return false;
        }
        videofile = mOutputFile.getPath();
        mMediaRecorder.setOutputFile(videofile);

        mMediaRecorder.setOrientationHint(270);
        // END_INCLUDE (configure_media_recorder)

        // Step 5: Prepare configured MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IllegalStateException e) {
            Log.d(TAG, "IllegalStateException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        } catch (IOException e) {
            Log.d(TAG, "IOException preparing MediaRecorder: " + e.getMessage());
            releaseMediaRecorder();
            return false;
        }
        return true;
    }

    /**
     * Asynchronous task for preparing the {@link android.media.MediaRecorder} since it's a long blocking
     * operation.
     */
    class MediaPrepareTask extends AsyncTask<Void, Void, Boolean> {

        @Override
        protected Boolean doInBackground(Void... voids) {
            // initialize video camera
            if (prepareVideoRecorder()) {
                // Camera is available and unlocked, MediaRecorder is prepared,
                // now you can start recording
                startHTime = SystemClock.uptimeMillis();
                mMediaRecorder.start();
                isRecording = true;
            } else {
                // prepare didn't work, release the camera
                releaseMediaRecorder();
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (!result) {
                MainActivity.this.finish();
            }
            // inform the user that recording has started
            setCaptureButtonText("Stop");
        }
    }

    final int delaytime = 3000;

    int[] circles = {
            R.id.circle1,
            R.id.circle2,
            R.id.circle3,
            R.id.circle4,
            R.id.circle5,
            R.id.circle6,
            R.id.circle7,
            R.id.circle8,
            R.id.circle9,
            R.id.circle10,
            R.id.circle11,
            R.id.circle12,
    };

    public void circleCalibrateOn(final int order) {
        new Handler().postDelayed(new Runnable() {
            public void run() {

                if (order >= 1) {
                    findViewById(circles[order - 1]).setVisibility(View.INVISIBLE);
                }
                if (order <= 11) {
                    try {
                        saveCalibrationInfo(order);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    findViewById(circles[order]).setVisibility(View.VISIBLE);
                    circleCalibrateOn(order + 1);
                }

                if (order == 12) {
                    try {
                        mMediaRecorder.stop();  // stop the recording
                    } catch (RuntimeException e) {
                        // RuntimeException is thrown when stop() is called immediately after start().
                        // In this case the output file is not properly constructed ans should be deleted.
                        Log.d(TAG, "RuntimeException: stop() is called immediately after start()");
                        //noinspection ResultOfMethodCallIgnored
                        mOutputFile.delete();
                    }
                    releaseMediaRecorder(); // release the MediaRecorder object
                    mCamera.lock();         // take camera access back from MediaRecorder

                    // inform the user that recording has stopped
                    setCaptureButtonText("Capture");
                    isRecording = false;
                    final Path path = Paths.get(videofile);
                    releaseCamera();
                    MediaMetadataRetriever retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(new File(videofile).toString());
                    serverurl = ((EditText) findViewById(R.id.serverinfo)).getText().toString();
                    double allframe = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT));
                    double time = Integer.parseInt(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
                    try {
                        result.accumulate("User", username.getText());
                        result.accumulate("File", path.getFileName());
                        result.accumulate("TimeStamp", df.format(startRecord));
                        result.accumulate("RotateAngle", 270);
                        result.accumulate("FPS", allframe / (time / 1000));
                        result.put("ScrOriginX", -(Double.parseDouble(result.get("ScrMilliWidth").toString()) * camera_x.getProgress() / 100));
                        result.accumulate("CalibPoints", CalibPoints);

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                        alertDialog.setTitle("Cablibration Info");
                        alertDialog.setMessage(result.toString());
                        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Complete",
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                try {
                                    BufferedWriter buf = new BufferedWriter(new FileWriter(path.toString().replace(".mp4", ".json"), true));
                                    buf.append(result.toString());
                                    buf.close();

                                    SimpleMultiPartRequest smr = new SimpleMultiPartRequest(Request.Method.POST, serverurl+"/upload",
                                            new Response.Listener<String>() {
                                                @Override
                                                public void onResponse(String response) {
                                                    Log.d("Response", response);
                                                    RequestQueue mRequestQueue = Volley.newRequestQueue(getApplicationContext());
                                                    SimpleMultiPartRequest smr2 = new SimpleMultiPartRequest(Request.Method.POST, serverurl+"/upload",
                                                            new Response.Listener<String>() {
                                                                @Override
                                                                public void onResponse(String response) {
                                                                    AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                                                                    alertDialog.setTitle("Calibration Complete");
                                                                    alertDialog.setMessage("Calibration is complete. If you want to try to calibrate again, Kill the app in the background and relaunch the application (Same name please)");
                                                                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Complete",
                                                                            new DialogInterface.OnClickListener() {
                                                                                public void onClick(DialogInterface dialog, int which) {
                                                                                }
                                                                            });
                                                                    alertDialog.show();
                                                                    Log.d("Response", response);
                                                                }
                                                            }, new Response.ErrorListener() {
                                                        @Override
                                                        public void onErrorResponse(VolleyError error) {
                                                        }
                                                    });
                                                    smr2.addFile("file", path.toString().replace(".mp4", ".json"));
                                                    mRequestQueue.add(smr2);
                                                }
                                            }, new Response.ErrorListener() {
                                                @Override
                                                public void onErrorResponse(VolleyError error) {
                                                }
                                    });
                                    smr.addFile("file", path.toString());

                                    RequestQueue mRequestQueue = Volley.newRequestQueue(getApplicationContext());
                                    mRequestQueue.add(smr);

                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                        alertDialog.show();
                    }
                }
        }, delaytime);
    }

    public void calibraionRun() {
        circleCalibrateOn(0);
    }

    public void saveCalibrationInfo(int order) throws JSONException {
        final int[] location = new int[2];
        findViewById(circles[order]).getLocationOnScreen(location);

        if (order == 1 || order == 2 || order == 5 || order == 6 || order == 9 || order == 10)
            location[1] += (findViewById(circles[order]).getHeight() / 2);
        if (order == 3 || order == 7 || order == 11)
            location[1] += findViewById(circles[order]).getHeight();
        if(order==4||order==5||order==6||order==7)
            location[0] += (findViewById(circles[order]).getWidth() / 2);
        if(order==8||order==9||order==10||order==11)
            location[0] += findViewById(circles[order]).getWidth();

        saveCalibrationMultiple(location[0], location[1], 0);
    }

    public void saveCalibrationMultiple(final int x, final int y, final int i) {
        if (i < 3) {
            new Handler().postDelayed(new Runnable() {
                public void run() {
                    try {
                        JSONObject calibcircle = new JSONObject();
                        calibcircle.accumulate("Millisecond", SystemClock.uptimeMillis() - startHTime);
                        calibcircle.accumulate("ScrPixelX", x);
                        calibcircle.accumulate("ScrPixelY", y);
                        CalibPoints.put(calibcircle);
                        saveCalibrationMultiple(x,y,i+1);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }
            }, 300);
        }
    }
}