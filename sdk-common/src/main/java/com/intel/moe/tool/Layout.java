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

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Created by wangjiahe on 3/22/16.
 */
public class Layout {
  List<View> viewsInLayout;
  String layoutFileName;
  public Layout(String layoutFileName){
    this.layoutFileName = layoutFileName;
    viewsInLayout = new ArrayList<View>();
  }

  public int putView(View view){
    viewsInLayout.add(view);
    return viewsInLayout.size();
  }

  @Override
  public String toString() {
    StringBuffer stringBuffer = new StringBuffer();
    stringBuffer.append("/*\r\n");
    stringBuffer.append(viewsInLayout.size() + "\r\n");
    for(View view:viewsInLayout){
      stringBuffer.append(view.getIdString() + "\r\n");
    }
    stringBuffer.append("*/\r\n");
    stringBuffer.append("public static final class " + layoutFileName + "\r\n");
    stringBuffer.append("{\r\n");
    for(View view:viewsInLayout){
      stringBuffer.append(view.toString());
    }
    stringBuffer.append("}\r\n");
    return stringBuffer.toString();
  }
}
