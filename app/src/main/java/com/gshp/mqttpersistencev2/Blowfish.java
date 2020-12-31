package com.gshp.mqttpersistencev2;

import android.annotation.SuppressLint;
import java.security.Key;
import java.util.Random;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Blowfish {

    @SuppressLint("GetInstance")
    public byte[] encrypt(String sub_key, String deviceInfo) throws Exception{

        Cipher cipher = null;
        cipher = Cipher.getInstance("Blowfish/ECB/PKCS5Padding");

        Key blowfishKey = new SecretKeySpec(sub_key.getBytes(), "Blowfish");
        assert cipher != null;
        cipher.init(Cipher.ENCRYPT_MODE, blowfishKey);

        return cipher.doFinal(deviceInfo.getBytes());
    }

    public int[] generateIndexes(){

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

        return new int[]{startIndex, endIndex};

    }
}
