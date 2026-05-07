package com.tianji.learning.service.impl;

import com.tianji.common.exceptions.BadRequestException;
import com.tianji.common.exceptions.BizIllegalException;
import com.tianji.common.utils.BeanUtils;
import com.tianji.common.utils.StringUtils;
import com.tianji.common.utils.UserContext;
import com.tianji.learning.domain.dto.QuestionFormDTO;
import com.tianji.learning.domain.po.InteractionQuestion;
import com.tianji.learning.mapper.InteractionQuestionMapper;
import com.tianji.learning.service.IInteractionQuestionService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 互动提问的问题表 服务实现类
 * </p>
 *
 * @author kaii
 * @since 2026-05-05
 */
@Service
public class InteractionQuestionServiceImpl extends ServiceImpl<InteractionQuestionMapper, InteractionQuestion> implements IInteractionQuestionService {

    /*
    * 保存提问
    * */
    @Override
    public void saveQuestion(QuestionFormDTO dto) {
        InteractionQuestion interactionQuestion = BeanUtils.copyBean(dto, InteractionQuestion.class);
        Long userId = UserContext.getUser();
        interactionQuestion.setUserId(userId);
        //保存到数据库中
        this.save(interactionQuestion);
    }

    @Override
    public void updateQuestion(Long id, QuestionFormDTO dto) {
        if(StringUtils.isBlank(dto.getTitle())||StringUtils.isBlank(dto.getDescription())||dto.getAnonymity() == null){
            throw new BizIllegalException("非法参数!");
        }
        InteractionQuestion question = this.getById(id);
        if(question==null){
            //查不到数据
            throw new BizIllegalException("非法参数!");
        }
        Long userId = UserContext.getUser();
        if(!userId.equals(question.getUserId())){
            //说明不是自己的问题
            throw new BadRequestException("不能修改他人数据!");
        }
        question = BeanUtils.copyBean(dto, InteractionQuestion.class);
        question.setId(id);
        //通过plus根据id修改数据库
        this.updateById(question);
    }
}
