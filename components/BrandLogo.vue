<script setup lang="ts">
withDefaults(
  defineProps<{
    tone?: 'light' | 'dark'
    size?: 'sm' | 'md' | 'lg'
  }>(),
  {
    tone: 'dark',
    size: 'md'
  }
)
</script>

<template>
  <span :class="['brand-lockup', `brand-lockup--${tone}`, `brand-lockup--${size}`]">
    <span class="brand-mark" aria-hidden="true">
      <svg viewBox="0 0 64 64" role="img">
        <rect class="mark-shell" x="7" y="7" width="50" height="50" rx="12" />
        <path class="mark-loop" d="M22 24c0-4 3-7 7-7h10c4 0 7 3 7 7v10c0 4-3 7-7 7h-8" />
        <path class="mark-stem" d="M22 24v15c0 4 3 7 7 7h13" />
        <path class="mark-z" d="M29 28h14L30 40h15" />
      </svg>
    </span>
    <span class="brand-name">企智通</span>
  </span>
</template>

<style scoped>
.brand-lockup {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  color: #17233c;
  font-weight: 800;
  letter-spacing: 0;
}

.brand-lockup--light {
  color: #ffffff;
}

.brand-lockup--sm {
  font-size: 17px;
}

.brand-lockup--md {
  font-size: 22px;
}

.brand-lockup--lg {
  gap: 12px;
  font-size: 28px;
}

.brand-mark {
  width: 40px;
  height: 40px;
  display: inline-grid;
  place-items: center;
  border-radius: 8px;
  color: #002fa7;
  background: #ffffff;
  box-shadow: inset 0 0 0 1px rgba(0, 47, 167, 0.22);
}

.brand-lockup--light .brand-mark {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.08);
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.44);
}

.brand-lockup--sm .brand-mark {
  width: 34px;
  height: 34px;
}

.brand-lockup--lg .brand-mark {
  width: 48px;
  height: 48px;
}

.brand-mark svg {
  width: 100%;
  height: 100%;
}

.mark-shell {
  fill: currentColor;
  opacity: 0.08;
  stroke: currentColor;
  stroke-width: 2.5;
}

.mark-loop,
.mark-stem,
.mark-z {
  fill: none;
  stroke: currentColor;
  stroke-linecap: round;
  stroke-linejoin: round;
}

.mark-loop,
.mark-stem {
  stroke-width: 4;
}

.mark-z {
  stroke-width: 3.7;
}

.brand-name {
  line-height: 1;
}
</style>
