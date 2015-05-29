/*
 * Copyright (C) 2008 ZXing authors
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

package ru.infonum.infonumscanner7;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.ResultPoint;

import java.util.ArrayList;
import java.util.List;



/**
 * Поле видоискателя.
 * Это вью накладывается поверх превью камеры. Оно добавляет прямоугольник видоискателя
 * и полупрозрачное остальное поле, имитируя лазерный сканер.
 *
 * This view is overlaid on top of the camera preview. It adds the viewfinder rectangle and partial
 * transparency outside it, as well as the laser scanner animation and result points.
 *
 * @author dswitkin@google.com (Daniel Switkin)
 */
public final class ViewfinderView extends View {

    private static final long ANIMATION_DELAY = 80L;
    private static final int CURRENT_POINT_OPACITY = 0xA0; // не влияет?
    private static final int MAX_RESULT_POINTS = 20;
    private static final int POINT_SIZE = 12; //def=6 - диаметр желтых точек при распознавании

    public static final int MIN_PREVIEW_PIXELS = 470 * 320; // normal screen
    public static final int MAX_PREVIEW_PIXELS = 1280 * 800;

    private static String TAG = ViewfinderView.class.getSimpleName();

    private static Camera camera;
    private final Paint paint;
    private Bitmap resultBitmap;
    private Bitmap bitmap3;
    private final int maskColor;
    private final int resultColor;
    private final int resultColor2;
    private final int resultPointColor;
    private List<ResultPoint> possibleResultPoints;
    private List<ResultPoint> lastPossibleResultPoints;
    private PorterDuffXfermode xfermode;

    public static String outStr = "";
    public static Rect frame;
    public static boolean done = false;
    public static Context ctx;

    // This constructor is used when the class is built from an XML resource.

    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);
        // Инициализируем здесь единожды, вместо того чтобы вызывать это каждый раз в onDraw().
        // Initialize these once for performance rather than calling them every time in onDraw().

        // создаем битмап для рисования на экране
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.viewfinder_mask);
        resultColor = resources.getColor(R.color.result_view);
        resultPointColor = resources.getColor(R.color.possible_result_points);
        resultColor2 = resources.getColor(R.color.result_points);
        possibleResultPoints = new ArrayList<ResultPoint>(5);
        lastPossibleResultPoints = null;
        xfermode = new PorterDuffXfermode(PorterDuff.Mode.DST_OUT);
        ctx = context;

    }

    public static void setCamera(Camera camera) {
        ViewfinderView.camera = camera;}

    @Override
    public void onDraw(Canvas canvas) {
        /*  Рисует полную картину на экране при запросе перерисовки
         *
         *  На экране рисуются:
         *  - превью с камеры, приведенное к размеру экрана
         *  - поверх - затемняющая рамка, оставляющая светлый прямоугольник в центре
         *  - поверх - лазерные точки в местах распознания маркеров куэра
         *
         *  Видимо, координаты точек передаются в системе матрицы камеры.
         *  Чтобы они правильно отрисовывались, нужно масштабировать.
         *
         *  размер канвы = размеру поверхности
         */

        // TODO проверять frame на null
        // определяем размеры области рисования
        int width = canvas.getWidth();
        int height = canvas.getHeight();

        // Получаем координаты центрального половинного фрейма

        frame = new Rect(0, 0, width, height);
        //Rect frame = getFramingRect();

        // Затемняем обрамление.
        canvas.drawColor(resultBitmap != null ? resultColor : maskColor);

        // для правильного рисования прозрачного круга: PorterDuff.Mode.DST_OUT.
        paint.setXfermode(xfermode);

        int rad = Math.min(width, height / 3);
        canvas.drawCircle(width / 2, height / 2, rad, paint);

        // пишем разрешение и др. на экран
        paint.setTextSize(60);
        canvas.drawText(width + " " + height + " " + rad, 100, 100, paint);

        if (resultBitmap != null) {
            // Рисуем непрозрачным получившийся битмап поверх прямоугольника для сканирования
            // то есть, стираем предыдущие точки, если новых нет = обновляем фрейм из превью
            // Draw the opaque result bitmap over the scanning rectangle

            paint.setAlpha(CURRENT_POINT_OPACITY);
            //--paint.setAlpha(255);

            // Draw the specified bitmap, scaling/translating automatically to fill the destination rectangle.
            // If the source rectangle is not null, it specifies the subset of the bitmap to draw.
            canvas.drawBitmap(resultBitmap, null, frame, paint);
        } else {
            // В этом случае рисуем скачущие точки
            // Получаем координаты растянутого центрального фрейма
            // и зачем-то называем его превью

            if (done) {
                //TODO сделать нормальную отрисовку эмблемы вместо круга сканирования
                // неправильно
                //canvas.drawColor(Color.GREEN);
                //canvas.drawCircle(width / 2, height / 2, rad, paint);
            }

            Rect previewFrame = CalcRect.getFramingRectInPreview();
            // TODO проверять previewFrame на null

            // вычисляем отношение сторон фрейма к сторонам превью
            // зачем-то считаем отношение сторон половинного фрейма к растянутому
            // получаем снова коэфф. растягивания экрана к матрице камеры
            // т.е. scaleX = (screen.x / cam.x) // прикол. это следует из getFramingRectInPreview()

            float scaleX = frame.width() / (float) previewFrame.width();
            float scaleY = frame.height() / (float) previewFrame.height();

            List<ResultPoint> currentPossible = possibleResultPoints;
            List<ResultPoint> currentLast = lastPossibleResultPoints;

            int frameLeft = frame.left;
            int frameTop = frame.top;

            if (currentPossible.isEmpty()) {
                lastPossibleResultPoints = null;
            } else {
                possibleResultPoints = new ArrayList<ResultPoint>(5);
                lastPossibleResultPoints = currentPossible;
                paint.setAlpha(CURRENT_POINT_OPACITY);
                paint.setColor(resultColor2);

                //TODO разобраться с синхронизацией
                synchronized (currentPossible) {
                    for (ResultPoint point : currentPossible) {
                        //  только для первого раза
                        // отрисовываются точки, но в неправильных местах из-за ошибок масштабирования
                        // левая граница фрейма + координата точки с поправкой
                        // на разницу сторон матрицы и экрана

                        //canvas.drawCircle(frameLeft - (int) (point.getX() * scaleX),
                        //frameTop - (int) (point.getY() * scaleY),
                        //POINT_SIZE, paint);
                        canvas.drawCircle((int) (width / 2 + point.getX()), (int) (height / 2 + point.getY()),
                                POINT_SIZE, paint);
                        canvas.drawText("" + (int)point.getX() + " " + (int)point.getY(), 700, 200, paint);

                        //canvas.drawCircle(
                        //        (960 - frameLeft + (int) (point.getX())) / 2,
                        //        (540 - frameTop + (int) (point.getY())) / 2,
                        //        POINT_SIZE,
                        //        paint
                        //);
                    }
                }
            }

            if (currentLast != null) {
                paint.setAlpha(CURRENT_POINT_OPACITY / 2);
                paint.setColor(resultPointColor);
                synchronized (currentLast) {
                    // отрисовываются точки, но в неправильных местах из-за ошибок масштабирования
                    // левая граница фрейма + координата точки с поправкой
                    // на разницу сторон матрицы и экрана

                    // координаты точек в системе координат экрана. (0,0)-левый верхний ланшафт.
                    // Радиус точки в два раза меньше?
                    float radius = POINT_SIZE / 2.0f;
                    for (ResultPoint point : currentLast) {

                        outStr += "Point.x.y " + point.getX() + " " + point.getY() + "\n";

                        //canvas.drawCircle((int) (frameLeft + point.getX() * scaleX),
                        //      (int) (frameTop + point.getY() * scaleY),
                        //      radius, paint);

                        canvas.drawCircle((int) (width / 2 + point.getX()), (int) (height / 2 + point.getY()),
                                POINT_SIZE * 2, paint);

                        canvas.drawText("" + (int)point.getX() + " " + (int)point.getY(), 700, 400, paint);

                    }
                }
            }

            // Запрос следующего обновления по интервалу анимации, но перерисовываются только точки.
            // а не вся маска видоискателя. Лазерная линия не используется.
            // Request another update at the animation interval, but only repaint the laser line,
            // not the entire viewfinder mask.
            postInvalidateDelayed(ANIMATION_DELAY,
                        frame.left - POINT_SIZE,
                        frame.top - POINT_SIZE,
                        frame.right + POINT_SIZE,
                        frame.bottom + POINT_SIZE);
        }
    }


    public static void drawBlackBitmap(BinaryBitmap blackBitmap) {

    }
    public void addPossibleResultPoint(ResultPoint point) {
        /*  Управляет стеком точек.
         *
         *  Используется в Main
         *  Получает координаты маркера и заносит точку в список (стек)
         *  Если стек полон, то очищает его половину
         */

        List<ResultPoint> points = possibleResultPoints;
        synchronized (points) {
            points.add(point);
            int size = points.size();
            if (size > MAX_RESULT_POINTS) {
                // trim it
                points.subList(0, size - MAX_RESULT_POINTS / 2).clear();
            }
        }
    }
    public static Point getScreenResolution() {
        /* Определяет аппаратное разрешение экрана и возвращает его в виде, где ширина всегда больше высоты
         *
         */
        //WindowManager manager = (WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE);
        //Display display = manager.getDefaultDisplay();
        Display display = ((WindowManager) ctx.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
        int width = display.getWidth(); //deprecated
        int height = display.getHeight(); //deprecated
        outStr += "Display.w.h " + width + "*" + height + "\n";

        //Point point = null; // в таком виде замена deprecated вызывает падение
        //display.getSize(point);
        //int height = point.y;
        //int width = point.x;

        // Работает только landscape.
        // Когда не landscape, предполагается, что это ошибка и переключается в него.
        // Дисплей у нас может быть только горизонтальным. Когда дисплей выходит из засыпания,
        // он может думать, что он в портретном режиме.
        // Если он не горизонтальный, предполагаем, что это ошибка и поворачиваем его.
        // .
        // We're landscape-only, and have apparently seen issues with display thinking it's portrait
        // when waking from sleep. If it's not landscape, assume it's mistaken and reverse them:
        if (width < height) {
            Log.i(TAG, "Дисплей сообщил о портретной ориентации; предполагаем, что это не правильно");
            outStr += "Портрет=false \n";
            int temp = width;
            width = height;
            height = temp;
        }
        return new Point(width, height);
    }
}
