package com.jwy.scd.api.idgen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 某个 bizTag 的号段运行状态，用于观察双 buffer 是否按预期工作
 * （对应 Leaf 的 {@code /cache} 监控页要展示的那些信息）。
 */
@Data
@Schema(description = "号段运行状态")
public class IdGenStatusDTO {

    @Schema(description = "业务标识", example = "order")
    private String bizTag;

    @Schema(description = "当前发号段的起点（含）", example = "1")
    private long currentBegin;

    @Schema(description = "当前发号段的终点（含）", example = "100000")
    private long currentEnd;

    @Schema(description = "当前段还剩多少没发出", example = "87321")
    private long currentRemaining;

    @Schema(description = "段长度（数据库 leaf_alloc.step）", example = "100000")
    private int step;

    @Schema(description = "预备段是否已就绪（true 表示段耗尽瞬间换指针即可，无需等 DB）", example = "true")
    private boolean nextReady;

    @Schema(description = "是否有预取任务正在执行", example = "false")
    private boolean prefetching;

    @Schema(description = "本服务实例累计发出多少号", example = "12679")
    private long issued;
}
