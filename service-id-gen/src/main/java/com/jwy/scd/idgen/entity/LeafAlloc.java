package com.jwy.scd.idgen.entity;

import lombok.Data;

/**
 * 号段分配表 {@code leaf_alloc} 的一行 —— 一行就是一个业务标签（bizTag）的号段账本。
 *
 * <p>{@link #maxId} 的语义是「<b>已经批发出去的最大值</b>」，不是「下一个可用值」。
 * 每次取段把它整体加上 {@link #step}，新段就是 {@code [maxId - step + 1, maxId]}。
 *
 * <p>种子行的 {@code max_id} 必须初始化成 <b>0</b>（而不是 Leaf 官方 DDL 的默认值 1），
 * 这样第一个段是 {@code [1, step]}、ID 从 1 开始。沿用 1 的话第一个段会是
 * {@code [2, step + 1]}——浪费一个号且反直觉。
 */
@Data
public class LeafAlloc {

    /** 业务标识，主键，同时也是「冲突域」：不同 bizTag 的号段互不相干 */
    private String bizTag;

    /** 已批发出去的最大值 */
    private Long maxId;

    /** 每次批发的段长度。放在数据库里，改 step 不需要重启服务 */
    private Integer step;

    private String description;
}
