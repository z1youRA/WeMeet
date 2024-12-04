# WeMeet

WeMeet 是一个基于 Android 的应用程序，允许用户在地图上共享位置并进行聊天。

## 配置

在运行项目之前，需要配置 `local.properties` 和 `secrets.properties` 文件。这些文件包含项目所需的敏感信息，如 API 密钥等。

### 配置 `local.properties`

`local.properties` 文件用于配置本地开发环境的路径信息。通常，这个文件包含 Android SDK 的路径。

1. 在项目根目录下创建一个名为 `local.properties` 的文件。
2. 添加以下内容，并根据你的 Android SDK 安装路径进行修改：

    ```properties
    sdk.dir=/path/to/your/android/sdk
    ```

### 配置 `secrets.properties`

`secrets.properties` 文件用于存储敏感信息，如 API 密钥。请确保不要将此文件提交到版本控制系统中。

1. 在项目根目录下创建一个名为 `secrets.properties` 的文件。
2. 添加以下内容，并根据你的实际情况进行修改：

    ```properties
    MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY
    ```

### 配置 `local.defaults.properties`

`local.defaults.properties` 文件用于存储默认的敏感信息，可以提交到版本控制系统中。

1. 在项目根目录下创建一个名为 `local.defaults.properties` 的文件。
2. 添加以下内容，并根据你的实际情况进行修改：

    ```properties
    MAPS_API_KEY=DEFAULT_API_KEY
    ```

## 配置服务器数据库

1. Rename `.env.example` to `.env`
2. Add your MongoDB Atlas credentials to `.env`
3. Never commit `.env` file