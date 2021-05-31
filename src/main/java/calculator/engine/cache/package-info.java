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

/**
 * 用于实现缓存指令。
 *
 * kp：缓存并不涉及到数据的计算、好像不应该在这个项目中实现、而且最优的实现方式应该是 自定义指令+自定义DataFetcher；
 *     但是 自定义指令+自定义DataFetcher 对于入门开发者来说也有很大学习和试错成本，因此在此项目进行一些探索、实现
 *     仍然含有很大意义。
 *
 * cache(cacheName:String,maxSize:Int,refreshAfterWrite:Int,expireAfterWrite:Int,expireAfterAccess:Int,ext:String)
 *
 * - cacheName：使用的缓存名称
 * - ext: 额外配置；
 * - 其他：同guava；
 */

@Beta
package calculator.engine.cache;

import calculator.engine.annotation.Beta;
