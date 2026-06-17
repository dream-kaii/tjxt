package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.query.QuestionAdminPageQuery;
import com.tianji.learning.domain.vo.QuestionAdminVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author 虎哥
 */
@RestController
@RequestMapping("/admin/questions")
@Api(tags = "互动问答相关接口")
@RequiredArgsConstructor
public class InteractionQuestionAdminController {

    private final IInteractionQuestionService questionService;

    @ApiOperation("管理端分页查询互动问题")
    @GetMapping("/page")
    public PageDTO<QuestionAdminVO> queryQuestionPageAdmin(QuestionAdminPageQuery query){
        return questionService.queryQuestionPageAdmin(query);
    }

    @ApiOperation("管理端隐藏或显示问题")
    @PutMapping("/{id}/hidden/{hidden}")
    public void updateQuestionHidden(@PathVariable("id") Long id,@PathVariable("hidden") Boolean hidden){
        questionService.updateQuestionHidden(id,hidden);
    }

    @ApiOperation("管理端查询详情互动问题")
    @GetMapping("/{id}")
    public QuestionAdminVO queryQuestionAdmin(@PathVariable Long id){
        return questionService.queryQuestionAdmin(id);
    }
}