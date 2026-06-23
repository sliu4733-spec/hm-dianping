package com.hmdp.amap.dto;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class AmapAroundResponse {
    private String status;
    private String info;
    private String infocode;
    private String count;
    private List<AmapPoi> pois = Collections.emptyList();

    public boolean success() {
        return "1".equals(status);
    }
}
