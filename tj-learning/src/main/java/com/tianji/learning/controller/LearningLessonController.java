package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.common.domain.query.PageQuery;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.vo.LearningLessonVO;
import com.tianji.learning.service.ILearningLessonService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.codehaus.groovy.classgen.FinalVariableAnalyzer;
import org.springframework.web.bind.annotation.*;

import javax.swing.text.html.parser.TagElement;
import java.util.ArrayList;
import java.util.List;

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

    @ApiOperation("查询我正在学习的课程")
    @GetMapping("/now")
    public LearningLessonVO queryMyCurrentLesson(){
        return lessonService.queryMyCurrentLesson();
    }

    @ApiOperation("查询课程的学习状态")
    @GetMapping("/{courseId}")
    public LearningLessonVO queryMyLessonStatus(@PathVariable("courseId") Long courseId){
        return lessonService.queryMyLessonStatus(courseId);
    }

    @ApiOperation("删除当前用户的课程")
    @DeleteMapping("/{courseId}")
    public void deleteMyLesson(@PathVariable("courseId") Long courseId){
        lessonService.deleteMyLesson(List.of(courseId));
    }

    @ApiOperation("查询用户课程是否有效")
    @GetMapping("/{courseId}/valid")
    public Long isLessonValid(@PathVariable("courseId") Long courseId){
        return lessonService.isLessonValid(courseId);
    }

}
