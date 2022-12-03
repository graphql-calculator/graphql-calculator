/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package calculator.generator;

public class Option {
    // 生成的代码类型
    private LanguageType languageType;
    // 默认的root类名称
    private String defaultRootClassName;
    // 是否生成注释
    private boolean willComment;
    // 使用 getter lombok
    private boolean lombokMode;
    // 是否使用基本类型（not 包装类型
    private boolean primitiveType;
}

enum LanguageType{
    JAVA,GOLANG
}
