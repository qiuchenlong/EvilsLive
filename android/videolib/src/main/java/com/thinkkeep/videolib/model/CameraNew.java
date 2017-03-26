package com.thinkkeep.videolib.model;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.thinkkeep.videolib.api.EvilsLiveStreamerConfig;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * API >= 21 调用类
 * Created by jason on 17/2/27.
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class CameraNew implements CameraSupport {
    private static final String TAG = CameraNew.class.getName();
    private final Context context;
    private CameraDevice mCamera;
    private CameraManager manager;
    private SurfaceView surfaceView;
    private EvilsLiveStreamerConfig config;

    private CaptureRequest.Builder mPreviewBuilder;

    private ImageReader mImageReader;

    /**
     *
     * A {@link Semaphore} to prevent the app from exiting before closing the mCamera.
     */
    private Semaphore cameraOpenCloseLock = new Semaphore(1);
    private boolean mIsInPreview;

    private OnPreviewFrameListener listener;

    private ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader imageReader) {
            Log.d(TAG, "onImageAvailable: xx");
            Image image = imageReader.acquireNextImage();
            ByteBuffer buffer = image.getPlanes()[0].getBuffer();
            byte[] data = new byte[buffer.remaining()];
            buffer.get(data);
            image.close();

            if (listener != null) {
                listener.onPreviewFrameListener(data, 754, 360);
            }
        }
    };


    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public CameraNew(final Context context) {
        this.context = context;
        this.manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    @Override
    public CameraSupport open(final int cameraId) {
        try {
            if (mIsInPreview) {
                reconfigureCamera();
                return this;
            }

            mIsInPreview = true;

            mImageReader = ImageReader.newInstance(640, 480, ImageFormat.JPEG, 2);
            mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, null);
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MICROSECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }

            String[] cameraIds = manager.getCameraIdList();

            CameraDevice.StateCallback deviceCallback = new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    cameraOpenCloseLock.release();
                    mCamera = camera;
                    createCameraPreviewSession();
                }

                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    Log.d(TAG, "onDisconnected:  open");
                    cameraOpenCloseLock.release();
                    camera.close();
                    mCamera = null;
                }

                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    cameraOpenCloseLock.release();
                    camera.close();
                    mCamera = null;
                }
            };
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG, "open: jjjj");
            }
            manager.openCamera(cameraIds[cameraId], deviceCallback, null);

        } catch (Exception e) {
            Log.e(TAG, "open: " + Log.getStackTraceString(e));
        }
        return this;
    }

    private void reconfigureCamera() {
        if(mCamera != null) {
            try {
                mCaptureSession.stopRepeating();

                mPreviewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL);
                mCaptureSession.capture(mPreviewBuilder.build(), mPreCaptureCallback, null);

                doPreviewConfiguration();
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    private void doPreviewConfiguration() {
        if (mCamera == null) {
            return;
        }

        try {
            mPreviewBuilder = mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            mPreviewBuilder.addTarget(surfaceView.getHolder().getSurface());
            mPreviewBuilder.addTarget(mImageReader.getSurface());

            mPreviewBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO);
            mPreviewBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
            mPreviewBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);

            mCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), mPreCaptureCallback, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.CaptureCallback mPreCaptureCallback
            = new CameraCaptureSession.CaptureCallback() {

        private void process(CaptureResult result) {

        }

        @Override
        public void onCaptureProgressed(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                        @NonNull CaptureResult partialResult) {
            process(partialResult);
        }

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureSession session, @NonNull CaptureRequest request,
                                       @NonNull TotalCaptureResult result) {
            process(result);
        }

    };

    private void createCameraPreviewSession() {
        if (mCamera == null) {
            return;
        }

        SurfaceHolder holder = surfaceView.getHolder();
        if (config != null) {
            holder.setFixedSize(640, 480);
        }
        Surface surface = holder.getSurface();
        List<Surface> surfaceList = Arrays.asList(surface, mImageReader.getSurface());

        try {
//            mCamera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
//            mPreviewBuilder.addTarget(surface);

            mCamera.createCaptureSession(surfaceList, sessionCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession mCaptureSession;

    private CameraCaptureSession.StateCallback sessionCallback = new CameraCaptureSession.StateCallback() {
        @Override
        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
            mCaptureSession = cameraCaptureSession;
            doPreviewConfiguration();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {

        }
    };

    private void updatePreview(CameraCaptureSession cameraCaptureSession) {
        if (mCamera == null) {
            return;
        }

        setUpCaptureRequestBuilder(mPreviewBuilder);

        try {
            cameraCaptureSession.setRepeatingRequest(mPreviewBuilder.build(), mPreCaptureCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder) {
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }

    @Override
    public int getOrientation(final int cameraId) {
        try {
            String[] cameraIds = manager.getCameraIdList();
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraIds[cameraId]);
            return characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        } catch (CameraAccessException e) {
            // TODO handle
            return 0;
        }
    }

    @Override
    public void setDisplayPreview(SurfaceView surfaceView) {
        this.surfaceView = surfaceView;
    }

    @Override
    public void setOnPreviewFrameListener(OnPreviewFrameListener listener) {
        this.listener = listener;
    }

    @Override
    public void setStreamConfig(EvilsLiveStreamerConfig config) {
        this.config = config;
    }

    @Override
    public void close() {
        Log.d(TAG, "close: ddddd");
        mIsInPreview = false;
        closeCamera();
    }

    private void closeCamera() {
        try {
            cameraOpenCloseLock.acquire();
            if (mCaptureSession != null) {
                mCaptureSession.close();
                mCaptureSession = null;
            }
            if (mCamera != null) {
                mCamera.close();
                mCamera = null;
            }
            if (mImageReader != null) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            cameraOpenCloseLock.release();
        }
    }
}