// Copyright (c) Philipp Wagner. All rights reserved.
// Licensed under the MIT license. See LICENSE file in the project root for full license information.

package com.lsq.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * This class is a simple View to display the faces.
 */
public class OverlayView extends View {

    private Paint mPaint;
    private Paint mTextPaint;
    private int mDisplayOrientation;
    private int mOrientation;
    private List<DetectObject> mResults = new ArrayList<>();
    private long mTime;

    public OverlayView(Context context) {
        super(context);
        initialize();
    }

    private void initialize() {
        // We want a green box around the face:
        mPaint = new Paint();
        mPaint.setColor(Color.GREEN);
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(5.0f);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
        mPaint.setStrokeMiter(100);

        mTextPaint = new Paint();
        //mTextPaint.setAntiAlias(true);
        //mTextPaint.setDither(true);
        mTextPaint.setTextSize(30);
        mTextPaint.setColor(Color.GREEN);
        mTextPaint.setStyle(Paint.Style.FILL);
    }

    public void drawResults(List<DetectObject> results, long time) {
        mResults = results;
        mTime = time;
    }

    public void setOrientation(int orientation) {
        mOrientation = orientation;
    }

    public void setDisplayOrientation(int displayOrientation) {
        mDisplayOrientation = displayOrientation;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int canvasWidth = canvas.getWidth();
        int canvasHeight = canvas.getHeight();

        canvas.drawText(mResults.size()+"",0, 30, mTextPaint);

        if (!mResults.isEmpty()) {
            Matrix matrix = new Matrix();
            Util.prepareMatrix(matrix, false, mDisplayOrientation, getWidth(), getHeight());
            canvas.save();
            matrix.postRotate(mOrientation);
            RectF rectF = new RectF();

            for(int i=0; i<mResults.size(); i++){
                rectF.set(mResults.get(i).location.top * canvasWidth,
                        mResults.get(i).location.left * canvasHeight,
                        mResults.get(i).location.bottom * canvasWidth,
                        mResults.get(i).location.right * canvasHeight);
                //matrix.mapRect(rectF);
                canvas.drawRect(rectF, mPaint);
                canvas.drawText(mResults.get(i).title + ":" + mResults.get(i).confidence,rectF.left, rectF.top+20, mTextPaint);
            }
            mResults.clear();
            canvas.restore();
        }
    }
}