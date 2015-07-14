package cn.feng.skin.manager.loader;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Log;
import cn.feng.skin.manager.config.SkinConfig;
import cn.feng.skin.manager.listener.ILoaderListener;
import cn.feng.skin.manager.listener.ISkinLoader;
import cn.feng.skin.manager.listener.ISkinUpdate;

/**
 * Skin Manager Instance
 * 
 * <ul>
 * <strong>get single runtime instance</strong>
 * <li> {@link #getInstance(Context)} </li>
 * </ul>
 * <ul>
 * <strong>attach a listener (Activity or fragment) to SkinManager</strong>
 * <li> {@link #onAttach(ISkinUpdate observer)} </li>
 * </ul>
 * <ul>
 * <strong>detach a listener (Activity or fragment) to SkinManager</strong>
 * <li> {@link #detach(ISkinUpdate observer)} </li>
 * </ul>
 * <ul>
 * <strong>load latest theme </strong>
 * <li> {@link #load()} </li>
 * <li> {@link #load(ILoaderListener callback)} </li>
 * </ul>
 * <ul>
 * <strong>load new theme with the giving skinPackagePath</strong>
 * <li> {@link #load(String skinPackagePath,ILoaderListener callback)} </li>
 * </ul>
 * 
 * 
 * @author fengjun
 */
public class SkinManager implements ISkinLoader{
	
	private static Object    			synchronizedLock    	= new Object();
	private static SkinManager 			instance;
	
	private List<ISkinUpdate>   skinObservers;
	private Context 					context;
	private String 						skinPackageName;
	private Resources 					mResources;
	private String 						skinPath;
	private boolean						isDefaultSkin			= false;
	
	public String getSkinPath() {
		return skinPath;
	}

	public static SkinManager getInstance(Context mContext) {
		if (instance == null) {
			synchronized (synchronizedLock) {
				if (instance == null){
					instance = new SkinManager(mContext.getApplicationContext());
				}
			}
		}
		return instance;
	}
	
	public String getSkinPackageName() {
		return skinPackageName;
	}
	
	public Resources getResources(){
		return mResources;
	}
	
	private SkinManager(Context mContext) {
		this.context = mContext;
	}
	
	public void restoreDefaultTheme(){
		SkinConfig.saveSkinPath(context, SkinConfig.DEFALT_SKIN);
		isDefaultSkin = true;
		mResources = context.getResources();
		notifySkinUpdate();
	}

	public void load(){
		String skin = SkinConfig.getCustomSkinPath(context);
		load(skin, null);
	}
	
	public void load(ILoaderListener callback){
		String skin = SkinConfig.getCustomSkinPath(context);
		if(SkinConfig.isDefaultSkin(context)){ return; }
		load(skin, callback);
	}
	
	/**
	 * Load resources from apk in asyc task
	 * @param skinPackagePath path of skin apk
	 * @param callback callback to notify user
	 */
	public void load(String skinPackagePath, final ILoaderListener callback) {
		
		new AsyncTask<String, Void, Resources>() {

			protected void onPreExecute() {
				if (callback != null) {
					callback.onStart();
				}
			};

			@Override
			protected Resources doInBackground(String... params) {
				try {
					if (params.length == 1) {
						String skinPkgPath = params[0];
						
						File file = new File(skinPkgPath); 
						if(file == null || !file.exists()){
							return null;
						}
						
						PackageManager mPm = context.getPackageManager();
						PackageInfo mInfo = mPm.getPackageArchiveInfo(skinPkgPath, PackageManager.GET_ACTIVITIES);
						skinPackageName = mInfo.packageName;

						AssetManager assetManager = AssetManager.class.newInstance();
						Method addAssetPath = assetManager.getClass().getMethod("addAssetPath", String.class);
						addAssetPath.invoke(assetManager, skinPkgPath);

						Resources superRes = context.getResources();
						Resources skinResource = new Resources(assetManager,superRes.getDisplayMetrics(),superRes.getConfiguration());
						
						SkinConfig.saveSkinPath(context, skinPkgPath);
						
						skinPath = skinPkgPath;
						isDefaultSkin = false;
						return skinResource;
					}
					return null;
				} catch (Exception e) {
					e.printStackTrace();
					return null;
				}
			};

			protected void onPostExecute(Resources result) {
				mResources = result;

				if (mResources != null) {
					if (callback != null) callback.onSuccess();
					notifySkinUpdate();
				}else{
					isDefaultSkin = true;
					if (callback != null) callback.onFailed();
				}
			};

		}.execute(skinPackagePath);
	}
	
	@Override
	public void attach(ISkinUpdate observer) {
		if(skinObservers == null){
			skinObservers = new ArrayList<ISkinUpdate>();
		}
		if(!skinObservers.contains(skinObservers)){
			skinObservers.add(observer);
		}
	}

	@Override
	public void detach(ISkinUpdate observer) {
		if(skinObservers == null) return;
		if(skinObservers.contains(observer)){
			skinObservers.remove(observer);
		}
	}

	@Override
	public void notifySkinUpdate() {
		if(skinObservers == null) return;
		for(ISkinUpdate observer : skinObservers){
			observer.onThemeUpdate();
		}
	}
	
	public int getColor(int resId){
		int originColor = context.getResources().getColor(resId);
		if(mResources == null || isDefaultSkin){
			return originColor;
		}
		
		String resName = context.getResources().getResourceEntryName(resId);
		
		int trueResId = mResources.getIdentifier(resName, "color", skinPackageName);
		int trueColor = 0;
		
		try{
			trueColor = mResources.getColor(trueResId);
		}catch(NotFoundException e){
			e.printStackTrace();
			trueColor = originColor;
		}
		
		return trueColor;
	}
	
	public Drawable getDrawable(int resId){
		Drawable originDrawable = context.getResources().getDrawable(resId);
		if(mResources == null || isDefaultSkin){
			return originDrawable;
		}
		String resName = context.getResources().getResourceEntryName(resId);
		
		int trueResId = mResources.getIdentifier(resName, "drawable", skinPackageName);
		
		Drawable trueDrawable = null;
		try{
			trueDrawable = mResources.getDrawable(trueResId);
		}catch(NotFoundException e){
			e.printStackTrace();
			trueDrawable = originDrawable;
		}
		
		return trueDrawable;
	}
}