# AirConditionerClient-Android-Java
AirConditionerClient-Android
分布式温控系统的客户端，用Android实现。 这只是客户端的实现，服务端看我的另外一个项目。

通信协议： Android客户端使用Socket TCP来和服务器通信。 通信协议——温控系统-通信协议格式.docx里描述了Socket报文。具体参照此文档。

项目概况： 这个项目是为了解决廉价酒店的温控系统而设计的，主要分为客户端和服务器两个部分。客户端可以向服务器请求开关机、调温、调风、调节模式。服务器针对客户端的请求，经过调度后将请求的结果返回给客户端，客户端可以针对请求的结果对界面的控件进行修改或者不修改。服务器会定时地将状态包发送到客户端，客户端接受到状态包后，会及时更新控件的内容，达到可以实时地向用户反映该房间的当前温度，空调的目标温度、制冷模式、风速、能耗和电费。

效果展示
![image](https://github.com/q474890522/AirConditionerClient-Android-Java/raw/master/效果图/effectpicture.jpg)
