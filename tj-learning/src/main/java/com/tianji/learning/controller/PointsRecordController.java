package com.tianji.learning.controller;


import com.tianji.learning.domain.vo.PointsStatisticsVO;
import com.tianji.learning.service.IPointsRecordService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 * 学习积分记录，每个月底清零 前端控制器
 * </p>
 *
 * @author kaii
 * @since 2026-05-19
 */
@RestController
@RequestMapping("/points")
@RequiredArgsConstructor
@Api
public class PointsRecordController {
    private final IPointsRecordService recordService;

    @ApiOperation("查询用户今日积分情况")
    @GetMapping("/today")
    public List<PointsStatisticsVO> queryMyPointsToday(){
        return recordService.queryMyPointsToday();
    }
}
