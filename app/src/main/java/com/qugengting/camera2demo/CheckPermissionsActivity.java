/**
 * 
 */
package com.qugengting.camera2demo;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.appcompat.app.AppCompatActivity;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class CheckPermissionsActivity extends AppCompatActivity {
	/**
	 * 需要进行检测的权限数组
	 */
	protected String[] needPermissions = {
			Manifest.permission.WRITE_EXTERNAL_STORAGE,
			Manifest.permission.READ_EXTERNAL_STORAGE,
			Manifest.permission.CAMERA
	};

	private static final int PERMISSON_REQUESTCODE = 0;
	private int type;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
	}

	/**
	 * 判断是否需要检测，防止不停的弹框
	 */
	private boolean isNeedCheck = true;
	
	@Override
	protected void onResume() {
		super.onResume();
		if (Build.VERSION.SDK_INT >= 23
				&& getApplicationInfo().targetSdkVersion >= 23) {
			if (isNeedCheck) {
				checkPermissions(0, needPermissions);
			}
		}
	}
	
	protected void checkPermissions(int type, String... needPermissions) {
		this.type = type;
		try {
			if (Build.VERSION.SDK_INT >= 23
					&& getApplicationInfo().targetSdkVersion >= 23) {
				List<String> needRequestPermissonList = findDeniedPermissions(needPermissions);
				if (null != needRequestPermissonList && needRequestPermissonList.size() > 0) {
					String[] array = needRequestPermissonList.toArray(new String[needRequestPermissonList.size()]);
					Method method = getClass().getMethod("requestPermissions", new Class[]{String[].class,
							int.class});

					method.invoke(this, array, PERMISSON_REQUESTCODE);
				} else {
					if (type == 1) {
						getPermission();
					}
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

	/**
	 * 获取权限集中需要申请权限的列表
	 * 
	 * @param permissions
	 * @return
	 * @since 2.5.0
	 *
	 */
	private List<String> findDeniedPermissions(String[] permissions) {
		List<String> needRequestPermissonList = new ArrayList<String>();
		if (Build.VERSION.SDK_INT >= 23
				&& getApplicationInfo().targetSdkVersion >= 23){
			try {
				for (String perm : permissions) {
					Method checkSelfMethod = getClass().getMethod("checkSelfPermission", String.class);
					Method shouldShowRequestPermissionRationaleMethod = getClass().getMethod("shouldShowRequestPermissionRationale",
							String.class);
					if ((Integer)checkSelfMethod.invoke(this, perm) != PackageManager.PERMISSION_GRANTED
							|| (Boolean)shouldShowRequestPermissionRationaleMethod.invoke(this, perm)) {
						needRequestPermissonList.add(perm);
					}
				}
			} catch (Throwable e) {

			}
		}
		return needRequestPermissonList;
	}

	/**
	 * 检测是否所有的权限都已经授权
	 * @param grantResults
	 * @return
	 * @since 2.5.0
	 *
	 */
	private boolean verifyPermissions(int[] grantResults) {
		for (int result : grantResults) {
			if (result != PackageManager.PERMISSION_GRANTED) {
				return false;
			}
		}
		return true;
	}

	@TargetApi(23)
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] paramArrayOfInt) {
		if (requestCode == PERMISSON_REQUESTCODE) {
			if (!verifyPermissions(paramArrayOfInt)) {
				showMissingPermissionDialog();
				isNeedCheck = false;
			} else {
				if (type == 1) {
					getPermission();
				}
			}
		}
	}

	protected void getPermission() {

	}

	@Override
	public void onBackPressed() {
		moveTaskToBack(true);
	}

	/**
	 * 显示提示信息
	 * 
	 * @since 2.5.0
	 *
	 */
	private void showMissingPermissionDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.notifyTitle);
		builder.setMessage(R.string.notifyMsg);

		// 拒绝, 退出应用
		builder.setNegativeButton(R.string.cancel,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						finish();
					}
				});

		builder.setPositiveButton(R.string.setting,
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						startAppSettings();
					}
				});

		builder.setCancelable(false);

		builder.show();
	}

	/**
	 *  启动应用的设置
	 * 
	 * @since 2.5.0
	 *
	 */
	private void startAppSettings() {
		Intent intent = new Intent(
				Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
		intent.setData(Uri.parse("package:" + getPackageName()));
		startActivity(intent);
	}
	
}
