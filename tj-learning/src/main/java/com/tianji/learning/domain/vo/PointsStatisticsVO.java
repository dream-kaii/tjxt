package com.tianji.learning.domain.vo;


import com.tianji.learning.enums.PointsRecordType;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@ApiModel("今日积分结果")
public class PointsStatisticsVO {
    @ApiModelProperty("积分类型")
    private PointsRecordType type;
    @ApiModelProperty("当前已获取积分")
    private Integer points;
    @ApiModelProperty("今日上限积分")
    private Integer maxPoints;

}
