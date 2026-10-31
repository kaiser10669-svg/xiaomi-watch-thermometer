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
    private boolean isFahrenheit = false;

    public void setFahrenheit(boolean fahrenheit) {
        if (this.isFahrenheit != fahrenheit) {
            this.isFahrenheit = fahrenheit;
            invalidate();
        }
    }
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

    private float density = 1.0f;
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
        density = getResources().getDisplayMetrics().density;

        tubeBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tubeBgPaint.setColor(Color.parseColor("#151518"));
        tubeBgPaint.setStyle(Paint.Style.FILL);

        tubeBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tubeBorderPaint.setColor(Color.parseColor("#33FFFFFF"));
        tubeBorderPaint.setStyle(Paint.Style.STROKE);
        tubeBorderPaint.setStrokeWidth(dp(1.2f));

        mercuryPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mercuryPaint.setStyle(Paint.Style.FILL);

        bulbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bulbPaint.setStyle(Paint.Style.FILL);

        tickMajorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickMajorPaint.setColor(Color.parseColor("#88FFFFFF"));
        tickMajorPaint.setStyle(Paint.Style.STROKE);
        tickMajorPaint.setStrokeWidth(dp(1.0f));

        tickMinorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        tickMinorPaint.setColor(Color.parseColor("#44FFFFFF"));
        tickMinorPaint.setStyle(Paint.Style.STROKE);
        tickMinorPaint.setStrokeWidth(dp(0.8f));

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.parseColor("#99FFFFFF"));
        textPaint.setTextSize(dp(8.5f));
        textPaint.setTextAlign(Paint.Align.CENTER);

        goldMarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        goldMarkPaint.setColor(Color.parseColor("#FFD700")); // Classic Golden 36.6 mark
        goldMarkPaint.setStyle(Paint.Style.STROKE);
        goldMarkPaint.setStrokeWidth(dp(1.8f));

        glassReflectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glassReflectionPaint.setColor(Color.parseColor("#22FFFFFF"));
        glassReflectionPaint.setStyle(Paint.Style.FILL);
    }

    private float dp(float val) {
        return val * density;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w <= 0 || h <= 0) return;

        float padLeft = dp(16f);
        float padRight = dp(16f);
        bulbRadius = dp(9.5f);
        tubeHeight = dp(8f);

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
            cancelAnimation();
            this.animatedTemp = clamped;
            invalidate();
            return;
        }

        cancelAnimation();

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

    public void cancelAnimation() {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
            animator = null;
        }
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
            float innerMargin = dp(1f);
            mercuryRect.set(tubeStartX, tubeTop + innerMargin, tubeStartX + mercuryWidth, tubeBottom - innerMargin);
            float cornerR = Math.min(tubeHeight / 2f, mercuryWidth / 2f);
            canvas.drawRoundRect(mercuryRect, cornerR, cornerR, mercuryPaint);
        }

        // Draw Bulb (Mercury Reservoir on the left)
        canvas.drawCircle(bulbCenterX, bulbCenterY, bulbRadius, bulbPaint);

        // Bulb glass specular highlight
        canvas.drawCircle(bulbCenterX - bulbRadius * 0.35f, bulbCenterY - bulbRadius * 0.35f, bulbRadius * 0.28f, glassReflectionPaint);

        // Draw Scale Ticks and Labels below tube
        float scaleLength = tubeEndX - tubeStartX;
        float majorTickY1 = tubeBottom + dp(2f);
        float majorTickY2 = majorTickY1 + dp(4f);
        float textY = majorTickY2 + dp(9f);
        float minorTickY1 = tubeBottom + dp(2f);
        float minorTickY2 = minorTickY1 + dp(2.2f);

        float goldFrac;
        if (!isFahrenheit) {
            // Celsius Scale (34°C - 42°C, Golden mark at 36.6°C)
            for (int t = 34; t <= 42; t++) {
                float tickFrac = (t - MIN_SCALE_TEMP) / (MAX_SCALE_TEMP - MIN_SCALE_TEMP);
                float tx = tubeStartX + scaleLength * tickFrac;
                canvas.drawLine(tx, majorTickY1, tx, majorTickY2, tickMajorPaint);
                if (t % 2 == 0) {
                    canvas.drawText(String.valueOf(t), tx, textY, textPaint);
                }
            }

            for (int step = 1; step < 40; step++) {
                if (step % 5 == 0) continue;
                float t = MIN_SCALE_TEMP + step * 0.2f;
                float tickFrac = (t - MIN_SCALE_TEMP) / (MAX_SCALE_TEMP - MIN_SCALE_TEMP);
                float tx = tubeStartX + scaleLength * tickFrac;
                canvas.drawLine(tx, minorTickY1, tx, minorTickY2, tickMinorPaint);
            }
            goldFrac = (36.6f - MIN_SCALE_TEMP) / (MAX_SCALE_TEMP - MIN_SCALE_TEMP);
        } else {
            // Fahrenheit Scale (94°F - 106°F, Golden medical mark at 98.6°F)
            float minF = MIN_SCALE_TEMP * 1.8f + 32f; // 93.2°F
            float maxF = MAX_SCALE_TEMP * 1.8f + 32f; // 107.6°F

            for (int f = 94; f <= 106; f += 2) {
                float tickFrac = (f - minF) / (maxF - minF);
                float tx = tubeStartX + scaleLength * tickFrac;
                canvas.drawLine(tx, majorTickY1, tx, majorTickY2, tickMajorPaint);
                canvas.drawText(String.valueOf(f), tx, textY, textPaint);
            }

            for (int f10 = 940; f10 <= 1060; f10 += 5) {
                if (f10 % 20 == 0) continue;
                float fVal = f10 / 10.0f;
                float tickFrac = (fVal - minF) / (maxF - minF);
                float tx = tubeStartX + scaleLength * tickFrac;
                canvas.drawLine(tx, minorTickY1, tx, minorTickY2, tickMinorPaint);
            }
            goldFrac = (98.6f - minF) / (maxF - minF);
        }

        // Classic Golden Arrow and Line
        float gx = tubeStartX + scaleLength * goldFrac;
        canvas.drawLine(gx, tubeTop - dp(4f), gx, tubeBottom + dp(3f), goldMarkPaint);

        goldMarkPaint.setStyle(Paint.Style.FILL);
        arrowPath.reset();
        arrowPath.moveTo(gx, tubeTop - dp(1f));
        arrowPath.lineTo(gx - dp(3f), tubeTop - dp(6f));
        arrowPath.lineTo(gx + dp(3f), tubeTop - dp(6f));
        arrowPath.close();
        canvas.drawPath(arrowPath, goldMarkPaint);
        goldMarkPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        cancelAnimation();
    }
}
