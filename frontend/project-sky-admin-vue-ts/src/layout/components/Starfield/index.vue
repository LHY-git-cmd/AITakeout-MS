<template>
  <!--
    星空层：固定在布局最底层，pointer-events:none 所以不拦截任何点击。
    纯 CSS 实现（多层 radial-gradient 点阵 + 伪元素），不引入动画依赖，
    也不使用超长 box-shadow 坐标，不额外生成大量 DOM（共 7 个空 div）。
  -->
  <div class="starfield" aria-hidden="true">
    <div class="star-layer layer-far" />
    <div class="star-layer layer-mid" />
    <div class="star-layer layer-near" />
    <div class="star-mist" />
    <div class="meteor meteor-1" />
    <div class="meteor meteor-2" />
    <div class="meteor meteor-3" />
    <div class="meteor meteor-4" />
    <div class="meteor meteor-5" />
    <div class="meteor meteor-6" />
    <div class="meteor meteor-7" />
    <div class="meteor meteor-8" />
    <div class="meteor meteor-9" />
    <div class="meteor meteor-10" />
    <div class="meteor meteor-11" />
    <div class="meteor meteor-12" />
  </div>
</template>

<script lang="ts">
import { Component, Vue } from 'vue-property-decorator'

@Component({
  name: 'Starfield',
})
export default class extends Vue {}
</script>

<style lang="scss" scoped>
/* ============================================================================
   流动星空：3 层星点视差漂移（远景慢 → 近景快）+ 星云雾气 + 7 颗流星

   流星「几乎随时可见」的原理（关键，改时长时务必保持）：
     每颗的可见时长 = 周期的 22%，7 颗的延迟按 1/7 周期均匀错开
     （错开幅度 ≈ 14.3%）→ 相邻两颗的可见窗口互相重叠、首尾相接，
     于是任意时刻至少有一颗正在划过。
     ⚠ 若改小「可见时长占比」或减少颗数，会出现空档。
        约束：可见时长占比 × 颗数 ≥ 100%
        例：22% × 7 = 154% ≥ 100% ✓

   无缝原理：用 background-position 平移，且位移量取「点阵平铺尺寸」的整数倍，
   循环回到起点时图案完全重合，永远不会出现跳变或接缝。
     L1 位移 = 420px × 1   （平铺 420px）
     L2 位移 = 620px × 2 / × 4（平铺 620px）
     L3 位移 = 360px × 3   （平铺 360px）
   ⚠ 改点阵坐标或 background-size 时，务必同步检查位移量仍是尺寸的整数倍。

   动画只出现在「星空」（本文件）与「品牌区」（layout/components/Sidebar）
   两处，并在文件末尾用 prefers-reduced-motion 整体停用。
   ============================================================================ */
.starfield {
  position: fixed;
  inset: 0;
  z-index: 0; /* 永远在内容之下 */
  pointer-events: none; /* 不拦截点击 */
  overflow: hidden;
  background:
    radial-gradient(900px 520px at 12% -8%, rgba(76, 141, 255, 0.2), transparent 70%),
    radial-gradient(760px 480px at 86% 6%, rgba(150, 96, 255, 0.15), transparent 72%),
    radial-gradient(1000px 620px at 60% 108%, rgba(28, 96, 180, 0.18), transparent 74%),
    linear-gradient(180deg, #0d1430 0%, #070b18 58%, #05080f 100%);
}

.star-layer {
  position: absolute;
  inset: 0;
  background-repeat: repeat;
  /* 柔和辉光：两个小半径 drop-shadow 让星点真正"亮"起来 */
  filter: drop-shadow(0 0 2px rgba(190, 214, 255, 0.55))
    drop-shadow(0 0 6px rgba(120, 170, 255, 0.35));
}

/* L1 · 远景微尘：密度最低，走得最慢 */
.layer-far {
  opacity: 0.78;
  background-image: radial-gradient(1.8px 1.8px at 18px 40px, #eef4ff 50%, transparent 54%),
    radial-gradient(1.7px 1.7px at 96px 14px, #ffffff 50%, transparent 54%),
    radial-gradient(2px 2px at 168px 122px, #dbe6ff 50%, transparent 54%),
    radial-gradient(1.7px 1.7px at 244px 62px, #f4f8ff 50%, transparent 54%),
    radial-gradient(1.9px 1.9px at 330px 168px, #ffffff 50%, transparent 54%),
    radial-gradient(1.7px 1.7px at 388px 96px, #dbe6ff 50%, transparent 54%),
    radial-gradient(2px 2px at 62px 232px, #ffffff 50%, transparent 54%),
    radial-gradient(1.8px 1.8px at 216px 316px, #eaf1ff 50%, transparent 54%);
  background-size: 420px 420px;
  animation: drift-far 300s linear infinite;
}

/* L2 · 中层星群：中等密度与速度 */
.layer-mid {
  opacity: 0.92;
  background-image: radial-gradient(2.2px 2.2px at 42px 66px, #ffffff 50%, transparent 54%),
    radial-gradient(2px 2px at 130px 24px, #e8f0ff 50%, transparent 54%),
    radial-gradient(2.4px 2.4px at 226px 158px, #ffffff 50%, transparent 54%),
    radial-gradient(2px 2px at 316px 92px, #dbe6ff 50%, transparent 54%),
    radial-gradient(2.2px 2.2px at 424px 214px, #ffffff 50%, transparent 54%),
    radial-gradient(2.1px 2.1px at 508px 46px, #f0f5ff 50%, transparent 54%),
    radial-gradient(2.3px 2.3px at 74px 268px, #ffffff 50%, transparent 54%),
    radial-gradient(2px 2px at 258px 430px, #e2ebff 50%, transparent 54%),
    radial-gradient(2.2px 2.2px at 466px 508px, #ffffff 50%, transparent 54%);
  background-size: 620px 620px;
  animation: drift-mid 260s linear infinite;
}

/* L3 · 近景亮星：最稀、最亮、走得最快，带轻微闪烁 */
.layer-near {
  opacity: 1;
  /* ⚠ 所有坐标必须落在 0~360 之间（等于平铺尺寸 360px）。
     超出范围的星点会被裁掉、根本不渲染 —— 这里曾有几颗写在 420/600/396，实际是无效的。
     半径也要留出余量：坐标 + 半径 必须 < 360。 */
  background-image: radial-gradient(3.1px 3.1px at 48px 62px, rgba(255, 255, 255, 1) 38%, transparent 56%),
    radial-gradient(3.4px 3.4px at 96px 148px, rgba(222, 238, 255, 1) 38%, transparent 56%),
    radial-gradient(2.8px 2.8px at 168px 296px, rgba(255, 255, 255, 0.98) 38%, transparent 56%),
    radial-gradient(3.5px 3.5px at 268px 62px, rgba(214, 232, 255, 1) 38%, transparent 56%),
    radial-gradient(3px 3px at 316px 210px, rgba(255, 255, 255, 1) 38%, transparent 56%),
    radial-gradient(2.7px 2.7px at 140px 236px, rgba(255, 240, 196, 0.98) 38%, transparent 56%),
    radial-gradient(3.2px 3.2px at 236px 322px, rgba(255, 255, 255, 0.98) 38%, transparent 56%),
    radial-gradient(2.9px 2.9px at 332px 122px, rgba(206, 228, 255, 1) 38%, transparent 56%),
    radial-gradient(3.3px 3.3px at 64px 338px, rgba(255, 255, 255, 0.96) 38%, transparent 56%),
    radial-gradient(2.8px 2.8px at 204px 28px, rgba(255, 255, 255, 0.98) 38%, transparent 56%),
    radial-gradient(3.1px 3.1px at 288px 268px, rgba(236, 244, 255, 1) 38%, transparent 56%),
    radial-gradient(2.6px 2.6px at 116px 74px, rgba(255, 255, 255, 0.95) 38%, transparent 56%);
  background-size: 360px 360px;
  animation: drift-near 132s linear infinite, twinkle 6.5s ease-in-out infinite alternate;
}

@keyframes drift-far {
  from {
    background-position: 0 0;
  }
  to {
    background-position: -420px 420px;
  }
}

@keyframes drift-mid {
  from {
    background-position: 0 0;
  }
  to {
    background-position: 1240px 2480px;
  }
}

@keyframes drift-near {
  from {
    background-position: 0 0;
  }
  to {
    background-position: -1080px -1080px;
  }
}

/* 亮度呼吸：只动 opacity，不触发重绘 */
@keyframes twinkle {
  from {
    opacity: 0.38;
  }
  to {
    opacity: 1;
  }
}

/* 星云雾气：缓慢游移 + 亮度呼吸（柔光无锐利边缘，位移接缝不可见） */
.star-mist {
  position: absolute;
  inset: -12%;
  background-image: radial-gradient(560px 380px at 22% 26%, rgba(86, 150, 255, 0.3), transparent 70%),
    radial-gradient(480px 340px at 74% 18%, rgba(160, 110, 255, 0.26), transparent 72%),
    radial-gradient(620px 420px at 48% 68%, rgba(40, 120, 220, 0.24), transparent 74%),
    radial-gradient(420px 300px at 88% 82%, rgba(120, 190, 255, 0.18), transparent 72%);
  filter: blur(16px);
  animation: drift-mist 190s ease-in-out infinite alternate;
}

@keyframes drift-mist {
  from {
    transform: translate3d(-1.5%, 1%, 0) scale(1.04);
    opacity: 0.55;
  }
  50% {
    opacity: 0.85;
  }
  to {
    transform: translate3d(2%, -2.5%, 0) scale(1.1);
    opacity: 0.6;
  }
}

/* ---------------------------------------------------------------------------
   7 颗流星：统一 24s 周期，延迟按 1/7 周期（≈3.43s）均匀错开，
   可见窗口首尾相接 → 几乎每时每刻都有流星在飞。

   每颗的「起点 → 终点」各不相同，覆盖屏幕不同区域；
   角度按 1512×945 基准在「像素空间」推导（见下），改动位移量需同步改角度：
     m1 46vw/22vh → atan2(208,695) ≈ 17°      m5 40vw/18vh → atan2(170,605) ≈ 16°
     m2 44vw/20vh → atan2(189,665) ≈ 16°      m6 38vw/17vh → atan2(161,575) ≈ 16°
     m3 42vw/20vh → atan2(189,635) ≈ 17°      m7 36vw/16vh → atan2(151,544) ≈ 16°
     m4 40vw/18vh → atan2(170,605) ≈ 16°
   屏幕坐标系 y 轴向下，故正角即顺时针，与右下运动方向一致。
   --------------------------------------------------------------------------- */
.meteor {
  position: absolute;
  top: 0;
  left: 0;
  width: 190px;
  height: 2px;
  border-radius: 999px;
  opacity: 0;
  pointer-events: none;
  will-change: transform, opacity;
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0) 0%,
    rgba(190, 214, 255, 0.35) 42%,
    rgba(255, 255, 255, 0.88) 74%,
    #ffffff 100%
  );
  filter: drop-shadow(0 0 6px rgba(150, 195, 255, 0.9));
}

.meteor::after {
  content: '';
  position: absolute;
  right: -3px;
  top: 50%;
  width: 6px;
  height: 6px;
  margin-top: -3px;
  border-radius: 50%;
  background: #fff;
  box-shadow: 0 0 10px 3px rgba(170, 205, 255, 0.85);
}

.meteor-1 { animation: meteor-a 24s linear infinite 0s; }
.meteor-2 { animation: meteor-b 24s linear infinite 2s; }
.meteor-3 { animation: meteor-c 24s linear infinite 4s; }
.meteor-4 { animation: meteor-d 24s linear infinite 6s; }
.meteor-5 { animation: meteor-e 24s linear infinite 8s; }
.meteor-6 { animation: meteor-f 24s linear infinite 10s; }
.meteor-7 { animation: meteor-g 24s linear infinite 12s; }
.meteor-8 { animation: meteor-h 24s linear infinite 14s; }
.meteor-9 { animation: meteor-i 24s linear infinite 16s; }
.meteor-10 { animation: meteor-j 24s linear infinite 18s; }
.meteor-11 { animation: meteor-k 24s linear infinite 20s; }
.meteor-12 { animation: meteor-l 24s linear infinite 22s; }

/* 每颗：0% 隐入 → 2% 全亮 → 20% 起淡出 → 22% 起完全隐藏（可见时长 22%） */
@keyframes meteor-a {
  0% { transform: translate3d(8vw, 2vh, 0) rotate(17deg); opacity: 0; }
  2% { opacity: 1; }
  20% { transform: translate3d(54vw, 24vh, 0) rotate(17deg); opacity: 1; }
  22%, 100% { transform: translate3d(60vw, 27vh, 0) rotate(17deg); opacity: 0; }
}

@keyframes meteor-b {
  0% { transform: translate3d(30vw, -2vh, 0) rotate(16deg); opacity: 0; }
  2% { opacity: 1; }
  20% { transform: translate3d(74vw, 18vh, 0) rotate(16deg); opacity: 1; }
  22%, 100% { transform: translate3d(80vw, 21vh, 0) rotate(16deg); opacity: 0; }
}

@keyframes meteor-c {
  0% { transform: translate3d(56vw, -4vh, 0) rotate(17deg); opacity: 0; }
  2% { opacity: 1; }
  20% { transform: translate3d(98vw, 16vh, 0) rotate(17deg); opacity: 1; }
  22%, 100% { transform: translate3d(104vw, 19vh, 0) rotate(17deg); opacity: 0; }
}

@keyframes meteor-d {
  0% { transform: translate3d(16vw, 22vh, 0) rotate(16deg); opacity: 0; }
  2% { opacity: 1; }
  20% { transform: translate3d(56vw, 40vh, 0) rotate(16deg); opacity: 1; }
  22%, 100% { transform: translate3d(62vw, 43vh, 0) rotate(16deg); opacity: 0; }
}

@keyframes meteor-e {
  0% { transform: translate3d(42vw, 18vh, 0) rotate(16deg); opacity: 0; }
  2% { opacity: 1; }
  20% { transform: translate3d(82vw, 36vh, 0) rotate(16deg); opacity: 1; }
  22%, 100% { transform: translate3d(88vw, 39vh, 0) rotate(16deg); opacity: 0; }
}

@keyframes meteor-f {
  0% { transform: translate3d(4vw, 34vh, 0) rotate(16deg); opacity: 0; }
  2% { opacity: 1; }
  20% { transform: translate3d(42vw, 51vh, 0) rotate(16deg); opacity: 1; }
  22%, 100% { transform: translate3d(48vw, 54vh, 0) rotate(16deg); opacity: 0; }
}

@keyframes meteor-g {
  0% { transform: translate3d(64vw, 8vh, 0) rotate(16deg); opacity: 0; }
  2% { opacity: 1; }
  20% { transform: translate3d(100vw, 24vh, 0) rotate(16deg); opacity: 1; }
  22%, 100% { transform: translate3d(106vw, 27vh, 0) rotate(16deg); opacity: 0; }
}

/* 新增的 5 颗：航程与高度各不相同，铺满屏幕各个区域 */
@keyframes meteor-h {
  0% { transform: translate3d(12vw, 6vh, 0) rotate(17deg); opacity: 0; }
  2% { opacity: 1; }
  20% { transform: translate3d(58vw, 28vh, 0) rotate(17deg); opacity: 1; }
  22%, 100% { transform: translate3d(64vw, 31vh, 0) rotate(17deg); opacity: 0; }
}

@keyframes meteor-i {
  0% { transform: translate3d(48vw, -2vh, 0) rotate(17deg); opacity: 0; }
  2% { opacity: 1; }
  20% { transform: translate3d(92vw, 20vh, 0) rotate(17deg); opacity: 1; }
  22%, 100% { transform: translate3d(98vw, 23vh, 0) rotate(17deg); opacity: 0; }
}

@keyframes meteor-j {
  0% { transform: translate3d(26vw, 26vh, 0) rotate(16deg); opacity: 0; }
  2% { opacity: 1; }
  20% { transform: translate3d(68vw, 45vh, 0) rotate(16deg); opacity: 1; }
  22%, 100% { transform: translate3d(74vw, 48vh, 0) rotate(16deg); opacity: 0; }
}

@keyframes meteor-k {
  0% { transform: translate3d(70vw, 2vh, 0) rotate(17deg); opacity: 0; }
  2% { opacity: 1; }
  20% { transform: translate3d(106vw, 20vh, 0) rotate(17deg); opacity: 1; }
  22%, 100% { transform: translate3d(112vw, 23vh, 0) rotate(17deg); opacity: 0; }
}

@keyframes meteor-l {
  0% { transform: translate3d(2vw, 40vh, 0) rotate(16deg); opacity: 0; }
  2% { opacity: 1; }
  20% { transform: translate3d(40vw, 58vh, 0) rotate(16deg); opacity: 1; }
  22%, 100% { transform: translate3d(46vw, 61vh, 0) rotate(16deg); opacity: 0; }
}

/* ---------------------------------------------------------------------------
   减少动态效果：只停「星空」这一处动画，静态星空完整保留。
   流星属于纯装饰，停用时整颗隐藏（留半截反而更怪）。
   --------------------------------------------------------------------------- */
@media (prefers-reduced-motion: reduce) {
  .star-layer {
    animation: none !important;
  }

  .layer-near {
    opacity: 0.95;
  }

  .star-mist {
    animation: none !important;
    opacity: 0.7;
    transform: none;
  }

  .meteor {
    animation: none !important;
    opacity: 0 !important;
  }
}
</style>
