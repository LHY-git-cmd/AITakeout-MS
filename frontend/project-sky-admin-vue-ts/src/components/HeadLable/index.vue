<template>
  <div class="HeadLable">
    <span
      v-if="goback"
      class="goBack"
      @click="goBack()"
    ><img
      src="@/assets/icons/btn_back@2x.png"
      alt=""
    > 返回</span>
    <span v-if="!butList">{{ title }}</span>
    <div v-if="butList">
      <slot />
    </div>
  </div>
</template>

<script lang="ts">
import { Component, Prop, Vue } from 'vue-property-decorator'

@Component({
  'name': 'Hamburger'
})
export default class extends Vue {
  @Prop({ 'default': false }) private goback!: boolean
  @Prop({ 'default': false }) private butList!: boolean
  @Prop({ 'default': '集团管理' }) private title!: string

  private toggleClick() {
    this.$emit('toggleClick')
  }

  private goBack() {
    this.$router.go(-1)
  }
}
</script>

<style lang="scss" scoped>
  .HeadLable{
    // position: absolute;
    // 透明底 + 跟随主题的主文字色（深色下浅字 / 浅色下深字）
    background: transparent;
    color: var(--text-1);
    height: 64px;
    font-size: 16px;
    // width: 300px;
    padding-left: 22px;
    line-height: 64px;
    font-weight: 700;
    margin-bottom: 15px;
    top:0px;
    left: 0px;
    opacity: 0;
    animation: opacity 500ms ease-out 800ms forwards;
    .goBack{
      border-right: solid 1px var(--field-border);
      padding-right: 14px;
      margin-right: 14px;
      font-size: 16px;
      color: var(--text-1);
      cursor: pointer;
      font-weight: 400;
      img{
        position: relative;
        top:24px;
        margin-right: 5px;
        width: 18px;
        height: 18px;
        float: left;
      }
    }
  }
  @keyframes opacity {
     0% {
       opacity: 0;
       left: 80px;
     }
     100% {
       opacity: 1;
       left: 0;
     }
   }
</style>
