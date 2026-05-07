package com.tianji.learning.controller;


import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.service.IInteractionQuestionService;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.springframework.web.bind.annotation.RestController;

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
    public void saveQuestion(QuestionFormDTO dto){
        questionService.saveQuestion(dto);
    }

    @ApiOperation("修改提问")
    @PutMapping("/{id}")
    public void updateQuestion(Long id,QuestionFormDTO dto){
        questionService.updateQuestion(id,dto);
    }

}
