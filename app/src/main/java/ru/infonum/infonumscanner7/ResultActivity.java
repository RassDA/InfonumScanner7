package ru.infonum.infonumscanner7;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import java.util.UUID;


public class ResultActivity extends Activity {

    public static final String RESULT = "result";
    public static final String BITMAP = "bitmap";
    public static final String FORMAT = "format";
    public static final String BITMAPSTR = "bitmapstr";
    public static final String BITMAPW = "bitmapw";
    public static final String BITMAPH = "bitmaph";
    public static final String LOG = "log";

    //String bitStr= "";
    private static String bitW = "";
    private static String bitH = "";
    private static String bitStr= "";
    private static String sFormat = "";
    private static String resultStr = "";

    String ss = "";
    String s2 = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.result_activity);

        //EditText tw = (EditText) findViewById(R.id.textView);
        //tw.setGravity(Gravity.CENTER);
        //tw.setTextSize(20);

        Intent intent = getIntent();

        resultStr = intent.getStringExtra(RESULT); // Результат распознавания
        sFormat = intent.getStringExtra(FORMAT); // тип кода - куэр или другой какой
        //bitStr = intent.getStringExtra(BITMAPSTR);
        bitW = intent.getStringExtra(BITMAPW);
        bitH = intent.getStringExtra(BITMAPH);



        // Проверка куэра на принадлежность Инфонуму по содержанию строки
        // Тестовое получение данных об устройстве

        if (resultStr !=null) {

            // Получаем номер телефона - получить гарантированно не удастся. Нужно использовать другие данные.

            //TelephonyManager tMgr=(TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            //String mPhoneNumber = tMgr.getLine1Number();

            final TelephonyManager tm = (TelephonyManager) getBaseContext().getSystemService(Context.TELEPHONY_SERVICE);

            // Получаем Android_ID устройства
            String androidId, tmDevice, tmSerial;
            tmDevice = "" + tm.getDeviceId();
            tmSerial = "" + tm.getSimSerialNumber();
            androidId = "" + android.provider.Settings.Secure.getString(getContentResolver(), android.provider.Settings.Secure.ANDROID_ID);

            UUID deviceUuid = new UUID(androidId.hashCode(), ((long)tmDevice.hashCode() << 32) | tmSerial.hashCode());
            String deviceId = deviceUuid.toString();  //RFC 4122 UUID - шифрованный


           //   не проверяется формат текста и номера
           //   не проверяется сотовый оператор




            final String SMS_PREFIX = "smsto";
            final String SMS_PREFIX2 = "sms"; // так тоже можно по мнению zxing
            final String URL_PREFIX = "http://";
            final String DEV_ID = "?deviceId=";

            String smsNum = "";
            String smsTxt = "";
            String urlSite = "";
            String urlStr = "";

            final String smsNumOutTxt1 = "Будет отправлено одно СМС на ";
            final String smsNumOutTxt2 = "неизвестный номер ";
            final String smsNumOutTxt3 = "номер Инфонум ";


            final String urlTxt = "Переход на сайт: ";
            final String trSmsDescript = "\nБудет отправлено одно СМС (по вашему тарифу на исходящие СМС на номер ";
            final String trOkTxt = "\nЭто табличка Инфонум. Безопасность обеспечена.\n";
            final String trNoTxt = "\nЭто НЕ табличка Инфонум. \nБезопасность НЕ обеспечена.\n";
            final String trNoTxt1 = "\nНомер телефона неизвестен.";
            final String trNoTxt2 = "\nПереход по ссылке может быть опасен.";
            final String trNoTxt3 = "\nОтправка СМС может вызвать денежные потери";
            final String txtUnknown = "Это не смс и не url. Результат распознавания: \n";

            String[] trustedSmsList = {"+79214278684", "+79119148047"}; //перечень наших номеров.
            String[] trustedSmsProviderList = {"СЗФ МТС", "СЗФ Мегафон"}; //перечень операторов этих номеров.
            String[]  trustedUrlList = {"infonum.ru", "infonum.com", "checkin.infonum.ru"}; //добавлять сайты в строку
            boolean trusted = false;
            String uriHost = "";
            String uriScheme = "";
            int i = 0;

            ss = resultStr + "\n";

            if (resultStr.length() > 0) {

                Uri uri = Uri.parse(resultStr);
                if (uri.getHost().length() > 0)
                    uriHost = uri.getHost().toLowerCase().trim();
                if (uri.getScheme().length() > 0) uriScheme = uri.getScheme().toLowerCase().trim();

                if (uri.getScheme() != null) {
                    if (uriScheme.equalsIgnoreCase(SMS_PREFIX)) {// *** В куэре содержится СМС

                        smsTxt = resultStr.substring(resultStr.indexOf(":") + 1); // удаляем префикс
                        if (smsTxt.contains(":")) {
                            // предполагаем (не проверяем): номер формата +70001112222, ":", текст
                            smsNum = smsTxt.substring(0, smsTxt.indexOf(":")).trim(); // номер - до ":"
                            smsTxt = smsTxt.substring(smsTxt.indexOf(":") + 1); // текст - после ":"
                        }

                        //ss += "\ntxt=" + smsTxt;
                        //ss += "\nnum=" + smsNum;

                        trusted = false;
                        for (i = 0; i < trustedSmsList.length; i++)
                            if (trustedSmsList[i].equalsIgnoreCase(smsNum)) trusted = true;

                        if (trusted) { // *** доверенный номер

                            // ss += trOkTxt + smsNum + "   Текст: " + smsTxt + trSmsDescript + trustedSmsProviderList[i] + ")";

                            //номер опознан как доверенный
                            // Отправляем смс наш номер не задавая вопросов, добавляем в текст deviceID,
                            // чтобы опознать по нему, если номер телефона еще не использовался.
                            // или чтобы связать их
/*
                            try {
                                Intent sendIntent = new Intent(Intent.ACTION_VIEW);
                                sendIntent.putExtra("sms_body", smsTxt + DEV_ID + deviceId);
                                sendIntent.putExtra("address", trustedSmsList[0]);  //такая фигня работает
                                sendIntent.setType("vnd.android-dir/mms-sms");
                                startActivity(sendIntent);

                            } catch (Exception e) {
                                Toast.makeText(getApplicationContext(),
                                        "Неудача. СМС не отправлено",
                                        Toast.LENGTH_LONG).show();
                                e.printStackTrace();
                            }
*/
                        } else // *** смс, но не наш номер
                            ss += trNoTxt + trNoTxt1 + "\n" + smsNum + "  " + smsTxt;
                    } else if (resultStr.substring(0, 10).toLowerCase().contains(URL_PREFIX)) { // *** В куэре содержится URL,

                        // признак: в начале строки "http://"
                        // Это стандарт (вместо префикса типа SMSTO:) для обозначения ссылки.
                        // Если закодировать урл без префикса "http://", то он будет сканироваться как текст,
                        // но, в некоторых мобильных сканерах, будет подчеркнут как ссылка, на которую можно нажать.
                        // Мы будем считать, что в наших куэрах "http://" должен присутствовать, иначе это не ссылка.

                        uriHost = uri.getHost().toLowerCase().trim();
                        trusted = false;
                        for (i = 0; i < trustedUrlList.length; i++)
                            if (trustedUrlList[i].equalsIgnoreCase(uriHost)) trusted = true;
                        if (trusted) { //*** сайт доверенный

                            // нашли в списке доверенных имя сканированного сайта
                            //ss += trOkTxt + "\n" + urlTxt + urlSite; // сообщаем, что сайт наш


                            //---Из документации. Работает. Запускает браузер по-умолчанию.
                            // ID устройства добавляются к адресу пока в открытом виде.

                            Intent intent2 = new Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(uri, DEV_ID + deviceId));
                            //startActivity(intent2);
                        } else { // *** сайт недоверенный
                            ss += trNoTxt + trNoTxt2 + "\n" + urlStr;
                        }
                    }
                } else {
                    // *** строка не смс и не урл
                    ss = txtUnknown + smsNum + ", " + smsTxt;
                }
            }
            // *** делаем в любом случае

            // Получаем всевозможные параметры сети и устройства и печатаем их на экран
/*
            ss += "\n\ndeviceID: " + deviceId + "  androidId: " + androidId;
            ss += "\nIMEI: " + tm.getDeviceId () + "  S/W ver.: " + tm.getDeviceSoftwareVersion ();
            ss += "\nSimSerial: " + tm.getSimSerialNumber () + "  myPhone: " + tm.getLine1Number ();

            ss += "\n\nОбслуживает провайдер:";
            ss += "\nТел: " + tm.getLine1Number () + "  Код: " + tm.getNetworkOperator ();
            ss += "\nСтрана: " + tm.getNetworkCountryIso () + "  Тип сети: " + tm.getNetworkType ();

            ss += "\n\nРегистрация СИМ:";
            ss += "\nТип телефона (1=GSM): " + tm.getPhoneType () + "\nПровайдер: " + tm.getSimOperatorName ();
            ss += "  Страна: " + tm.getSimCountryIso () + "\nSIM Serial: " + tm.getSimSerialNumber ();
            ss += "\nСостояние СИМ: " + tm.getSimState () + "\nSubscriber Id (IMSI): " + tm.getSubscriberId ();
            ss += "\nРоуминг включен: " + tm.isNetworkRoaming () + "  Тип сети: " + tm.getNetworkType ();
            //не знает такого ss += "\nСМС вкл: " + tm.isSmsCapable ();
*/
/**
 *           ss += "\n#";
 *           ss += uri.getScheme();
 *           ss += "\n#";
 *           ss += uri.getSchemeSpecificPart();
 *           ss += "\n#";
 *           ss += uri.getAuthority();
 *           ss += "\n#";
 *           ss += uri.isHierarchical();
 *           ss += "\n#";
 *           ss += uriHost;
 *           ss += "\n#";
 *           ss += uri.getHost();
 *           ss += "\n#";
 *           ss += trustedUrlList[0];
 *           ss += "\n#";
 *           ss += trustedUrlList[1];
 */

            ss += "\n" + ViewfinderView.outStr;
            ss += "\n" + sFormat + " bitW=" + bitW + " bitH=" + bitH;
            //ss += "\n" + bitStr;

            //tw.setText(ss);

        }

        //без xml:
        //setContentView(tw, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Button buttonLog = (Button) findViewById(R.id.buttonLog);


        buttonLog.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                Intent intentLog = new Intent(ResultActivity.this, LogActivity.class);

                intentLog.putExtra(ResultActivity.LOG, ss);

                //intent.putExtra(ResultActivity.RESULT, resultStr);
                //intent.putExtra(ResultActivity.FORMAT, sFormat);
                //intent.putExtra(ResultActivity.BITMAPSTR, bitmapStr);
                //intent.putExtra(ResultActivity.BITMAPW, bitW);
                //intent.putExtra(ResultActivity.BITMAPH, bitH);

                intentLog.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intentLog);
            }
        });


    }


}
