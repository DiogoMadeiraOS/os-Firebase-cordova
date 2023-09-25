"use strict";

var path = require("path");
var utils = require("./utilities");

module.exports = function(context) {
  let cordovaAbove8 = utils.isCordovaAbove(context, 8);
  let defer;
  if (cordovaAbove8) {
    defer = require("q").defer();
  } else {
    defer = context.requireCordovaModule("q").defer();
  }
  
  let platform = context.opts.plugin.platform;
  let platformConfig = utils.getPlatformConfigs(platform);
  if (!platformConfig) {
    utils.handleError("Invalid platform", defer);
  }

  let sourceFolderPath = platformConfig.getSoundSourceFolder()
  let destFolderPath = platformConfig.getSoundDestinationFolder()

  if(!utils.checkIfFolderExists(destFolderPath)) {
    utils.createOrCheckIfFolderExists(destFolderPath)
  }

  let files = utils.getFilesFromPath(sourceFolderPath);
  if (!files) {
    utils.handleError("No directory found", defer);
  }
  else {
    let filteredFiles = files.filter(function(file){
      return file.endsWith(platformConfig.soundFileExtension) == true;
    });
  
    filteredFiles.forEach(function (f) {
      let filePath = path.join(sourceFolderPath, f);
      let destFilePath = path.join(destFolderPath, f);
      utils.copyFromSourceToDestPath(defer, filePath, destFilePath);
    });
  }
}
