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

package calculator.engine.service;


import java.util.List;
import java.util.stream.Collectors;

public class ConsumerServiceClient {

    static class UserInfo {
        private long userId;
        private int age;
        private String name;
        private String email;

        public UserInfo(long userId, int age, String name, String email) {
            this.userId = userId;
            this.age =age;
            this.name = name;
            this.email = email;
        }

        public long getUserId() {
            return userId;
        }

        public void setUserId(long userId) {
            this.userId = userId;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }
    }

    public static UserInfo getUserInfoById(long userId) {
        return new UserInfo(userId, (int) (userId * 10 % 100), userId + "_name", userId + "dugk@foxmail.com");
    }

    public static List<UserInfo> batchUserInfoByIds(List<Long> userIdList) {
        return userIdList.stream().map(ConsumerServiceClient::getUserInfoById).collect(Collectors.toList());
    }

}
