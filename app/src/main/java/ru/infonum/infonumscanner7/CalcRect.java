package ru.infonum.infonumscanner7;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.hardware.Camera;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static ru.infonum.infonumscanner7.ViewfinderView.*;

/**
 * Created by d1i on 29.05.15.
 */
public class CalcRect {
    private static Camera camera;
    private static Rect framingRect;
    public static Rect framingRectInPreview;

    private static final int MIN_FRAME_WIDTH = 240;
    private static final int MIN_FRAME_HEIGHT = 240;
    private static final int MAX_FRAME_WIDTH = 960; // = 1920/2
    private static final int MAX_FRAME_HEIGHT = 540; // = 1080/2

    private static String TAG = CalcRect.class.getSimpleName();

    public static synchronized Rect getFramingRectInPreview() {
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

    public static synchronized Rect getFramingRect() {
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
            Point screenResolution = ViewfinderView.getScreenResolution();
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


    private static Point findBestPreviewSizeValue(Camera.Parameters parameters) {

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
                outStr += "Размер превью, наиболее подходящий под размер экрана: " + exactPoint + "\n";
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
