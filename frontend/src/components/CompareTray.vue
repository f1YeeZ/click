<script setup>
import { useRouter } from 'vue-router'
import { useCompareStore } from '../stores/compare'
const compare = useCompareStore()
const router = useRouter()
const go = () => {
  router.push({ path: '/compare', query: { ids: compare.ids.join(',') } })
}
</script>

<template>
  <aside v-if="compare.items.length" class="compare-tray">
    <div><span class="eyebrow">COMPARE QUEUE</span><strong>{{ compare.items.length }} / 4 款已选择</strong></div>
    <div class="tray-items"><span class="tray-chip" v-for="item in compare.items" :key="item.id">{{ item.displayName }}</span></div>
    <div class="tray-actions"><button class="text-button" @click="compare.clear">清空</button><button class="button" @click="go">进入对比 →</button></div>
  </aside>
</template>
