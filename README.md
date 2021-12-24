# ElytraData 鞘翅数据
<image src="https://jitpack.io/v/ElytraServers/ElytraData.svg?style=flat-square"></image>
<image src="https://img.shields.io/github/license/ElytraServers/ElytraData?label=License&style=flat-square"></image>
<image src="https://img.shields.io/github/stars/ElytraServers/ElytraData?label=Stars&style=flat-square"></image>
<image src="https://img.shields.io/badge/author-Taskeren-red?style=flat-square"></image>

鞘翅数据内置了 MongoDB 支持，为后续的插件提供数据库。你可以使用下列方法获取到 `MongoClient`，`MongoDatabase` 实例。

## 使用

### (前置) 添加依赖

你需要在你插件的 `plugin.yml` 中添加

```yaml
depend: ["ElytraData"]
```

然后在你插件的项目中引入

```xml
<project>
    <repositories>
        <repository>
            <id>jitpack</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>
    
    <dependencies>
        <dependency>
            <groupId>com.github.ElytraServers</groupId>
            <artifactId>ElytraData</artifactId>
            <version>${THE_LATEST_VERSION}</version>
        </dependency>
    </dependencies>
</project>
```

### 获取 MongoClient 和 MongoDatabase

```java
import cn.elytra.data.ElytraDataPlugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class ExamplePlugin extends JavaPlugin {

	@Override
	public void onEnable() {
		// 获取 MongoClient
		var client = ElytraDataPlugin.getInstance().getClient();

		// 获取插件 MongoDatabase
		var pluginDb = ElytraDataPlugin.getInstance().getDatabase(this);
		
		// 获取自定义 MongoDatabase
		var customDb = ElytraDataPlugin.getInstance().getDatabase("CUSTOM_NAME_HERE");
	}
}
```