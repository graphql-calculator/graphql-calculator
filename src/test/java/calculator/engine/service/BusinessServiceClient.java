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

public class BusinessServiceClient {

    static class SellerInfo {
        private long sellerId;
        private int age;
        private String name;
        private String email;

        public SellerInfo(long sellerId, int age, String name, String email) {
            this.sellerId = sellerId;
            this.age = age;
            this.name = name;
            this.email = email;
        }

        public long getSellerId() {
            return sellerId;
        }

        public void setSellerId(long sellerId) {
            this.sellerId = sellerId;
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

    public static SellerInfo getSellerInfoById(long sellerId) {
        return new SellerInfo(sellerId, (int) (sellerId * 10 % 100), sellerId + "_name", sellerId + "dugk@foxmail.com");
    }

    public static List<SellerInfo> batchSellerInfoByIds(List<Long> sellerIdList) {
        return sellerIdList.stream().map(BusinessServiceClient::getSellerInfoById).collect(Collectors.toList());
    }
}
