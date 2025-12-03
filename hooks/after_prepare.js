#!/usr/bin/env node

/**
 * Hook to fix iOS library search paths for cordova-ios 8.x
 * 
 * cordova-ios 8.x changed the project structure from {ProjectName}/ to App/
 * This hook updates library search paths that reference the old structure
 */

var fs = require('fs');
var path = require('path');

module.exports = function(context) {
    // Only run for iOS platform
    if (!context.opts.platforms || context.opts.platforms.indexOf('ios') === -1) {
        return;
    }

    var iosDir = path.join(context.opts.projectRoot, 'platforms', 'ios');
    
    // Find the .xcodeproj directory (cordova-ios 8.x uses App.xcodeproj)
    var pbxprojPath = path.join(iosDir, 'App.xcodeproj', 'project.pbxproj');
    
    // Check if the project file exists
    if (!fs.existsSync(pbxprojPath)) {
        // Try to find any .xcodeproj (for older cordova-ios versions)
        try {
            var files = fs.readdirSync(iosDir);
            var xcodeprojDir = files.find(function(f) { return f.endsWith('.xcodeproj'); });
            if (xcodeprojDir) {
                pbxprojPath = path.join(iosDir, xcodeprojDir, 'project.pbxproj');
            }
        } catch (e) {
            return;
        }
    }
    
    if (!fs.existsSync(pbxprojPath)) {
        return;
    }

    console.log('ZebraPrinter: Fixing iOS library search paths for cordova-ios 8.x...');

    try {
        var content = fs.readFileSync(pbxprojPath, 'utf8');
        var modified = false;

        // Fix library search paths: any {ProjectName}/Plugins/ should be App/Plugins/ in cordova-ios 8.x
        // Match patterns like: "$(SRCROOT)/ProjectName/Plugins/ca-cleversolutions-zebraprinter"
        // But only if App/Plugins/ directory exists (indicating cordova-ios 8.x)
        
        var appPluginsPath = path.join(iosDir, 'App', 'Plugins');
        if (fs.existsSync(appPluginsPath)) {
            // We're on cordova-ios 8.x, fix the paths
            // This regex matches $(SRCROOT)/{AnyProjectName}/Plugins/ and replaces with $(SRCROOT)/App/Plugins/
            var searchPathRegex = /\$\(SRCROOT\)\/([^\/]+)\/Plugins\/ca-cleversolutions-zebraprinter/g;
            var newContent = content.replace(searchPathRegex, function(match, projectName) {
                if (projectName !== 'App') {
                    modified = true;
                    return '$(SRCROOT)/App/Plugins/ca-cleversolutions-zebraprinter';
                }
                return match;
            });
            
            if (modified) {
                fs.writeFileSync(pbxprojPath, newContent, 'utf8');
                console.log('ZebraPrinter: Fixed iOS library search paths');
            }
        }
    } catch (error) {
        console.error('ZebraPrinter: Error fixing library search paths:', error.message);
    }
};
