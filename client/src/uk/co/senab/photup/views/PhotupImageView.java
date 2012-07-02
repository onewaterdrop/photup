package uk.co.senab.photup.views;

import java.lang.ref.WeakReference;

import com.lightbox.android.photoprocessing.PhotoProcessing;

import uk.co.senab.bitmapcache.BitmapLruCache;
import uk.co.senab.bitmapcache.CacheableBitmapWrapper;
import uk.co.senab.bitmapcache.CacheableImageView;
import uk.co.senab.photup.PhotupApplication;
import uk.co.senab.photup.model.Filter;
import uk.co.senab.photup.model.PhotoUpload;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.util.AttributeSet;

public class PhotupImageView extends CacheableImageView {

	private static class PhotoTask extends AsyncTask<PhotoUpload, Void, CacheableBitmapWrapper> {

		private final WeakReference<PhotupImageView> mImageView;
		private final BitmapLruCache mCache;
		private final boolean mFetchFullSize;

		public PhotoTask(PhotupImageView imageView, BitmapLruCache cache, boolean fullSize) {
			mImageView = new WeakReference<PhotupImageView>(imageView);
			mCache = cache;
			mFetchFullSize = fullSize;
		}

		@Override
		protected void onPreExecute() {
			super.onPreExecute();

			PhotupImageView iv = mImageView.get();
			if (null != iv) {
				iv.setImageDrawable(null);
			}
		}

		@Override
		protected CacheableBitmapWrapper doInBackground(PhotoUpload... params) {
			final PhotoUpload upload = params[0];
			CacheableBitmapWrapper wrapper = null;

			PhotupImageView iv = mImageView.get();
			if (null != iv) {
				Bitmap bitmap = mFetchFullSize ? upload.getOriginal(iv.getContext()) : upload.getThumbnail(iv
						.getContext());

				if (null != bitmap) {
					final String key = mFetchFullSize ? upload.getOriginalKey() : upload.getThumbnailKey();
					wrapper = new CacheableBitmapWrapper(key, bitmap);
				}
			}

			return wrapper;
		}

		@Override
		protected void onPostExecute(CacheableBitmapWrapper result) {
			super.onPostExecute(result);

			PhotupImageView iv = mImageView.get();
			if (null != iv) {
				iv.setImageCachedBitmap(result);
			}

			mCache.put(result);
		}
	}

	static final class FilterRunnable implements Runnable {

		private final Filter mFilter;
		private final PhotupImageView mImageView;
		private final PhotoUpload mUpload;

		public FilterRunnable(PhotupImageView imageView, PhotoUpload upload) {
			mImageView = imageView;
			mUpload = upload;
			mFilter = upload.getFilterUsed();
		}

		public void run() {
			Bitmap bitmap = mUpload.getOriginal(mImageView.getContext());
			final Bitmap filteredBitmap = PhotoProcessing.filterPhoto(bitmap, mFilter.getId());
			bitmap.recycle();

			mImageView.post(new Runnable() {
				public void run() {
					mImageView.setImageBitmap(filteredBitmap);
				}
			});
		}
	};

	private PhotoTask mCurrentTask;

	public PhotupImageView(Context context) {
		super(context);
	}

	public PhotupImageView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public void requestThumbnail(final PhotoUpload upload) {
		requestImage(upload, false);
	}

	public void requestFullSize(final PhotoUpload upload) {
		if (upload.hasFilter()) {
			requestFilter(upload);
		} else {
			requestImage(upload, true);
		}
	}

	private void requestFilter(final PhotoUpload upload) {
		PhotupApplication app = PhotupApplication.getApplication(getContext());
		app.getExecutorService().submit(new FilterRunnable(this, upload));
	}

	private void requestImage(final PhotoUpload upload, final boolean fullSize) {
		if (null != mCurrentTask) {
			mCurrentTask.cancel(false);
		}

		final String key = fullSize ? upload.getOriginalKey() : upload.getThumbnailKey();
		BitmapLruCache cache = PhotupApplication.getApplication(getContext()).getImageCache();
		final CacheableBitmapWrapper cached = cache.get(key);

		if (null != cached && cached.hasValidBitmap()) {
			setImageCachedBitmap(cached);
		} else {
			// Means we have an object with an invalid bitmap so remove it
			if (null != cached) {
				cache.remove(key);
			}

			mCurrentTask = new PhotoTask(this, cache, fullSize);

			// FIXME Need to fix this for less than v11
			if (VERSION.SDK_INT >= VERSION_CODES.HONEYCOMB) {
				PhotupApplication app = PhotupApplication.getApplication(getContext());
				mCurrentTask.executeOnExecutor(app.getExecutorService(), upload);
			} else {
				mCurrentTask.execute(upload);
			}
		}
	}

	public void recycleBitmap() {
		Bitmap currentBitmap = getCurrentBitmap();
		if (null != currentBitmap) {
			setImageDrawable(null);
			currentBitmap.recycle();
		}
	}

	public Bitmap getCurrentBitmap() {
		Drawable d = getDrawable();
		if (d instanceof BitmapDrawable) {
			return ((BitmapDrawable) d).getBitmap();
		}

		return null;
	}

}
