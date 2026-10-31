package com.kaiserdev.watchthermometer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class SparklineView extends View {

    private final List<Float> dataPoints = new ArrayList<>();
    private final int maxPoints = 40;

    private Paint linePaint;
    private Paint fillPaint;
    private Paint dotPaint;
    private Paint dotGlowPaint;
    private Paint gridPaint;

    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    public SparklineView(Context context) {
        super(context);
        init();
    }

    public SparklineView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SparklineView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#FF8C00"));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dpToPx(2.2f));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);

        fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);

        dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotPaint.setColor(Color.WHITE);
        dotPaint.setStyle(Paint.Style.FILL);

        dotGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        dotGlowPaint.setColor(Color.parseColor("#77FF8C00"));
        dotGlowPaint.setStyle(Paint.Style.FILL);

        gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        gridPaint.setColor(Color.parseColor("#22FFFFFF"));
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setStrokeWidth(dpToPx(1f));
    }

    public synchronized void addPoint(float value) {
        if (value <= 10.0f || value >= 55.0f) return;
        dataPoints.add(value);
        if (dataPoints.size() > maxPoints) {
            dataPoints.remove(0);
        }
        postInvalidate();
    }

    public synchronized float getMin() {
        if (dataPoints.isEmpty()) return 0f;
        float min = Float.MAX_VALUE;
        for (float v : dataPoints) {
            if (v < min) min = v;
        }
        return min;
    }

    public synchronized float getMax() {
        if (dataPoints.isEmpty()) return 0f;
        float max = -Float.MAX_VALUE;
        for (float v : dataPoints) {
            if (v > max) max = v;
        }
        return max;
    }

    public synchronized float getAvg() {
        if (dataPoints.isEmpty()) return 0f;
        float sum = 0f;
        for (float v : dataPoints) {
            sum += v;
        }
        return sum / dataPoints.size();
    }

    public synchronized int getCount() {
        return dataPoints.size();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padTop = dpToPx(6);
        float padBottom = dpToPx(6);
        float padLeft = dpToPx(8);
        float padRight = dpToPx(8);

        float drawW = w - padLeft - padRight;
        float drawH = h - padTop - padBottom;

        // Draw middle guideline
        canvas.drawLine(padLeft, padTop + drawH * 0.5f, padLeft + drawW, padTop + drawH * 0.5f, gridPaint);

        List<Float> points;
        synchronized (this) {
            if (dataPoints.size() < 2) {
                // If only 1 point, draw single dot in center
                if (dataPoints.size() == 1) {
                    float cx = padLeft + drawW * 0.5f;
                    float cy = padTop + drawH * 0.5f;
                    canvas.drawCircle(cx, cy, dpToPx(5), dotGlowPaint);
                    canvas.drawCircle(cx, cy, dpToPx(2.5f), dotPaint);
                }
                return;
            }
            points = new ArrayList<>(dataPoints);
        }

        float minVal = Float.MAX_VALUE;
        float maxVal = -Float.MAX_VALUE;
        for (float p : points) {
            if (p < minVal) minVal = p;
            if (p > maxVal) maxVal = p;
        }

        float range = maxVal - minVal;
        if (range < 0.8f) {
            float mid = (maxVal + minVal) / 2.0f;
            minVal = mid - 0.4f;
            maxVal = mid + 0.4f;
            range = 0.8f;
        }

        linePath.reset();
        fillPath.reset();

        float stepX = drawW / (points.size() - 1);
        float lastX = padLeft;
        float lastY = padTop + drawH * 0.5f;

        for (int i = 0; i < points.size(); i++) {
            float val = points.get(i);
            float norm = (val - minVal) / range;
            float x = padLeft + i * stepX;
            float y = padTop + (1.0f - norm) * drawH;

            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, padTop + drawH);
                fillPath.lineTo(x, y);
            } else {
                float prevX = padLeft + (i - 1) * stepX;
                float prevY = padTop + (1.0f - (points.get(i - 1) - minVal) / range) * drawH;
                float cx = (prevX + x) / 2.0f;
                linePath.cubicTo(cx, prevY, cx, y, x, y);
                fillPath.cubicTo(cx, prevY, cx, y, x, y);
            }

            if (i == points.size() - 1) {
                lastX = x;
                lastY = y;
            }
        }

        fillPath.lineTo(lastX, padTop + drawH);
        fillPath.close();

        // Fill with vertical gradient
        fillPaint.setShader(new LinearGradient(
                0, padTop, 0, padTop + drawH,
                Color.parseColor("#44FF8C00"), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        ));

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        // Draw active right pulse dot
        canvas.drawCircle(lastX, lastY, dpToPx(5f), dotGlowPaint);
        canvas.drawCircle(lastX, lastY, dpToPx(2.5f), dotPaint);
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
