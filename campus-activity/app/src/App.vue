<script>
export default {
  onLaunch() {
    console.log('校园活动启动')
  }
}
</script>

<style>
page {
  background-color: #f5f6f8;
  color: #16181d;
  font-size: 28rpx;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
}

view,
text,
input,
textarea {
  box-sizing: border-box;
}

.page {
  min-height: 100vh;
  padding-bottom: 40rpx;
  overflow-x: hidden;
}

.card {
  background: #ffffff;
  border-radius: 20rpx;
  padding: 28rpx;
  margin: 20rpx 24rpx;
  width: calc(100% - 48rpx);
  overflow: hidden;
}

.row {
  display: flex;
  align-items: center;
}

.between {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.muted {
  color: #8a8f99;
  font-size: 24rpx;
  word-break: break-word;
}

.title {
  font-size: 32rpx;
  font-weight: 600;
  color: #16181d;
}

.tag {
  display: inline-block;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  font-size: 22rpx;
  line-height: 1.6;
  background: #f2f4f7;
  color: #5b6270;
}

.tag-blue {
  background: #eef4ff;
  color: #1f6feb;
}

.tag-green {
  background: #e9f7f1;
  color: #0f8a5f;
}

.tag-orange {
  background: #fdf4e6;
  color: #a35a00;
}

.tag-red {
  background: #fdeeed;
  color: #b3261e;
}

.btn-primary {
  background: #1f6feb;
  color: #ffffff;
  border-radius: 12rpx;
  font-size: 30rpx;
  height: 88rpx;
  line-height: 88rpx;
  text-align: center;
}

.btn-primary[disabled] {
  background: #c9d3e0;
  color: #ffffff;
}

.btn-ghost {
  background: #ffffff;
  color: #1f6feb;
  border: 1rpx solid #cfe0fd;
  border-radius: 12rpx;
  font-size: 28rpx;
  height: 76rpx;
  line-height: 74rpx;
  text-align: center;
}

.empty {
  padding: 120rpx 0;
  text-align: center;
  color: #9aa0aa;
  font-size: 26rpx;
}

.divider {
  height: 1rpx;
  background: #eef0f3;
  margin: 20rpx 0;
}
</style>
