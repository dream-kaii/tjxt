package com.tianji.learning.controller;


import com.tianji.learning.domain.po.PointsBoard;
import com.tianji.learning.domain.po.PointsBoardSeason;
import com.tianji.learning.service.IPointsBoardSeasonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author kaii
 * @since 2026-05-19
 */
@RestController
@RequestMapping("/boards")
@RequiredArgsConstructor
@Api
public class PointsBoardSeasonController {
    private final IPointsBoardSeasonService boardSeasonService;

    @ApiOperation("查询赛季列表功能")
    @GetMapping("/seasons/list")
    public List<PointsBoardSeason> querySeasonsed(){
        return boardSeasonService.querySeasonsed();
    }

}
