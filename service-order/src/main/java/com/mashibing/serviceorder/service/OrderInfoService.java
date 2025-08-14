package com.mashibing.serviceorder.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mashibing.internalcommon.constant.CommonStatusEnum;
import com.mashibing.internalcommon.constant.IdentityConstants;
import com.mashibing.internalcommon.constant.OrderConstants;
import com.mashibing.internalcommon.dto.Car;
import com.mashibing.internalcommon.dto.OrderInfo;
import com.mashibing.internalcommon.dto.PriceRule;
import com.mashibing.internalcommon.dto.ResponseResult;
import com.mashibing.internalcommon.request.OrderRequest;
import com.mashibing.internalcommon.request.PriceRuleIsNewRequest;
import com.mashibing.internalcommon.responese.OrderDriverResponse;
import com.mashibing.internalcommon.responese.TerminalResponse;
import com.mashibing.internalcommon.util.RedisPrefixUtils;
import com.mashibing.serviceorder.mapper.OrderInfoMapper;
import com.mashibing.serviceorder.remote.ServiceDriverUserClient;
import com.mashibing.serviceorder.remote.ServiceMapClient;
import com.mashibing.serviceorder.remote.ServicePriceClient;
import lombok.extern.slf4j.Slf4j;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author JPP
 * @since 2025-07-12
 */
@Service
@Slf4j
public class OrderInfoService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private ServicePriceClient servicePriceClient;

    @Autowired
    private ServiceDriverUserClient serviceDriverUserClient;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 新建订单
     * @param orderRequest
     * @return
     */
    public ResponseResult add(OrderRequest orderRequest) {

        // 测试当前城市是否有可用的司机
        ResponseResult<Boolean> availableDriver = serviceDriverUserClient.isAvailableDriver(orderRequest.getAddress());
        log.info("测试城市是否有司机结果："+availableDriver.getData());
        if (!availableDriver.getData()){
            return ResponseResult.fail(CommonStatusEnum.CITY_DRIVER_EMPTY.getCode(),CommonStatusEnum.CITY_DRIVER_EMPTY.getValue());
        }

        // 需要判断计价规则的版本是否为最新
        PriceRuleIsNewRequest priceRuleIsNewRequest = new PriceRuleIsNewRequest();
        priceRuleIsNewRequest.setFareType(orderRequest.getFareType());
        priceRuleIsNewRequest.setFareVersion(orderRequest.getFareVersion());
        ResponseResult<Boolean> aNew = servicePriceClient.isNew(priceRuleIsNewRequest);
        if (!(aNew.getData())){
            return ResponseResult.fail(CommonStatusEnum.PRICE_RULE_CHANGED.getCode(),CommonStatusEnum.PRICE_RULE_CHANGED.getValue());
        }

        // 需要判断 下单的设备是否是 黑名单设备
//        if (isBlackDevice(orderRequest)) {
//            return ResponseResult.fail(CommonStatusEnum.DEVICE_IS_BLACK.getCode(), CommonStatusEnum.DEVICE_IS_BLACK.getValue());
//        }

        // 判断：下单的城市和计价规则是否正常
        if(!isPriceRuleExists(orderRequest)){
            return ResponseResult.fail(CommonStatusEnum.CITY_SERVICE_NOT_SERVICE.getCode(),CommonStatusEnum.CITY_SERVICE_NOT_SERVICE.getValue());
        }

        // 判断乘客 是否有进行中的订单
        if (isPassengerOrderGoingon(orderRequest.getPassengerId()) > 0){
            return ResponseResult.fail(CommonStatusEnum.ORDER_GOING_ON.getCode(),CommonStatusEnum.ORDER_GOING_ON.getValue());
        }

        // 创建订单
        OrderInfo orderInfo = new OrderInfo();

        BeanUtils.copyProperties(orderRequest,orderInfo);

        orderInfo.setOrderStatus(OrderConstants.ORDER_START);

        LocalDateTime now = LocalDateTime.now();
        orderInfo.setGmtCreate(now);
        orderInfo.setGmtModified(now);

        orderInfoMapper.insert(orderInfo);
        dispatchRealTimeOrder(orderInfo);
        return ResponseResult.success("");
    }

    @Autowired
    private ServiceMapClient serviceMapClient;

//    @Autowired
//    private RedissonClient redissonClient;

    /**
     * 实时订单派单逻辑
     * 如果返回1：派单成功
     * @param orderInfo
     */
    public int dispatchRealTimeOrder(OrderInfo orderInfo){
        log.info("循环一次");
        int result = 0;

        //2km
        String depLatitude = orderInfo.getDepLatitude();
        String depLongitude = orderInfo.getDepLongitude();

        String center = depLatitude+","+depLongitude;

        List<Integer> radiusList = new ArrayList<>();
        radiusList.add(2000);
        radiusList.add(4000);
        radiusList.add(5000);
        // 搜索结果
        ResponseResult<List<TerminalResponse>> listResponseResult = null;
        // goto是为了测试。
        radius:
        for (int i=0;i<radiusList.size();i++){
            Integer radius = radiusList.get(i);
            listResponseResult = serviceMapClient.terminalAroundSearch(center,radius );

            log.info("在半径为"+radius+"的范围内，寻找车辆,结果："+ JSONArray.fromObject(listResponseResult.getData()).toString());
            
            // 获得终端  [{"carId":1578641048288702465,"tid":"584169988"}]

            // 解析终端
            List<TerminalResponse> data = listResponseResult.getData();
            // 为了测试是否从地图上获取到司机
//            List<TerminalResponse> data = new ArrayList<>();
            for (int j=0;j<data.size();j++){
                TerminalResponse terminalResponse = data.get(j);
                Long carId = terminalResponse.getCarId();

                String longitude = terminalResponse.getLongitude();
                String latitude = terminalResponse.getLatitude();

                // 查询是否有对于的可派单司机
                ResponseResult<OrderDriverResponse> availableDriver = serviceDriverUserClient.getAvailableDriver(carId);
                if(availableDriver.getCode() == CommonStatusEnum.AVAILABLE_DRIVER_EMPTY.getCode()){
                    log.info("没有车辆ID："+carId+",对于的司机");
                    continue;
                } else {
                    log.info("车辆ID："+carId+"找到了正在出车的司机");

                    OrderDriverResponse orderDriverResponse = availableDriver.getData();
                    Long driverId = orderDriverResponse.getDriverId();
                    String driverPhone = orderDriverResponse.getDriverPhone();
                    String licenseId = orderDriverResponse.getLicenseId();
                    String vehicleNo = orderDriverResponse.getVehicleNo();
                    String vehicleTypeFromCar = orderDriverResponse.getVehicleType();

                    // 判断车辆的车型是否符合？
                    String vehicleType = orderInfo.getVehicleType();
                    if (!vehicleType.trim().equals(vehicleTypeFromCar.trim())){
                        System.out.println("车型不符合");
                        continue ;
                    }

//                    String lockKey = (driverId+"").intern();
//                    RLock lock = redissonClient.getLock(lockKey);
//                    lock.lock();
//
                    // 判断司机 是否有进行中的订单
                    if (isDriverOrderGoingon(driverId) > 0){
//                        lock.unlock();
                        continue ;
                    }
                    // 订单直接匹配司机
                    // 查询当前车辆信息
                    QueryWrapper<Car> carQueryWrapper = new QueryWrapper<>();
                    carQueryWrapper.eq("id",carId);
//
//                    // 设置订单中和司机车辆相关的信息
//                    orderInfo.setDriverId(driverId);
//                    orderInfo.setDriverPhone(driverPhone);
//                    orderInfo.setCarId(carId);
//                    // 从地图中来
//                    orderInfo.setReceiveOrderCarLongitude(longitude);
//                    orderInfo.setReceiveOrderCarLatitude(latitude);
//
//                    orderInfo.setReceiveOrderTime(LocalDateTime.now());
//                    orderInfo.setLicenseId(licenseId);
//                    orderInfo.setVehicleNo(vehicleNo);
//                    orderInfo.setOrderStatus(OrderConstants.DRIVER_RECEIVE_ORDER);
//
//                    orderInfoMapper.updateById(orderInfo);
//
//                    // 通知司机
//                    JSONObject driverContent = new  JSONObject();
//
//                    driverContent.put("orderId",orderInfo.getId());
//                    driverContent.put("passengerId",orderInfo.getPassengerId());
//                    driverContent.put("passengerPhone",orderInfo.getPassengerPhone());
//                    driverContent.put("departure",orderInfo.getDeparture());
//                    driverContent.put("depLongitude",orderInfo.getDepLongitude());
//                    driverContent.put("depLatitude",orderInfo.getDepLatitude());
//
//                    driverContent.put("destination",orderInfo.getDestination());
//                    driverContent.put("destLongitude",orderInfo.getDestLongitude());
//                    driverContent.put("destLatitude",orderInfo.getDestLatitude());

//                    PushRequest pushRequest = new PushRequest();
//                    pushRequest.setUserId(driverId);
//                    pushRequest.setIdentity(IdentityConstants.DRIVER_IDENTITY);
//                    pushRequest.setContent(driverContent.toString());

                }
            }

        }
        return result;
    }

    /**
     * 计价规则是否存在
     * @param orderRequest
     * @return
     */
    private boolean isPriceRuleExists(OrderRequest orderRequest){
        String fareType = orderRequest.getFareType();
        int index = fareType.indexOf("$");
        String cityCode = fareType.substring(0, index);
        String vehicleType = fareType.substring(index + 1);

        PriceRule priceRule = new PriceRule();
        priceRule.setCityCode(cityCode);
        priceRule.setVehicleType(vehicleType);

        ResponseResult<Boolean> booleanResponseResult = servicePriceClient.ifPriceExists(priceRule);
        return booleanResponseResult.getData();

    }

    /**
     * 是否是黑名单
     * @param orderRequest
     * @return
     */
    private boolean isBlackDevice(OrderRequest orderRequest) {
        String deviceCode = orderRequest.getDeviceCode();
        // 生成key
        String deviceCodeKey = RedisPrefixUtils.blackDeviceCodePrefix + deviceCode;
        Boolean aBoolean = stringRedisTemplate.hasKey(deviceCodeKey);
        if (aBoolean){
            String s = stringRedisTemplate.opsForValue().get(deviceCodeKey);
            int i = Integer.parseInt(s);
            if (i >= 2){
                // 当前设备超过下单次数
                return true;
            }else {
                stringRedisTemplate.opsForValue().increment(deviceCodeKey);
            }

        }else {
            stringRedisTemplate.opsForValue().setIfAbsent(deviceCodeKey,"1",1L, TimeUnit.HOURS);
        }
        return false;
    }

    /**
     * 判断是否有 业务中的订单
     * @param passengerId
     * @return
     */
    private int isPassengerOrderGoingon(Long passengerId){
        // 判断有正在进行的订单不允许下单
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("passenger_id",passengerId);
        queryWrapper.and(wrapper->wrapper.eq("order_status",OrderConstants.ORDER_START)
                .or().eq("order_status",OrderConstants.DRIVER_RECEIVE_ORDER)
                .or().eq("order_status",OrderConstants.DRIVER_TO_PICK_UP_PASSENGER)
                .or().eq("order_status",OrderConstants.DRIVER_ARRIVED_DEPARTURE)
                .or().eq("order_status",OrderConstants.PICK_UP_PASSENGER)
                .or().eq("order_status",OrderConstants.PASSENGER_GETOFF)
                .or().eq("order_status",OrderConstants.TO_START_PAY)
        );

        Integer validOrderNumber = orderInfoMapper.selectCount(queryWrapper);

        return validOrderNumber;

    }

    /**
     * 判断是否有 业务中的订单
     * @param driverId
     * @return
     */
    private int isDriverOrderGoingon(Long driverId){
        // 判断有正在进行的订单不允许下单
        QueryWrapper<OrderInfo> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("driver_id",driverId);
        queryWrapper.and(wrapper->wrapper
                .eq("order_status",OrderConstants.DRIVER_RECEIVE_ORDER)
                .or().eq("order_status",OrderConstants.DRIVER_TO_PICK_UP_PASSENGER)
                .or().eq("order_status",OrderConstants.DRIVER_ARRIVED_DEPARTURE)
                .or().eq("order_status",OrderConstants.PICK_UP_PASSENGER)

        );


        Integer validOrderNumber = orderInfoMapper.selectCount(queryWrapper);
        log.info("司机Id："+driverId+",正在进行的订单的数量："+validOrderNumber);

        return validOrderNumber;

    }



}
