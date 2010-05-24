/*
 * Copyright (C) 2010 William Robert Beene
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package bwr.edgefinder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.hardware.Camera.Size;
import android.net.Uri;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.Media;
import android.view.View;

public class EdgeView extends View implements PreviewCallback {
	
	static {
		System.loadLibrary("edgefinder");
	}
	
	private static final int THRESHOLD = 30;
	
	private byte[] cameraPreview = null;
	private boolean cameraPreviewValid = false;
	private final Lock cameraPreviewLock = new ReentrantLock();
	private final Paint edgePaint = new Paint();
	private int width, height;
	private boolean captureNextFrame = false;
	
	public EdgeView(Context context) {
		super(context);
		edgePaint.setColor(Color.WHITE);
	}

	/**
	 * Native method for calculating the edge image.
	 * 
	 * @param source - source image
	 * @param width - width of the source image
	 * @param height - height of the source image
	 * @param canvas - canvas to draw on
	 * @param paint - paint used to draw edges
	 */
	private native void findEdges(int threshold, byte[] source, int width, int height, Canvas canvas, Paint paint);
	
	private void saveCapturedFrame(Bitmap image) {
		ContentValues metaData = new ContentValues(3);
		metaData.put(Media.TITLE, "Edge Finder Capture");
		metaData.put(Media.DESCRIPTION, "Edge Finder Capture");
		metaData.put(Media.MIME_TYPE, "image/png");

		Uri uri = getContext().getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, metaData);
		OutputStream os = null;
		
		try {
			os = getContext().getContentResolver().openOutputStream(uri);
			image.compress(Bitmap.CompressFormat.PNG, 100, os);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		if(os != null) {
			try {
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void captureNextFrame() {
		captureNextFrame = true;
	}
	
	@Override
	protected void onDraw(Canvas canvas) {
		canvas.drawColor(Color.BLACK);

		if (cameraPreviewLock.tryLock()) {
			try {
				if (cameraPreview != null && cameraPreviewValid) {
					if(captureNextFrame) {
						Bitmap edgeImage = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
						Canvas edgeImageCanvas = new Canvas(edgeImage);
												
						findEdges(THRESHOLD, cameraPreview, width, height, edgeImageCanvas, edgePaint);
						
						canvas.drawBitmap(edgeImage, 0f, 0f, null);
						
						saveCapturedFrame(edgeImage);
						captureNextFrame = false;
					} else {
						findEdges(THRESHOLD, cameraPreview, width, height, canvas, edgePaint);
					}
					cameraPreviewValid = false;
				}
			} finally {
				cameraPreviewLock.unlock();
			}
		}
	}
	
	public void onPreviewFrame(byte[] data, Camera camera) {
		if (cameraPreviewLock.tryLock()) {
			try {
				if(!cameraPreviewValid) {
					cameraPreviewValid = true;

					final Size s = camera.getParameters().getPreviewSize();
					
					width = s.width;
					height = s.height;
					final int length = width * height;
					
					if(cameraPreview == null || cameraPreview.length != length) {
						cameraPreview = new byte[length];
					}
					
					System.arraycopy(data, 0, cameraPreview, 0, length);
				}
			} finally {
				cameraPreviewLock.unlock();
				postInvalidate();
			}
		}
	}

}
