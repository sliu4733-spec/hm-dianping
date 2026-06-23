Vue.component("footBar", {
  template: `
    <div class="foot">
      <div class="foot-box" :class="{active: activeBtn === 1}" @click="toPage(1)">
        <div class="foot-view"><i class="el-icon-s-home"></i></div>
        <div class="foot-text">首页</div>
      </div>
      <div class="foot-box" :class="{active: activeBtn === 2}" @click="toPage(2)">
        <div class="foot-view"><i class="el-icon-map-location"></i></div>
        <div class="foot-text">地图</div>
      </div>
      <div class="foot-box" @click="toPage(0)">
        <img class="add-btn" src="/imgs/add.png" alt="">
      </div>
      <div class="foot-box" :class="{active: activeBtn === 3}" @click="toPage(3)">
        <div class="foot-view" style="font-size:24px">💬</div>
        <div class="foot-text">客服</div>
      </div>
      <div class="foot-box" :class="{active: activeBtn === 4}" @click="toPage(4)">
        <div class="foot-view"><i class="el-icon-user"></i></div>
        <div class="foot-text">我的</div>
      </div>
    </div>
  `,
  props: ["activeBtn"],
  methods: {
    toPage(i) {
      if (i === 0) {
        location.href = "/blog-edit.html";
      } else if (i === 2) {
        var type = 1;
        var name = "美食";
        try {
          var cachedType = localStorage.getItem("last_map_type");
          var cachedName = localStorage.getItem("last_map_name");
          if (cachedType) type = cachedType;
          if (cachedName) name = cachedName;
        } catch (e) {}
        location.href = "/map.html?type=" + encodeURIComponent(type) + "&name=" + encodeURIComponent(name);
      } else if (i === 3) {
        location.href = "/customer-service.html";
      } else if (i === 4) {
        location.href = "/info.html";
      } else if (i === 1) {
        location.href = "/";
      }
    }
  }
});
