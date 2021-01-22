package com.gshp.mqttpersistencev2;

import android.annotation.SuppressLint;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import static java.lang.Thread.sleep;

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class ExampleJobService extends JobService {

    private static final String TAG = "ExampleJobService";
    private boolean jobCancelled = false;
    private MqttAndroidClient client;



    @Override
    public boolean onStartJob(JobParameters params) {
        //Log.d(TAG, "Job started");
        try {
            connectAndPublish(params);
        } catch (MqttException | IOException e) {
            e.printStackTrace();
        }
        return true;
    }


    public void connectAndPublish(final JobParameters params) throws MqttException, IOException {
        final String clientId = MqttClient.generateClientId();
        client = new MqttAndroidClient(getApplication().getApplicationContext(), "tcp://187.208.100.198:1883",
                clientId);

        final SharedPreferences sharedPreferences = getApplication().getSharedPreferences("IoTE", Context.MODE_PRIVATE);
        final String id = sharedPreferences.getString("ID", "-");
        final String key = sharedPreferences.getString("KEY", "-");
        final  int firstTime = sharedPreferences.getInt("FIRST_TIME", 1);

        final StringBuffer sb = new StringBuffer();
        final String[] str = {""};


            client.connect(null, new IMqttActionListener() {
            @SuppressLint("GetInstance")
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {

                final String encryptKey = UUID.randomUUID().toString().replace("-", "").substring(0, 4);
                String deviceInfo = "";
                Log.d(TAG, "onSuccess: Successfully client connected!!");

                //String id = "19e46b97-80b6-45f3-838e-785f69102a96"; //SIMULACIÓN
                String id = "73059017-c2c8-43af-9c48-41481c6dec85"; //SIMULACIÓN
                int firstTime = 0; //SIMULACIÓN

                if(firstTime == 1){
                    try
                    {

                        Process p = Runtime.getRuntime().exec("curl https://ipinfo.io/ip");
                        BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                        while((str[0] = inputStream.readLine())!= null){
                            sb.append(str[0]);
                        }
                        String publicIp = sb.toString();
                        deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"11\", \"value\": \"Activo\", \"ip_address\": \""+publicIp+"\"}"+firstTime+"&";
                        //HACER EL CAMBIO DE VARIABLE FIRSTIME !!!!!!!
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                }else{
                    deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"11\", \"value\": \"Activo\"}"+firstTime+"&";
                }

                Blowfish blowfish = new Blowfish();
                ByteArrayOutputStream bytesMqtt = new ByteArrayOutputStream();

                try {
                    bytesMqtt.write(encryptKey.getBytes());
                    bytesMqtt.write(blowfish.encrypt(encryptKey,deviceInfo));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                MqttMessage mqttMessage = new MqttMessage(bytesMqtt.toByteArray());

                try {
                    client.publish("CyC",mqttMessage);
                } catch (MqttException e) {
                    e.printStackTrace();
                }

            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {

            }
        });

        jobFinished(params, false);

    }




    @Override
    public boolean onStopJob(JobParameters params) {
        Log.d(TAG, "Job cancelled before completion");
        jobCancelled = true;
        return true; //persistencia !
    }


}
