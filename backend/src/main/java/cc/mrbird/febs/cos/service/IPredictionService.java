package cc.mrbird.febs.cos.service;

import cc.mrbird.febs.cos.entity.PredictionResult;

// 预测服务接口
public interface IPredictionService {
    /**
     * 预测出库量
     *
     * @param name   物品名称
     * @param typeId 物品ID
     * @return 预测结果
     */
    PredictionResult predictSales(String name, Integer typeId);

    /**
     * 预测库存需求
     *
     * @param name   物品名称
     * @param typeId 物品ID
     * @return 库存需求预测结果
     */
    PredictionResult predictInventoryDemand(String name, Integer typeId);
}