window.UserLocation = {
  lat: 30.2741,
  lng: 120.1551,
  city: "杭州",
  detected: false,

  cities: [
    { name: "杭州", lat: 30.2741, lng: 120.1551 },
    { name: "成都", lat: 30.5728, lng: 104.0668 },
    { name: "北京", lat: 39.9042, lng: 116.4074 },
    { name: "上海", lat: 31.2304, lng: 121.4737 },
    { name: "广州", lat: 23.1291, lng: 113.2644 },
    { name: "深圳", lat: 22.5431, lng: 114.0579 },
    { name: "重庆", lat: 29.4316, lng: 106.9123 },
    { name: "武汉", lat: 30.5928, lng: 114.3055 },
    { name: "西安", lat: 34.3416, lng: 108.9398 },
    { name: "南京", lat: 32.0603, lng: 118.7969 }
  ],

  findCity: function(lat, lng) {
    var minDist = Infinity;
    var city = "杭州";
    for (var i = 0; i < this.cities.length; i++) {
      var c = this.cities[i];
      var d = Math.sqrt(Math.pow(lat - c.lat, 2) + Math.pow(lng - c.lng, 2));
      if (d < minDist) {
        minDist = d;
        city = c.name;
      }
    }
    return minDist < 2 ? city : "当前位置";
  },

  save: function(loc) {
    this.lat = Number(loc.lat);
    this.lng = Number(loc.lng);
    this.city = loc.city || this.findCity(this.lat, this.lng);
    this.detected = !!loc.detected;
    localStorage.setItem("user_location", JSON.stringify({
      lat: this.lat,
      lng: this.lng,
      city: this.city,
      detected: this.detected
    }));
  },

  selectCity: function(cityName) {
    var city = this.cities.find(function(item) { return item.name === cityName; });
    if (!city) return;
    this.save({ lat: city.lat, lng: city.lng, city: city.name, detected: false });
  },

  init: function(cb, forceGps) {
    var self = this;
    var cached = localStorage.getItem("user_location");
    if (cached && !forceGps) {
      try {
        var loc = JSON.parse(cached);
        self.lat = Number(loc.lat) || self.lat;
        self.lng = Number(loc.lng) || self.lng;
        self.city = loc.city || self.city;
        self.detected = !!loc.detected;
        if (cb) cb(self);
        return;
      } catch (e) {}
    }

    if (!navigator.geolocation) {
      if (cb) cb(self);
      return;
    }

    navigator.geolocation.getCurrentPosition(
      function(pos) {
        self.save({
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
          city: self.findCity(pos.coords.latitude, pos.coords.longitude),
          detected: true
        });
        if (cb) cb(self);
      },
      function() {
        if (cb) cb(self);
      },
      { timeout: 5000, enableHighAccuracy: true, maximumAge: 60000 }
    );
  }
};

window.getCurrentLocation = function(forceGps) {
  return new Promise(function(resolve) {
    window.UserLocation.init(function(loc) {
      resolve({
        lat: loc.lat,
        lng: loc.lng,
        city: loc.city,
        detected: loc.detected
      });
    }, forceGps);
  });
};
