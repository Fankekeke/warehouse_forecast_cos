package cc.mrbird.febs.cos.service.impl;

import cc.mrbird.febs.cos.dao.StockInfoMapper;
import cc.mrbird.febs.cos.entity.*;
import cc.mrbird.febs.cos.service.IPredictionService;
import cc.mrbird.febs.cos.service.IStockInfoService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class PredictionServiceImpl implements IPredictionService {

    private final StockInfoMapper stockInfoMapper;

    @Override
    public PredictionResult predictSales(String name, Integer typeId) {
        // 获取历史出库数据（例如过去30天）
        List<Sale> historicalSales = stockInfoMapper.getHistoricalSales(name, typeId);

        // 数据预处理
        double[] salesData = historicalSales.stream()
                .filter(Objects::nonNull)
                .mapToDouble(Sale::getQuantity)
                .toArray();

        // 使用LSTM进行预测
        PredictionResult result = performLSTMPrediction(salesData, 30);
        // 生成未来15天的日期列表
        List<String> predictionDates = generateFutureDates(15);
        // 设置日期信息
        result.setDates(predictionDates);

        return result;
    }

    @Override
    public PredictionResult predictInventoryDemand(String name, Integer typeId) {
        // 获取历史库存数据
        List<Inventory> historicalInventory = stockInfoMapper.getHistoricalInventory(name, typeId);

        // 数据预处理
        double[] inventoryData = historicalInventory.stream()
                .filter(Objects::nonNull)
                .mapToDouble(Inventory::getQuantity)
                .toArray();

        // 使用LSTM进行预测
        PredictionResult result = performLSTMPrediction(inventoryData, 15);
        // 生成未来15天的日期列表
        List<String> predictionDates = generateFutureDates(15);
        // 设置日期信息
        result.setDates(predictionDates);

        return result;
    }

    private PredictionResult performLSTMPrediction(double[] historicalData, int days) {
        // 数据归一化
        double[] normalizedData = normalizeData(historicalData);

        // 构建时间序列数据集
        List<double[]> sequences = createTimeSeriesSequences(normalizedData, 10);

        // 计算趋势因子
        double trendFactor = calculateTrendFactor(historicalData);

        // 构建和训练LSTM模型（简化实现）
        // 在实际应用中，这里会使用深度学习框架如DL4J或调用Python服务
        double[] predictions = new double[days];

        // 简化的LSTM预测逻辑（实际实现需要深度学习框架）
        if (sequences.size() > 0) {
            double[] lastSequence = sequences.get(sequences.size() - 1);
            // 基于最后一个序列进行预测（简化版）
            for (int i = 0; i < days; i++) {
                // 简单移动平均作为示例
                double sum = 0;
                for (double val : lastSequence) {
                    sum += val;
                }
                double basePrediction = sum / lastSequence.length;

                // 应用趋势因子，区分上升和下降趋势
                if (trendFactor > 0) {
                    // 上升趋势
                    predictions[i] = basePrediction * (1 + Math.abs(trendFactor) * (i + 1) * 0.1);
                } else if (trendFactor < 0) {
                    // 下降趋势
                    predictions[i] = basePrediction * (1 - Math.abs(trendFactor) * (i + 1) * 0.1);
                } else {
                    // 无明显趋势
                    predictions[i] = basePrediction;
                }
            }
        } else {
            // 默认预测值
            Arrays.fill(predictions, 0.0);
        }

        // 反归一化预测结果
        double[] denormalizedPredictions = denormalizeData(predictions, historicalData);

        // 计算置信度（基于数据稳定性和趋势一致性）
        double confidence = calculateConfidence(historicalData, trendFactor);

        // 转换为List<Double>并保留一位小数
        List<Double> predictionList = new ArrayList<>();
        for (double value : denormalizedPredictions) {
            predictionList.add(new BigDecimal(value).setScale(1, RoundingMode.HALF_UP).doubleValue());
        }

        // 创建并返回包含预测值和置信度的结果对象
        PredictionResult result = new PredictionResult();
        result.setPredictedValues(predictionList);
        result.setConfidence(confidence);

        return result;
    }

    /**
     * 计算模型置信度
     * @param data 历史数据
     * @param trendFactor 趋势因子
     * @return 置信度值（0-1之间）
     */
    private double calculateConfidence(double[] data, double trendFactor) {
        if (data.length < 2) {
            return 0.0;
        }

        // 计算数据的标准差作为稳定性指标
        double mean = Arrays.stream(data).average().orElse(0.0);
        double variance = Arrays.stream(data)
                .map(x -> Math.pow(x - mean, 2))
                .average()
                .orElse(0.0);
        double stdDev = Math.sqrt(variance);

        // 标准化标准差（值越小表示数据越稳定，置信度越高）
        double stabilityFactor = 1.0 / (1.0 + stdDev / (mean + 1e-10)); // 避免除零

        // 趋势强度因子（趋势越明显，置信度越高）
        double trendStrength = Math.abs(trendFactor);

        // 综合置信度计算
        double confidence = 0.7 * stabilityFactor + 0.3 * Math.min(trendStrength, 1.0);

        // 确保置信度在合理范围内
        return Math.max(0.0, Math.min(1.0, confidence));
    }

    /**
     * 计算趋势因子
     * @param data 历史数据
     * @return 趋势因子，正值表示上升趋势，负值表示下降趋势
     */
    private double calculateTrendFactor(double[] data) {
        if (data.length < 2) {
            return 0.0;
        }

        // 使用线性回归计算趋势
        int n = data.length;
        double sumX = 0, sumY = 0, sumXY = 0, sumXX = 0;

        for (int i = 0; i < n; i++) {
            sumX += i;
            sumY += data[i];
            sumXY += i * data[i];
            sumXX += i * i;
        }

        // 计算斜率作为趋势因子
        double slope = (n * sumXY - sumX * sumY) / (n * sumXX - sumX * sumX);

        // 标准化趋势因子
        double mean = sumY / n;
        double trendFactor = mean != 0 ? slope / Math.abs(mean) : 0;

        return trendFactor;
    }

    /**
     * 数据归一化
     *
     * @param data 原始数据
     * @return 归一化后的数据
     */
    private double[] normalizeData(double[] data) {
        if (data.length == 0) {
            return new double[0];
        }

        double min = Arrays.stream(data).min().orElse(0.0);
        double max = Arrays.stream(data).max().orElse(1.0);
        double range = max - min;

        return Arrays.stream(data)
                .map(val -> range == 0 ? 0.0 : (val - min) / range)
                .toArray();
    }

    /**
     * 数据反归一化
     *
     * @param normalizedData 归一化数据
     * @param originalData   原始数据（用于获取min/max值）
     * @return 反归一化后的数据
     */
    private double[] denormalizeData(double[] normalizedData, double[] originalData) {
        if (originalData.length == 0) {
            return normalizedData;
        }

        double min = Arrays.stream(originalData).min().orElse(0.0);
        double max = Arrays.stream(originalData).max().orElse(1.0);
        double range = max - min;

        return Arrays.stream(normalizedData)
                .map(val -> val * range + min)
                .toArray();
    }

    /**
     * 创建时间序列序列
     *
     * @param data           数据
     * @param sequenceLength 序列长度
     * @return 时间序列序列列表
     */
    private List<double[]> createTimeSeriesSequences(double[] data, int sequenceLength) {
        List<double[]> sequences = new ArrayList<>();

        for (int i = 0; i <= data.length - sequenceLength; i++) {
            double[] sequence = new double[sequenceLength];
            System.arraycopy(data, i, sequence, 0, sequenceLength);
            sequences.add(sequence);
        }

        return sequences;
    }

    /**
     * 生成未来指定天数的日期列表
     * @param days 天数
     * @return 日期字符串列表
     */
    private List<String> generateFutureDates(int days) {
        List<String> dates = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (int i = 1; i <= days; i++) {
            LocalDate futureDate = today.plusDays(i);
            dates.add(futureDate.toString()); // 格式: yyyy-MM-dd
        }
        return dates;
    }
}
