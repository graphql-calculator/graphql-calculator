package calculator.generator;

import java.util.List;

class Query {
    private Commodity commodityAlias;
    private Consumer consumer;

    public Commodity getCommodityAlias() {
        return commodityAlias;
    }

    public Consumer getConsumer() {
        return consumer;
    }

    public void setCommodityAlias(Commodity commodityAlias) {
        this.commodityAlias = commodityAlias;
    }

    public void setConsumer(Consumer consumer) {
        this.consumer = consumer;
    }

}
class Commodity {
    private List<ItemBaseInfo> itemList;

    public List<ItemBaseInfo> getItemList() {
        return itemList;
    }

    public void setItemList(List<ItemBaseInfo> itemList) {
        this.itemList = itemList;
    }

}
class ItemBaseInfo {
    private List<Sku> skuList;

    public List<Sku> getSkuList() {
        return skuList;
    }

    public void setSkuList(List<Sku> skuList) {
        this.skuList = skuList;
    }

}
class Sku {
    private Integer itemId;

    public Integer getItemId() {
        return itemId;
    }

    public void setItemId(Integer itemId) {
        this.itemId = itemId;
    }

}
class Consumer {
    private List<User> userInfoList;

    public List<User> getUserInfoList() {
        return userInfoList;
    }

    public void setUserInfoList(List<User> userInfoList) {
        this.userInfoList = userInfoList;
    }

}
class User {
    private Integer userId;
    private Integer age;
    private String name;
    private String email;

    public Integer getUserId() {
        return userId;
    }

    public Integer getAge() {
        return age;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

}