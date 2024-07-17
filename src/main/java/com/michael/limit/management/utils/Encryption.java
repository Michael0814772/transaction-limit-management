package com.michael.limit.management.utils;

import com.michael.limit.management.exception.exceptionMethod.InternalServerException;
import jakarta.xml.bind.DatatypeConverter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

@Component
@Slf4j
public class Encryption {

    public String encrypt(String message) {
        byte[] encryptedMessage = null;
        try {
            byte[] vectorBytes = DatatypeConverter.parseHexBinary("4ef56a7c01cd9ef32bc34ef56a7c89ab");
            byte[] keyBytes = DatatypeConverter.parseHexBinary("2bc34ef56a7c89ab4ef56a7c01cd9ed3");

            IvParameterSpec ivspec = new IvParameterSpec(vectorBytes);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, ivspec.getIV());
            SecretKeySpec keyspec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(1, keyspec, gcmParameterSpec);
            encryptedMessage = cipher.doFinal(message.getBytes("UTF-8"));
        } catch (Exception var7) {
            log.info("error: " + var7);
            throw new InternalServerException("Err: error passing encryption");
        }

        String messageConverted = DatatypeConverter.printHexBinary(encryptedMessage);

        if (messageConverted.length() > 195) {
            messageConverted = messageConverted.substring(0, 195);
        }
        return messageConverted;
    }

    public String decrypt(String message) {
        String plainMessage = "";
        try {
            byte[] vectorBytes = DatatypeConverter.parseHexBinary("4ef56a7c01cd9ef32bc34ef56a7c89ab");
            byte[] keyBytes = DatatypeConverter.parseHexBinary("2bc34ef56a7c89ab4ef56a7c01cd9ed3");
            IvParameterSpec ivspec = new IvParameterSpec(vectorBytes);
            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, ivspec.getIV());
            SecretKeySpec keyspec = new SecretKeySpec(keyBytes, "AES");
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(2, keyspec, gcmParameterSpec);
            byte[] decryptedMessage = cipher.doFinal(DatatypeConverter.parseHexBinary(message));
            plainMessage = new String(decryptedMessage, "UTF-8");
        } catch (Exception var7) {
            log.info("error: " + var7);
            throw new InternalServerException("Err: error passing encryption");
        }
        return plainMessage;
    }
}
