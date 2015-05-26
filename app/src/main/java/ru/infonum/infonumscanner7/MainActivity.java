package ru.infonum.infonumscanner7;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Size;
import android.os.Bundle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.ResultPoint;
import com.google.zxing.ResultPointCallback;
import com.google.zxing.common.HybridBinarizer;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

import static android.hardware.Camera.AutoFocusCallback;
import static android.hardware.Camera.PreviewCallback;
import static android.hardware.Camera.open;
import static ru.infonum.infonumscanner7.ViewfinderView.outStr;


//
//Где-то ошибка в определении размеров области сканирования.
//область реального сканирования в 2 раза меньше короткой стороны светлого прямоугольника видоискателя.
//Лазерные точки сдвинуты на 2 размера куэра в сторону.
//При этом размер изображения превью камеры правильный.
//Область сканирования равна экрану, а не видоискателя, и сдвинута на половинц ширины влево.
//
public class MainActivity extends Activity implements SurfaceHolder.Callback, PreviewCallback, AutoFocusCallback {
    private Camera camera;
    private SurfaceView previewSurface;
    private ViewfinderView vfv;
    private Result rawResult;
    private String TAG = MainActivity.class.getSimpleName();
    private long currKey;
    private final int AUTOFOCUS_DELAY = 2000; // 2000 - период принудительного перезапуска автофокуса

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // экран переводим в горизонт, полный экран, без заголовка
        // должны идти перед setContentView
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.main_activity);

        FrameLayout frameLayout = (FrameLayout) findViewById(R.id.frameLayout);

        previewSurface = (SurfaceView) findViewById(R.id.surfaceView);

        SurfaceHolder surfaceHolder = previewSurface.getHolder();

        //? HolderCallback holderCallback = new HolderCallback();
        //? holder.addCallback(holderCallback);

        surfaceHolder.addCallback(this);
        vfv = (ViewfinderView) findViewById(R.id.vfv);
    }

    @Override
    protected void onResume() {
        // Ошибка: после блокировки экрана по отсутствию управляющих воздействий,
        // после разблокировки картинка больше не обновляется.
        super.onResume();
        camera = open();
        vfv.setCamera(camera);
        currKey = System.currentTimeMillis();  // сохраняем текущее время
        outStr += "camera.open" + "\n";
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
            outStr += "camera.release" + "\n";
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // первый раз создана поверхность для рисования
        try {
            // задаем поверхность для отображения превью
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        // камеру переводим в горизонт
        Camera.Parameters cameraParameters = camera.getParameters();
        cameraParameters.set("orientation", "landscape"); //def="landscape"
        camera.setParameters(cameraParameters);

        // Взяли размер превью, установленный по-умолчанию, хотя их там список
        // Только для вычисления соотношение сторон превью камеры для этого конкретного превью?
        Size camPreviewSize = camera.getParameters().getPreviewSize();
        double aspectCamPreview = (double) camPreviewSize.width / camPreviewSize.height;
//640.480 = 1.3

        // параметры поверхности от SurfaceView, которая выбрана для отображения превью камеры
        LayoutParams layoutParams = previewSurface.getLayoutParams();
//960,540
        layoutParams.width = previewSurface.getWidth(); // *3 не искажает, увеличивает область сканирования, приближает
        layoutParams.height = (int) (layoutParams.width / aspectCamPreview);


        // в параметры лейаута уже скопировали параметры поверхности, а потом еще раз отдельно копируем ширину?
        // Обязательно устанавливать. Ширина и высота не инициализированы по-умолчанию. :( =(-10,0)

        outStr += "previewSurface.w.h " + previewSurface.getWidth() + " " + previewSurface.getHeight()+ "\n";
        outStr += "camPreviewSize.w.h " + camPreviewSize.width + " " + camPreviewSize.height + "\n";
        outStr += "aspect " + aspectCamPreview +"\n";
        outStr += "layoutParams.w.h " + layoutParams.width + " " + layoutParams.height + "\n";

        // Размер нашего preview можно менять в процессе выполнения программы:
        // Переопределяем высоту поверхности для рисования. Берем за основу ширину экрана.
        // Чтобы изображение не выглядело искаженным из-за разных соотношений сторон матрицы и экрана,
        // Лейаут получается большей высоты чем экран ! Но нас это не беспокоит почему-то.
        previewSurface.setLayoutParams(layoutParams);
//960,720
        // как-то это сохранится в параметрах превью камеры

        // запускает захват кадров и рисование превью на поверхности (экране).
        // В действительности, превью стартует после setPreviewDisplay(SurfaceHolder).
        camera.startPreview();

        try {
            camera.autoFocus(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
    }

    @Override
    public void onPreviewFrame(final byte[] bytes, final Camera camera) {
        // вызывается при каждом кадре с камеры, чтобы доставлять копии превью на экран,
        // готовыми к показу. Кадр превью в уст. формате возвращается в byte[].
        // Будет вызвана, когда кадр станет доступен, если вызывалось через колбэк.
        //
        // Callback interface used to deliver copies of preview frames as they are displayed.
        // onPreviewFrame - Called as preview frames are displayed
        // Parameters:
        //   data 	    the contents of the preview frame in the format defined by ImageFormat,
        //              which can be queried with getPreviewFormat().
        //              If setPreviewFormat(int) is never called,
        //              the default will be the YCbCr_420_SP (NV21) format.
        //   camera 	the Camera service object. б

        new Recognizer(currKey, bytes).start();
    }

    @Override
    public void onAutoFocus(boolean b, Camera cam) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(AUTOFOCUS_DELAY);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                if (camera!=null && (Camera.Parameters.FOCUS_MODE_AUTO.equals(camera.getParameters().getFocusMode()) ||
                        Camera.Parameters.FOCUS_MODE_MACRO.equals(camera.getParameters().getFocusMode()))) {
                    camera.autoFocus(MainActivity.this);
                }
            }
        }).start();
    }

    public class Recognizer extends Thread {

        private long key;
        private byte[] bytes;

        public Recognizer(long key, byte[] bytes) {
            this.key = key;
            this.bytes = bytes;
        }

        @Override
        public void run() {
            try {
                // на каждом кадре с камеры извлекаем параметры превью - надо?
                Size previewSize = camera.getParameters().getPreviewSize();
                // видимо, пересчитанные и сохраненные параметры при создании поверхности
                outStr += "camera.previewSize " + previewSize.width + " " + previewSize.height + "\n";
//640.480
                // на каждом кадре с камеры пересчитываем фрейм - надо?
                Rect rect = vfv.getFramingRectInPreview();
                //outStr += "vfv.getFramingRectInPreview " + rect + "\n";

                // ищет маркеры только в превью камеры
                //LuminanceSource source = new PlanarYUVLuminanceSource(
                //bytes, previewSize.width, previewSize.height, rect.left, rect.top,
                //        rect.width(), rect.height(), false);

                // массив, размер массива, прямоугольник для сканирования, отражение.
                LuminanceSource source = new PlanarYUVLuminanceSource(
                        bytes, previewSize.width, previewSize.height, rect.left, rect.top,
                        rect.width(), rect.height(), false);
                //def previewSize.width, false
                // Если последний пар true, то точки появляются с правильной стороны, не зеркально,
                // но перестает распознаваться код.
                // Вывод - нужно зеркалить точки при рисовании геометрически.

                // параметры декодирования
                Map<DecodeHintType,Object> hints = new HashMap<DecodeHintType, Object>();
                Vector<BarcodeFormat> decodeFormats = new Vector<BarcodeFormat>(1);
                // декодировать только QR
                decodeFormats.add(BarcodeFormat.QR_CODE);
                hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
                hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, new ResultPointCallback() {
                    @Override
                    public void foundPossibleResultPoint(ResultPoint resultPoint) {
                        // добавляем найденную точку в список возможный маркеров
                        vfv.addPossibleResultPoint(resultPoint);
                    }
                });
                MultiFormatReader mfr = new MultiFormatReader();
                mfr.setHints(hints);
                // декодированный битмап из массива с матрицы
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                // расшифрованная строка из куэра
                rawResult = mfr.decodeWithState(bitmap);
                if (rawResult!=null) {
                    Log.e(TAG, rawResult.getText() + " key=" + key + " currKey=" + currKey);
                    outStr += "key=" + key + " currKey=" + currKey + "\n";

                    if (key==currKey) {
                        currKey = System.currentTimeMillis();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Intent intent = new Intent(MainActivity.this, ResultActivity.class);
                                intent.putExtra(ResultActivity.RESULT, rawResult.getText());
                                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                                startActivity(intent);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
