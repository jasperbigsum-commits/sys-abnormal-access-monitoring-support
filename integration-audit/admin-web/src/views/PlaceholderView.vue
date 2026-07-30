<script setup lang="ts">
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const title = computed(() => String(route.meta.title ?? '风险运营'));
</script>

<template>
    <section class="page-section placeholder-view">
        <header class="page-heading">
            <div>
                <h1>{{ title }}</h1>
                <p>异常访问监测与管理审计工作区</p>
            </div>
        </header>
        <div class="surface-panel placeholder-view__body">
            <span class="eyebrow">RISK OPERATIONS</span>
            <strong>{{ title }}</strong>
        </div>
    </section>
</template>
