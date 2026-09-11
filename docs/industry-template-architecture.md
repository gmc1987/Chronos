# Chronos 行业模板与可插拔模块

## 目标

Chronos 采用“公共平台能力 + 行业模板 + 行业领域模块 + 独立启动包”的产品结构。
公共模块中不再新增医院、学校等行业判断；行业差异通过
`IndustryTemplateProvider` 扩展契约提供。

## 当前模块

| 模块 | 作用 |
| --- | --- |
| `platform-industry` | 行业扩展契约、模板注册中心、激活配置、安装版本记录和公共上下文接口 |
| `industry-hospital-template` | 医疗品牌、医院组织类型、科室类型、医疗岗位类别及功能声明 |
| `industry-education-template` | 教育品牌、学校组织类型、院系类型、教育岗位类别及功能声明 |
| `hospital-app` | 医院部署启动包，引用医院模板和 `hospital-oa` |
| `education-app` | 教育部署启动包，只引用教育模板和公共平台模块 |
| `education-class-scheduling` | 教育行业走班教学任务、教室资源、周课表与排课冲突校验 |

## 依赖规则

1. `platform-*` 不能依赖具体行业模板。
2. 行业模板依赖 `platform-industry`，实现 `IndustryTemplateProvider`。
3. 行域业务使用独立表和独立模块，不能向 IAM、流程、消息等公共表追加行业字段。
4. 启动包决定最终打包哪些行业模块；医院和教育部署不能相互引用业务模块。
5. 前端行业模块通过 `src/industries/<industry>` 导出品牌、选项和行业路由。

## 行业上下文接口

登录前可访问：

```text
GET /api/public/industry/context
```

Vite 本地代理会去掉 `/api` 前缀，因此后端同时接受
`/public/industry/context` 和 `/api/public/industry/context`。

接口返回当前行业编码、模板版本、品牌、组织类型、部门类型、岗位类别、功能及导航声明。
前端启动时优先读取服务端上下文，服务端不可用时使用
`VITE_CHRONOS_INDUSTRY` 指定的构建期模板。

## 启动方式

医院版本：

```bash
./mvnw -pl hospital-app -am spring-boot:run
```

教育版本：

```bash
./mvnw -pl education-app -am spring-boot:run
```

教育前端构建：

```bash
VITE_CHRONOS_INDUSTRY=EDUCATION npm run build
```

## 数据库

首次部署先执行：

```text
docs/sql/industry-template-platform-v1.sql
```

`sys_industry_template_installation` 只记录已安装版本和当前激活模板。
模板安装器不会覆盖用户已经调整的菜单、权限、流程、表单或门户配置。
未来模板升级需要提供带版本号、可审计、可重复执行的迁移器。

## 新增行业步骤

1. 新建 `industry-<code>-template` Maven 模块。
2. 实现 `IndustryTemplateProvider` 并声明唯一行业编码和模板版本。
3. 在 `src/industries/<code>` 增加前端行业描述和路由。
4. 新建独立部署启动包，只引用所需公共模块、行业模板和行业业务模块。
5. 为行业领域表提供独立数据库迁移。
6. 分别执行后端编译、前端行业构建和实际数据库启动验证。

## 下一阶段

教育模板已经增加 `education-class-scheduling` 走班排课模块，当前支持教学任务、
教室资源、周课表维护，以及教师、教学班、教室和容量冲突校验。
后续仍建议按 `education-academic`、`education-teaching`、`education-student`
继续拆分学年学期、专业班级、教师学生、课程、选课、考试和成绩功能。
