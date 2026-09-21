package com.jwy.scd.api.idgen.dto;

import com.jwy.scd.api.idgen.support.Segment;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 一个号段（闭区间 {@code [begin, end]}）。
 *
 * <p>这是 {@code /api/id/segment} 的返回值，也是「号段下放」架构里唯一的跨进程契约：
 * service-id-gen 把一段连续号码批发给业务服务，业务服务在本地把它发完，
 * 期间一次网络请求都不需要。
 */
@Data
@Schema(description = "号段：一段连续的、已独占分配给调用方的 ID 区间")
public class SegmentDTO {

    @Schema(description = "业务标识", example = "order")
    private String bizTag;

    @Schema(description = "段内起始 ID（含）", example = "1")
    private long begin;

    @Schema(description = "段内结束 ID（含）", example = "100000")
    private long end;

    @Schema(description = "段长度", example = "100000")
    private int step;

    /** 把传输对象转成可发号的 {@link Segment}（游标从 begin 开始） */
    public Segment toSegment() {
        return new Segment(begin, end);
    }

    public static SegmentDTO of(String bizTag, long begin, long end) {
        SegmentDTO dto = new SegmentDTO();
        dto.setBizTag(bizTag);
        dto.setBegin(begin);
        dto.setEnd(end);
        long len = end - begin + 1;
        dto.setStep((int) Math.min(Integer.MAX_VALUE, len));
        return dto;
    }
}
