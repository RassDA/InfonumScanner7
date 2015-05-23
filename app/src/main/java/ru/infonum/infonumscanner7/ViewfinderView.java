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
    private static final int POINT_SIZE = 12; //def=6 - диаметр жетых точек при распознавании

    private static final int MIN_FRAME_WIDTH = 240;
    private static final int MIN_FRAME_HEIGHT = 240;
    private static final int MAX_FRAME_WIDTH = 960; // = 1920/2
    private static final int MAX_FRAME_HEIGHT = 540; // = 1080/2

    private static final int MIN_PREVIEW_PIXELS = 470 * 320; // normal screen
    private static final int MAX_PREVIEW_PIXELS = 1280 * 800;

    private Camera camera;
    private final Paint paint;
    private Bitmap resultBitmap;
    private final int maskColor;
    private final int resultColor;
    private final int resultColor2;
    private final int resultPointColor;
    private List<ResultPoint> possibleResultPoints;
    private List<ResultPoint> lastPossibleResultPoints;
    private String TAG = ViewfinderView.class.getSimpleName();
    private Rect framingRect,framingRectInPreview;

    public static String outStr = "";

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
        resultColor2 = resources.getColor(R.color.result_points);
        possibleResultPoints = new ArrayList<ResultPoint>(5);
        lastPossibleResultPoints = null;
    }

    public void setCamera(Camera camera) {
    this.camera = camera;
  }

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
         *
         *
         *
         */


        // Получаем координаты центрального половинного фрейма
        Rect frame = getFramingRect();
        // TODO проверять frame на null
        // определяем размеры области рисования
        int width = canvas.getWidth();
        int height = canvas.getHeight();
///*--- отключает затемнение обрамления видоискателя + скачущие точки


        // Затемняем обрамление.
        // Draw the exterior (i.e. outside the framing rect) darkened
        // 1. Рисуем темную полосу по всей ширине экрана до верха светлой части
        // 2,3. Рисуем темные прямоугольники слева и справа от светлой части по ее высоте
        // 4. Рисуем темную полосу по всей ширине экрана от низа светлой части


        //устанавливаем цвет заливки фрейма
        paint.setColor(resultBitmap != null ? resultColor : maskColor);
        //canvas.drawRect(0, 0, width, height, paint);

        //canvas.drawRect(0, 0, width, frame.top, paint);
        //canvas.drawRect(0, frame.top, frame.left, frame.bottom + 1, paint);
        //canvas.drawRect(frame.right + 1, frame.top, width, frame.bottom + 1, paint);
        //canvas.drawRect(0, frame.bottom + 1, width, height, paint);

        //paint.setAlpha(0xFFFFFFFF);
        paint.setStyle(Paint.Style.STROKE);
        canvas.drawCircle(
                width / 2,
                height / 2,
                width / 4,
                paint
        );

        if (resultBitmap != null) {
            // Рисуем прозрачным получившийся битмап поверх прямоугольника для сканирования
            // то есть, стираем предыдущие точки, если новых нет = обновляем фрейм из превью
            // Draw the opaque result bitmap over the scanning rectangle
            paint.setAlpha(CURRENT_POINT_OPACITY);
            canvas.drawBitmap(resultBitmap, null, frame, paint);
        } else {
            // В этом случае рисуем скачущие точки = лазерная линия
            // Получаем координаты растянутого центрального фрейма
            // и зачем-то называем его превью
            Rect previewFrame = getFramingRectInPreview();
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
                synchronized (currentPossible) {
                    for (ResultPoint point : currentPossible) {
                        //  только для первого раза
                        // отрисовываются точки, но в неправильных местах из-за ошибок масштабирования
                        // левая граница фрейма + координата точки с поправкой
                        // на разницу сторон матрицы и экрана

                        //canvas.drawCircle(frameLeft - (int) (point.getX() * scaleX),
                        //frameTop - (int) (point.getY() * scaleY),
                        //POINT_SIZE, paint);

                        canvas.drawCircle(
                                (960 - frameLeft + (int) (point.getX())) / 2,
                                (540 - frameTop + (int) (point.getY())) / 2,
                                POINT_SIZE,
                                paint
                        );
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

                        canvas.drawCircle(
                                (960 - frameLeft + (int) (point.getX())) /2,
                                (540 - frameTop + (int) (point.getY())) / 2,
                                POINT_SIZE,
                                paint
                        );

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
//*//---
    }

    public void addPossibleResultPoint(ResultPoint point) {
        /*  Работает с возможно распознанными маркерами куэра, которые потом изображаются в виде лазерных точек,
         *    обращаясь к zxing?
         *
         *    Используется в Main
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

    public synchronized Rect getFramingRectInPreview() {
        /* Зачем-то ??? возвращает размеры растянутого половинного центрального фрейма,
         * растянутого по каждой стороне на отношение
         * разрешения матрицы камеры к разрешению экрана по этой стороне.
         * Коррекция центрального фрейма?
         */
        // Вычислим, если не делали этого
        if (framingRectInPreview == null) {
            if (camera==null) throw new IllegalStateException("Camera is null!");
            // получаем координаты половинного прямоугольника в центре
            Rect framingRect = getFramingRect();

            if (framingRect == null) {
                return null;
            }
            outStr += "framingRect Центр0.5 " + framingRect + "\n";
//240,135-720,405
            // создаем еще один такой же половинный в центре
            Rect rect = new Rect(framingRect);
            // получаем от камеры все ее параметры и передаем в функцию,
            // которая сама берет оттуда список разрешений ее превью
            // и возвращает наиболее близкое к пропорциям экрана.
            Point cameraResolution = findBestPreviewSizeValue(camera.getParameters());
            outStr += "cameraResolution " + cameraResolution + "\n";
//1280,720
            // получаем разрешение экрана в сравнимом формате
            Point screenResolution = getScreenResolution();
//960,540
            if (cameraResolution == null || screenResolution == null) {
                // вызвали слишком рано, еще не инициализировались
                return null;
            }

            // Растягиваем фрейм, вычисляем координаты положения фрейма на экране, умножая
            // на отношение разрешений камеры к экрану по каждой координате
            // Зачем ???

            //rect.left = rect.left * cameraResolution.x / screenResolution.x; // Ошибка - всегда целое

            double xCamToScreen = (double)cameraResolution.x / (double)screenResolution.x;
//1280:960=
            double yCamToScreen  = (double)cameraResolution.y / (double)screenResolution.y;
//720:540=
// 240,135-720,405
            //rect.left = (int)(rect.left * xCamToScreen);
            //rect.top = (int)(rect.top * yCamToScreen);
            //rect.right = (int)(rect.right * xCamToScreen);
            //rect.bottom = (int)(rect.bottom * yCamToScreen);
//320.180-960.540
            outStr += "rect " + rect + "\n";

            //rect.left = 0;
            //rect.right = 960;
            //rect.top = 0;
            //rect.bottom = 540;

            framingRectInPreview = rect;
            outStr += "framingRectInPreview " + rect + "\n";

        }
        return framingRectInPreview;
    }

    public synchronized Rect getFramingRect() {
        /* Вычисляет и возвращает координаты половинного центрального фрейма.
         * Определяет предпочтительные координаты внутреннего прямоугольника обрамления=фрейма.
         * Размеры - половина от размера экрана по каждой стороне.
         * Проверяет, чтобы фрейм не оказался слишком большим или маленьким
         * по константам минимальных и максимальных размеров сторон фрейма.
         * Если не вписывается в пределы, то используется предел в качестве размера.
         * Вычисляет смещение фрейма после определения размеров,
         * чтобы он оказался в центре экрана при данном разрешении экрана.
         * Возвращает абсолютные координаты.
         */

        if (framingRect == null) {
            Point screenResolution = getScreenResolution();
            if (screenResolution == null) {
                // вызвали слишком рано, еще не инициализировались
                return null;
            }
            // подсчитываем и проверяем размеры фрейма, чтобы он был в два раза меньше экрана по каждому измерению,
            // но не слишком большим или маленьким
            int width = findDesiredDimensionInRange(screenResolution.x, MIN_FRAME_WIDTH, MAX_FRAME_WIDTH);
            int height = findDesiredDimensionInRange(screenResolution.y, MIN_FRAME_HEIGHT, MAX_FRAME_HEIGHT);
            outStr += "Desired.w.h " + width + "*" + height + "\n";

            // Смещение фрейма, чтобы он находился точно в центре экрана
            int leftOffset = (screenResolution.x - width) / 2;
            int topOffset = (screenResolution.y - height) / 2;
            outStr += "Offset.left.top " + leftOffset  + "*" +  topOffset + "\n";

            framingRect = new Rect(leftOffset, topOffset, leftOffset + width, topOffset + height);

            //--- можем сделать фрейм вовесь экран
            //framingRect = new Rect(0, 0, screenResolution.x, screenResolution.y);

            Log.d(TAG, "Рассчитанный размер прямоугольника рамки: " + framingRect);
            outStr += "framingRect " + framingRect + "\n";
        }
        return framingRect;
    }


    private static int findDesiredDimensionInRange(int resolution, int hardMin, int hardMax) {
        /* Возвращает предпочтительные размеры стороны фрейма для данного размера стороны экрана
         * с учетом минимального и максимального допустимого размера.
         * Хотим стороны фрейма приблизительно в 50% стороны экрана.
         * Если меньше или больше - используются пределы.
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
        /* Определяет аппаратное разрешение экрана и возвращает его в виде, где ширина всегда больше высоты
         *
         */
        WindowManager manager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        Display display = manager.getDefaultDisplay();
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

    private Point findBestPreviewSizeValue(Camera.Parameters parameters) {

        /* Определяет и возвращает наилучшие размеры превью, получаемого от камеры,
         * выбирает подходящий из списка из параметров.
         *
         * Переработанная функция из:
         * zxing/android-core/src/main/java/com/google/zxing/client/android/camera/CameraConfigurationUtils.java
         * В оригинале вместо float везде используется double
         *
         * При невозможности определения использует значения по-умолчанию.
         * Берет из параметров камеры список поддерживаемых разрешений превью и
         * выбирает одно с пропорциями, наиболее близкими к пропорциям экрана.
         *
         */

        List<Camera.Size> rawSupportedSizes = parameters.getSupportedPreviewSizes();
        if (rawSupportedSizes == null) {
            // При невозможности определения использует значения по-умолчанию.
            Log.w(TAG, "Устройство не возвращает размеры поддерживаемых превью; используем умолчания");
            outStr += "Устройство не возвращает размеры поддерживаемых превью; используем умолчания" + "\n";

            Camera.Size defaultSize = parameters.getPreviewSize();
            outStr += "Camera.Size: " + defaultSize + "\n";
            return new Point(defaultSize.width, defaultSize.height);
        }

        // Сортитуем разрешения в списке по убыванию количества пикселов
        List<Camera.Size> supportedPreviewSizes = new ArrayList<Camera.Size>(rawSupportedSizes);
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
        // преобразуем список разрешений превью в строку, только чтобы показать их в логе
        if (Log.isLoggable(TAG, Log.INFO)) {
            StringBuilder previewSizesString = new StringBuilder();
            for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
                previewSizesString.append(supportedPreviewSize.width).append('x')
                        .append(supportedPreviewSize.height).append(' ');
            }
            Log.i(TAG, "Поддерживаемые размеры превью: " + previewSizesString);
            outStr += "Поддерживаемые размеры превью: " + previewSizesString + "\n";

        }
        // Далее, выбираем наиболее подходящее из списка.

        // Здесь будем сохранять разрешение с пропорциями, наименее отличающимися от разрешения экрана
        Point bestSize = null;
        // Определяем физическое разрешение экрана и соотношение его сторон
        Point screenResolution = getScreenResolution();
        float screenAspectRatio = (float) screenResolution.x / (float) screenResolution.y;
        outStr += "screenAspectRatio " + screenAspectRatio + "\n";

        float diff = Float.POSITIVE_INFINITY;
        // Перебираем разрешения превью по списку
        for (Camera.Size supportedPreviewSize : supportedPreviewSizes) {
            // проверяем, на выходит ли количество пикселов в данном разрешении превью из списка
            //   за заданные пределы. Если выходит - берем следующее.
            int realWidth = supportedPreviewSize.width;
            int realHeight = supportedPreviewSize.height;

            int pixels = realWidth * realHeight;
            if (pixels < MIN_PREVIEW_PIXELS || pixels > MAX_PREVIEW_PIXELS) {
                continue;
            }

            // Проверяем ориентацию превью и исправляем на горизонтальную
            boolean isCandidatePortrait = realWidth < realHeight;
            int maybeFlippedWidth = isCandidatePortrait ? realHeight : realWidth;
            int maybeFlippedHeight = isCandidatePortrait ? realWidth : realHeight;

            //если размер превью в точности равен размеру экрана - ок
            if (maybeFlippedWidth == screenResolution.x && maybeFlippedHeight == screenResolution.y) {
                Point exactPoint = new Point(realWidth, realHeight);
                Log.i(TAG, "Найден размер превью, наиболее подходящий под размер экрана: " + exactPoint);
                outStr = "Размер превью, наиболее подходящий под размер экрана: " + exactPoint + "\n";
                return exactPoint;
            }
            // иначе, сверяем соотношения сторон у экрана и превью
            float aspectRatio = (float) maybeFlippedWidth / (float) maybeFlippedHeight;
            float newDiff = Math.abs(aspectRatio - screenAspectRatio);
            // сохраняем данное разрешение превью как лучшее, если разница между
            // его пропорциями и экрана меньше, чем у предыдущего из списка.
            // начальное diff = 1. Любая разность пропорций newDiff по модулю будет < 1,
            // если только одна из них не 0.
            outStr += "newDiff " + newDiff + "\n";

            if (newDiff < diff) {
                bestSize = new Point(realWidth, realHeight);
                diff = newDiff;
                outStr += "bestSize " + bestSize + "\n";

            }
        }

        // интересно, что будет использовано разрешение с лучшими пропорциями,
        // которое может иметь меньшее разрешение, что позволит лучше вписать превью в экран.
        // На разрешение сканирование не влияет.
        // Раз не нашли никакого или 0, используем значение по-умолчанию из параметров камеры
        if (bestSize == null) {
            Camera.Size defaultSize = parameters.getPreviewSize();
            bestSize = new Point(defaultSize.width, defaultSize.height);
            Log.i(TAG, "Нет подходящих по размеру превью, используем по-умолчанию: " + bestSize);
            outStr += "Нет подходящих по размеру превью, используем по-умолчанию: " + bestSize + "\n";
        }

        Log.i(TAG, "Найден наиболее подходящий размер превью: " + bestSize);
        outStr += "bestSize " + bestSize + "\n";

        return bestSize;
    }
}
