package com.mashibing.servicemap.service;

import com.mashibing.internalcommon.dto.ResponseResult;
import com.mashibing.internalcommon.responese.DirectionResponse;
import com.mashibing.servicemap.remote.MapDirectionClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class DirectionService {

    @Autowired
    private MapDirectionClient mapDirectionClient;

    public ResponseResult driving(String depLongitude,String depLatitude, String destLongitude,String destLatitude) {

        // 调用第三方地图接口
        mapDirectionClient.direction(depLongitude, depLatitude, destLongitude, destLatitude);

        DirectionResponse direction = new DirectionResponse();
        direction.setDistance(123);
        direction.setDuration(11);
        return ResponseResult.success(direction);
    }

}
