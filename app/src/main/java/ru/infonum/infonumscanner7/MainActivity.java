package ru.infonum.infonumscanner7;

import android.app.Activity;
import android.content.Context;
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



//
//Где-то ошибка в определении размеров области сканирования.
//область реального сканирования в 2 раза меньше короткой стороны светлого прямоугольника видоискателя.
//Лазерные точки сдвинуты на 2 размера куэра в сторону.
//При этом размер изображения превью камеры правильный.
//Область сканирования равна экрану, а не видоискателя, и сдвинута на половинц ширины влево.
//
public class MainActivity extends Activity implements SurfaceHolder.Callback, PreviewCallback, AutoFocusCallback {
    private Camera camera;
    private SurfaceView preview;
    private ViewfinderView vfv;
    private Result rawResult;
    private String TAG = MainActivity.class.getSimpleName();
    private long currKey;
    private final int AUTOFOCUS_DELAY = 2000; // 2000 - период принудительного перезапуска автофокуса
    public Context context = getBaseContext();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout fl = new FrameLayout(this);  //тип верстки с одним эл. в строке.
        //Если внутри несколько элементов, то след. будет поверх предыд.
        // Обычно это пустое пространство на экране, которое можно заполнить
        // только дочерним объектом View или ViewGroup. Все дочерние элементы FrameLayout
        // прикрепляются к верхнему левому углу экрана.
        // В разметке FrameLayout нельзя определить различное местоположение для дочернего объекта View.
        // Последующие дочерние объекты View будут просто рисоваться поверх предыдущих представлений,
        // частично или полностью затеняя их, если находящийся сверху объект непрозрачен

        preview = new SurfaceView(this); // предоставляет отдельную область для рисования,
        // действия с которой должны быть вынесены в отдельный поток приложения.
        // Создание класса, унаследованного от SurfaceView и реализующего интерфейс SurfaceHolder.Callback
        SurfaceHolder surfaceHolder = preview.getHolder();
        surfaceHolder.addCallback(this);   // хотим получать соответствующие обратные вызовы.
        // будем отрисовывать картинку с камеры и...
        fl.addView(preview, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        vfv = new ViewfinderView(this, null);
        // ...и видоискатель поверх

        // если убрать - не показывает видоискатель и точки, но сканирует не хуже.
        // Требуется размер куэр 1/4 ширины экрана.
        fl.addView(vfv, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        setContentView(fl);// падает в api14



    }

    @Override
    protected void onResume() {
        // Ошибка: после блокировки экрана по отсутствию управляющих воздействий,
        // после разблокировки картинка больше не обновляется.
        super.onResume();
        camera = open();
        vfv.setCamera(camera);
        currKey = System.currentTimeMillis();  // сохраняем текущее время
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camera != null) {
            camera.setPreviewCallback(null);
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera.setPreviewDisplay(holder);
            camera.setPreviewCallback(this);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Size camPreviewSize = camera.getParameters().getPreviewSize();
        float aspect = (float) camPreviewSize.width / camPreviewSize.height; //def. соотношение сторон камеры
        //aspect = 1 / aspect;

        LayoutParams layoutParams = preview.getLayoutParams();
        Camera.Parameters cameraParameters = camera.getParameters();
        cameraParameters.set("orientation", "landscape"); //def="landscape"
        //parameters.set("orientation", "portrait");
        camera.setParameters(cameraParameters);

        // Где-то ошибка в подсчетах каких-то размеров. Сканирует только при видимом размере куэра
        // примерно в 2..3 раза меньше короткой стороны прямоугольника видоискателя.
        // Помогает умножение на 2 размеров lp. Но, при каждом след. возобновлении сканирования,
        // размер изображения увеличивается вдвое, пока приложение не вылетает. Но сканирует - ОК!!!
        layoutParams.width = preview.getWidth(); // *2 не искажает, увеличивает область сканирования, приближает
        // Берем за основу ширину экрана
        // чтобы изображение не выглядело искаженным из-за разных соотношений сторон матрицы и экрана,
        // рисовать его будем с соотношением строн матрицы камеры
        layoutParams.height = (int) (layoutParams.width / aspect); // можно *2
        preview.setLayoutParams(layoutParams); // устанавливаем размеры области для рисования

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
                Size previewSize = camera.getParameters().getPreviewSize(); // от камеры
                Rect rect = vfv.getFramingRectInPreview();
                LuminanceSource source = new PlanarYUVLuminanceSource(
                        bytes, previewSize.width, previewSize.height, rect.left, rect.top,
                        rect.width(), rect.height(), false);
                //def previewSize.width,

                Map<DecodeHintType,Object> hints = new HashMap<DecodeHintType, Object>();
                Vector<BarcodeFormat> decodeFormats = new Vector<BarcodeFormat>(1);
                decodeFormats.add(BarcodeFormat.QR_CODE);
                hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
                hints.put(DecodeHintType.NEED_RESULT_POINT_CALLBACK, new ResultPointCallback() {
                    @Override
                    public void foundPossibleResultPoint(ResultPoint resultPoint) {
                        vfv.addPossibleResultPoint(resultPoint);
                    }
                });
                MultiFormatReader mfr = new MultiFormatReader();
                mfr.setHints(hints);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
                rawResult = mfr.decodeWithState(bitmap);
                if (rawResult!=null) {
                    Log.e(TAG, rawResult.getText() + " key=" + key + " currKey=" + currKey);
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
