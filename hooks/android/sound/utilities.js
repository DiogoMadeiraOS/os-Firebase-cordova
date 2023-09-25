"use strict"

var path = require("path");
var fs = require("fs");

var utils = require("./utils");

var constants = {
  platforms: "platforms",
  android: {
    platform: "android",
    wwwFolder: "assets/www",
    soundFileExtension: ".wav",
    getSoundDestinationFolder: function() {
      return "platforms/android/app/src/main/res/raw";
    },
    getSoundSourceFolder: function() {
      return "platforms/android/app/src/main/assets/www";
    }
  }
};

function handleError(errorMessage, defer) {
  console.log(errorMessage);
  defer.reject();
}

function checkIfFolderExists(path) {
  return fs.existsSync(path);
}

function getFilesFromPath(path) {
  return fs.readdirSync(path);
}

function createOrCheckIfFolderExists(path) {
  if (!fs.existsSync(path)) {
    fs.mkdirSync(path);
  }
}

function getPlatformConfigs(platform) {
  if (platform === constants.android.platform) {
    return constants.android;
  } else if (platform === constants.ios.platform) {
    return constants.ios;
  }
}

function isCordovaAbove(context, version) {
  let cordovaVersion = context.opts.cordova.version;
  console.log(cordovaVersion);
  let sp = cordovaVersion.split('.');
  return parseInt(sp[0]) >= version;
}


function copyFromSourceToDestPath(defer, sourcePath, destPath) {
  fs.createReadStream(sourcePath).pipe(fs.createWriteStream(destPath))
  .on("close", function (err) {
    defer.resolve();
  })
  .on("error", function (err) {
    console.log(err);
    defer.reject();
  });
}

module.exports = {
  isCordovaAbove,
  handleError,
  getPlatformConfigs,
  copyFromSourceToDestPath,
  getFilesFromPath,
  createOrCheckIfFolderExists,
  checkIfFolderExists
};