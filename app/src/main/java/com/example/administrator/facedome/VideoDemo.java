package com.example.administrator.facedome;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import android.Manifest.permission;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PreviewCallback;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Process;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.Toast;

import com.example.administrator.facedemo.R;
import com.example.administrator.facedome.util.FaceRect;
import com.example.administrator.facedome.util.FaceUtil;
import com.example.administrator.facedome.util.ParseResult;
import com.example.administrator.facedome.utility.Utility;
import com.example.administrator.facedome.weight.SuperSurfaceView;
import com.iflytek.cloud.ErrorCode;
import com.iflytek.cloud.FaceDetector;
import com.iflytek.cloud.FaceRequest;
import com.iflytek.cloud.RequestListener;
import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.util.Accelerometer;
import com.tarek360.instacapture.Instacapture;

import org.json.JSONException;
import org.json.JSONObject;

import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;



/**
 * 离线视频流检测示例
 * 该业务仅支持离线人脸检测SDK，请开发者前往<a href="http://www.xfyun.cn/">讯飞语音云</a>SDK下载界面，下载对应离线SDK
 */
public class VideoDemo extends Activity {
	private final static String TAG = VideoDemo.class.getSimpleName();
	private SurfaceView mPreviewSurface;
	private SurfaceView mFaceSurface;
	private Camera mCamera;
	private int mCameraId = CameraInfo.CAMERA_FACING_FRONT;
	// Camera nv21格式预览帧的尺寸，默认设置640*480
	private int PREVIEW_WIDTH = 640;
	private int PREVIEW_HEIGHT = 480;
	// 预览帧数据存储数组和缓存数组
	private byte[] nv21;
	private byte[] buffer;
	// 缩放矩阵
	private Matrix mScaleMatrix = new Matrix();
	// 加速度感应器，用于获取手机的朝向
	private Accelerometer mAcc;
	// FaceDetector对象，集成了离线人脸识别：人脸检测、视频流检测功能
	private FaceDetector mFaceDetector;
	private boolean mStopTrack;
	private Toast mToast;
	private long mLastClickTime;
	private int isAlign = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_video_demo);
	
		initUI();

		mFaceRequest = new FaceRequest(this);



		nv21 = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 2];
		buffer = new byte[PREVIEW_WIDTH * PREVIEW_HEIGHT * 2];
		mAcc = new Accelerometer(VideoDemo.this);
		mFaceDetector = FaceDetector.createDetector(VideoDemo.this, null);
	}
	
	
	private Callback mPreviewCallback = new Callback() {
		
		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			closeCamera();
		}
		
		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			openCamera();
		}
		
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			mScaleMatrix.setScale(width/(float)PREVIEW_HEIGHT, height/(float)PREVIEW_WIDTH);
		}
	};
	
	private void setSurfaceSize() {
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		
		int width = metrics.widthPixels;
		int height = (int) (width * PREVIEW_WIDTH / (float)PREVIEW_HEIGHT);
		LayoutParams params = new LayoutParams(width, height);
		params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
		
		mPreviewSurface.setLayoutParams(params);
		mFaceSurface.setLayoutParams(params);
	}

	@SuppressLint("ShowToast")
	@SuppressWarnings("deprecation")
	private void initUI() {
		Instacapture.INSTANCE.enableLogging(true);

		mPreviewSurface = (SurfaceView) findViewById(R.id.sfv_preview);
		mFaceSurface = (SurfaceView) findViewById(R.id.sfv_face);

		mPreviewSurface.getHolder().addCallback(mPreviewCallback);
		mPreviewSurface.getHolder().setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		mFaceSurface.setZOrderOnTop(true);
		mFaceSurface.getHolder().setFormat(PixelFormat.TRANSLUCENT);
		//新建一个
		mCameraHelper = CameraHelper.createHelper(VideoDemo.this);

		// 点击SurfaceView，切换摄相头
		mFaceSurface.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View v) {
				// 只有一个摄相头，不支持切换
				if (Camera.getNumberOfCameras() == 1) {
					showTip("只有后置摄像头，不能切换");
					return;
				}
				closeCamera();
				if (CameraInfo.CAMERA_FACING_FRONT == mCameraId) {
					mCameraId = CameraInfo.CAMERA_FACING_BACK;
				} else {
					mCameraId = CameraInfo.CAMERA_FACING_FRONT;
				}
				openCamera();
			}
		});
		
		// 长按SurfaceView 500ms后松开，摄相头聚集
		mFaceSurface.setOnTouchListener(new OnTouchListener() {
			
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					mLastClickTime = System.currentTimeMillis();
					break;
				case MotionEvent.ACTION_UP:
					if (System.currentTimeMillis() - mLastClickTime > 500) {
						mCamera.autoFocus(null);
						return true;
					}
					break;
					
				default:
					break;
				}
				return false;
			}
		});
		
		RadioGroup alignGruop = (RadioGroup) findViewById(R.id.align_mode);
		alignGruop.setOnCheckedChangeListener(new OnCheckedChangeListener() {
			
			@Override
			public void onCheckedChanged(RadioGroup arg0, int arg1) {
				switch (arg1) {
				case R.id.detect:
					isAlign = 0;
					break;
				case R.id.align:
					isAlign = 1;
					break;
				default:
					break;
				}
			}
		});
		
		setSurfaceSize();
		mToast = Toast.makeText(VideoDemo.this, "", Toast.LENGTH_SHORT);
	}
	
	private void openCamera() {
		if (null != mCamera) {
			return;
		}
		
		if (!checkCameraPermission()) {
			showTip("摄像头权限未打开，请打开后再试");
			mStopTrack = true;
			return;
		}
		
		// 只有一个摄相头，打开后置
		if (Camera.getNumberOfCameras() == 1) {
			mCameraId = CameraInfo.CAMERA_FACING_BACK;
		}
		
		try {
			mCamera = Camera.open(mCameraId);
			if (CameraInfo.CAMERA_FACING_FRONT == mCameraId) {
				showTip("前置摄像头已开启，点击可切换");
			} else {
				showTip("后置摄像头已开启，点击可切换");
			}
		} catch (Exception e) {
			e.printStackTrace();
			closeCamera();
			return;
		}
		
		Parameters params = mCamera.getParameters();
		params.setPreviewFormat(ImageFormat.NV21);
		params.setPreviewSize(PREVIEW_WIDTH, PREVIEW_HEIGHT);
		mCamera.setParameters(params);
		
		// 设置显示的偏转角度，大部分机器是顺时针90度，某些机器需要按情况设置
		mCamera.setDisplayOrientation(90);
		mCamera.setPreviewCallback(new PreviewCallback() {
			
			@Override
			public void onPreviewFrame(byte[] data, Camera camera) {
				System.arraycopy(data, 0, nv21, 0, data.length);
			}
		});
		
		try {
			mCamera.setPreviewDisplay(mPreviewSurface.getHolder());
			mCamera.startPreview();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if(mFaceDetector == null) {
			/**
			 * 离线视频流检测功能需要单独下载支持离线人脸的SDK
			 * 请开发者前往语音云官网下载对应SDK
			 */
			// 创建单例失败，与 21001 错误为同样原因，参考 http://bbs.xfyun.cn/forum.php?mod=viewthread&tid=9688
			showTip( "创建对象失败，请确认 libmsc.so 放置正确，\n 且有调用 createUtility 进行初始化" );
		}
	}
	
	private void closeCamera() {
		if (null != mCamera) {
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			mCamera.release();
			mCamera = null;
		}
	}
	
	private boolean checkCameraPermission() {
		int status = checkPermission(permission.CAMERA, Process.myPid(), Process.myUid());
		if (PackageManager.PERMISSION_GRANTED == status) {
			return true;
		}
		
		return false;
	}

	boolean flag=true;
	@Override
	protected void onResume() {
		super.onResume();
		
		if (null != mAcc) {
			mAcc.start();
		}
		
		mStopTrack = false;

		new Thread(new Runnable() {
			
			@Override
			public void run() {
				while (!mStopTrack) {
					if (null == nv21) {
						continue;
					}
					Log.e(TAG, "run: 进来了 方法");
					synchronized (nv21) {
						System.arraycopy(nv21, 0, buffer, 0, nv21.length);
					}
					
					// 获取手机朝向，返回值0,1,2,3分别表示0,90,180和270度
					int direction = Accelerometer.getDirection();
					//判断是否是牵制摄像头
					boolean frontCamera = (CameraInfo.CAMERA_FACING_FRONT == mCameraId);
					// 前置摄像头预览显示的是镜像，需要将手机朝向换算成摄相头视角下的朝向。
					// 转换公式：a' = (360 - a)%360，a为人眼视角下的朝向（单位：角度）
					if (frontCamera) {
						// SDK中使用0,1,2,3,4分别表示0,90,180,270和360度
						direction = (4 - direction)%4;
					}

					if(mFaceDetector == null) {
						/**
						 * 离线视频流检测功能需要单独下载支持离线人脸的SDK
						 * 请开发者前往语音云官网下载对应SDK
						 */
						// 创建单例失败，与 21001 错误为同样原因，参考 http://bbs.xfyun.cn/forum.php?mod=viewthread&tid=9688
						showTip( "创建对象失败，请确认 libmsc.so 放置正确，\n 且有调用 createUtility 进行初始化" );
						break;
					}
					
					String result = mFaceDetector.trackNV21(buffer, PREVIEW_WIDTH, PREVIEW_HEIGHT, isAlign, direction);
					//
					Log.e(TAG, "result:"+result);
//					result:{"ret":0,"face":[{"position":{"bottom":352,"right":344,"left":102,"top":110},
// "landmark":{"right_eye_right_corner":{"y":296,"x":276},"left_eye_left_corner":{"y":149,"x":276},
// "right_eye_center":{"y":272,"x":276},"left_eyebrow_middle":{"y":164,"x":317},"right_eyebrow_left_corner":{"y":244,"x":316},
// "mouth_right_corner":{"y":256,"x":154},"mouth_left_corner":{"y":172,"x":151},"left_eyebrow_left_corner":{"y":137,"x":313},
// "right_eyebrow_middle":{"y":278,"x":320},"left_eye_center":{"y":169,"x":276},"nose_left":{"y":182,"x":203},
// "mouth_lower_lip_bottom":{"y":212,"x":138},"nose_right":{"y":245,"x":203},"left_eyebrow_right_corner":{"y":192,"x":311},
// "right_eye_left_corner":{"y":250,"x":274},"nose_bottom":{"y":213,"x":191},"nose_top":{"y":210,"x":210},"mouth_middle":{"y":212,"x":151},
// "left_eye_right_corner":{"y":191,"x":274},"mouth_upper_lip_top":{"y":213,"x":164},"right_eyebrow_right_corner":{"y":311,"x":311}}}]}
					//解析
					FaceRect[] faces = ParseResult.parseResult(result);
					//获得Canvas对象并锁定画布
					Canvas canvas = mFaceSurface.getHolder().lockCanvas();
					if (null == canvas) {
						continue;
					}
					//
					canvas.drawColor(0, PorterDuff.Mode.CLEAR);
					canvas.setMatrix(mScaleMatrix);

					if( faces == null || faces.length <=0 ) {
						mFaceSurface.getHolder().unlockCanvasAndPost(canvas);
						continue;
					}
					
					if (null != faces && frontCamera == (CameraInfo.CAMERA_FACING_FRONT == mCameraId)) {
						//画框
						if (flag){
							//截屏
//							captureScreenshot();
//							Bitmap bitmap = Bitmap.createBitmap(mPreviewSurface.getWidth(), mPreviewSurface.getHeight(), Bitmap.Config.ARGB_8888);
//							Canvas canvas = new Canvas(bitmap);
//							draw(canvas);  // SurfaceHolder.lockCanvas()返回的Canvas 绘制什么内容，我们定义的也绘制一遍。
							takePicture();
						}
						flag=false;

						for (FaceRect face: faces) {
							//将矩形随原图顺时针旋转90度
							face.bound = FaceUtil.RotateDeg90(face.bound, PREVIEW_WIDTH, PREVIEW_HEIGHT);
							if (face.point != null) {
								for (int i = 0; i < face.point.length; i++) {
									face.point[i] = FaceUtil.RotateDeg90(face.point[i], PREVIEW_WIDTH, PREVIEW_HEIGHT);
								}
							}
							//在指定画布上将人脸框出来
							FaceUtil.drawFaceRect(canvas, face, PREVIEW_WIDTH, PREVIEW_HEIGHT, frontCamera, false);
						}
					} else {
						Log.e(TAG, "faces:0");
					}
					
					mFaceSurface.getHolder().unlockCanvasAndPost(canvas);


				}
			}
		}).start();
	}

	private boolean mCanTakePic = true;
	private boolean mIsPause = false;
	private CameraHelper mCameraHelper;

	private void takePicture() {
		// 拍照，发起人脸注册
		try {
			if(mCamera != null && mCanTakePic){
				Log.d(TAG, "takePicture");
				mCamera.takePicture(mShutterCallback, null, mPictureCallback);
				mCanTakePic = false;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private Camera.ShutterCallback mShutterCallback = new Camera.ShutterCallback() {

		@Override
		public void onShutter() {

		}
	};

	private Camera.PictureCallback mPictureCallback = new Camera.PictureCallback() {

		@Override
		public void onPictureTaken(byte[] data, Camera camera) {
			Log.d(TAG, "onPictureTaken");
			if (!mIsPause) {
				mCameraHelper.setCacheData(data, mCameraId, VideoDemo.this);
				//发送消息 开始人脸识别
//				mHandler.sendEmptyMessage(MSG_FACE_START);
//				showImage(mCameraHelper.getImageBitmap());
				verify(mCameraHelper.getImageData());
			}
			mCanTakePic = true;
		}
	};


//	public static Bitmap takeScreenShot(Activity act) {
//		if (act == null || act.isFinishing()) {
//			Log.d(TAG, "act参数为空.");
//			return null;
//		}
//
//		// 获取当前视图的view
//		View scrView = act.getWindow().getDecorView();
//		scrView.setDrawingCacheEnabled(true);
//		scrView.buildDrawingCache(true);
//
//		// 获取状态栏高度
//		Rect statuBarRect = new Rect();
//		scrView.getWindowVisibleDisplayFrame(statuBarRect);
//		int statusBarHeight = statuBarRect.top;
//		int width = act.getWindowManager().getDefaultDisplay().getWidth();
//		int height = act.getWindowManager().getDefaultDisplay().getHeight();
//
//		Bitmap scrBmp = null;
//		try {
//			// 去掉标题栏的截图
//			scrBmp = Bitmap.createBitmap( scrView.getDrawingCache(), 0, statusBarHeight,
//					width, height - statusBarHeight);
//		} catch (IllegalArgumentException e) {
//			Log.d("", "#### 旋转屏幕导致去掉状态栏失败");
//		}
//		scrView.setDrawingCacheEnabled(false);
//		scrView.destroyDrawingCache();
//		return scrBmp;
//	}

	protected void captureScreenshot(@Nullable View... ignoredViews) {
		Instacapture.INSTANCE.captureRx(this, ignoredViews).subscribe(new Action1<Bitmap>() {
			@Override
			public void call(final Bitmap bitmap) {

				Bitmap bmp = bitmap;
				String fileSrc = null;

				// 若返回数据不为null，保存至本地，防止裁剪时未能正常保存
				if(null != bmp){
					showTip("保存了bitmap 图片");
					FaceUtil.saveBitmapToFile(VideoDemo.this, bmp);
				}
				// 获取图片保存路径
				fileSrc = FaceUtil.getImagePath(VideoDemo.this);
				updateGallery(fileSrc);
				showTip("图片路径是："+fileSrc);
				Log.e(TAG, "图片路径是："+fileSrc );
				// 获取图片的宽和高
				BitmapFactory.Options options = new BitmapFactory.Options();
				options.inJustDecodeBounds = true;

				mImage = BitmapFactory.decodeFile(fileSrc, options);

				// 压缩图片
				options.inSampleSize = Math.max(1, (int) Math.ceil(Math.max(
						(double) options.outWidth / 1024f,
						(double) options.outHeight / 1024f)));
				options.inJustDecodeBounds = false;

				mImage = BitmapFactory.decodeFile(fileSrc, options);

				// 若mImageBitmap为空则图片信息不能正常获取
				if(null == mImage) {
					showTip("图片信息无法正常获取！");
					return;
				}

				// 部分手机会对图片做旋转，这里检测旋转角度
				int degree = FaceUtil.readPictureDegree(fileSrc);
				if (degree != 0) {
					// 把图片旋转为正的方向
					mImage = FaceUtil.rotateImage(degree, mImage);
				}

				ByteArrayOutputStream baos = new ByteArrayOutputStream();

				//可根据流量及网络状况对图片进行压缩
				mImage.compress(Bitmap.CompressFormat.JPEG, 80, baos);

				mImageData = baos.toByteArray();

				int ret = ErrorCode.SUCCESS;
				((ImageView) findViewById(R.id.online_img)).setImageBitmap(mImage);

				mFaceRequest.setParameter(SpeechConstant.AUTH_ID, "2");
				mFaceRequest.setParameter(SpeechConstant.WFR_SST, "verify");
				ret = mFaceRequest.sendRequest(mImageData, mRequestListener);

//				Utility.getScreenshotFileObservable(VideoDemo.this, bitmap)
//						.observeOn(AndroidSchedulers.mainThread())
//						.subscribe(new Action1<File>() {
//							@Override
//							public void call(File file) {
//								showTip("图片地址"+file.getAbsolutePath());
//								finish();
//							}
//						});
			}
		});

	}




	private void showImage(Bitmap bitmap){

		Bitmap bmp = bitmap;
		String fileSrc = null;

		// 若返回数据不为null，保存至本地，防止裁剪时未能正常保存
		if(null != bmp){
			showTip("保存了bitmap 图片");
			FaceUtil.saveBitmapToFile(VideoDemo.this, bmp);
		}
		// 获取图片保存路径
		fileSrc = FaceUtil.getImagePath(VideoDemo.this);
		updateGallery(fileSrc);
		showTip("图片路径是："+fileSrc);
		Log.e(TAG, "图片路径是："+fileSrc );
		// 获取图片的宽和高
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;

		mImage = BitmapFactory.decodeFile(fileSrc, options);

		// 压缩图片
		options.inSampleSize = Math.max(1, (int) Math.ceil(Math.max(
				(double) options.outWidth / 1024f,
				(double) options.outHeight / 1024f)));
		options.inJustDecodeBounds = false;

		mImage = BitmapFactory.decodeFile(fileSrc, options);

		// 若mImageBitmap为空则图片信息不能正常获取
		if(null == mImage) {
			showTip("图片信息无法正常获取！");
			return;
		}

		// 部分手机会对图片做旋转，这里检测旋转角度
		int degree = FaceUtil.readPictureDegree(fileSrc);
		if (degree != 0) {
			// 把图片旋转为正的方向
			mImage = FaceUtil.rotateImage(degree, mImage);
		}

		ByteArrayOutputStream baos = new ByteArrayOutputStream();

		//可根据流量及网络状况对图片进行压缩
		mImage.compress(Bitmap.CompressFormat.JPEG, 80, baos);

		mImageData = baos.toByteArray();

		int ret = ErrorCode.SUCCESS;
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				((ImageView) findViewById(R.id.online_img2)).setImageBitmap(mImage);
			}
		});

//		mFaceRequest.setParameter(SpeechConstant.AUTH_ID, "2");
//		mFaceRequest.setParameter(SpeechConstant.WFR_SST, "verify");
//		ret = mFaceRequest.sendRequest(mImageData, mRequestListener);

	}

	private void verify(byte[] mImageData){
		int ret = ErrorCode.SUCCESS;
		mFaceRequest.setParameter(SpeechConstant.AUTH_ID, "3");
		mFaceRequest.setParameter(SpeechConstant.WFR_SST, "verify");
		ret = mFaceRequest.sendRequest(mImageData, mRequestListener);
	}

	private Bitmap mImage = null;
	private byte[] mImageData = null;
	// FaceRequest对象，集成了人脸识别的各种功能
	private FaceRequest mFaceRequest;
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		String fileSrc = null;
		if (requestCode == FaceUtil.REQUEST_CROP_IMAGE) {
			// 获取返回数据
			Bitmap bmp = data.getParcelableExtra("data");
			// 若返回数据不为null，保存至本地，防止裁剪时未能正常保存
			if(null != bmp){
				FaceUtil.saveBitmapToFile(VideoDemo.this, bmp);
			}
			// 获取图片保存路径
			fileSrc = FaceUtil.getImagePath(VideoDemo.this);
			// 获取图片的宽和高
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;

			mImage = BitmapFactory.decodeFile(fileSrc, options);

			// 压缩图片
			options.inSampleSize = Math.max(1, (int) Math.ceil(Math.max(
					(double) options.outWidth / 1024f,
					(double) options.outHeight / 1024f)));
			options.inJustDecodeBounds = false;

			mImage = BitmapFactory.decodeFile(fileSrc, options);

			// 若mImageBitmap为空则图片信息不能正常获取
			if(null == mImage) {
				showTip("图片信息无法正常获取！");
				return;
			}

			// 部分手机会对图片做旋转，这里检测旋转角度
			int degree = FaceUtil.readPictureDegree(fileSrc);
			if (degree != 0) {
				// 把图片旋转为正的方向
				mImage = FaceUtil.rotateImage(degree, mImage);
			}

			ByteArrayOutputStream baos = new ByteArrayOutputStream();

			//可根据流量及网络状况对图片进行压缩
			mImage.compress(Bitmap.CompressFormat.JPEG, 80, baos);

			mImageData = baos.toByteArray();

			int ret = ErrorCode.SUCCESS;

			mFaceRequest.setParameter(SpeechConstant.AUTH_ID, "2");
			mFaceRequest.setParameter(SpeechConstant.WFR_SST, "verify");
			ret = mFaceRequest.sendRequest(mImageData, mRequestListener);

		}

	}
	private RequestListener mRequestListener = new RequestListener() {

		@Override
		public void onEvent(int eventType, Bundle params) {
		}

		@Override
		public void onBufferReceived(byte[] buffer) {

			try {
				String result = new String(buffer, "utf-8");
				Log.e("FaceDemo", result);

				JSONObject object = new JSONObject(result);
				String type = object.optString("sst");
				if ("reg".equals(type)) {
					//注册
//					register(object);
				} else if ("verify".equals(type)) {
					//验证
					verify(object);
					//关闭
					finish();
				} else if ("detect".equals(type)) {
					//检测
//					detect(object);
				} else if ("align".equals(type)) {
					//聚焦
//					align(object);
				}
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (JSONException e) {
				// TODO: handle exception
			}
		}



		@Override
		public void onCompleted(SpeechError error) {


			if (error != null) {
				switch (error.getErrorCode()) {
					case ErrorCode.MSP_ERROR_ALREADY_EXIST:
						showTip("authid已经被注册，请更换后再试");
						break;
					default:
						showTip(error.getPlainDescription(true));
						break;
				}
			}
		}
	};

	private void verify(JSONObject obj) throws JSONException {
		int ret = obj.getInt("ret");
		if (ret != 0) {
			showTip("验证失败");
			return;
		}
		if ("success".equals(obj.get("rst"))) {
			if (obj.getBoolean("verf")) {
				showTip("通过验证，欢迎回来！");
			} else {
				showTip("验证不通过");
			}
		} else {
			showTip("验证失败");
		}
	}


	//更新媒体库
	private void updateGallery(String filename) {
		MediaScannerConnection.scanFile(this, new String[] {filename}, null,
				new MediaScannerConnection.OnScanCompletedListener() {

					@Override
					public void onScanCompleted(String path, Uri uri) {

					}
				});
	}

	@Override
	protected void onPause() {
		super.onPause();
		closeCamera();
		if (null != mAcc) {
			mAcc.stop();
		}
		mStopTrack = true;
	}
	
	@Override
	protected void onDestroy() {
		super.onDestroy();
		if( null != mFaceDetector ){
			// 销毁对象
			mFaceDetector.destroy();
		}
	}
	
	private void showTip(final String str) {
		mToast.setText(str);
		mToast.show();
	}

}
