package com.hmdp.amap.dto;

import lombok.Data;

import java.util.Map;

@Data
public class AmapPoi {
    private String id;
    private String name;
    private String type;
    private String address;
    private String location;
    private String distance;
    private String pname;
    private String cityname;
    private String adname;
    private Map<String, Object> bizExt;
}
