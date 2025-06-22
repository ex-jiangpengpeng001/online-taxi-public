package com.mashibing.servicemap.service;

import com.mashibing.internalcommon.constant.AmapConfigConstants;
import com.mashibing.internalcommon.dto.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DicDistrictService {

    @Value("${amap.key}")
    private String amapKey;

    public ResponseResult initDicDistrict(String keywords){

        StringBuilder url = new StringBuilder();
        url.append(AmapConfigConstants.DISTRICT_URL);
        url.append("?");
        url.append("keywords=");
        url.append(keywords);
        url.append("&");
        url.append("subdistrict=3");
        url.append("&");
        url.append("key=");
        url.append(amapKey);
        log.info("url: " + url.toString());


        return ResponseResult.success();
    }
}
