<template>
  <div>
    <section class="panel page-heading-panel">
      <span class="eyebrow">Favorites</span>
      <h1>我的收藏</h1>
      <p class="muted">集中查看已经收藏的学习资料。</p>
    </section>
    <div class="grid page-grid">
      <ResourceCard v-for="item in records" :key="item.id" :resource="item" />
    </div>
    <el-empty v-if="records.length === 0" description="暂无收藏" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { favoriteApi } from '../api'
import ResourceCard from '../components/ResourceCard.vue'

const records = ref([])

onMounted(async () => {
  records.value = await favoriteApi.list()
})
</script>
