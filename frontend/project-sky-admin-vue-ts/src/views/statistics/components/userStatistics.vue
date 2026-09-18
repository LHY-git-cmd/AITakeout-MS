<template>
  <div class="container">
    <h2 class="homeTitle">用户统计</h2>
    <div class="charBox">
      <div id="usermain" style="width: 100%; height: 320px"></div>
      <ul class="orderListLine user">
        <li class="one"><span></span>用户总量（个）</li>
        <li class="three"><span></span>新增用户（个）</li>
      </ul>
    </div>
  </div>
</template>

<script lang="ts">
import { chartPalette, splitLineStyle } from '@/utils/chartTheme'
import { Component, Vue, Prop, Watch } from 'vue-property-decorator'
import * as echarts from 'echarts'
@Component({
  name: 'UserStatistics',
})
export default class extends Vue {
  @Prop() private userdata!: any
  @Watch('userdata')
  getData() {
    this.$nextTick(() => {
      this.initChart()
    })
  }
  initChart() {
    type EChartsOption = echarts.EChartsOption
    const chartDom = document.getElementById('usermain') as any
    const myChart = echarts.init(chartDom)
    const palette = chartPalette()
    var option: any
    option = {
      // legend: {
      //   itemHeight: 3, //图例高
      //   itemWidth: 12, //图例宽
      //   icon: 'rect', //图例
      //   show: true,
      //   top: 'bottom',
      //   data: ['用户总量', '新增用户'],
      // },
      tooltip: {
        trigger: 'axis',
        backgroundColor: 'transparent', // 深色主题：透明，露出卡片底色
        borderRadius: 2, //边框圆角
        textStyle: {
          color: palette.title, // 深色主题：白色字体
          fontSize: 12, //字体大小
          fontWeight: 300,
        },
      },
      grid: {
        top: '5%',
        left: '20',
        right: '50',
        bottom: '12%',
        containLabel: true,
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        axisLabel: {
          //X轴字体颜色
          textStyle: {
            color: palette.axisLabel,
            fontSize: '12px',
          },
        },
        axisLine: {
          //X轴线颜色
          lineStyle: {
            color: palette.axis,
            width: 1, //x轴线的宽度
          },
        },
        data: this.userdata.dateList, //后端传来的动态数据
      },
      yAxis: [
        {
          type: 'value',
          min: 0,
          //max: 500,
          //interval: 100,
          axisLabel: {
            textStyle: {
              color: palette.axisLabel,
              fontSize: '12px',
            },
            // formatter: "{value} ml",//单位
          },
          // 网格线：随主题读取，保证与背景对比
          splitLine: splitLineStyle(),
        }, //左侧值
      ],
      series: [
        {
          name: '用户总量',
          type: 'line',
          // stack: 'Total',
          smooth: false, //否平滑曲线
          showSymbol: false, //未显示鼠标上移的圆点
          symbolSize: 10,
          // symbol:"circle", //设置折线点定位实心点
          itemStyle: {
            normal: {
              color: '#FFD000',
              lineStyle: {
                color: '#FFD000',
              },
            },
            emphasis: {
              color: palette.title,
              borderWidth: 5,
              borderColor: '#FFC100',
            },
          },

          data: this.userdata.totalUserList,
        },
        {
          name: '新增用户',
          type: 'line',
          // stack: 'Total',
          smooth: false, //否平滑曲线
          showSymbol: false, //未显示鼠标上移的圆点
          symbolSize: 10, //圆点大小
          // symbol:"circle", //设置折线点定位实心点
          itemStyle: {
            normal: {
              color: '#FD7F7F',
              fontWeigth: 300,
              lineStyle: {
                color: '#FD7F7F',
              },
            },
            emphasis: {
              // 圆点颜色
              color: palette.title,
              borderWidth: 5,
              borderColor: '#FD7F7F',
            },
          },

          data: this.userdata.newUserList,
        },
      ],
    }
    option && myChart.setOption(option)
  }
}
</script>
<style scoped>
</style>
