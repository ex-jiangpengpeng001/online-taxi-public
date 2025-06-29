package com.mashibing.apiDriver.service;

import com.mashibing.internalcommon.dto.ResponseResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class VerificationCodeService {


    public ResponseResult checkAndsendVerificationCode(String driverPhone){



        return ResponseResult.success("");
    }
}