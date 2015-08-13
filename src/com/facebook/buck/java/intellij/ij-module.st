<?xml version="1.0" encoding="UTF-8"?>
<module type="JAVA_MODULE" version="4">
%if(androidFacet)%
  <component name="FacetManager">
    <facet type="android" name="Android">
      <configuration>
        <option name="ENABLE_SOURCES_AUTOGENERATION" value="false" />
        <option name="MANIFEST_FILE_RELATIVE_PATH" value="%android_manifest%" />
        <option name="RES_FOLDER_RELATIVE_PATH" value="%res%" />
        <option name="USE_CUSTOM_APK_RESOURCE_FOLDER" value="false" />
        <option name="LIBRARY_PROJECT" value="true" />
        <option name="UPDATE_PROPERTY_FILES" value="false" />
        <resOverlayFolders />
        <includeSystemProguardFile>false</includeSystemProguardFile>
        <includeAssetsFromLibraries>true</includeAssetsFromLibraries>
        <additionalNativeLibs />
      </configuration>
    </facet>
  </component>
%endif%
  <component name="NewModuleRootManager" inherit-compiler-output="true">
    <content url="%contentRoot.url%">
%contentRoot.folders:{folder |
      <%folder.type% url="%folder.url%" %\\%
        isTestSource="%folder.isTestSource%" %\\%
        %if(folder.packagePrefix)% packagePrefix="%folder.packagePrefix%" %endif% />
}%
    </content>
%dependencies:{dep |
  %if(dep.module)%
    <orderEntry type="%dep.type%" module-name="%dep.module.name%" scope="%dep.module.scope%" %\\%
      %if(dep.module.exported)% exported="" %endif% />
  %elseif(dep.library)%
    <orderEntry type="%dep.type%" name="%dep.library.name%" scope="%dep.library.scope%" %\\%
      level="project" %if(dep.library.exported)% exported="" %endif% />
  %elseif(dep.sourceFolder)%
    <orderEntry type="%dep.type%" forTests="false" />
  %endif%
}%
    <orderEntry type="inheritedJdk" />
  </component>
</module>
