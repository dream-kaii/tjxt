package com.tianji.learning.service;

import com.tianji.learning.domain.po.SignRecord;
import com.baomidou.mybatisplus.extension.service.IService;
import com.tianji.learning.domain.vo.SignResultVO;

/**
 * <p>
 * 签到记录表 服务类
 * </p>
 *
 * @author kaii
 * @since 2026-05-19
 */
public interface ISignRecordService extends IService<SignRecord> {

    SignResultVO addSignRecords();

    int[] querySignRecords();

}
