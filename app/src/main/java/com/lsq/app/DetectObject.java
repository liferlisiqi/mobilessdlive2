package com.lsq.app;

import android.graphics.RectF;

public class DetectObject {
    public String ID;
    public String title;
    public Float confidence;
    public RectF location;

    public DetectObject(String ID, String title, Float confidence, RectF location) {
        this.ID = ID;
        this.title = title;
        this.confidence = confidence;
        this.location = location;
            }
}
