安装脚本：
```shell
docker run --name nacos-standalone-derby -e MODE=standalone -e NACOS_AUTH_TOKEN=SecretKey012345678901234567890123456789012345678901234567890123456789 -e NACOS_AUTH_IDENTITY_KEY=nacos-server -e NACOS_AUTH_IDENTITY_VALUE=nacos-server-secret -p 8080:8080 -p 8848:8848 -p 9848:9848 -d nacos/nacos-server:v3.2.4
``` 

访问 web：http://rxs:8080/next/#/login

用户名：nacos

密码：123456