# Agent Skills技术协议与开源实现

## 01背景

随着模型能力提升，智能体已能与完整计算环境（如代码执行、文件系统）交互，执行跨领域复杂任务，更强大的智能体需要模块化、可扩展、可移植的方式注入领域专业知识，一个常规的做法是使用"Tool Calling"的方式，目前MCP（Model Context Protocol）协议已成为业界普遍采用的工具调用标准接口协议；然而，复杂的功能需求客观上对工具的承载能力带来了挑战，为了应对这一问题，Skills应运而生，其采用更加复杂的上下文表征，附带资源文件和可执行脚本，通过启发式上下文加载的方式来压缩上下文，使得智能体可以完成更加复杂的任务。

从可复用的视角看，Skills理念为“技能即知识”，将人类的流程性知识打包为可复用、可组合的“技能”，无需为每个场景重建定制智能体，以结构化文件夹形式（含指令、脚本、资源）动态加载，使智能体在特定任务上表现更优。构建技能如同编写入职指南，降低专业化门槛，任何人都能通过提炼并共享自身的流程性知识，以模块化方式为智能体赋予特定能力。

## 02Agent Skills是什么？

### 1. 工程文件结构

```java
skill-name/
├── SKILL.md              # Main skill definition           (Required)
├── reference.md          # Detailed reference material     (Optional)
├── LICENSE.txt           # License information             (Optional)
├── resources/            # Additional resources            (Optional)
│   ├── template.xlsx     # Example files
│   └── data.json         # Data files
└── scripts/              # Executable scripts              (Optional)
    ├── main.py           # Main implementation
    └── helper.py         # Helper functions
```

### 2. SKILL.md 文件格式

SKILL.md 文件使用YAML前置内容定义元数据，后续为详细说明的Markdown内容。

💡 说明：

○ name和description字段为必填项。
○ pdf/SKILL.md文件的正文部分应提供关于技能的全面描述，包括功能、使用说明、参考资料、资源和示例。SKILL.md示例：

### 3、绑定附加内容

附加的文件可以包含在SKILL.md中以扩展技能功能，例如：
○ References (例如 reference.md 和 forms.md)
○ Scripts 目前支持的类型包括python、shell、js等

### 4、技能和上下文

○ 推荐设置技能文件的token限制，以确保在上下文窗口限制内高效加载

