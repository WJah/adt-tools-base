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

/**
 * Created by wangjiahe on 3/22/16.
 */
public class View {
  int id;
  String idString;
  int x;
  int y;
  int width;
  int height;
  String type;
  String text;
  String onClick;

  public View(int id, String idString, int x, int y, int width, int height, String type, String text, String onClick) {
    this.id = id;
    this.idString = idString;
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
    this.type = type;
    this.text = text;
    this.onClick = onClick;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getIdString() {
    return idString;
  }

  public void setIdString(String idString) {
    this.idString = idString;
  }

  public int getX() {
    return x;
  }

  public void setX(int x) {
    this.x = x;
  }

  public int getY() {
    return y;
  }

  public void setY(int y) {
    this.y = y;
  }

  public int getWidth() {
    return width;
  }

  public void setWidth(int width) {
    this.width = width;
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(int height) {
    this.height = height;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getText() {
    return text;
  }

  public void setText(String text) {
    this.text = text;
  }

  public String getOnClick() {
    return onClick;
  }

  public void setOnClick(String onClick) {
    this.onClick = onClick;
  }

  //override toString() according to R.java format
  @Override
  public String toString() {
    StringBuffer stringBuffer = new StringBuffer();
    stringBuffer.append("public static final class " + idString + "\r\n");
    stringBuffer.append("{\r\n");
    stringBuffer.append("\tpublic static final int id = " + "R.id." + idString + ";\r\n");
    stringBuffer.append("\tpublic static final String type = \"" + type + "\";\r\n");
    stringBuffer.append("\tpublic static final int x = " + x + ";\r\n");
    stringBuffer.append("\tpublic static final int y = " + y + ";\r\n");
    stringBuffer.append("\tpublic static final int width = " + width + ";\r\n");
    stringBuffer.append("\tpublic static final int height = " + height + ";\r\n");
    stringBuffer.append("\tpublic static final String text = \"" + text + "\";\r\n");
    stringBuffer.append(onClick.equals("") ? "":"\tpublic static final String onClick = \"" + onClick + "\";\r\n");
    stringBuffer.append("}\r\n");
    return stringBuffer.toString();
  }
}
