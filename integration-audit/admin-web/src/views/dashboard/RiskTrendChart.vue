<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent } from 'echarts/components';
import { init, use, type ECharts } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';

import type { TrendPoint } from '@/domain/monitoring';

const props = defineProps<{ points: TrendPoint[] }>();
const container = ref<HTMLElement>();
let chart: ECharts | undefined;
let resizeObserver: ResizeObserver | undefined;

use([LineChart, GridComponent, TooltipComponent, CanvasRenderer]);

function render(): void {
    if (!chart) return;
    chart.setOption({
        animationDuration: 350,
        grid: { left: 38, right: 18, top: 28, bottom: 32 },
        tooltip: { trigger: 'axis', valueFormatter: (value: unknown) => `${String(value)} 次` },
        xAxis: {
            type: 'category',
            boundaryGap: false,
            data: props.points.map((point) => new Date(point.occurredAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })),
            axisLine: { lineStyle: { color: '#b9c9c7' } },
            axisLabel: { color: '#66787e' }
        },
        yAxis: {
            type: 'value',
            minInterval: 1,
            axisLabel: { color: '#66787e' },
            splitLine: { lineStyle: { color: '#e6eceb' } }
        },
        series: [{
            name: '风险事件',
            type: 'line',
            smooth: 0.25,
            symbolSize: 7,
            data: props.points.map((point) => point.value),
            lineStyle: { width: 3, color: '#208078' },
            itemStyle: { color: '#208078', borderColor: '#ffffff', borderWidth: 2 },
            areaStyle: { color: 'rgba(32,128,120,.12)' },
            label: { show: true, position: 'top', color: '#315d5a', fontWeight: 600 }
        }]
    });
}

onMounted(() => {
    if (!container.value) return;
    chart = init(container.value);
    render();
    if (typeof ResizeObserver !== 'undefined') {
        resizeObserver = new ResizeObserver(() => chart?.resize());
        resizeObserver.observe(container.value);
    }
});
watch(() => props.points, render, { deep: true });
onBeforeUnmount(() => {
    resizeObserver?.disconnect();
    chart?.dispose();
});
</script>

<template>
    <div ref="container" class="risk-trend-chart" role="img" :aria-label="`近时段风险趋势，共 ${points.length} 个数据点`" />
</template>
