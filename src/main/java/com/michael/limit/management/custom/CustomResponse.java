package com.michael.limit.management.custom;

import java.util.HashMap;
import java.util.Map;

public class CustomResponse {

    public static Map<String, Object> response(String message, String status, Object responseObj) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("responseMsg", message);
        map.put("responseCode", status);
        map.put("responseDetails", responseObj);

        return map;
    }

    public static Map<String, Object> responseNoObject(String message, String status) {
        Map<String, Object> map = new HashMap<String, Object>();
        map.put("responseMsg", message);
        map.put("responseCode", status);

        return map;
    }
}
