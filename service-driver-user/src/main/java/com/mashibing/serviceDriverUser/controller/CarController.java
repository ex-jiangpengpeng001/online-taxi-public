package com.mashibing.serviceDriverUser.controller;

import com.mashibing.internalcommon.dto.Car;
import com.mashibing.internalcommon.dto.ResponseResult;
import com.mashibing.serviceDriverUser.service.CarService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author JPP
 * @since 2025-06-26
 */
@RestController
public class CarController {

    @Autowired
    private CarService carService;

    @PostMapping("/car")
    public ResponseResult addCar(@RequestBody Car car){
        return carService.addCar(car);
    }

    @GetMapping("/car")
    public ResponseResult<Car> getCarById(Long carId){

        return carService.getCarById(carId);
    }
}
