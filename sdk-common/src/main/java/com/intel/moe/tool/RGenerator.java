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

import com.android.builder.model.ClassField;
import com.android.ide.common.rendering.api.IImageFactory;
import com.android.ide.common.rendering.api.RenderSession;
import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.ViewInfo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Created by wangjiahe on 3/11/16.
 */
public class RGenerator {
  static int viewCount = 0;
  static int layoutCount = 0;
  String layoutFileName;
  Layout layout;
  Object credential;
  public static String mProjectPath;
  public RGenerator(SessionParams params,Object credential,String mProjectPath){
    this.layoutFileName = getLayoutFileName(params);
    this.credential = credential;
    this.mProjectPath = mProjectPath;
  }
  public void getViewInfo(RenderSession session){
    System.out.println(session.toString());
    System.out.println(session.getRootViews().size());
    List<ViewInfo> viewInfoList = session.getRootViews();
    layout = new Layout(layoutFileName);
    for(ViewInfo viewInfo : viewInfoList){
      getViewInfo(viewInfo);
    }
    //System.out.println(layout.toString());
    FileUtil.generateTmpFile(mProjectPath,layoutFileName,layout.toString(),credential);
    FileUtil.mergeTmpFilesToR(mProjectPath,credential);

  }
  //Traversal view tree
  public void getViewInfo(ViewInfo viewInfo){
    List<ViewInfo> children = viewInfo.getChildren();
    for(ViewInfo childViewInfo : children){
      getViewInfo(childViewInfo);
    }
    try{
      Object viewObject = viewInfo.getViewObject();
      if (viewObject != null){
        final Class<?> viewClass= Class.forName(viewInfo.getClassName(), true, viewObject.getClass().getClassLoader());
        if (!viewClass.getSuperclass().getName().equals("android.view.ViewGroup")){
          Method method = null;
          method = viewClass.getMethod("getText");
          if (method == null){
            method = viewClass.getSuperclass().getMethod("getText");
          }
          String text = "";
          if (method != null) {
            text = method.invoke(viewObject).toString();
          }
          viewCount++;
          //get view type
          String typeName = viewInfo.getClassName();
          String[] typeTmp = typeName.split("\\.");
          int typeTmpLength = typeTmp.length;
          String type = typeTmp[typeTmpLength - 1];
          //create view object
          View view = new View(RIdDefine.VIEW + viewCount,getIdString(viewInfo.getCookie()),viewInfo.getLeft(),viewInfo.getTop(),
                               viewInfo.getRight() - viewInfo.getLeft(),viewInfo.getBottom() - viewInfo.getTop(),type,
                               text,text);

          layout.putView(view);
        }
        else if(viewClass.getSuperclass().getName().equals("android.view.ViewGroup")){
          layoutCount++;
        }
      }
    }catch (Exception e){
      e.printStackTrace();
    }
  }

  private String getIdString(Object mCookie){
    try {
      Class<?> cookieClass = Class.forName("com.intellij.psi.impl.source.xml.XmlTagImpl",true,mCookie.getClass().getClassLoader());
      Field field = cookieClass.getDeclaredField("myAttributeValueMap");
      field.setAccessible(true);
      Map<String,String> myAttributeValueMap = (Map<String,String>)field.get(mCookie);
      String valuesOfId = myAttributeValueMap.get("android:id");
      String[] tmps = valuesOfId.split("/");
      if (tmps.length > 1){
        return tmps[1];
      }
    }
    catch (ClassNotFoundException e) {
      e.printStackTrace();
    }
    catch (NoSuchFieldException e) {
      e.printStackTrace();
    }
    catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    return "not found";
  }

  private String getLayoutFileName(SessionParams params){
    IImageFactory iImageFactory = params.getImageFactory();
    try {
      Field field = iImageFactory.getClass().getDeclaredField("myPsiFile");
      field.setAccessible(true);
      Object o = field.get(iImageFactory);
      String xmlFile = o.toString();
      String xmlFileName = xmlFile.split(":")[1];
      return xmlFileName.substring(0,xmlFileName.length() - 4);
    }
    catch (NoSuchFieldException e) {
      e.printStackTrace();
    }
    catch (IllegalAccessException e) {
      e.printStackTrace();
    }
    return "";
  }
}
