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
    private final float[] renderBuffer = new float[maxPoints];

    private Paint linePaint;
    private Paint fillPaint;
    private Paint dotPaint;
    private Paint dotGlowPaint;
    private Paint gridPaint;

    private final Path linePath = new Path();
    private final Path fillPath = new Path();

    private float density = 1.0f;
    private LinearGradient fillGradient;
    private float cachedDrawH = 0f;
    private float cachedPadTop = 0f;

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
        density = getResources().getDisplayMetrics().density;

        linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        linePaint.setColor(Color.parseColor("#FF8C00"));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(2.2f));
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
        gridPaint.setStrokeWidth(dp(1f));
    }

    private float dp(float val) {
        return val * density;
    }

    public synchronized void addPoint(float value) {
        if (value <= 10.0f || value >= 55.0f) return;
        dataPoints.add(value);
        if (dataPoints.size() > maxPoints) {
            dataPoints.remove(0);
        }
        postInvalidate();
    }

    public synchronized void clear() {
        dataPoints.clear();
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

    public static class Stats {
        public float min;
        public float avg;
        public float max;
        public int count;
    }

    public synchronized Stats getStats() {
        Stats s = new Stats();
        s.count = dataPoints.size();
        if (s.count == 0) return s;
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;
        float sum = 0f;
        for (float v : dataPoints) {
            if (v < min) min = v;
            if (v > max) max = v;
            sum += v;
        }
        s.min = min;
        s.max = max;
        s.avg = sum / s.count;
        return s;
    }

    public synchronized int getCount() {
        return dataPoints.size();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;
        cachedPadTop = dp(6);
        float padBottom = dp(6);
        cachedDrawH = h - cachedPadTop - padBottom;
        fillGradient = new LinearGradient(
                0, cachedPadTop, 0, cachedPadTop + cachedDrawH,
                Color.parseColor("#44FF8C00"), Color.TRANSPARENT,
                Shader.TileMode.CLAMP
        );
        fillPaint.setShader(fillGradient);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padTop = dp(6);
        float padBottom = dp(6);
        float padLeft = dp(8);
        float padRight = dp(8);

        float drawW = w - padLeft - padRight;
        float drawH = h - padTop - padBottom;

        // Draw middle guideline
        canvas.drawLine(padLeft, padTop + drawH * 0.5f, padLeft + drawW, padTop + drawH * 0.5f, gridPaint);

        int count;
        synchronized (this) {
            count = dataPoints.size();
            if (count < 2) {
                if (count == 1) {
                    float cx = padLeft + drawW * 0.5f;
                    float cy = padTop + drawH * 0.5f;
                    canvas.drawCircle(cx, cy, dp(5), dotGlowPaint);
                    canvas.drawCircle(cx, cy, dp(2.5f), dotPaint);
                }
                return;
            }
            for (int i = 0; i < count; i++) {
                renderBuffer[i] = dataPoints.get(i);
            }
        }

        float minVal = Float.MAX_VALUE;
        float maxVal = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            float p = renderBuffer[i];
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

        float stepX = drawW / (count - 1);
        float lastX = padLeft;
        float lastY = padTop + drawH * 0.5f;

        for (int i = 0; i < count; i++) {
            float val = renderBuffer[i];
            float norm = (val - minVal) / range;
            float x = padLeft + i * stepX;
            float y = padTop + (1.0f - norm) * drawH;

            if (i == 0) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, padTop + drawH);
                fillPath.lineTo(x, y);
            } else {
                float prevX = padLeft + (i - 1) * stepX;
                float prevY = padTop + (1.0f - (renderBuffer[i - 1] - minVal) / range) * drawH;
                float cx = (prevX + x) / 2.0f;
                linePath.cubicTo(cx, prevY, cx, y, x, y);
                fillPath.cubicTo(cx, prevY, cx, y, x, y);
            }

            if (i == count - 1) {
                lastX = x;
                lastY = y;
            }
        }

        fillPath.lineTo(lastX, padTop + drawH);
        fillPath.close();

        if (fillGradient == null || Math.abs(drawH - cachedDrawH) > 1f) {
            cachedDrawH = drawH;
            cachedPadTop = padTop;
            fillGradient = new LinearGradient(
                    0, padTop, 0, padTop + drawH,
                    Color.parseColor("#44FF8C00"), Color.TRANSPARENT,
                    Shader.TileMode.CLAMP
            );
            fillPaint.setShader(fillGradient);
        }

        canvas.drawPath(fillPath, fillPaint);
        canvas.drawPath(linePath, linePaint);

        // Draw active pulse dot on latest reading
        canvas.drawCircle(lastX, lastY, dp(5f), dotGlowPaint);
        canvas.drawCircle(lastX, lastY, dp(2.5f), dotPaint);
    }
}
