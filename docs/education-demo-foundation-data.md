# 教育演示基础数据

`Chronos/scripts/education-demo-foundation-data.sql` 是独立于 Flyway 的本地/非生产演示数据脚本，用于准备一所职业学校的基础主数据。它不会修改迁移历史，也不会创建新的 IAM 角色。

## 数据规模

| 类型 | 数量 |
| --- | ---: |
| 学校 | 1 |
| 组织单元 | 17（3 个行政/教学单元、4 个年级单元、10 个专业单元） |
| 年级 | 4 |
| 专业 | 10 |
| 行政班 | 40 |
| 学生 | 2,000（每班 50 人） |
| 教师 | 50（40 名班主任、10 名专业负责人） |
| 教室 | 50（40 间班级教室、10 间实训室） |
| 家长/监护关系 | 2,000 / 2,000 |
| 演示账号 | 4,052 |

脚本使用 `DEMO-` 主数据编码和 `demo.` 登录名前缀，并在事务结束前执行数量断言；重复执行不会增加重复记录。执行失败会回滚本次事务。

## 执行

先完成 `education-app` 的 Flyway 迁移，再在非生产数据库执行：

```bash
psql -v ON_ERROR_STOP=1 \
  -U Chronos \
  -d ChronosEducation \
  -f Chronos/scripts/education-demo-foundation-data.sql
```

也可以使用当前教育数据库的环境变量：

```bash
PGURL="${CHRONOS_DB_URL#jdbc:postgresql:}"
psql "$PGURL" -v ON_ERROR_STOP=1 \
  -U "${CHRONOS_DB_USERNAME:-Chronos}" \
  -d "${CHRONOS_DB_NAME:-ChronosEducation}" \
  -f Chronos/scripts/education-demo-foundation-data.sql
```

统一演示密码为 `ChronosDemo@2026`，只允许用于本地演示，禁止带入生产环境。

## 账号命名

- `demo.admin`：平台管理员。
- `demo.registrar`：教务管理员。
- `demo.teacher.major01` 至 `demo.teacher.major10`：专业负责人。
- `demo.teacher.g1c01` 至 `demo.teacher.g4c10`：40 名班主任。
- `demo.student.demo-g1-c01-s001` 至 `demo.student.demo-g4-c10-s050`：学生账号。
- 对应的 `demo.parent.*`：家长账号。

学生和家长账号均绑定到对应业务档案；每名演示学生有一条有效主监护关系。脚本只引用仓库中已有的角色代码，若某个可选角色尚未部署，账号仍会创建，但不会获得不存在的角色。
