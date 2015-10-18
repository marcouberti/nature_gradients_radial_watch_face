package com.marcouberti.naturegradientsradial;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.support.wearable.view.CircledImageView;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by Marco on 05/09/15.
 */
public class CustomGradientView extends View {

    Paint paint = new Paint();
    public int[] gradients;

    public CustomGradientView(Context context) {
        super(context);
        initPaint(context);
    }

    public CustomGradientView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaint(context);
    }

    public CustomGradientView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaint(context);
    }

    private void initPaint(Context ctx){
        if(isInEditMode()) {
            gradients = new int[]{Color.MAGENTA, Color.CYAN};
        }else {
            gradients = ctx.getResources().getIntArray(R.array.violet_illusion_array);
        }
        paint.setAntiAlias(true);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Shader shader = new RadialGradient(getMeasuredWidth()/2f, getMeasuredHeight()/2f,getMeasuredHeight()/1.5f, gradients[0], gradients[1], Shader.TileMode.MIRROR);

        paint.setShader(shader);
        //canvas.drawRect(0, 0, this.getMeasuredWidth(), this.getMeasuredHeight(), paint);
        canvas.drawCircle(this.getMeasuredWidth()/2, this.getMeasuredHeight()/2, this.getMeasuredWidth()/2, paint);
    }
}
