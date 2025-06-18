package com.mashibing.servicemap.service;

import com.mashibing.internalcommon.dto.ResponseResult;
import com.mashibing.internalcommon.responese.DirectionResponse;
import org.springframework.stereotype.Service;

@Service
public class DirectionService {

    public ResponseResult driving(String depLongitude,String depLatitude, String destLongitude,String destLatitude) {

        DirectionResponse direction = new DirectionResponse();
        direction.setDistance(123);
        direction.setDuration(11);
        return ResponseResult.success(direction);
    }

}
