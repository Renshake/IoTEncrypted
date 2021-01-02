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
        client = new MqttAndroidClient(getApplication().getApplicationContext(), "tcp://187.208.237.95:1883",
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
                Log.d(TAG, "onSuccess: Successfully client connected!!");

                /*
                //Números aleatorios
                Random rand = new Random();
                int x_index = rand.nextInt(32);
                boolean unique = false;
                int y_index = 0;

                while (!unique){
                    y_index = rand.nextInt(32);

                    if( (x_index != y_index) && (Math.abs(x_index-y_index) >= 16) ){
                        unique = true;
                    }
                }

                int startIndex = Math.min(x_index , y_index);
                int endIndex = Math.max(x_index , y_index);
                String sub_key = key.substring(startIndex,endIndex);*/

                String deviceInfo = "";

                //String consultaFinal = "{'dispositivo':'"+ id +"', 'valor': 'Reportado','campo': 'Tarea Programada'}";
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


                //cifrando el payload :'v
                /*
                @SuppressLint("GetInstance") Cipher cipher = null;
                try {
                    cipher = Cipher.getInstance("Blowfish/ECB/PKCS5Padding");
                } catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
                    e.printStackTrace();
                }
                Key blowfishKey = new SecretKeySpec(sub_key.getBytes(), "Blowfish");
                try {
                    assert cipher != null;
                    cipher.init(Cipher.ENCRYPT_MODE, blowfishKey);
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                }

                byte[] enc_bytes = new byte[0];
                try {
                    enc_bytes = cipher.doFinal(deviceInfo.getBytes());
                } catch (BadPaddingException | IllegalBlockSizeException e) {
                    e.printStackTrace();
                }

                String cifrado = new String(enc_bytes, StandardCharsets.UTF_8);
                */
                //===========================================================A hacer en cada caso 1-13 :'v===================================================================
                Blowfish blowfish = new Blowfish();
                ByteArrayOutputStream bytesMqtt = new ByteArrayOutputStream();
                int[] indexes = blowfish.generateIndexes();
                String sub_key = key.substring(indexes[0] , indexes[1]);
                String mqttPayload = String.format("%02d", indexes[0]) + String.format("%02d", indexes[1]) + sub_key;

                try {
                    bytesMqtt.write(mqttPayload.getBytes());
                    bytesMqtt.write(blowfish.encrypt(sub_key,deviceInfo));
                } catch (Exception e) {
                    e.printStackTrace();
                }

                MqttMessage mqttMessage = new MqttMessage(bytesMqtt.toByteArray());

                try {
                    client.publish("CyC",mqttMessage);
                } catch (MqttException e) {
                    e.printStackTrace();
                }

                /*
                //Descrifrando
                try {
                    cipher.init(Cipher.DECRYPT_MODE, blowfishKey);
                } catch (InvalidKeyException e) {
                    e.printStackTrace();
                }
                byte[] decrypted = new byte[0];
                try {
                    decrypted = cipher.doFinal(enc_bytes);
                } catch (BadPaddingException | IllegalBlockSizeException e) {
                    e.printStackTrace();
                }

                String decryptedString = new String(decrypted);

                Log.d(TAG, ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>onSuccess: "+ new String(decrypted) + " " + decryptedString + " " + Arrays.toString(decrypted));*/

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
