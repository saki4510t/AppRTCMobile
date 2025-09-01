package org.appspot.apprtc;

import android.Manifest;
import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.serenegiant.apprtcmobile.R;
import com.serenegiant.dialog.RationalDialogV4;
import com.serenegiant.system.BuildCheck;
import com.serenegiant.system.PermissionUtils;

import java.util.Arrays;
import java.util.List;

public abstract class BaseActivity extends AppCompatActivity
	implements RationalDialogV4.DialogResultListener {

	private static final boolean DEBUG = true;	// set false on production
	private static final String TAG = BaseActivity.class.getSimpleName();

	private String[] mMissingPermissions;
	private PermissionUtils mPermissions;

	@Override
	protected void onCreate(@Nullable final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		try {
			final List<String> missingPermissions = PermissionUtils.missingPermissions(this);
			mMissingPermissions = missingPermissions.toArray(new String[0]);
		} catch (PackageManager.NameNotFoundException e) {
			Log.w(TAG, e);
			mMissingPermissions = new String[0];
		}
		mPermissions = new PermissionUtils(this, mPermissionCallback)
			.prepare(this, mMissingPermissions);
	}

	/**
	 * RationalDialogV4からのコールバックリスナー
	 * @param dialog
	 * @param permissions
	 * @param result
	 */
	@SuppressLint("NewApi")
	@Override
	public void onDialogResult(@NonNull final RationalDialogV4 dialog,
		@NonNull final String[] permissions, final boolean result) {

		if (result) {
			// メッセージダイアログでOKを押された時はパーミッション要求する
			if (BuildCheck.isMarshmallow()) {
				mPermissions.requestPermission(permissions, false);
				return;
			}
		}
		// メッセージダイアログでキャンセルされた時とAndroid6でない時は自前でチェックして#checkPermissionResultを呼び出す
		for (final String permission: permissions) {
			checkPermissionResult(permission,
				PermissionUtils.hasPermission(this, permission));
		}
	}

	/**
	 * パーミッション要求の結果をチェック
	 * ここではパーミッションを取得できなかった時にToastでメッセージ表示するだけ
	 * @param permission
	 * @param result
	 */
	private void checkPermissionResult(
		final String permission, final boolean result) {

		// パーミッションがないときにはメッセージを表示する
		if (!result && (permission != null)) {
			if (android.Manifest.permission.RECORD_AUDIO.equals(permission)) {
				Toast.makeText(getApplicationContext(),
					R.string.permission_audio, Toast.LENGTH_SHORT).show();
			}
			if (android.Manifest.permission.WRITE_EXTERNAL_STORAGE.equals(permission)) {
				Toast.makeText(getApplicationContext(),
					R.string.permission_ext_storage, Toast.LENGTH_SHORT).show();
			}
			if (android.Manifest.permission.CAMERA.equals(permission)) {
				Toast.makeText(getApplicationContext(),
					R.string.permission_camera, Toast.LENGTH_SHORT).show();
			}
			if (android.Manifest.permission.INTERNET.equals(permission)) {
				Toast.makeText(getApplicationContext(),
					R.string.permission_network, Toast.LENGTH_SHORT).show();
			}
			if (Manifest.permission.BLUETOOTH.equals(permission)
				|| Manifest.permission.BLUETOOTH_CONNECT.equals(permission)) {
				Toast.makeText(getApplicationContext(),
					R.string.permission_bluetooth, Toast.LENGTH_SHORT).show();
			}
			if (Manifest.permission.ACCESS_COARSE_LOCATION.equals(permission)
				|| Manifest.permission.ACCESS_FINE_LOCATION.equals(permission)) {
				Toast.makeText(getApplicationContext(),
					R.string.permission_location, Toast.LENGTH_SHORT).show();
			}
		}
	}

	/**
	 * アプリの実行に必要なパーミッションを一括して要求する
	 * @return
	 */
	protected boolean checkPermissions() {
		if (DEBUG) Log.v(TAG, "checkPermissions:missingPermissions=" + Arrays.toString(mMissingPermissions));
		return mPermissions.requestPermission(mMissingPermissions, false);
	}

	private final PermissionUtils.PermissionCallback mPermissionCallback
		= new PermissionUtils.PermissionCallback() {
		@Override
		public void onPermissionShowRational(@NonNull final String permission) {
			if (DEBUG) Log.v(TAG, "onPermissionShowRational:" + permission);
			final RationalDialogV4 dialog
				= RationalDialogV4.showDialog(BaseActivity.this, permission);
			if (dialog == null) {
				if (DEBUG) Log.v(TAG, "onPermissionShowRational:" +
					"デフォルトのダイアログ表示ができなかったので自前で表示しないといけない," + permission);
				// FIXME 未実装
			}
		}

		@Override
		public void onPermissionShowRational(@NonNull final String[] permissions) {
			if (DEBUG) Log.v(TAG, "onPermissionShowRational:" + Arrays.toString(permissions));
			// FIXME 未実装
		}

		@Override
		public void onPermissionDenied(@NonNull final String permission) {
			if (DEBUG) Log.v(TAG, "onPermissionDenied:" + permission);
			// ユーザーがパーミッション要求を拒否したときの処理
			// FIXME 未実装
		}

		@Override
		public void onPermission(@NonNull final String permission) {
			if (DEBUG) Log.v(TAG, "onPermission:" + permission);
			// ユーザーがパーミッション要求を承認したときの処理
		}

		@Override
		public void onPermissionNeverAskAgain(@NonNull final String permission) {
			if (DEBUG) Log.v(TAG, "onPermissionNeverAskAgain:" + permission);
			// 端末のアプリ設定画面を開くためのボタンを配置した画面へ遷移させる
			// FIXME 未実装
		}

		@Override
		public void onPermissionNeverAskAgain(@NonNull final String[] permissions) {
			if (DEBUG) Log.v(TAG, "onPermissionNeverAskAgain:" + Arrays.toString(permissions));
			// 端末のアプリ設定画面を開くためのボタンを配置した画面へ遷移させる
			// FIXME 未実装
		}
	};
}
