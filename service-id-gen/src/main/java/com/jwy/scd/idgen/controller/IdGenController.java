package com.jwy.scd.idgen.controller;

import com.jwy.scd.api.idgen.IdGenApi;
import com.jwy.scd.api.idgen.dto.IdGenStatusDTO;
import com.jwy.scd.api.idgen.dto.SegmentDTO;
import com.jwy.scd.idgen.service.SegmentIdGen;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 发号接口实现：实现 service-id-gen-api 中定义的 {@link IdGenApi} 契约。
 *
 * <p>HTTP 映射（{@code /api/id/*}）全部继承自接口方法上的 {@code @GetMapping}，
 * 这里只写业务逻辑，保证「对外契约 = api 模块」这一条不被实现类偷偷改掉
 * （本项目的 service-system / service-order 也是同一个写法）。
 *
 * <p>注意这里<b>没有</b> {@code @EnableFeignClients} 的配合，本类是 provider 而不是消费者：
 * 它实现 {@link IdGenApi}，而 api 模块里的 {@code @FeignClient} 注解在 provider 侧
 * 不会被处理（pom 里也没有 openfeign 依赖）。
 */
@RestController
public class IdGenController implements IdGenApi {

    private final SegmentIdGen idGen;

    public IdGenController(SegmentIdGen idGen) {
        this.idGen = idGen;
    }

    /** 领取一个号段：无状态，直接向数据库要一段 */
    @Override
    public SegmentDTO getSegment(String bizTag) {
        return idGen.fetchSegment(bizTag);
    }

    /** 领取单个 ID：走本服务的双 buffer */
    @Override
    public long nextId(String bizTag) {
        return idGen.nextId(bizTag);
    }

    @Override
    public List<IdGenStatusDTO> status() {
        return idGen.status();
    }
}
