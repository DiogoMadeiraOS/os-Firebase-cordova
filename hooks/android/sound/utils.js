module.exports = {
  getAppName: function (context) {
    let ConfigParser = context.requireCordovaModule("cordova-lib").configparser;
    let config = new ConfigParser("config.xml");
    return config.name();
  }
};