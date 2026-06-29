package org.microsoft.qintelipass.models;

public class User {
    private String id;
    private String phone;
    private String wechatOpenId;
    private String status;
    private String name;
    
    public User() {}
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    
    public String getWechatOpenId() { return wechatOpenId; }
    public void setWechatOpenId(String wechatOpenId) { this.wechatOpenId = wechatOpenId; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
