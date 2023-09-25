"use strict";

let path = require("path");
let utils = require("./utilities");
let AdmZip = require("adm-zip");

let constants = {
  soundZipFile: "sounds.zip"
};

function copyWavFiles(platformConfig, source, dest, defer) {
  let files = utils.getFilesFromPath(source);

  let filteredFiles = files.filter(function(file){
    return file.endsWith(platformConfig.soundFileExtension) == true;
  });
  
  copyFiles(filteredFiles, source, dest, defer)
}

function copyFiles(files, source, dest, defer){
  if(!files){
    utils.handleError("Something went wrong when trying to unzip sounds.zip, no files were found", defer);
    return
  }
  
  if(!utils.checkIfFileOrFolderExists(dest)) {
    utils.createOrCheckIfFolderExists(dest);
  }

  for(const element of files) {
    let filePath = path.join(source, element);
    let destFilePath = path.join(dest, element);
    utils.copyFromSourceToDestPath(defer, filePath, destFilePath);
  }
}

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
  let soundFolderPath = platformConfig.getSoundDestinationFolder();
  soundFolderPath = path.join(context.opts.projectRoot, soundFolderPath);

  let soundZipFile = path.join(sourcePath, constants.soundZipFile);

  if(utils.checkIfFileOrFolderExists(soundZipFile)){
    let zip = new AdmZip(soundZipFile);
    zip.extractAllTo(sourcePath, true);
    
    let entriesNr = zip.getEntries().length;
    
    if(entriesNr == 0) {
      utils.handleError("Sound zip file is empty, either delete it or add one or more files", defer);
      return
    }
  
    let zipFolder = sourcePath + "/sounds"

    if(!utils.checkIfFileOrFolderExists(zipFolder)){
      /**to deal with the following case:
       * iOS + one file in zip + O11
      **/
      if(sourcePath != soundFolderPath)
        copyWavFiles(platformConfig, sourcePath, soundFolderPath, defer)
    } else { 
      copyWavFiles(platformConfig, zipFolder, soundFolderPath, defer)  
    }
  }
}
