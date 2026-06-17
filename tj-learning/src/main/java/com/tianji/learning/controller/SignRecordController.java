package com.tianji.learning.controller;


import com.tianji.learning.domain.vo.SignResultVO;
import com.tianji.learning.service.ISignRecordService;
import io.swagger.annotations.ApiOperation;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

/**
 * <p>
 * 签到记录表 前端控制器
 * </p>
 *
 * @author kaii
 * @since 2026-05-19
 */
@RestController
@RequestMapping("/sign-records")
@RequiredArgsConstructor
public class SignRecordController {
    private final ISignRecordService recordService;

    @ApiOperation("用户签到功能接口")
    @PostMapping
    public SignResultVO addSignRecords(){
        return recordService.addSignRecords();
    }

    @ApiOperation("查询用户签到记录")
    @GetMapping
    public int[] querySignRecords(){
        return recordService.querySignRecords();
    }
}
