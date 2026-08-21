<script setup>
const bilibiliUrl = 'https://space.bilibili.com/509985180?spm_id_from=333.1007.0.0'
</script>

<template>
  <aside class="social-widget" aria-label="社交媒体">
    <div class="social-tooltip" role="tooltip">
      <div class="social-profile">
        <span class="social-avatar" aria-hidden="true">哔</span>
        <span class="social-details">
          <strong>Bilibili</strong>
          <span>UID 509985180</span>
          <small>访问个人空间</small>
        </span>
      </div>
    </div>

    <a
      class="social-icon"
      :href="bilibiliUrl"
      target="_blank"
      rel="noopener noreferrer"
      aria-label="在新标签页打开 Bilibili 个人空间，UID 509985180"
    >
      <span class="social-layer" aria-hidden="true">
        <span></span><span></span><span></span><span></span>
        <span class="social-layer-face">
          <svg viewBox="0 0 24 24" focusable="false" aria-hidden="true">
            <path d="M8.2 3.2 10.5 6h3l2.3-2.8 1.4 1.1L15.8 6H18a3 3 0 0 1 3 3v8a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V9a3 3 0 0 1 3-3h2.2L6.8 4.3l1.4-1.1ZM6 8a1 1 0 0 0-1 1v8a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9a1 1 0 0 0-1-1H6Zm2.5 3a1.25 1.25 0 1 1 0 2.5 1.25 1.25 0 0 1 0-2.5Zm7 0a1.25 1.25 0 1 1 0 2.5 1.25 1.25 0 0 1 0-2.5Z"/>
          </svg>
        </span>
      </span>
      <span class="social-label">Bilibili</span>
    </a>
  </aside>
</template>

<style scoped>
/* From Uiverse.io by imtausef, adapted for GearDB and keyboard access. */
.social-widget {
  --social-accent: var(--gear-accent, #78df5c);
  --social-accent-soft: rgb(120 223 92 / 14%);
  position: relative;
  z-index: 1;
  width: 55px;
  height: 55px;
  border-radius: 8px;
  font-size: 17px;
}

.social-tooltip {
  position: absolute;
  bottom: 82px;
  right: 0;
  width: 210px;
  padding: 7px;
  border: 1px solid var(--gear-line, #29322b);
  border-radius: 8px;
  background: rgb(11 14 12 / 94%);
  box-shadow:
    inset 3px 3px 7px rgb(0 0 0 / 22%),
    inset -3px -3px 9px rgb(255 255 255 / 4%),
    8px 12px 28px rgb(0 0 0 / 38%);
  opacity: 0;
  pointer-events: none;
  transform: translateY(10px) scale(0.97);
  transform-origin: bottom right;
  transition: opacity 180ms ease, transform 260ms cubic-bezier(0.22, 1, 0.36, 1);
  visibility: hidden;
}

.social-tooltip::after {
  position: absolute;
  bottom: -5px;
  right: 20px;
  width: 9px;
  height: 9px;
  border-right: 1px solid var(--gear-line, #29322b);
  border-bottom: 1px solid var(--gear-line, #29322b);
  background: #0b0e0c;
  content: '';
  transform: rotate(45deg);
}

.social-profile {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 66px;
  padding: 9px;
  border: 1px solid var(--gear-accent-line, rgb(120 223 92 / 38%));
  border-radius: 7px;
  background: var(--gear-surface, #141915);
}

.social-avatar {
  display: grid;
  flex: 0 0 46px;
  width: 46px;
  height: 46px;
  place-items: center;
  border: 1px solid var(--social-accent);
  border-radius: 7px;
  background: var(--social-accent-soft);
  color: var(--social-accent);
  font-size: 22px;
  font-weight: 800;
}

.social-details {
  display: flex;
  min-width: 0;
  flex-direction: column;
  color: var(--gear-text-soft, #c5cec7);
  line-height: 1.25;
}

.social-details strong {
  color: var(--social-accent);
  font-size: 15px;
  font-weight: 750;
}

.social-details > span {
  margin-top: 3px;
  color: var(--gear-text, #f1f5f1);
  font: 600 11px var(--gear-mono, monospace);
}

.social-details small {
  margin-top: 5px;
  color: var(--gear-muted, #8e9a91);
  font-size: 11px;
}

.social-icon {
  position: relative;
  display: block;
  width: 55px;
  height: 55px;
  color: var(--social-accent);
  text-decoration: none;
}

.social-layer {
  position: relative;
  display: block;
  width: 55px;
  height: 55px;
  transition: transform 300ms cubic-bezier(0.22, 1, 0.36, 1);
}

.social-layer > span {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
  border: 1px solid var(--social-accent);
  border-radius: 7px;
  background: var(--gear-bg, #0b0e0c);
  transition: opacity 300ms ease, transform 300ms cubic-bezier(0.22, 1, 0.36, 1), box-shadow 300ms ease;
}

.social-layer-face svg {
  width: 29px;
  height: 29px;
  fill: currentColor;
}

.social-label {
  position: absolute;
  bottom: -5px;
  left: 50%;
  color: var(--social-accent);
  font: 650 11px var(--gear-mono, monospace);
  opacity: 0;
  pointer-events: none;
  transform: translateX(-50%);
  transition: bottom 220ms ease, opacity 180ms ease;
  white-space: nowrap;
}

.social-widget:hover .social-tooltip,
.social-widget:focus-within .social-tooltip {
  opacity: 1;
  pointer-events: auto;
  transform: translateY(0) scale(1);
  visibility: visible;
}

.social-icon:hover .social-layer,
.social-icon:focus-visible .social-layer {
  transform: rotate(-35deg) skew(20deg);
}

.social-icon:hover .social-label,
.social-icon:focus-visible .social-label {
  bottom: -34px;
  opacity: 1;
}

.social-icon:hover .social-layer > span,
.social-icon:focus-visible .social-layer > span {
  box-shadow: -1px 1px 4px rgb(120 223 92 / 24%);
}

.social-icon:hover .social-layer > span:nth-child(1),
.social-icon:focus-visible .social-layer > span:nth-child(1) { opacity: 0.2; }
.social-icon:hover .social-layer > span:nth-child(2),
.social-icon:focus-visible .social-layer > span:nth-child(2) { opacity: 0.4; transform: translate(5px, -5px); }
.social-icon:hover .social-layer > span:nth-child(3),
.social-icon:focus-visible .social-layer > span:nth-child(3) { opacity: 0.6; transform: translate(10px, -10px); }
.social-icon:hover .social-layer > span:nth-child(4),
.social-icon:focus-visible .social-layer > span:nth-child(4) { opacity: 0.8; transform: translate(15px, -15px); }
.social-icon:hover .social-layer > span:nth-child(5),
.social-icon:focus-visible .social-layer > span:nth-child(5) { opacity: 1; transform: translate(20px, -20px); }

.social-icon:focus-visible {
  border-radius: 7px;
  outline: 2px solid var(--social-accent);
  outline-offset: 5px;
}

@media (max-width: 900px) {
  .social-widget {
    transform: scale(0.86);
    transform-origin: bottom right;
  }
}

@media (prefers-reduced-motion: reduce) {
  .social-widget,
  .social-tooltip,
  .social-layer,
  .social-layer > span,
  .social-label {
    transition: none;
  }
}
</style>
