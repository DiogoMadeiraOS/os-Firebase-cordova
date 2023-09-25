"use strict";

var path = require("path");
var utils = require("./utilities");

var constants = {
  soundFolder: "/sounds"
};

module.exports = function(context) {
  let cordovaAbove8 = utils.isCordovaAbove(context, 8);
  let defer;
  if (cordovaAbove8) {
    defer = require("q").defer();
  } else {
    defer = context.requireCordovaModule("q").defer();
  }

  let platform = context.opts.platforms[0];
  let platformConfig = utils.getPlatformConfigs(platform);

  if (!platformConfig) {
    utils.handleError("Invalid platform", defer);
  }

  let sourcePath = utils.getPlatformSoundPath(context, platformConfig)
  let soundFolderPath = path.join(sourcePath, constants.soundFolder);

  if(utils.checkIfFileOrFolderExists(soundFolderPath)){
    utils.removeFolder(soundFolderPath);
  } 

}
