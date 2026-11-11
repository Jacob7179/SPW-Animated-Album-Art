<p align="center">
  <img src="docs/assets/cover.png" width="320" alt="SPW Animated Album Art">
</p>

<h1 align="center">SPW Animated Album Art</h1>

<p align="center"><a href="README.md">English</a> | 简体中文</p>

适用于 Salt Player Windows/Linux 版本的模组。将 `square_animated_artwork.mp4` 放在歌曲文件旁边，然后导入 `plugin-com.spw.animated.album.art-0.4.5.1.spmod`。

视频会在原生封面画布中替换静态封面的像素。亮度滤镜及其设置选项已移除，解码后的视频源颜色将直接显示。已有的 `dim_video` 设置将被忽略。

支持在封面与歌词视图切换时实时解析封面坐标。视频直接循环播放，不会将结尾与开头混合过渡。

随附 FFmpeg 的许可信息位于 `licenses/`。可使用 `build.ps1` 和 JDK 25 构建；构建还需要宿主程序归档文件以及已缓存的 Kotlin 2.4.20 编译器。

## 版本 0.4.5.1：构建、下载与播放修复

- 通过将官方 Compose Runtime 添加为“仅编译”（compile-only）依赖项，修复了 Compose API 的构建错误。
- MP4 直接下载现改用 Java HTTPS，从而避免了因内置 FFmpeg 的 TLS 支持差异而导致的问题。
- 完整视频验证明确使用 `rawvideo` 格式；在线下载时无需依赖 `libx264`。
- 在原有的插件目录日志之外，新增了持久化的用户日志。
- 下载的 Apple MP4 文件在重新封装（remux）时无需重新编码，旨在将起始时间戳归零，并修正时长信息。
- 在原生封面完成绘制前，保持视频首帧显示，防止播放进度在用户不可见的情况下向前推进。
- 新的缓存文件名采用 `seek-v1-` 前缀，确保带有偏移时间戳的旧缓存文件会被重新下载。
- 修复了在宿主类加载器（host class loader）未提供 `CodeSource.location` 时的 Linux 启动问题；现改为通过类资源（class resources）及 JVM 类路径（classpath）来识别宿主环境。
- 针对旧版 Linux FFmpeg 构建环境，允许在 MP4 重新封装过程中出现可恢复的时间戳警告；但完整视频验证仍保持严格标准。
- 校验重新封装后的数据包时间戳；若 FFmpeg 改变了原始时间戳，则保留原 MP4 文件。播放时，系统会在帧率转换前将时间轴重置回原始基准。

## 第三方致谢与许可

在线封面功能使用 [m8tec 的 Apple Music Animated Artworks](https://github.com/m8tec/apple-music-animated-artworks) 公开 API，服务托管于 [artwork.m8tec.top](https://artwork.m8tec.top/)。该服务的服务器端源代码并未包含在本插件中。

SPW Animated Album Art 及其独立编写的 API 客户端采用 [GNU AGPL v3.0](LICENSE)（`AGPL-3.0-only`）许可证。审查期间未能确认上述外部项目的上游软件许可证；此处的致谢不代表授予使用许可，也不表示该服务采用 MIT 许可证。本项目的 AGPL-3.0-only 许可证不涵盖外部服务、下载的 Apple Music 封面素材或第三方商标。服务使用及封面素材的相关权利仍受适用条款和权利持有者的规定约束。

详情请参阅[第三方声明](licenses/THIRD_PARTY_NOTICES.md)及 [FFmpeg 第三方许可声明](licenses/FFmpeg-THIRD-PARTY.md)。

## 项目许可证

Copyright (c) 2026 Jacob。SPW Animated Album Art 仅采用 GNU Affero General Public License 第 3 版（`AGPL-3.0-only`）授权。完整条款请参阅 [LICENSE](LICENSE)。第三方组件及封面素材保留各自的许可和权利，详情见[第三方声明](licenses/THIRD_PARTY_NOTICES.md)。

## 构建与兼容性

需要 JDK 25 和已配置的 Salt Player 宿主程序归档文件。在 Windows 上运行 `build.bat`。构建使用官方 Compose Runtime 1.12.0 作为仅编译时依赖，以便即使宿主包的元数据不完整，Kotlin 仍能解析顶层 API。Compose Runtime 不会打包进插件，而是由 Salt Player 在运行时提供。替代的 PowerShell 构建方式要求 Gradle 缓存中已有该依赖。

构建前，将 Windows 可执行文件放在 `bin/ffmpeg.exe`，Linux x86-64 可执行文件放在 `bin/ffmpeg`。打包时仅包含这两个可执行文件及许可声明，不包含 FFmpeg 发行包中的其他文件。在 Linux 上会自动恢复文件所有者的执行权限。二进制文件必须与系统架构匹配，且位于允许执行程序的文件系统上。

原生封面方法通过源代码追踪信息、JVM 方法签名和类型特征进行动态定位。若缺少 `CodeSource.location`，宿主归档定位会检查类资源、类加载器 URL 和 JVM 类路径。只有包含预期宿主类的位置才会被接受；如果找不到宿主程序，将输出诊断信息，而不是发生空指针错误。插件没有固定的 Salt Player 版本限制；缺失或存在歧义的 API 特征会产生诊断信息，但这并不保证兼容所有版本。

## 在线下载与日志

优先使用本地视频文件。启用在线获取后，插件会根据专辑艺人及专辑名称搜索匹配的动态封面，将其下载至数据缓存，并在正式使用前验证整个视频。如果搜索或下载失败，则保留原生静态封面。直接下载 MP4 使用 Java HTTPS；仅提供 HLS 的来源仍需要 FFmpeg 支持 HTTPS。

持久化日志保存位置：

- Linux：`$XDG_STATE_HOME/SPW-Animated-Album-Art/animated-cover.log`；若未设置该环境变量，则为 `~/.local/state/SPW-Animated-Album-Art/animated-cover.log`。
- Windows：`%LOCALAPPDATA%/SPW-Animated-Album-Art/animated-cover.log`。

插件也会尝试在安装目录中写入 `animated-cover.log`。日志包括启动检查、搜索与下载阶段、元数据缺失、HTTP 错误以及 FFmpeg 验证错误。

Linux 上的实际播放仍需在目标系统中验证；构建和实时下载测试已在 Windows 上完成。

下载的 Apple MP4 会在不重新编码的情况下重新封装，以将起始时间戳归零并重写时长。例如，一个源时间戳为 10–20 秒的 10 秒视频片段会转换为正常的 0–10 秒 MP4。新缓存文件使用 `seek-v1-` 前缀，因此时间戳损坏或偏移的旧缓存会重新下载。此操作不会补充源视频中不存在的画面。

Linux 时间戳处理会在重新封装时保留数据包时间戳，并对照原始下载文件检查数据包内容、PTS、DTS、时长及时间基转换。如果本地 FFmpeg 改变时间信息或无法重新封装，则改为保留原始 MP4，并在帧率转换之前通过播放滤镜重新基准化时间戳。缓存前仍会执行完整解码验证。因此，即使使用较旧的 Linux FFmpeg，也能保留源视频画面；但通过此回退机制保留的缓存文件，在外部播放器中仍可能显示原始时间戳偏移。

对于受支持的单视频轨道 Apple 分片 MP4，插件现在会直接通过 Java 从每个分片的解码时间中减去初始偏移量，以重新基准化时间戳。编码样本、样本时长、合成时间偏移和 MP4 box 位置均保持不变，从而避免不同平台重新封装器的行为差异。剩余的一帧合成延迟由播放时的 `setpts` 滤镜处理。包含编辑列表、多轨道、外部分片索引或明确非零头部时长的文件，仍使用经过验证的 FFmpeg 处理流程。新的 `seek-v1-` 缓存文件名会使旧的偏移缓存重新获取。

为支持外部播放器中的手动跳转，对于受支持的 Apple 分片文件，插件会先通过 Java 重新基准化时间戳，再重新封装为常规 MP4，同时重建样本/关键帧索引及 fast-start 元数据。重新封装后会验证数据包内容和时间信息。如果本地 FFmpeg 无法保留时间信息，重新基准化后的源文件仍可供插件使用，但外部播放器的跳转功能可能仍受限制；该回退情况会明确记录在日志中。新的 `seek-v1-` 缓存名称可避免重复使用早期的分片缓存文件。
