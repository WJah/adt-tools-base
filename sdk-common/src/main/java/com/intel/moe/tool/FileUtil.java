/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intel.moe.tool;

import com.android.ide.common.rendering.RenderSecurityManager;

import java.io.*;

/**
 * Created by wangjiahe on 3/22/16.
 */
public class FileUtil {

  public static void generateTmpFile(String path, String fileName,String content, Object credential) {
    //Can't write during Rendering except deactivate RenderSecurityManager
    RenderSecurityManager securityManager = RenderSecurityManager.getCurrent();
    securityManager.setActive(false, credential);
    //store R.java under /Project_Root_Path/tmp
    makeDir(path + "/tmp");
    String filePath = path + "/tmp/"+ fileName +".java";
    File file = updateFile(filePath);
    synchronized (file) {
      try {
        FileWriter fileWriter = new FileWriter(filePath);
        fileWriter.write(content);
        fileWriter.close();
      }
      catch (IOException e) {
        e.printStackTrace();
      }
    }
    securityManager.setActive(true, credential);
  }

  public static void mergeTmpFilesToR(String path){
    mergeTmpFilesToR(path,null);
  }
  //merge tmp java file(s) into R.java
  public static void mergeTmpFilesToR(String path, Object credential) {
    //Can't write during Rendering except deactivate RenderSecurityManager
    RenderSecurityManager securityManager = RenderSecurityManager.getCurrent();
    if (credential != null) {
      securityManager.setActive(false, credential);
    }
    //store R.java under /Project_Root_Path/gen
    makeDir(path + "/gen");
    File file = new File(path + "/tmp");
    File[] files = file.listFiles();
    try {
      FileWriter fileWriter = null;
      if (files.length > 0) {
        File R = updateFile(path + "/gen/R.java");
        fileWriter = new FileWriter(R,true);
        fileWriter.append("public final class R {\r\n");
      }
      else {
        return;
      }
      BufferedReader bufferedReader = null;
      int count = 0;
      int viewCountOfEachLayout = Integer.MAX_VALUE;
      int viewCountOfAll = 0;
      int fileCount = 0;
      if (fileWriter != null) {
        fileWriter.append("public static final class id {\r\n");
      }
      //merge temp fils into R.java
      for (File f : files) {
        fileCount++;
        String fileName = f.getName();
        fileWriter.append("\tpublic static final int " + fileName.substring(0, fileName.length() - 5) +
                          " = 0x" + Integer.toHexString(RIdDefine.ACTIVITY + fileCount) + ";\r\n");
        bufferedReader = new BufferedReader(new FileReader(f));
        String line = bufferedReader.readLine();
        while (line != null) {
          count++;
          if (count == 2) {
            viewCountOfEachLayout = Integer.parseInt(line.trim());
            viewCountOfAll += viewCountOfEachLayout;
          }
          if (count >= 3 && count <= viewCountOfEachLayout + 2) {
            fileWriter.append("\tpublic static final int " + line + " = 0x" + Integer.toHexString(RIdDefine.VIEW + viewCountOfAll - viewCountOfEachLayout + count - 2) + ";\r\n");
          }
          else if(count - 2 > viewCountOfEachLayout){
            break;
          }
          line = bufferedReader.readLine();
        }
        count = 0;
        viewCountOfEachLayout = Integer.MAX_VALUE;
        bufferedReader.close();
        bufferedReader = null;
      }
      if (fileWriter != null) {
        fileWriter.append("}\r\n");
        fileWriter.append("public static final class layout {\r\n");
      }
      fileCount = 0;
      for (File f : files) {
        fileCount++;
        String fileName = f.getName();
        fileWriter.append("\tpublic static final int " + fileName.substring(0, fileName.length() - 5) +
                          "_layout = 0x" + Integer.toHexString(RIdDefine.LAYOUT + fileCount) + ";\r\n");
      }
      if (fileWriter != null) {
        fileWriter.append("}\r\n");
        fileWriter.append("public static final class canvas {\r\n");
      }
      for (File f : files){
        bufferedReader = new BufferedReader(new FileReader(f));
        String line = bufferedReader.readLine();
        while (line != null){
          fileWriter.append(line + "\r\n");
          line = bufferedReader.readLine();
        }
      }
      if (fileWriter != null) {
        fileWriter.append("}\r\n}");
      }
      fileWriter.flush();
      fileWriter.close();
      fileWriter = null;
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    if (credential != null) {
      securityManager.setActive(true, credential);
    }

  }

  private static File updateFile(String path) {
    File file = new File(path);
    try {
      if (file.exists() && file.delete()) {
        file.createNewFile();
      }
      else if (!file.exists()) {
        file.createNewFile();
      }
    }
    catch(IOException e){
      e.printStackTrace();
    }
    return file;
  }

  private static boolean makeDir(String path){
    File dir = new File(path);
    if (dir.exists() && dir.isDirectory()){
      return false;
    }
    else {
      return dir.mkdir();
    }
  }
}
