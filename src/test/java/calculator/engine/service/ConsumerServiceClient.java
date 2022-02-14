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


import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ConsumerServiceClient {

    static class UserInfo {
        private int userId;
        private int age;
        private String name;
        private String email;
        private String clientVersion;

        public UserInfo(int userId, int age, String name, String email, String clientVersion) {
            this.userId = userId;
            this.age = age;
            this.name = name;
            this.email = email;
            this.clientVersion = clientVersion;
        }

        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
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

    public static UserInfo getUserInfoById(int userId, String clientVersion) {
        if (userId == 0L) {
            return null;
        }

        return new UserInfo(userId, userId * 10 % 100, userId + "_name", userId + "dugk@foxmail.com", clientVersion);
    }

    public static List<UserInfo> batchUserInfoByIds(List<Integer> userIdList, String clientVersion) {
        if (userIdList == null || userIdList.isEmpty()) {
            return Collections.emptyList();
        }
        return userIdList.stream().map(userId -> getUserInfoById(userId, clientVersion)).collect(Collectors.toList());
    }


    static class NewUserInfo {
        private int userId;
        private String sceneKey;
        private boolean isNewUser;

        public NewUserInfo(int userId, String sceneKey, boolean isNewUser) {
            this.userId = userId;
            this.sceneKey = sceneKey;
            this.isNewUser = isNewUser;
        }

        public int getUserId() {
            return userId;
        }

        public void setUserId(int userId) {
            this.userId = userId;
        }

        public String getSceneKey() {
            return sceneKey;
        }

        public void setSceneKey(String sceneKey) {
            this.sceneKey = sceneKey;
        }

        public boolean isNewUser() {
            return isNewUser;
        }

        public void setNewUser(boolean newUser) {
            isNewUser = newUser;
        }
    }

    public static NewUserInfo getNewUserInfoById(String redisKey) {
        if (redisKey == null) {
            return null;
        }

        String[] split = redisKey.split(":");
        if (split.length != 3) {
            return null;
        }

        return new NewUserInfo(Integer.parseInt(split[2]), split[0] + "_" + split[1], Integer.parseInt(split[2]) % 2 == 0);
    }
}
