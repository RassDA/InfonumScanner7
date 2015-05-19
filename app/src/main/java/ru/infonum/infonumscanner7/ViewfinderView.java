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
import android.graphics.Rect;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import com.google.zxing.ResultPoint;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
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

    private static final int[] SCANNER_ALPHA = {0, 64, 128, 192, 255, 192, 128, 64};
    private static final long ANIMATION_DELAY = 80L;
    private static final int CURRENT_POINT_OPACITY = 0xA0; // не влияет?
    private static final int MAX_RESULT_POINTS = 20;
    private static final int POINT_SIZE = 12; //def=6 - диаметр жетых точек при распознавании

    private static final int MIN_FRAME_WIDTH = 240;
    private static final int MIN_FRAME_HEIGHT = 240;
    private static final int MAX_FRAME_WIDTH = 960; // = 1920/2
    private static final int MAX_FRAME_HEIGHT = 540; // = 1080/2

    private static final int MIN_PREVIEW_PIXELS = 470 * 320; // normal screen
    private static final int MAX_PREVIEW_PIXELS = 1280 * 800;

    private static final double MAX_ASPECT_DISTORTION = 0.15;

    private Camera camera;
    private final Paint paint;
    private Bitmap resultBitmap;
    private final int maskColor;
    private final int resultColor;
    private final int resultPointColor;
    private int scannerAlpha;

    private List<ResultPoint> possibleResultPoints;
    private List<ResultPoint> lastPossibleResultPoints;
    private String TAG = ViewfinderView.class.getSimpleName();
    private Rect framingRect,framingRectInPreview;

    // This constructor is used when the class is built from an XML resource.

    public ViewfinderView(Context context, AttributeSet attrs) {
        super(context, attrs);

        // Инициализируем здесь единожды, вместо того чтобы вызывать это каждый раз в onDraw().
        // Initialize these once for performance rather than calling them every time in onDraw().
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Resources resources = getResources();
        maskColor = resources.getColor(R.color.viewfinder_mask);
        resultColor = resources.getColor(R.color.result_view);
        resultPointColor = resources.getColor(R.color.possible_result_points);
        possibleResultPoints = new ArrayList<ResultPoint>(5);
        lastPossibleResultPoints = null;
    }

    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (camera == null) { //from ZX
            return; // not ready yet, early draw before done configuring
        }
        // запрашиваем размеры светлого фрейма
        Rect frame = getFramingRect();
        Rect previewFrame = getFramingRectInPreview();
        if (frame == null || previewFrame == null) {
            return;
        }
            // определяем размеры области рисования
        int width = canvas.getWidth();
        int height = canvas.getHeight();
//   отключает затемнение обрамления видоискателя + скачущие точки

        // Затемняем обрамление.
        // Draw the exterior (i.e. outside the framing rect) darkened
        // 1. Рисуем темную полосу по всей ширине экрана до верха светлой части
        // 2,3. Рисуем темные прямоугольники слева и справа от светлой части по ее высоте
        // 4. Рисуем темную полосу по всей ширине экрана от низа светлой части
        paint.setColor(resultBitmap != null ? resultColor : maskColor);
        canvas.drawRect(0, 0, width, frame.top, paint);
        canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
        canvas.drawRect(0, frame.bottom + 1, width, height, paint);

        if (resultBitmap != null) {
            // Рисуем прозрачным получившийся битмап поверх прямоугольника для сканирования
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(CURRENT_POINT_OPACITY);
            canvas.drawBitmap(resultBitmap, null, frame, paint);


        } else {

            // Draw a red "laser scanner" line through the middle to show decoding is active
            // Рисуем красную линию "лазерного сканера" по середине, чтобы показать, что декодирование активно
            //paint.setColor(laserColor);
            paint.setColor(maskColor);
            paint.setAlpha(SCANNER_ALPHA[scannerAlpha]);
            scannerAlpha = (scannerAlpha + 1) % SCANNER_ALPHA.length;

            int middle = frame.height() / 2 + frame.top;
            canvas.drawRect(frame.left + 2, middle - 1, frame.right - 1, middle + 2, paint);


            // В этом случае рисуем скачушие точки = лазерная линия
            // вычисляем наилучший размер светлого фрейма
            //Rect previewFrame = getFramingRectInPreview(); //findev
            // TODO проверять previewFrame на null
            // вычисляем отношение сторон фрейма к сторонам превью
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
                paint.setColor(resultPointColor);
                synchronized (currentPossible) {
                    for (ResultPoint point : currentPossible) {
                        canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                                frameTop + (int) (point.getY() * scaleY),
                                POINT_SIZE, paint);
                    }
                }
            }

            if (currentLast != null) {
                paint.setAlpha(CURRENT_POINT_OPACITY / 2);
                paint.setColor(resultPointColor);
                synchronized (currentLast) {
                    float radius = POINT_SIZE / 2.0f;
                    for (ResultPoint point : currentLast) {
                        canvas.drawCircle(frameLeft + (int) (point.getX() * scaleX),
                                frameTop + (int) (point.getY() * scaleY),
                                radius, paint);
                    }
                }
            }

            // Запрос следующего обновления по интервалу анимации, но перерисовывается только линия лазера.
            // а не вся маска видоискателя.
            // Request another update at the animation interval, but only repaint the laser line,
            // not the entire viewfinder mask.
            postInvalidateDelayed(ANIMATION_DELAY,
                    frame.left - POINT_SIZE,
                    frame.top - POINT_SIZE,
                    frame.right + POINT_SIZE,
                    frame.bottom + POINT_SIZE);
        }
    }

    public void addPossibleResultPoint(ResultPoint point) {
        /* Работает с лазерными точками, обращаясь к zxing
         *
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

    public synchronized Rect getFramingRectInPreview() {
        /* Определяет размеры прямоугольника фрейма в превью
         * Растягивает фрейм с учетом разрешений экрана и камеры
         */
        // Вычислим, если не делали этого
        if (framingRectInPreview == null) {
            if (camera==null) throw new IllegalStateException("Camera is null!");
            Rect framingRect = getFramingRect();
            if (framingRect == null) {
                return null;
            }
            Rect rect = new Rect(framingRect);
            Point cameraResolution = findBestPreviewSizeValue(camera.getParameters(), getScreenResolution());
            Point screenResolution = getScreenResolution();
            if (cameraResolution == null || screenResolution == null) {
                // вызвали слишком рано, еще не инициализировались
                return null;
            }
            // Растягиваем фрейм, вычисляем координаты положения фрейма на экране, умножая
            // на отношение разрешений камеры к экрану по каждой координате
            rect.left = rect.left * cameraResolution.x / screenResolution.x;
            rect.right = rect.right * cameraResolution.x / screenResolution.x;
            rect.top = rect.top * cameraResolution.y / screenResolution.y;
            rect.bottom = rect.bottom * cameraResolution.y / screenResolution.y;
            framingRectInPreview = rect;
        }
        return framingRectInPreview;
    }

    public synchronized Rect getFramingRect() {
        /* Вычисляет предпочтительные координаты внутреннего прямоугольника обрамления=фрейма
         * на основе разрешения экрана и констант минимальных и максимальных размеров фрейма.
         * Чтобы поле для куэра не было слишком большим или маленьким.
         * Вычисляет расположение фрейма на экране при данном разрешении экрана.
         */

        if (framingRect == null) {
            Point screenResolution = getScreenResolution();
            if (screenResolution == null) {
                // вызвали слишком рано, еще не инициализировались
                return null;
            }

            int width = findDesiredDimensionInRange(screenResolution.x, MIN_FRAME_WIDTH, MAX_FRAME_WIDTH);
            int height = findDesiredDimensionInRange(screenResolution.y, MIN_FRAME_HEIGHT, MAX_FRAME_HEIGHT);

            // Смещение фрейма
            int leftOffset = (screenResolution.x - width) / 2;
            int topOffset = (screenResolution.y - height) / 2;
            framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);

            //--- когда не используем рамку
            //framingRect = new Rect(0, 0, screenResolution.x, screenResolution.y);

            Log.d(TAG, "Рассчитанный размер прямоугольника рамки: " + framingRect);
        }
        return framingRect;
    }


    private static int findDesiredDimensionInRange(int resolution, int hardMin, int hardMax) {
        /* Вычисляет предпочтительные размеры фрейма для данного разрешения экрана
         * с учетом минимального и максимального допустимого размера.
         * Хотим фрейм приблизительно в 50% экрана.
         */

        int dim = resolution / 2; // Target 50% of each dimension
        if (dim < hardMin) {
            return hardMin;
        }
        if (dim > hardMax) {
            return hardMax;
        }
        return dim;
    }


    private Point getScreenResolution() {
        /* Определяет разрешение экрана и возвращает его в виде, где ширина всегда больше высоты
         *
         */
        WindowManager manager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
        int width = display.getWidth(); //deprecated
        int height = display.getHeight(); //deprecated

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
            int temp = width;
            width = height;
            height = temp;
        }
        return new Point(width, height);
    }

    private Point findBestPreviewSizeValue(Camera.Parameters parameters, Point screenResolution) {

        /* Определяет наилучшие размеры превью, получаемого от камеры, запрашивая их у камеры
         * и выбирая подходящее.
         *
         * Это переработанная функция из:
         * zxing/android-core/src/main/java/com/google/zxing/client/android/camera/CameraConfigurationUtils.java
         *В оригинале вместо float везде используется double
         *
         * При невозможности определения используем значения по-умолчанию.
         * Запрашиваем у камеры список поддерживаемых разрешений превью. Если камера не дает список,
         * запрашиваем у нее значения превью по-умолчанию и работаем с ними.
         *
         *
         *
         *
         */

        List<Camera.Size> rawSupportedSizes = parameters.getSupportedPreviewSizes();
        if (rawSupportedSizes == null) {
            Log.w(TAG, "Устройство не возвращает размеры поддерживаемых превью; используем умолчания");
            Camera.Size defaultSize = parameters.getPreviewSize();
            if (defaultSize == null) {
                throw new IllegalStateException("Parameters contained no preview size!");
            }
            return new Point(defaultSize.width, defaultSize.height);
        }

        // Сортитуем разрешения по убыванию общего количества пикселей x*y
        List<Camera.Size> supportedPreviewSizes = new ArrayList<>(rawSupportedSizes);
        Collections.sort(supportedPreviewSizes, new Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size a, Camera.Size b) {
                int aPixels = a.height * a.width;
                int bPixels = b.height * b.width;
                if (bPixels < aPixels) {
                    return -1;
                }
                if (bPixels > aPixels) {
                    return 1;
                }
                return 0;
            }
        });
        // строим список разрешений превью в виде строки, только чтобы показать в логе
        if (Log.isLoggable(TAG, Log.INFO)) {
            StringBuilder previewSizesString = new StringBuilder();
            for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
                previewSizesString.append(supportedPreviewSize.width).append('x')
                        .append(supportedPreviewSize.height).append(' ');
            }
            Log.i(TAG, "Поддерживаемые размеры превью: " + previewSizesString);
        }

        double screenAspectRatio = (double) screenResolution.x / (double) screenResolution.y;

        // удаляем неподходящие размеры из списка
        Iterator<Camera.Size> it = supportedPreviewSizes.iterator();
        while (it.hasNext()) {
            Camera.Size supportedPreviewSize = it.next();
            int realWidth = supportedPreviewSize.width;
            int realHeight = supportedPreviewSize.height;
            if (realWidth * realHeight < MIN_PREVIEW_PIXELS) {
                it.remove();
                continue;
            }

            // Далее, выбираем наиболее подходящее из списка.
            // Сначала определяем физическое разрешение экрана и соотношение его сторон
            // проверяем ориентацию превью и исправляем на горизонтальную
            boolean isCandidatePortrait = realWidth < realHeight;
            int maybeFlippedWidth = isCandidatePortrait ? realHeight : realWidth;
            int maybeFlippedHeight = isCandidatePortrait ? realWidth : realHeight;
            double aspectRatio = (double) maybeFlippedWidth / (double) maybeFlippedHeight;
            double distortion = Math.abs(aspectRatio - screenAspectRatio);
            if (distortion > MAX_ASPECT_DISTORTION) {
                it.remove();
                continue;
            }

            if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y) {
                Point exactPoint = new Point(realWidth, realHeight);
                Log.i(TAG, "Найден размер превью, наиболее подходящий под размер экрана: " + exactPoint);
                return exactPoint;
            }
        }
        //если размер превью в точности равен размеру экрана - ок
        // если нет совпадающего, используем самое большое разрешение превью.
        // Это нехорошо для устройств со слабым CPU. Разрабы надеются, что сюда попадут только
        // с устройствами на Андроидом 4+, которые помощнее, чем совсем старые.

        if (!supportedPreviewSizes.isEmpty()) {
            Camera.Size largestPreview = supportedPreviewSizes.get(0);
            Point largestSize = new Point(largestPreview.width, largestPreview.height);
            Log.i(TAG, "Используем наибольшее из подходящих разрешений: " + largestSize);
            return largestSize;
        }

        // If there is nothing at all suitable, return current preview size
        // Если ничего не подходит, возвращаем текущее разрешение превью
        Camera.Size defaultPreview = parameters.getPreviewSize();
        if (defaultPreview == null) {
            throw new IllegalStateException("Parameters contained no preview size!");
        }

        Point defaultSize = new Point(defaultPreview.width, defaultPreview.height);
        Log.i(TAG, "No suitable preview sizes, using default: " + defaultSize);
        return defaultSize;
    }




}
