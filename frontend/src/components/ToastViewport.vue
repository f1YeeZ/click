<script setup>
import { toastState, dismissToast } from '../services/toast'
</script>

<template>
  <Teleport to="body">
    <TransitionGroup class="app-toast-stack" name="app-toast" tag="div" aria-live="polite" aria-atomic="false">
      <div v-for="toast in toastState.items" :key="toast.id" class="app-toast" :class="toast.type" :role="toast.type === 'error' ? 'alert' : 'status'">
        <span class="app-toast-icon" aria-hidden="true">{{ toast.type === 'error' ? '!' : toast.type === 'info' ? 'i' : '✓' }}</span>
        <p><strong>{{ toast.title }}</strong><span>{{ toast.message }}</span></p>
        <button type="button" aria-label="关闭提示" @click="dismissToast(toast.id)">×</button>
      </div>
    </TransitionGroup>
  </Teleport>
</template>
