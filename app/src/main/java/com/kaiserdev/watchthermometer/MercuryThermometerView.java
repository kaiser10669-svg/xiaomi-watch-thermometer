package com.kaiserdev.watchthermometer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

public class MercuryThermometerView extends View {

    private float currentTemp = 36.6f;
    private float animatedTemp = 36.6f;
    private ValueAnimator animator;

    private Paint tubeBgPaint;
    private Paint tubeBorderPaint;
    private Paint mercuryPaint;
    private Paint bulbPaint;
    private Paint tickMajorPaint;
    private Paint tickMinorPaint;
    private Paint textPaint;
    private Paint goldMarkPaint;
    private Paint glassReflectionPaint;

    private final RectF tubeRect = new RectF();
    private final RectF mercuryRect = new RectF();
    private final Path arrowPath = new Path();

    private float bulbCenterX;
    private float bulbCenterY;
    private float bulbRadius;
    private float tubeStartX;
    private float tubeEndX;
    private float tubeTop;
    private float tubeBottom;
    private float tubeHeight;

    private LinearGradient mercuryGrad;
    private RadialGradient bulbGrad;

    private static final float MIN_SCALE_TEMP = 34.0f;
    private static final float MAX_SCALE_TEMP = 42.0f;

    public MercuryThermometerView(Context context) {
        super(context);
        init();
    }

    public MercuryThermometerView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public MercuryThermometerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        tubeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tubeBgPaint.setColor(Color.parseColor("#151518"));
        tubeBgPaint.setStyle(Paint.Style.FILL);

        tubeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tubeBorderPaint.setColor(Color.parseColor("#33FFFFFF"));
        tubeBorderPaint.setStyle(Paint.Style.STROKE);
        tubeBorderPaint.setStrokeWidth(dpToPx(1.2f));

        mercuryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mercuryPaint.setStyle(Paint.Style.FILL);

        bulbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bulbPaint.setStyle(Paint.Style.FILL);

        tickMajorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickMajorPaint.setColor(Color.parseColor("#88FFFFFF"));
        tickMajorPaint.setStyle(Paint.Style.STROKE);
        tickMajorPaint.setStrokeWidth(dpToPx(1.0f));

        tickMinorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickMinorPaint.setColor(Color.parseColor("#44FFFFFF"));
        tickMinorPaint.setStyle(Paint.Style.STROKE);
        tickMinorPaint.setStrokeWidth(dpToPx(0.8f));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#99FFFFFF"));
        textPaint.setTextSize(dpToPx(8.5f));
        textPaint.setTextAlign(Paint.Align.CENTER);

        goldMarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        goldMarkPaint.setColor(Color.parseColor("#FFD700")); // Classic Golden 36.6 mark
        goldMarkPaint.setStyle(Paint.Style.STROKE);
        goldMarkPaint.setStrokeWidth(dpToPx(1.8f));

        glassReflectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glassReflectionPaint.setColor(Color.parseColor("#22FFFFFF"));
        glassReflectionPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        float padLeft = dpToPx(16f);
        float padRight = dpToPx(16f);
        bulbRadius = dpToPx(9.5f);
        tubeHeight = dpToPx(8f);

        bulbCenterX = padLeft + bulbRadius;
        bulbCenterY = h * 0.38f;

        tubeStartX = bulbCenterX + bulbRadius * 0.7f;
        tubeEndX = w - padRight;
        tubeTop = bulbCenterY - tubeHeight / 2f;
        tubeBottom = bulbCenterY + tubeHeight / 2f;

        tubeRect.set(tubeStartX, tubeTop, tubeEndX, tubeBottom);

        mercuryGrad = new LinearGradient(
                tubeStartX, tubeTop, tubeEndX, tubeBottom,
                new int[]{Color.parseColor("#D50000"), Color.parseColor("#FF1744"), Color.parseColor("#FF8C00")},
                null, Shader.TileMode.CLAMP
        );
        mercuryPaint.setShader(mercuryGrad);

        bulbGrad = new RadialGradient(
                bulbCenterX - bulbRadius * 0.3f, bulbCenterY - bulbRadius * 0.3f,
                bulbRadius * 1.2f,
                new int[]{Color.parseColor("#FF5252"), Color.parseColor("#D50000"), Color.parseColor("#8A0000")},
                null, Shader.TileMode.CLAMP
        );
        bulbPaint.setShader(bulbGrad);
    }

    public void setTemperature(float tempC, boolean animate) {
        if (Float.isNaN(tempC) || Float.isInfinite(tempC)) {
            tempC = 36.6f;
        }
        float clamped = Math.max(MIN_SCALE_TEMP, Math.min(MAX_SCALE_TEMP, tempC));
        this.currentTemp = clamped;

        if (!animate) {
            this.animatedTemp = clamped;
            invalidate();
            return;
        }

        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }

        animator = ValueAnimator.ofFloat(animatedTemp, clamped);
        animator.setDuration(700);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                float val = (float) a.getAnimatedValue();
                if (!Float.isNaN(val) && !Float.isInfinite(val)) {
                    animatedTemp = val;
                    invalidate();
                }
            }
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // Draw Capillary Glass Tube Background
        canvas.drawRoundRect(tubeRect, tubeHeight / 2f, tubeHeight / 2f, tubeBgPaint);
        canvas.drawRoundRect(tubeRect, tubeHeight / 2f, tubeHeight / 2f, tubeBorderPaint);

        // Calculate Mercury Fill Level
        float frac = (animatedTemp - MIN_SCALE_TEMP) / (MAX_SCALE_TEMP - MIN_SCALE_TEMP);
        if (Float.isNaN(frac)) frac = 0.325f;
        frac = Math.max(0f, Math.min(1f, frac));
        float mercuryWidth = (tubeEndX - tubeStartX) * frac;

        if (mercuryWidth > 0) {
            mercuryRect.set(tubeStartX, tubeTop + dpToPx(1f), tubeStartX + mercuryWidth, tubeBottom - dpToPx(1f));
            canvas.drawRoundRect(mercuryRect, tubeHeight / 2f, tubeHeight / 2f, mercuryPaint);
        }

        // Draw Bulb (Mercury Reservoir on the left)
        canvas.drawCircle(bulbCenterX, bulbCenterY, bulbRadius, bulbPaint);

        // Bulb glass specular highlight
        canvas.drawCircle(bulbCenterX - bulbRadius * 0.35f, bulbCenterY - bulbRadius * 0.35f, bulbRadius * 0.28f, glassReflectionPaint);

        // Draw Scale Ticks and Labels below tube
        float scaleLength = tubeEndX - tubeStartX;
        for (int t = 34; t <= 42; t++) {
            float tickFrac = (t - MIN_SCALE_TEMP) / (MAX_SCALE_TEMP - MIN_SCALE_TEMP);
            float tx = tubeStartX + scaleLength * tickFrac;
            float ty1 = tubeBottom + dpToPx(2f);
            float ty2 = ty1 + dpToPx(4f);

            canvas.drawLine(tx, ty1, tx, ty2, tickMajorPaint);
            if (t % 2 == 0) {
                canvas.drawText(String.valueOf(t), tx, ty2 + dpToPx(9f), textPaint);
            }
        }

        // Minor ticks (every 0.2°C)
        for (float t = 34.2f; t < 42.0f; t += 0.2f) {
            if (Math.abs(t - Math.round(t)) < 0.05f) continue;
            float tickFrac = (t - MIN_SCALE_TEMP) / (MAX_SCALE_TEMP - MIN_SCALE_TEMP);
            float tx = tubeStartX + scaleLength * tickFrac;
            float ty1 = tubeBottom + dpToPx(2f);
            float ty2 = ty1 + dpToPx(2f);
            canvas.drawLine(tx, ty1, tx, ty2, tickMinorPaint);
        }

        // Classic Golden 36.6°C Arrow and Line
        float goldFrac = (36.6f - MIN_SCALE_TEMP) / (MAX_SCALE_TEMP - MIN_SCALE_TEMP);
        float gx = tubeStartX + scaleLength * goldFrac;
        canvas.drawLine(gx, tubeTop - dpToPx(4f), gx, tubeBottom + dpToPx(3f), goldMarkPaint);

        goldMarkPaint.setStyle(Paint.Style.FILL);
        arrowPath.reset();
        arrowPath.moveTo(gx, tubeTop - dpToPx(1f));
        arrowPath.lineTo(gx - dpToPx(3f), tubeTop - dpToPx(6f));
        arrowPath.lineTo(gx + dpToPx(3f), tubeTop - dpToPx(6f));
        arrowPath.close();
        canvas.drawPath(arrowPath, goldMarkPaint);
        goldMarkPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
