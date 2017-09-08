package com.example.administrator.facedome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.SurfaceView;

public class Element {
    private float mX;
    private float mY;

    private Bitmap mBitmap;

    public Element(SurfaceView mSurfaceView ) {
        mBitmap =  Bitmap.createBitmap(mSurfaceView.getWidth(), mSurfaceView.getHeight(), Bitmap.Config.ARGB_8888);
        mX =mBitmap.getWidth();
        mY =mBitmap.getHeight();
    }

    public void doDraw(Canvas canvas) {
        canvas.drawBitmap(mBitmap, mX, mY, null);
    }
}
