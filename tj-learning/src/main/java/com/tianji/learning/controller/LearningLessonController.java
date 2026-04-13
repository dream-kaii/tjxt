package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.codehaus.groovy.classgen.FinalVariableAnalyzer;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

import javax.swing.text.html.parser.TagElement;

/**
 * <p>
 * 学生课表 前端控制器
 * </p>
 *
 * @author kaii
 * @since 2026-04-12
 */
@RestController
@Api(tags = "我的课表相关接口")
@RequiredArgsConstructor
@RequestMapping("/lesson")
public class LearningLessonController {

    private final ILearningLessonService lessonService;

    @ApiOperation("查询我的课表，排序字段latest_learn_time:学习时间排序,create_time:购买时间排序")
    @GetMapping("/page")
    public PageDTO<LearningLessonVO> queryMyLessons(@RequestBody PageQuery pageQuery){
        return lessonService.queryMyLessons(pageQuery);
    }

}
