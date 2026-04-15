package com.tianji.learning.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.tianji.api.dto.IdAndNumDTO;
import com.tianji.learning.domain.po.LearningRecord;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;


/**
 * <p>
 * 学生学习计划表 Mapper 接口
 * </p>
 *
 * @author kaii
 * @since 2026-04-12
 */
public interface LearningRecordMapper extends BaseMapper<LearningRecord> {


}
