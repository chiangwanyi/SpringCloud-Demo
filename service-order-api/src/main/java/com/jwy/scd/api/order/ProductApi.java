package com.jwy.scd.api.order;

import com.jwy.scd.api.order.dto.ProductDTO;
import com.jwy.scd.api.order.dto.ProductSaveDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * service-order 对外提供的商品服务契约。
 *
 * <p>说明：本项目为学习演示，商品与库存暂时放在 service-order 自己的库里
 * （好处是「扣库存 + 写订单」处于同一个本地事务，不必引入分布式事务即可保证一致）。
 * 待后续学习 Seata 时，再把商品库存抽成独立的 service-product，
 * 那时「跨服务扣库存」就自然变成了分布式事务的真实场景。
 *
 * <p>路径前缀 {@code /api/product} 写在每个方法上，原因见 {@link OrderApi}。
 */
@FeignClient(name = "service-order", contextId = "productApi")
@Tag(name = "商品管理", description = "商品查询、新增、修改与删除")
public interface ProductApi {

    @Operation(summary = "按主键查询商品", description = "根据商品主键 id 返回商品详情")
    @GetMapping("/api/product/{id}")
    ProductDTO getProduct(@Parameter(description = "商品主键 ID", example = "1") @PathVariable("id") Long id);

    @Operation(summary = "查询商品列表", description = "返回所有已上架/下架商品")
    @GetMapping("/api/product/list")
    List<ProductDTO> listProducts();

    @Operation(summary = "新增商品", description = "创建一个商品，成功返回商品信息")
    @PostMapping("/api/product")
    ProductDTO createProduct(@RequestBody ProductSaveDTO dto);

    @Operation(summary = "修改商品", description = "按主键更新商品（名称/价格/库存/状态）")
    @PutMapping("/api/product/{id}")
    ProductDTO updateProduct(@Parameter(description = "商品主键 ID", example = "1") @PathVariable("id") Long id,
                             @RequestBody ProductSaveDTO dto);

    @Operation(summary = "删除商品", description = "按主键逻辑删除商品（del_flag = 1）")
    @DeleteMapping("/api/product/{id}")
    void deleteProduct(@Parameter(description = "商品主键 ID", example = "1") @PathVariable("id") Long id);
}
