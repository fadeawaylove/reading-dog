# 整页阅读排版对齐番茄 7.3.9.32

2026-10-28。本次替换正文排版与分页实现，并已覆盖安装到真实小米 6。保留菜单、书架、网络业务、用户偏好及并发聊天的界面改动。

**当前结论：采集的 105 组同文本参考中，逐行文本/原文偏移、每页起止偏移、段评所在页全部一致。截图字形边界偏差不超过 1 个物理像素。尚不能据此声称任意章节、设备、字重设置均与番茄完全一致。**

## 参考身份和取证

- 参考 App：`com.dragon.read`，7.3.9.32。APK SHA-256：`ab70ed786ae97d93ddfac9efd8d48dac8ee27a05eab5d16fbcecbd9033a31d23`。
- 实际设备：小米 6，Android 9，1080×1920，density/scaledDensity=3；没有使用模拟器。
- 实际对照章节：《冷宫侍卫，从与皇后双修开始无敌》第 209 章“难不成你也有难言之隐”，book `7678661500596145214`，chapter `7700242590037199934`。使用 App 已能正常阅读的完整正文，没有修改番茄 APK，也没有为此替换网络接口。
- 使用 REA 检查 Java 配置和实际 `libtttext.so`，再使用 Florida 采集当前 TTEpubChapter 的原文、运行时配置、逐行字符矩形、分页范围和段评 delegate。没有替换为标准 Frida。
- `libtttext.so` SHA-256：`82898cea5f5ea39e42fc9515cef690b62c0ac04e94a7ec75fd91d245abc3d34c`。主要追踪点：`LineRange::DistributeSpace` 0x3190c、`TextLine::AddBreakableRuns` 0x42bc8、`GetAvailableCompressType` 0x443cc、`PunctuationCompressUtils::GetCompressType/ResolveCompressType` 0x4ccc8/0x4cd14；断词选择 0x2efc0、边缘限制 0x29e4c。REA IDA 部分请求返回 post-operation readiness 错误，其已返回的伪代码保留为 partial evidence，没有将其伪称为完整验证。
- Java `uo7.z0.b0()->getProperties().f()` / `m0()->h()` 和 `to7.a.h()` 为标题字号来源链的一部分。默认配置字段并不等于最终 CSS 的效果：例如 24 号下配置 titleLineSpace=65，而实际标题 CSS 的 line-space=13.5px 经 em/像素换算后为 40.5 物理像素；实现采用运行时结果。

## 实际生效的设置映射

下面尺寸为参考设备上的物理像素，dp 映射统一通过 density 计算。

| 项目 | 番茄生效值 / 本次映射 |
|---|---|
| 正文矩形 | x=72..1008，y=90..1809；宽 936，高 1719；对应侧边 24dp、顶部 30dp、底部 37dp |
| 正文字号 | 字号档 × scaledDensity；24 档为 72px |
| 字体 | 方正悠黑 `FZVariable-YouHeiJ.ttf`，默认 wght=400；方正悠宋 `FZYouSJVFWT1.ttf` |
| 标题字号 | 正文字号 <20 加 4；20..24 加 3；25..29 加 2；30 加 1；保留原生 CSS em 比例六位小数的往返结果 |
| 标题字重 | 同字体，fake bold；左对齐，无首行缩进 |
| 标题额外行距 | 实际标题像素字号 × 0.5 |
| 正文首行缩进 | 两个 em，原文不插入空格 |
| 正文行框 | 字号 × 1.4；额外行距上下平分 |
| 三档额外行距 | round(像素字号 × 0.65 / 0.8 / 1.0)，按 double 运算后取整 |
| 段距 | 前两档 20dp；大间距 26dp |
| 页底可容纳范围 | 按字形框判断，最后半份额外行距可超过内容底边 |
| 标点规则 | conditional 压缩，压缩比例上限 0.5，仅全角标点，CJK 分组回退限制 4；实际字形墨迹位置参与压缩类别判断 |
| 对齐 | 非末行在断行计算中分配可用空间；ASCII 单词内部不分配字距，段尾不强行拉伸 |
| 段评尺寸 | 字号 ≤19 为 24dp 方形；20..29 为 26dp；30 为 30dp。只有实际正数段评占位 |
| 段评预留与绘制 | 排版预留方形宽度 +4dp；可见方形从最后文本末端开始，不能再加 4dp 可见间隔；位置向整数截断 |
| 段评图形与计数 | 使用原 Drawable 渲染的三种尺寸图形；数字按 TextView 的 includeFontPadding/整数居中基线绘制 |

24 号段尾气泡的真实 TextView bounds 为 `[324,606,402,684]`，计数字号 27px，局部 baseline=49，flags=1313。模型与绘制已对齐；[原始检查结果](typography-evidence/native-comment-view.json)保留了层级中其他同文字 TextView，不能误取讨论区数字。

## 代码变化

`ReaderTextLayout` 独立完成 Unicode 文字簇、全上下文测量、标点压缩、禁则/断点回退、行内空间分配、末行处理和英文断词。英文模式和例外表来自参考库的压缩资源；不在原文插入连字符，必要的可见断词连字符属于绘制结果。

`ReaderPaginator` 使用实际内容矩形，不再按整数汉字列缩窄。它输出不可变分页/片段集合及共享行坐标；绘制、分页和段评命中都消费同一结果。段评作为真实末尾行内占位参与断行，不再把最后一个原文字绑定固定气泡宽度。网络段评数到达后在空闲时以相同原文锚点重排，拖动/动画期间不改变当前分页。

原文 UTF-16 偏移继续用于阅读进度、书签和段评索引。参考 TTText 的 scalar 偏移仅在采集数据规范化时转换，不改写业务原文。页数缓存 profile 升级为 `tomato73932-clusters-v4`，旧页数重新计算。旧 `StaticLayout` / SIMPLE 断行检查不再作为预期。

## 真机结果与完整对照

[105 组矩阵结果](typography-evidence/matrix.json)：5,649 段，18,588 行。断行差异 0，字形分配坐标差异 0，分页/页范围差异 0，行框纵坐标差异 0，段评所在页差异 0。最大水平坐标误差 0.0001221px，最大行框纵坐标误差 0.0006104px。

[逐行与分页 CSV 报告](typography-evidence/line-page-comparison.zip)包含每个 fixture 的原文起止偏移、逐行原文、对应行位置和页起止值；参考原始数据在 `tests/typography-goldens.zip`。样本来源及复跑步骤见 [测试说明](tests/TYPOGRAPHY.md)。段评所在行同时由其段尾行的精确文本和页内顺序约束，单独比较其所在页，不能把 native delegate 内部矩形误认为可见 View bounds。

六组同文本截图均由真实 MI 6 捕获。左为番茄，右为生产 ReaderSurface 在独立测试包中渲染同一原文，字体/字号/行距和正数段评对应。独立包避免向主 App 书架写入测试书。菜单、页眉页脚、章末讨论区属保留的业务 UI，不计入正文图像范围；章末裁切为 x72..1008、y90..720，其余为 x72..1008、y90..1809。原截图保留完整画面。

| 样本 | 并排截图 | 叠加差异图 | 单字墨迹边界最大偏差 |
|---|---|---|---|
| 悠黑 24 / 适中 / 章节首页 | [截图](typography-evidence/sans-normal-page0-paired.png) | [叠加](typography-evidence/sans-normal-page0-overlay.png) | 1px |
| 悠黑 / 连续正文页 1 | [截图](typography-evidence/sans-normal-page1-paired.png) | [叠加](typography-evidence/sans-normal-page1-overlay.png) | 1px |
| 悠黑 / 连续正文页 2 | [截图](typography-evidence/sans-normal-page2-paired.png) | [叠加](typography-evidence/sans-normal-page2-overlay.png) | 1px |
| 悠黑 / 末页与段评 | [截图](typography-evidence/sans-normal-last-paired.png) | [叠加](typography-evidence/sans-normal-last-overlay.png) | 1px |
| 悠宋 24 / 大间距 / 正文页 1 | [截图](typography-evidence/serif-large-page1-paired.png) | [叠加](typography-evidence/serif-large-page1-overlay.png) | 0px |
| 悠宋 24 / 适中 / 末页与段评 | [截图](typography-evidence/serif-normal-last-paired.png) | [叠加](typography-evidence/serif-normal-last-overlay.png) | 0px |

字形边界明细见 [glyph-bounds-report.json](typography-evidence/glyph-bounds-report.json)。图像比较分别归一化墨色，以保留另一聊天明确要求的浅底纯黑文字。**这不是位图逐像素相等**：悠黑首页与正文页 1 在 coverage>0.35 的轮廓距离检查中各有 1 个像素超出邻域，其他四张为 0；悠黑平均 coverage 差异约 0.0026..0.0036。软件绘制试验没有消除该差异，未为压低指标而修改阈值或加入经验位移。它与逐字边界最大偏差 1px 是不同指标，原始 pixel-report 均保留。不能将归一化后的结论表述为墨色/抗锯齿完全相同。

## 主 App 安装、用户章节和恢复

本次主 App APK 使用最新工作区全部生产源码构建，包含另一聊天的纯黑文字、主页字号及段评下拉关闭改动；没有单独提交、推送或发布版本。

[实际章节集成检查](typography-evidence/integration.json)通过：原《神类起源？》章节锚点 1938 保留；字号调整后仍覆盖原锚点；前后翻页返回原页；实际段评矩形点击命中正确段落并加载评论内容。截图保存在本目录 `user-*.png`。

[安装/保留记录](typography-evidence/installed.json)：已安装 APK 与交付 APK 字节相同；原书架 ID/添加时间/最近阅读顺序、进度内容、书签保留；ReaderActivity 偏好文件逐字节相同。常规联网刷新更新了书籍元数据/封面签名 URL；恢复进度的正常保存更新了 progress.updated，因此没有宣称数据库文件哈希不变。没有清除两款 App 数据。

番茄已恢复为原书/原章节第 23 页（24 页中的末页，页脚 5004/5223）、悠黑 24、适中间距/页边距、无动画 5、原日间背景。误打开的推荐书浏览记录已单独移除，原历史保留。主 App 恢复原阅读设置与锚点，最终展示书架。

安装包：[reading-dog-typography.apk](releases/reading-dog-typography.apk)，沿用原版本和签名，SHA-256：`4992216040c20363770818c350d0d17b55ac4db3241745f32a545338b7f5da43`。生产源文件/资源哈希见 [source-sha256.json](typography-evidence/source-sha256.json)。

## 未扩大宣称的边界

已测完整章节矩阵及专项原生回放覆盖当前 16..30 字号、三档行距和两种字体。截图和实际气泡 View 像素检查集中于 24 号，没有逐一拍摄所有字号/字重/主题组合。自定义加粗保留本 App 原有实现与用户偏好，尚未建立番茄全部字重档位的独立映射；不能称其已完全一致。其他设备尺寸、系统字体缩放和更多章节也没有完整原生参考矩阵。因此本次可以交付已验证的排版实现和证据，不能宣布“所有场景与番茄完全一致”。
