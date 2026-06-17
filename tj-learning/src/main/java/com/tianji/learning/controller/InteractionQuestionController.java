package com.tianji.learning.controller;


import com.tianji.common.domain.dto.PageDTO;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.query.QuestionPageQuery;
import com.tianji.learning.domain.vo.QuestionVO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * <p>
 * 互动提问的问题表 前端控制器
 * </p>
 *
 * @author kaii
 * @since 2026-05-05
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/questions")
public class InteractionQuestionController {
    private final IInteractionQuestionService questionService;

    @ApiOperation("新增提问")
    @PostMapping
    public void saveQuestion(@RequestBody QuestionFormDTO dto){
        questionService.saveQuestion(dto);
    }

    @ApiOperation("修改提问")
    @PutMapping("/{id}")
    public void updateQuestion(@PathVariable Long id,QuestionFormDTO dto){
        questionService.updateQuestion(id,dto);
    }

    @ApiOperation("分页查询")
    @GetMapping("/page")
    public PageDTO<QuestionVO> queryQuestionPage(@RequestBody QuestionPageQuery pageQuery){
        return questionService.queryQuestionPage(pageQuery);
    }

    @ApiOperation("根据ID查询提问")
    @GetMapping("/{id}")
    public QuestionVO queryQuestion(@PathVariable Long id){
        return questionService.queryQuestionById(id);
    }

    @ApiOperation("删除用户的提问")
    @DeleteMapping("/{id}")
    public void deleteQuestion(@PathVariable Long id){
        questionService.deleteQuestion(id);
    }
}
