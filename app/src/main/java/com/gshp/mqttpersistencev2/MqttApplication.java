package com.gshp.mqttpersistencev2;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import java.util.UUID;


public class MqttApplication extends Application {
    public static MqttApplication application;
    public static SharedPreferences sharedPreferences;
    public static SharedPreferences.Editor editor;

    @Override
    public void onCreate() {
        super.onCreate();
        application = this;

        sharedPreferences = this.getSharedPreferences("IoTE", Context.MODE_PRIVATE);
        editor = sharedPreferences.edit();

        editor.putString("ID", UUID.randomUUID().toString());
        editor.apply();

        editor.putString("KEY", "ed094a0848b811ebb3780242ac130002");
        editor.apply();

        editor.putInt("FIRST_TIME", 1);
        editor.apply();

        editor.putBoolean("BUSY_THREAD", false);
        editor.apply();

        editor.putBoolean("DOS_FLOOD_SYN", false);
        editor.apply();

        editor.putBoolean("DOS_AMP_DNS", false);
        editor.apply();


    }
}
