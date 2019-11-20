package com.qugengting.camera2demo;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import androidx.core.app.ActivityCompat;

import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * @author:xuruibin
 * @date:2019/11/18
 * Description:参考：https://www.jianshu.com/p/0ea5e201260f
 *                  github:https://github.com/smashinggit/Study
 */
public class CameraHelper {
    private static final String TAG = CameraHelper.class.getSimpleName();
    private CameraManager mCameraManager;
    private ImageReader mImageReader;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCameraCaptureSession;

    private String mCameraId = "0";
    private CameraCharacteristics mCameraCharacteristics;
    private int mCameraSensorOrientation = 0;                                            //摄像头方向
    private int mCameraFacing = CameraCharacteristics.LENS_FACING_FRONT;                //默认使用前置摄像头;
    private int mDisplayRotation;                                                       //手机方向

    private boolean canTakePic = true;                                                       //是否可以拍照
    private boolean canExchangeCamera = false;                                               //是否可以切换摄像头

    private Handler mCameraHandler;
    private HandlerThread handlerThread = new HandlerThread("CameraThread");

    private Size mPreviewSize = new Size(PREVIEW_WIDTH, PREVIEW_HEIGHT);                      //预览大小
    private Size mSavePicSize = new Size(SAVE_WIDTH, SAVE_HEIGHT);                            //保存图片大小

    private static final int PREVIEW_WIDTH = 720;                               //预览的宽度
    private static final int PREVIEW_HEIGHT = 1280;                             //预览的高度
    private static final int SAVE_WIDTH = 720;                                  //保存图片的宽度
    private static final int SAVE_HEIGHT = 1280;                                //保存图片的高度

    private CameraActivity mActivity;
    private TextureView mTextureView;

    public CameraHelper(CameraActivity activity, TextureView textureView) {
        this.mActivity = activity;
        this.mTextureView = textureView;
        mDisplayRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        init();
    }

    private void init() {
        handlerThread.start();
        mCameraHandler = new Handler(handlerThread.getLooper());

        mTextureView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                initCameraInfo();
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                releaseCamera();
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

    /**
     * 初始化
     */
    private void initCameraInfo() {
        mCameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        String[] cameraIdList;
        try {
            cameraIdList = mCameraManager.getCameraIdList();
            for (String id : cameraIdList) {
                CameraCharacteristics cameraCharacteristics = mCameraManager.getCameraCharacteristics(id);
                int facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing == mCameraFacing) {
                    mCameraId = id;
                    mCameraCharacteristics = cameraCharacteristics;
                    break;
                }
            }
            //获取当前设备支持的相机特性
            int supportLevel = mCameraCharacteristics.get(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            if (supportLevel == CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY) {
//            mActivity.toast("相机硬件不支持新特性")
            }

            //获取摄像头方向
            mCameraSensorOrientation = mCameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            //获取StreamConfigurationMap，它是管理摄像头支持的所有输出格式和尺寸
            StreamConfigurationMap configurationMap = mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            Size[] savePicSize = configurationMap.getOutputSizes(ImageFormat.JPEG);          //保存照片尺寸
            Size[] previewSize = configurationMap.getOutputSizes(SurfaceTexture.class);        //预览尺寸

            boolean exchange = exchangeWidthAndHeight(mDisplayRotation, mCameraSensorOrientation);

            if (exchange) {
                mSavePicSize = getBestSize(
                        mSavePicSize.getHeight(), mSavePicSize.getWidth(),
                        mSavePicSize.getHeight(), mSavePicSize.getWidth(),
                        Arrays.asList(savePicSize));
            } else {
                mSavePicSize = getBestSize(
                        mSavePicSize.getWidth(), mSavePicSize.getHeight(),
                        mSavePicSize.getWidth(), mSavePicSize.getHeight(),
                        Arrays.asList(savePicSize));
            }

            if (exchange) {
                mPreviewSize = getBestSize(
                        mPreviewSize.getHeight(), mPreviewSize.getWidth(),
                        mPreviewSize.getHeight(), mPreviewSize.getWidth(),
                        Arrays.asList(previewSize));
            } else {
                mPreviewSize = getBestSize(
                        mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                        mPreviewSize.getWidth(), mPreviewSize.getHeight(),
                        Arrays.asList(previewSize));
            }

            mTextureView.getSurfaceTexture().setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

            mImageReader = ImageReader.newInstance(mSavePicSize.getWidth(), mSavePicSize.getHeight(), ImageFormat.JPEG, 1);
            mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = reader.acquireNextImage();
                    ByteBuffer byteBuffer = image.getPlanes()[0].getBuffer();
                    byte[] byteArray = new byte[byteBuffer.remaining()];
                    byteBuffer.get(byteArray);
                    reader.close();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length);
                    if (mCameraSensorOrientation == 270) {
                        bitmap = BitmapUtils.mirror(bitmap);
                    }
                    String path = mActivity.getExternalCacheDir().getAbsolutePath() + File.separator;

                    if (mCameraFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                        bitmap = BitmapUtils.rotateBitmap(bitmap, 360 - mCameraSensorOrientation);
                    } else {
                        bitmap = BitmapUtils.rotateBitmap(bitmap, mCameraSensorOrientation);
                    }
                    bitmap = BitmapUtils.imageZoom(bitmap, 600);
                    String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
                    String name = timeStamp + ".jpg";
                    File file = new File(path);
                    File targetFile = new File(path + name);
                    image.close();
                    boolean result = BitmapUtils.saveBitmap(bitmap, file, targetFile);
                    //注意这里是异步线程，不允许直接操作界面
                    if (result) {
                        Log.e(TAG, "保存成功，路径 : " + path + name);
                    } else {
                        Log.e(TAG, "保存失败");
                    }
                }
            }, mCameraHandler);

            openCamera();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void openCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            mCameraManager.openCamera(mCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice camera) {
                    mCameraDevice = camera;
                    createCaptureSession(camera);
                }

                @Override
                public void onDisconnected(CameraDevice camera) {

                }

                @Override
                public void onError(CameraDevice camera, int error) {

                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 创建预览会话
     */
    private void createCaptureSession(CameraDevice cameraDevice) {
        try {
            //创建一个新的Capture请求
            //描述了一次操作请求，拍照、预览等操作都需要先传入CaptureRequest参数，
            // 具体的参数控制也是通过CameraRequest的成员变量来设置
            final CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            Surface surface = new Surface(mTextureView.getSurfaceTexture());
            captureRequestBuilder.addTarget(surface);  // 给此次请求添加一个Surface对象作为图像的输出目标
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);      // 闪光灯
            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE); // 自动对焦

            // 创建CaptureSession会话。
            // 第一个参数 outputs 是一个 List 数组，相机会把捕捉到的图片数据传递给该参数中的 Surface 。
            // 第二个参数 StateCallback 是创建会话的状态回调。
            // 第三个参数描述了 StateCallback 被调用时所在的线程
            cameraDevice.createCaptureSession(Arrays.asList(surface, mImageReader.getSurface()), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    mCameraCaptureSession = session;
                    try {
                        //根据传入的 CaptureRequest 对象开始一个无限循环的捕捉图像的请求。第二个参数 listener 为捕捉图像的回调
                        session.setRepeatingRequest(captureRequestBuilder.build(), mCaptureCallBack, mCameraHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {

                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private CameraCaptureSession.CaptureCallback mCaptureCallBack = new CameraCaptureSession.CaptureCallback() {
        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
            super.onCaptureCompleted(session, request, result);
            canExchangeCamera = true;
            canTakePic = true;
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request, CaptureFailure failure) {
            super.onCaptureFailed(session, request, failure);
        }

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
            super.onCaptureStarted(session, request, timestamp, frameNumber);
        }
    };

    /**
     * 拍照
     */
    public void takePic() {

        if (mCameraDevice == null || !mTextureView.isAvailable() || !canTakePic) {
            Log.e("tag", "err");
            return;
        }
        try {
            CaptureRequest.Builder captureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            captureRequestBuilder.addTarget(mImageReader.getSurface());

            captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE); // 自动对焦
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);     // 闪光灯
//            captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, mCameraSensorOrientation);      //根据摄像头方向对保存的照片进行旋转，使其为"自然方向"
            mCameraCaptureSession.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {
                @Override
                public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request, long timestamp, long frameNumber) {
                    super.onCaptureStarted(session, request, timestamp, frameNumber);
                }

                @Override
                public void onCaptureProgressed(CameraCaptureSession session, CaptureRequest request, CaptureResult partialResult) {
                    super.onCaptureProgressed(session, request, partialResult);
                }

                @Override
                public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request, TotalCaptureResult result) {
                    super.onCaptureCompleted(session, request, result);
                }

                @Override
                public void onCaptureSequenceCompleted(CameraCaptureSession session, int sequenceId, long frameNumber) {
                    super.onCaptureSequenceCompleted(session, sequenceId, frameNumber);
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * 根据提供的参数值返回与指定宽高相等或最接近的尺寸
     *
     * @param targetWidth  目标宽度
     * @param targetHeight 目标高度
     * @param maxWidth     最大宽度(即TextureView的宽度)
     * @param maxHeight    最大高度(即TextureView的高度)
     * @param sizeList     支持的Size列表
     * @return 返回与指定宽高相等或最接近的尺寸
     */
    private Size getBestSize(int targetWidth, int targetHeight, int maxWidth, int maxHeight, List<Size> sizeList) {
        List<Size> bigEnough = new ArrayList<Size>();     //比指定宽高大的Size列表
        List<Size> notBigEnough = new ArrayList<Size>();  //比指定宽高小的Size列表

        for (Size size : sizeList) {
            //宽<=最大宽度  &&  高<=最大高度  &&  宽高比 == 目标值宽高比
            if (size.getWidth() <= maxWidth && size.getHeight() <= maxHeight
                    && size.getWidth() == size.getHeight() * targetWidth / targetHeight) {

                if (size.getWidth() >= targetWidth && size.getHeight() >= targetHeight)
                    bigEnough.add(size);
                else
                    notBigEnough.add(size);
            }
        }

        //选择bigEnough中最小的值  或 notBigEnough中最大的值
        Size size;
        if (bigEnough.size() > 0) {
            size = Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            size = Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            size = sizeList.get(0);
        }
        return size;
    }

    /**
     * 根据提供的屏幕方向 [displayRotation] 和相机方向 [sensorOrientation] 返回是否需要交换宽高
     */
    private boolean exchangeWidthAndHeight(int displayRotation, int sensorOrientation) {
        boolean exchange = false;
        switch (displayRotation) {
            case Surface.ROTATION_0:
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    exchange = true;
                }
                break;
            case Surface.ROTATION_180:
                if (sensorOrientation == 90 || sensorOrientation == 270) {
                    exchange = true;
                }
                break;
            case Surface.ROTATION_90:
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    exchange = true;
                    break;
                }
            case Surface.ROTATION_270:
                if (sensorOrientation == 0 || sensorOrientation == 180) {
                    exchange = true;
                    break;
                }
        }
        return exchange;
    }

    /**
     * 切换摄像头
     */
    public void exchangeCamera() {
        if (mCameraDevice == null || !canExchangeCamera || !mTextureView.isAvailable()) {
            return;
        }

        if (mCameraFacing == CameraCharacteristics.LENS_FACING_FRONT) {
            mCameraFacing = CameraCharacteristics.LENS_FACING_BACK;
        } else {
            mCameraFacing = CameraCharacteristics.LENS_FACING_FRONT;
        }
        mPreviewSize = new Size(PREVIEW_WIDTH, PREVIEW_HEIGHT); //重置预览大小
//        mDisplayRotation = mActivity.getWindowManager().getDefaultDisplay().getRotation();
        releaseCamera();
        initCameraInfo();
    }

    public void releaseCamera() {
        mCameraCaptureSession.close();
        mCameraCaptureSession = null;

        mCameraDevice.close();
        mCameraDevice = null;

        mImageReader.close();
        mImageReader = null;

        canExchangeCamera = false;
    }

    public void releaseThread() {
        handlerThread.quitSafely();
    }

    private class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size size1, Size size2) {
            return java.lang.Long.signum(size1.getWidth() * size1.getHeight() - size2.getWidth() * size2.getHeight());
        }
    }
}
