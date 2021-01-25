package com.iote.backdoor;

import android.annotation.SuppressLint;
import java.security.Key;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Blowfish {

    @SuppressLint("GetInstance")
    public byte[] encrypt(String sub_key, String deviceInfo) throws Exception{

        Cipher cipher = Cipher.getInstance("Blowfish/ECB/PKCS5Padding");
        Key blowfishKey = new SecretKeySpec(sub_key.getBytes(), "Blowfish");
        assert cipher != null;
        cipher.init(Cipher.ENCRYPT_MODE, blowfishKey);
        return cipher.doFinal(deviceInfo.getBytes());
    }

}
