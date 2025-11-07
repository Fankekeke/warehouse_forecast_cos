package cc.mrbird.febs.cos.service;

// 预测服务接口
public interface IPredictionService {
    /**
     * 预测出库量
     *
     * @param name   物品名称
     * @param typeId 物品ID
     * @param days   预测天数
     * @return 预测结果
     */
    PredictionResult predictSales(String name, Integer typeId, Integer days);

    /**
     * 预测库存需求
     *
     * @param name   物品名称
     * @param typeId 物品ID
     * @return 库存需求预测结果
     */
    PredictionResult predictInventoryDemand(String name, Integer typeId, Integer days);
}