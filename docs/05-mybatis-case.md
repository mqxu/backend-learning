# 学生成绩查询案例：从建表到多表查询

这份教程用一个小案例把 MyBatis-Plus 的查询走一遍。案例本身很简单：输入一个学生 id，查出他的成绩单，显示姓名、课程名、分数。

看着简单，但它把新手最容易卡住的几件事都串起来了：表怎么设计、实体怎么和表对上、查询条件怎么写、为什么单表查不出课程名、多表查询的两种做法分别是什么。

## 案例全貌

三张表，两个班级，六个学生，十五条成绩。最终要拿到的东西长这样：

| 学生 | 课程 | 分数 |
| --- | --- | --- |
| 张伟 | 后端工程化开发 | 88.50 |
| 张伟 | 前端工程化开发 | 92.00 |
| 张伟 | Web 应用系统综合实训 | 76.00 |

三张表的关系：

```text
t_student（学生）  1 ─── n  t_score（成绩）  n ─── 1  t_course（课程）
```

一个学生有多条成绩，一门课有多条成绩，成绩表站在中间，把两边接起来。这种表在数据库里叫中间表，也叫关联表。

**为什么成绩表里只存两个 id**

先看一个反例。假如成绩表长这样：

| id | student_name | course_name | score |
| --- | --- | --- | --- |
| 1 | 张伟 | 后端工程化开发 | 88.50 |
| 2 | 张伟 | 前端工程化开发 | 92.00 |

问题很快就来了。张伟改名叫“张玮”，这两行都得改，改漏一行数据就不一致。课程名改了也一样。数据存了两份，就有两份可能对不上。

所以成绩表只存 id：

| id | student_id | course_id | score |
| --- | --- | --- | --- |
| 1 | 1 | 1 | 88.50 |
| 2 | 1 | 2 | 92.00 |

名字放在各自的表里，一处一份，改一次就全改。代价是查的时候要自己把名字取出来 —— 这正是后面多表查询要解决的事。

## 建表

学生表：

```sql
CREATE TABLE t_student
(
    id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(32)        NOT NULL COMMENT '姓名',
    gender      TINYINT            NOT NULL DEFAULT 0 COMMENT '性别，0 未知 / 1 男 / 2 女',
    age         TINYINT UNSIGNED COMMENT '年龄',
    class_name  VARCHAR(32)        NOT NULL COMMENT '班级',
    create_time DATETIME           NOT NULL COMMENT '创建时间，自动填充',
    update_time DATETIME           NOT NULL COMMENT '更新时间，自动填充',
    deleted     TINYINT            NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_class_name (class_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='学生表';
```

课程表：

```sql
CREATE TABLE t_course
(
    id      bigint unsigned  NOT NULL AUTO_INCREMENT COMMENT '主键',
    name    varchar(64)      NOT NULL COMMENT '课程名',
    teacher varchar(32)               DEFAULT NULL COMMENT '授课教师',
    credit  tinyint unsigned NOT NULL DEFAULT '0' COMMENT '学分',
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='课程表';
```

成绩表：

```sql
CREATE TABLE t_score
(
    id          BIGINT UNSIGNED AUTO_INCREMENT COMMENT '主键',
    student_id  BIGINT UNSIGNED NOT NULL COMMENT '学生 id，对应 t_student.id',
    course_id   BIGINT UNSIGNED NOT NULL COMMENT '课程 id，对应 t_course.id',
    score       DECIMAL(5, 2)   NOT NULL DEFAULT 0.00 COMMENT '分数',
    create_time DATETIME        NOT NULL COMMENT '创建时间，自动填充',
    update_time DATETIME        NOT NULL COMMENT '更新时间，自动填充',
    deleted     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除，0 未删除 / 1 已删除',
    PRIMARY KEY (id),
    KEY idx_student_id (student_id),
    KEY idx_course_id (course_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT ='成绩表';
```

几处说明：

- `BIGINT UNSIGNED AUTO_INCREMENT`：主键交给数据库自动生成，从 1 往上加。Java 里对应 `Long`。
- `DECIMAL(5, 2)`：分数不能用 `FLOAT`，浮点数存 88.5 可能变成 88.499999。`DECIMAL(5,2)` 表示总共 5 位数字，小数点后 2 位，Java 里对应 `BigDecimal`。
- `deleted`：逻辑删除标记。删数据时不是真的 `DELETE`，而是把它改成 1，查的时候自动过滤掉。
- `KEY idx_class_name`：给班级建索引。按班级查的时候数据库不用全表扫描。

三张表之间没有建外键。外键会让删数据、清表都变麻烦，实际项目里通常把这种约束放在程序里管。

准备一些数据：

```sql
INSERT INTO t_student ( id, NAME, gender, age, class_name, create_time, update_time, deleted )
VALUES
	( 1, '张伟', 1, 20, '软件2411', '2026-09-01 08:00:00', '2026-09-01 08:00:00', 0 ),
	( 2, '李娜', 2, 19, '软件2411', '2026-09-01 08:00:00', '2026-09-01 08:00:00', 0 ),
	( 3, '王强', 1, 21, '软件2412', '2026-09-01 08:00:00', '2026-09-01 08:00:00', 0 ),
	( 4, '刘敏', 2, 20, '软件2412', '2026-09-01 08:00:00', '2026-09-01 08:00:00', 0 ),
	( 5, '陈晨', 1, 22, '软件2421', '2026-09-01 08:00:00', '2026-09-01 08:00:00', 0 ),
	( 6, '赵雪', 2, 19, '软件2421', '2026-09-01 08:00:00', '2026-09-01 08:00:00', 1 );
INSERT INTO t_course ( id, NAME, teacher, credit )
VALUES
	( 1, '后端工程化开发', '许老师', 4 ),
	( 2, '前端工程化开发', '许老师', 3 ),
	( 3, 'Web 应用系统综合实训', '许老师', 3 );
INSERT INTO t_score ( id, student_id, course_id, score, create_time, update_time, deleted )
VALUES
	( 1, 1, 1, 88.50, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 ),
	( 2, 1, 2, 92.00, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 ),
	( 3, 1, 3, 76.00, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 ),
	( 4, 2, 1, 95.00, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 ),
	( 5, 2, 2, 89.50, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 ),
	( 6, 2, 3, 91.00, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 ),
	( 7, 3, 1, 58.00, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 ),
	( 8, 3, 2, 63.50, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 ),
	( 9, 3, 3, 45.00, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 ),
	( 10, 4, 1, 72.00, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 ),
	( 11, 4, 2, 80.00, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 ),
	( 12, 5, 1, 66.00, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 ),
	( 13, 5, 3, 88.00, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 ),
	( 14, 6, 1, 90.00, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 ),
	( 15, 6, 2, 85.00, '2026-10-10 09:00:00', '2026-10-10 09:00:00', 0 );
```

## 写实体类

实体类的作用是把一行数据变成一个 Java 对象。列名 `class_name` 对应属性 `className`，数据库的 `null` 对应 Java 的 `null`。

`entity/Student.java`：

```java
@Data
@TableName("t_student")
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer gender;

    private Integer age;

    private String className;

    @TableField(fill = FieldFill.INSERT)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;

    @TableLogic
    @TableField(fill = FieldFill.INSERT)
    private Integer deleted;
}
```

`entity/Course.java`

```java
@Data
@TableName("t_course")
public class Course {

    /**
     * BIGINT UNSIGNED —— 自增主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * VARCHAR(64) —— 课程名，建了唯一索引
     */
    private String name;

    /**
     * VARCHAR(32) —— 授课教师
     */
    private String teacher;

    /**
     * TINYINT UNSIGNED —— 学分
     */
    private Integer credit;
}
```

相关注解的作用：

| 注解 | 作用 | 不写会怎样 |
| --- | --- | --- |
| `@Data` | Lombok 生成 getter、setter、`toString` | 需要手写几十行方法 |
| `@TableName("t_student")` | 告诉程序这个类对应哪张表 | 程序猜的表名是 `student`，找不到表就会报错 |
| `@TableId(type = IdType.AUTO)` | 标明主键，值由数据库自增生成 | 插入时程序会自己算一个 id，和数据库冲突 |
| `@TableField(fill = FieldFill.INSERT)` | 新增时自动填这一列 | 这一列是 `NOT NULL`，不填会插入失败 |
| `@TableLogic` | 标记逻辑删除字段 | `delete` 会真的删掉数据，查不回来 |

属性名和列名的对应关系不需要一个个写，这样配置：

```yaml
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true   # 下划线转驼峰
```

`class_name` 去掉下划线变成 `classname`，再按驼峰还原成 `className`，正好对上。只有当属性名和列名对不上时（比如列叫 `stu_name`、属性叫 `name`），才需要在属性上补 `@TableField("stu_name")`。

`entity/Score.java` 结构多两个字段 `studentId`、`courseId`，分数是 `BigDecimal`：

```java
@Data
@TableName("t_score")
public class Score {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private Long courseId;

    private BigDecimal score;

    // createTime / updateTime / deleted 与 Student 完全相同，这里省略
}
```

## Mapper

`mapper/StudentMapper.java`：

```java
public interface StudentMapper extends BaseMapper<Student> {

}
```

`mapper/CourseMapper.java`：

```java
public interface CourseMapper.java extends BaseMapper<Course> {

}
```

`mapper/ScoreMapper.java`：

```java
public interface ScoreMapper.java extends BaseMapper<Score> {

}
```

`BaseMapper` 已经准备好一批方法，继承过来就能用：

| 方法 | 干什么 | 生成的 SQL 大致是 |
| --- | --- | --- |
| `insert(entity)` | 新增 | `INSERT INTO t_student (...) VALUES (...)` |
| `deleteById(id)` | 按主键删 | `UPDATE t_student SET deleted = 1 WHERE id = ?` |
| `updateById(entity)` | 按主键改 | `UPDATE t_student SET ... WHERE id = ?` |
| `selectById(id)` | 按主键查 | `SELECT ... WHERE id = ? AND deleted = 0` |
| `selectList(wrapper)` | 按条件查多条 | `SELECT ... WHERE deleted = 0 AND ...` |
| `selectCount(wrapper)` | 统计条数 | `SELECT COUNT(*) ...` |
| `selectPage(page, wrapper)` | 分页查 | `SELECT ... LIMIT ?, ?` |
| `selectObjs(wrapper)` | 只取一列 | `SELECT name ...` |

表里有两个地方值得注意：`deleteById` 发的是 `UPDATE`，`selectById` 自己带了 `deleted = 0`。这两件事都是 `@TableLogic` 干的，业务代码里一个字都不用写。

类名到表名的映射是 MyBatis-Plus 启动时扫描出来的，前提是启动主类上必须添加如下注解：

```java
@MapperScan(basePackages = "top.mqxu.mybatis.mapper")
```

> 没有它，Mapper 接口不会被扫描，启动时会报找不到 bean。

## Service

Mapper 负责 “和数据库打交道”，Service 负责 “业务逻辑”，只写通用方法做不到的部分即可，这层不能省。

Service 的一般形式如下，分为接口和实现类：

```java
public interface StudentService extends IService<Student> {
    // 只写自己加工过的方法，通用的增删改查从 IService 继承
    Student getStudent(Long id);
}
```

```java
@Service
@RequiredArgsConstructor
public class StudentServiceImpl extends ServiceImpl<StudentMapper, Student> implements StudentService {

    @Override
    public Student getStudent(Long id) {
        return getById(id);
    }
}
```

`ServiceImpl<StudentMapper, Student>` 这两个泛型的意思是“用 StudentMapper 来操作 Student”。继承之后，`getById`、`list`、`page`、`save`、`removeById` 这些方法直接能用，和 Mapper 里那批方法一一对应。

`@RequiredArgsConstructor` 是 Lombok 的注解，给所有 `final` 字段生成构造器，Spring 看到构造器就自动把 `ScoreMapper`、`CourseMapper` 注入进来。等价于写一个构造函数加 `@Autowired`。

### CourseService

接口：

```java
public interface CourseService extends IService<Course> {

    /**
     * 批量新增，底层是 saveBatch
     */
    boolean saveCourses(List<Course> courses);

    /** 按主键批量删除，底层是 removeByIds，t_course 没有逻辑删除列，这里是物理删除 */
    boolean removeCourses(List<Long> ids);

    /** 查询全部，按 id 升序 */
    List<Course> listCourses();
}
```

实现类：

```java
@Service
public class CourseServiceImpl extends ServiceImpl<CourseMapper, Course> implements CourseService {

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean saveCourses(List<Course> courses) {
        if (courses == null || courses.isEmpty()) {
            return false;
        }
        return saveBatch(courses);
    }

    @Override
    public boolean removeCourses(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return false;
        }
        return removeByIds(ids);
    }

    @Override
    public List<Course> listCourses() {
        return list(new LambdaQueryWrapper<Course>().orderByAsc(Course::getId));
    }
}
```

### StudentService

#### 接口

```java
/**
 * 学生服务
 *
 * <p>方法按“从简单到复杂”排列
 * 先单表查询，再聚合统计，最后多表查询。</p>
 *
 * @author mqxu
 */
public interface StudentService extends IService<Student> {

    /**
     * 按 id 查一个学生，查不到返回 null
     */
    Student getStudent(Long id);

    /**
     * 查全部学生
     */
    List<Student> listAll();

    /**
     * 按班级查
     */
    List<Student> listByClass(String className);

    /**
     * 按姓名模糊查
     */
    List<Student> searchByName(String name);

    /**
     * 按年龄区间查，两端都可以不传
     */
    List<Student> listByAgeRange(Integer minAge, Integer maxAge);

    /**
     * 分页查学生
     */
    Page<Student> pageStudents(long current, long size);

    /**
     * 只取姓名这一列
     */
    List<String> listNames();

    /**
     * 统计每个学生的选课门数、总分、平均分
     */
    List<Map<String, Object>> scoreStat();

    /**
     * 查某学生的成绩单：分两次单表查询，在 Java 里拼起来
     */
    List<ScoreSheetVO> scoreSheetBySteps(Long studentId);

    /**
     * 查某学生的成绩单：一条 join SQL 直接查出来
     */
    List<ScoreSheetVO> scoreSheetByJoin(Long studentId);
}
```

#### 实现类

```java
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import top.mqxu.mybatis.entity.Course;
import top.mqxu.mybatis.entity.Score;
import top.mqxu.mybatis.entity.Student;
import top.mqxu.mybatis.mapper.CourseMapper;
import top.mqxu.mybatis.mapper.ScoreMapper;
import top.mqxu.mybatis.mapper.StudentMapper;
import top.mqxu.mybatis.service.StudentService;
import top.mqxu.mybatis.vo.ScoreSheetVO;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学生服务实现
 *
 * <p>继承 ServiceImpl 之后，save、getById、list、page 这些方法直接就可以用了，
 * 这里的代码只负责“通用方法写不出来的那部分”。</p>
 *
 * @author mqxu
 */
@Service
@RequiredArgsConstructor
public class StudentServiceImpl extends ServiceImpl<StudentMapper, Student> implements StudentService {

    /**
     * 成绩表操作，多表查询写在它的 Mapper 里
     */
    private final ScoreMapper scoreMapper;

    /**
     * 课程表操作，分步查询成绩单时要用它把课程名查出来
     */
    private final CourseMapper courseMapper;

    /* ==================== 单表查询 ==================== */

    @Override
    public Student getStudent(Long id) {
        // selectById 会自己拼接 WHERE id = ? AND deleted = 0
        return getById(id);
    }

    @Override
    public List<Student> listAll() {
        // 不带条件查全部，同样会自动带上 deleted = 0
        return list();
    }

    @Override
    public List<Student> listByClass(String className) {
        return list(Wrappers.<Student>lambdaQuery()
                .eq(Student::getClassName, className)
                .orderByAsc(Student::getId));
    }

    @Override
    public List<Student> searchByName(String name) {
        // 第一个参数是“这个条件要不要生效”，传空字符串时就相当于不按姓名过滤
        return list(Wrappers.<Student>lambdaQuery()
                .like(StringUtils.hasText(name), Student::getName, name)
                .orderByAsc(Student::getId));
    }

    @Override
    public List<Student> listByAgeRange(Integer minAge, Integer maxAge) {
        // ge 是大于等于，le 是小于等于；两端都传 null 时就等于查全部
        return list(Wrappers.<Student>lambdaQuery()
                .ge(minAge != null, Student::getAge, minAge)
                .le(maxAge != null, Student::getAge, maxAge)
                .orderByAsc(Student::getAge)
                .orderByAsc(Student::getId));
    }

    @Override
    public Page<Student> pageStudents(long current, long size) {
        // 分页插件会把它翻译成 LIMIT ?, ?，另外再发一条 COUNT 查总数
        return page(new Page<>(current, size), Wrappers.<Student>lambdaQuery()
                .orderByAsc(Student::getId));
    }

    @Override
    public List<String> listNames() {
        // 只查 name 一列，再把结果从 Object 转成 String
        return listObjs(Wrappers.<Student>lambdaQuery()
                .select(Student::getName)
                .orderByAsc(Student::getId), Object::toString);
    }

    /* ==================== 分组统计 ==================== */

    @Override
    public List<Map<String, Object>> scoreStat() {
        // QueryWrapper 用字符串写列名，适合拼聚合函数；Lambda 写法表达不了 COUNT(*) 这类表达式
        return scoreMapper.selectMaps(new QueryWrapper<Score>()
                .select("student_id",
                        "COUNT(*) AS course_count",
                        "SUM(score) AS total_score",
                        "ROUND(AVG(score), 2) AS avg_score")
                .groupBy("student_id")
                .orderByDesc("total_score"));
    }

    /* ==================== 多表查询：两种写法 ==================== */

    @Override
    public List<ScoreSheetVO> scoreSheetBySteps(Long studentId) {
        // 第一步：查学生本人，拿到姓名。
        // 如果这个学生已经被逻辑删除，getById 会返回 null，直接返回空列表
        Student student = getById(studentId);
        if (student == null) {
            return List.of();
        }

        // 第二步：查这个学生的所有成绩记录
        List<Score> scores = scoreMapper.selectList(Wrappers.<Score>lambdaQuery()
                .eq(Score::getStudentId, studentId)
                .orderByAsc(Score::getId));
        if (scores.isEmpty()) {
            return List.of();
        }

        // 第三步：把用到的课程 id 收集起来，一次查回来。
        // 不要在循环里一条条查，那样 3 条成绩就要发 3 次 SQL
        Set<Long> courseIds = scores.stream()
                .map(Score::getCourseId)
                .collect(Collectors.toSet());
        Map<Long, String> courseNameMap = courseMapper.selectByIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName));

        // 第四步：在内存里把三段数据拼成成绩单
        return scores.stream()
                .map(score -> new ScoreSheetVO(
                        score.getId(),
                        student.getName(),
                        courseNameMap.getOrDefault(score.getCourseId(), "课程不存在"),
                        score.getScore()))
                .toList();
    }

    @Override
    public List<ScoreSheetVO> scoreSheetByJoin(Long studentId) {
        // 三张表的关联条件都写在 SQL 里，一次查询拿到结果
        return scoreMapper.selectScoreSheet(studentId);
    }
}

```

### 代码解释

#### 按 id 查

```java
@Override
public Student getStudent(Long id) {
    return getById(id);
}
```

调用 `GET /students/1`，控制台会打印这样一段：

```text
==>  Preparing: SELECT id,name,gender,age,class_name,create_time,update_time,deleted FROM t_student WHERE id=? AND deleted=0
==> Parameters: 1(Long)
<==    Columns: id, name, gender, age, class_name, create_time, update_time, deleted
<==        Row: 1, 张伟, 1, 20, 软件2301, 2026-09-01 08:00:00, 2026-09-01 08:00:00, 0
<==      Total: 1
```

怎么读这段日志：

- `Preparing` 是最终发给数据库的 SQL，`?` 是占位符。
- `Parameters` 是占位符被填进去的值。
- `Row` 是查回来的一行数据，顺序和 `Columns` 对应。
- `Total` 是结果条数。

注意 SQL 末尾的 `AND deleted=0`。这行条件你没有写，是 `@TableLogic` 加上的，所以逻辑删除的学生自然查不出来。

查一个不存在的 id，`getById` 返回 `null`，不抛异常。这就是为什么 Controller 直接返回它也没问题，前端拿到 `null` 显示空即可。

#### 各种条件查询

##### **查全部**

```java
public List<Student> listAll() {
    return list();
}
```

`list()` 不带条件，SQL 是 `SELECT ... FROM t_student WHERE deleted=0`。库里六条数据，返回五条 —— id 为 6 的赵雪 `deleted = 1`，被过滤掉了。

##### **按一个字段相等查**

```java
public List<Student> listByClass(String className) {
    return list(Wrappers.<Student>lambdaQuery()
            .eq(Student::getClassName, className)
            .orderByAsc(Student::getId));
}
```

`Wrappers.<Student>lambdaQuery()` 造一个查询条件对象，后面像搭积木一样往上加条件。

- `eq` 是等于，`Student::getClassName` 是“我要用 className 这个字段”，`className` 是它的值。
- 用方法引用而不是字符串，好处是字段名写错了编译器直接报错，不会等到运行时。
- `orderByAsc` 是排序，按 id 从小到大。

生成的 SQL：

```sql
SELECT ... FROM t_student WHERE deleted=0 AND (class_name = ?) ORDER BY id ASC
```

##### **模糊查**

```java
public List<Student> searchByName(String name) {
    return list(Wrappers.<Student>lambdaQuery()
            .like(StringUtils.hasText(name), Student::getName, name)
            .orderByAsc(Student::getId));
}
```

`like` 拼出来是 `name LIKE '%张%'`，两边都带百分号，所以查“张”能找到“张伟”。

第一个参数 `StringUtils.hasText(name)` 是个开关：传了姓名才加这个条件，没传（空字符串或 null）就整段跳过，等于查全部。这样同一个方法能应付“有名字”和“没名字”两种请求，不用写 if 判断再拼两次。

##### **区间查**

```java
public List<Student> listByAgeRange(Integer minAge, Integer maxAge) {
    return list(Wrappers.<Student>lambdaQuery()
            .ge(minAge != null, Student::getAge, minAge)
            .le(maxAge != null, Student::getAge, maxAge)
            .orderByAsc(Student::getAge)
            .orderByAsc(Student::getId));
}
```

`ge` 是大于等于，`le` 是小于等于。两端都传 null 时两个条件都不生效，等于查全部。两个 `orderByAsc` 叠加，先按年龄排，年龄相同的再按 id 排，保证每次查询顺序一致。

#### **分页**

```java
public Page<Student> pageStudents(long current, long size) {
    return page(new Page<>(current, size), Wrappers.<Student>lambdaQuery()
            .orderByAsc(Student::getId));
}
```

`new Page<>(1, 2)` 表示第 1 页、每页 2 条。`page()` 返回的 `Page` 对象里有四个常用值：

| 方法 | 含义 | 例子里（5 条数据，每页 2 条） |
| --- | --- | --- |
| `getRecords()` | 当前页的数据 | 2 条 |
| `getTotal()` | 总条数 | 5 |
| `getPages()` | 总页数 | 3 |
| `getCurrent()`、`getSize()` | 当前页码、每页条数 | 1、2 |

分页插件会发两条 SQL：一条 `SELECT COUNT(*)` 查总数，一条带 `LIMIT` 的查询取当前页。

```sql
SELECT COUNT(*) AS total FROM t_student WHERE deleted=0
SELECT ... FROM t_student WHERE deleted=0 ORDER BY id ASC LIMIT ?,?
```

#### 只查一列

页面上只需要一个姓名下拉框，没必要把整行都查出来。

```java
public List<String> listNames() {
    return listObjs(Wrappers.<Student>lambdaQuery()
            .select(Student::getName)
            .orderByAsc(Student::getId), Object::toString);
}
```

`select` 指定只查哪几列，`listObjs` 把结果当成一串单值返回，第二个参数负责把值转成 `String`。

结果：`["张伟", "李娜", "王强", "刘敏", "陈晨"]`。

#### 分组统计

到现在为止，查的都是“某个学生的信息”。

如果换个问法：每个学生一共考了几分？这是分组统计，SQL 里叫 `GROUP BY`：

```java
public List<Map<String, Object>> scoreStat() {
    return scoreMapper.selectMaps(new QueryWrapper<Score>()
            .select("student_id",
                    "COUNT(*) AS course_count",
                    "SUM(score) AS total_score",
                    "ROUND(AVG(score), 2) AS avg_score")
            .groupBy("student_id")
            .orderByDesc("total_score"));
}
```

这里换成了 `QueryWrapper` 而不是 `lambdaQuery`，因为 `COUNT(*)`、`SUM(score)` 这种表达式没法用方法引用来写，只能写成字符串。`selectMaps` 表示“结果不要实体，给我一串 Map”，每行一个 Map，键是列名或别名。

跑出来的结果：

| student_id | course_count | total_score | avg_score |
| --- | --- | --- | --- |
| 2 | 3 | 275.50 | 91.83 |
| 1 | 3 | 256.50 | 85.50 |
| 6 | 2 | 175.00 | 87.50 |
| 3 | 3 | 166.50 | 55.50 |
| 5 | 2 | 154.00 | 77.00 |
| 4 | 2 | 152.00 | 76.00 |

结果是对的，但页面没法直接用，存在两个问题：

第一，只有 `student_id`，没有姓名。学生看到的是一串数字，不知道 2 是谁。

第二，第 3 行出现了 `student_id = 6`，这是赵雪，她的记录已经逻辑删除了。成绩表里她那条成绩还在，而这段 SQL 只查了成绩表，自然把她算进来了。

两个问题其实是同一个原因**：数据分散在三张表里，只查一张表凑不齐。要显示姓名，就得把学生表接进来**。

#### 多表查询

##### 第一种做法：分步查

思路很直白：一条一条查，最后在 Java 里拼。

```text
第 1 步  按 id 查学生      → 拿到姓名「张伟」
第 2 步  按 student_id 查成绩 → 拿到 3 条：course_id = 1、2、3，分数 88.5、92、76
第 3 步  按这 3 个 course_id 查课程 → 拿到三个课程名
第 4 步  在内存里把三段数据配成一张成绩单
```

代码：

```java
@Override
public List<ScoreSheetVO> scoreSheetBySteps(Long studentId) {
    // 第一步：查学生本人，拿到姓名
    Student student = getById(studentId);
    if (student == null) {
        return List.of();
    }

    // 第二步：查这个学生的所有成绩记录
    List<Score> scores = scoreMapper.selectList(Wrappers.<Score>lambdaQuery()
            .eq(Score::getStudentId, studentId)
            .orderByAsc(Score::getId));
    if (scores.isEmpty()) {
        return List.of();
    }

    // 第三步：把用到的课程 id 收集起来，一次查回来
    Set<Long> courseIds = scores.stream()
            .map(Score::getCourseId)
            .collect(Collectors.toSet());
    Map<Long, String> courseNameMap = courseMapper.selectByIds(courseIds).stream()
            .collect(Collectors.toMap(Course::getId, Course::getName));

    // 第四步：拼成成绩单
    return scores.stream()
            .map(score -> new ScoreSheetVO(
                    score.getId(),
                    student.getName(),
                    courseNameMap.getOrDefault(score.getCourseId(), "课程不存在"),
                    score.getScore()))
            .toList();
}
```

第三步为什么要先收集 id 再一次性查？三个课程 id 如果一条一条查，就是 3 次数据库往返；收集成一个 `Set` 用 `IN` 查，只要 1 次。这个差别在数据量大的时候会被放大成几百次和一次的区别。

`courseNameMap` 是“课程 id → 课程名”的对照表，第四步拿 `course_id` 去查名字，一次构建反复使用。

`ScoreSheetVO` 是专门为这个返回结果建的类，放 `vo` 包里。它不对应任何表，只是“结果长什么样”：

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreSheetVO {
    private Long scoreId;
    private String studentName;
    private String courseName;
    private BigDecimal score;
}
```

运行结果（`GET /students/1/scores`）：

```json
[
  { "scoreId": 1, "studentName": "张伟", "courseName": "后端工程化开发", "score": 88.50 },
  { "scoreId": 2, "studentName": "张伟", "courseName": "前端工程化开发", "score": 92.00 },
  { "scoreId": 3, "studentName": "张伟", "courseName": "Web 应用系统综合实训", "score": 76.00 }
]
```

这种做法的好处是四步全是普通单表查询，逻辑删除、自动填充、分页都自动生效，出问题也好查。代价是多几次数据库往返，且排序没法按关联表的字段来。

##### 第二种做法：一条 join

数据库本身就支持把几张表接起来查，关键字是 `JOIN ... ON`。读法是：把 A 表和 B 表按 `ON` 后面的条件对齐，拼成一张宽表，再往里挑数据。

```java
@Select("""
        SELECT s.id        AS scoreId,
               st.name     AS studentName,
               c.name      AS courseName,
               s.score     AS score
        FROM t_score s
                 JOIN t_student st ON st.id = s.student_id AND st.deleted = 0
                 JOIN t_course c ON c.id = s.course_id
        WHERE s.deleted = 0
          AND s.student_id = #{studentId}
        ORDER BY s.id
        """)
List<ScoreSheetVO> selectScoreSheet(@Param("studentId") Long studentId);
```

这段 SQL 的三个要点：

**一是别名要对上 VO 的属性名。** 数据库返回的列名是什么，MyBatis 就按这个名字去找同名属性。`AS studentName` 对应 VO 里的 `studentName`；如果写成 `AS student_name`，又没有开驼峰映射，这个名字就填不进去，字段是 `null`。这是新手最常见的一个“查到了但全是空”的原因。

**二是两张表的 `deleted = 0` 要自己写。** 逻辑删除只对 MyBatis-Plus 自动生成的 SQL 生效，手写的 SQL 它不管。上面 `s.deleted = 0` 过滤已删成绩，`st.deleted = 0` 过滤已删学生。少写后面那个，赵雪就会重新出现在结果里。

**三是 `ON` 的条件可以顺手带上过滤。** `JOIN t_student st ON st.id = s.student_id AND st.deleted = 0` 和把 `st.deleted = 0` 写在 `WHERE` 里效果一样，放 `ON` 里读起来更接近“只有有效的学生才接进来”。

对应的控制台日志：

```text
==>  Preparing: SELECT s.id AS scoreId, st.name AS studentName, c.name AS courseName, s.score AS score
                 FROM t_score s
                 JOIN t_student st ON st.id = s.student_id AND st.deleted = 0
                 JOIN t_course c ON c.id = s.course_id
                 WHERE s.deleted = 0 AND s.student_id = ? ORDER BY s.id
==> Parameters: 1(Long)
<==        Row: 1, 张伟, 后端工程化开发, 88.50
<==        Row: 2, 张伟, 前端工程化开发, 92.00
<==        Row: 3, 张伟, Web 应用系统综合实训, 76.00
<==      Total: 3
```

一条 SQL，结果和分步查询完全相同。测试里专门写了一条用例，把两种方式的返回值逐字段对比，防止以后改动时两边跑偏。

##### 两种做法怎么选

| | 分步查 | 一条 join |
| --- | --- | --- |
| 要写 SQL 吗 | 不用 | 要 |
| 几次数据库往返 | 3 到 4 次 | 1 次 |
| 逻辑删除 | 自动生效 | 要自己写条件 |
| 分页 | 对主表分页，总数天然正确 | 关联表一对多时，总数可能不准 |
| 排序 | 只能按本表字段排 | 可以按任意表的字段排 |
| 适合 | 常规列表页、详情页 | 列表要按关联表字段筛选或排序 |

新手阶段建议先用分步查：出错了容易定位，每一步都能单独查一次验证。等写出感觉了，再换成 join 优化往返次数。

##### 容易踩的坑

**查到了，但字段全是 null。** 检查三处：SQL 的别名和 VO 属性名是否一致、有没有开 `map-underscore-to-camel-case`、VO 有没有无参构造和 setter（用 Lombok 的 `@Data` 就有，手写类忘了写就会出问题）。

**成绩单里出现了已删除的学生。** 手写 join 时漏了 `st.deleted = 0`。记住逻辑删除不是全局拦截，是自己拼 SQL 时得自己写。

**数据明明在库里，`getById` 却返回 null。** 去库里看一眼 `deleted` 字段，可能是 1。这种情况下用 `getById`、`selectList` 都查不到，只能自己写 SQL。

**在循环里查数据库。** 先查出 10 个学生，再在 `for` 循环里按 id 一个个查班级，就是 11 次查询。应该先把 id 收集起来，用 `IN` 查一次。这种写法叫 N+1，数据量一上来就能感觉到卡。

**BigDecimal 用 `equals` 比较判不等。** `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` 返回 `false`，因为精度不同。比较大小用 `compareTo`，测试里用 AssertJ 的 `isEqualByComparingTo`。

## Controller

```java
package top.mqxu.mybatis.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import top.mqxu.mybatis.entity.Student;
import top.mqxu.mybatis.service.StudentService;
import top.mqxu.mybatis.vo.ScoreSheetVO;

import java.util.List;
import java.util.Map;

/**
 * 学生成绩接口
 *
 * @author mqxu
 */
@RestController
@RequestMapping("/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;

    /**
     * 全部学生
     */
    @GetMapping
    public List<Student> list() {
        return studentService.listAll();
    }

    /**
     * 按班级查，例如 /students/class?className=软件2301
     */
    @GetMapping("/class")
    public List<Student> listByClass(@RequestParam String className) {
        return studentService.listByClass(className);
    }

    /**
     * 按姓名模糊查，例如 /students/search?name=张
     */
    @GetMapping("/search")
    public List<Student> search(@RequestParam(required = false) String name) {
        return studentService.searchByName(name);
    }

    /**
     * 按年龄区间查，例如 /students/age-range?minAge=19&maxAge=20
     */
    @GetMapping("/age-range")
    public List<Student> listByAgeRange(@RequestParam(required = false) Integer minAge,
                                        @RequestParam(required = false) Integer maxAge) {
        return studentService.listByAgeRange(minAge, maxAge);
    }

    /**
     * 分页，例如 /students/page?current=1&size=2
     */
    @GetMapping("/page")
    public Page<Student> page(@RequestParam(defaultValue = "1") long current,
                              @RequestParam(defaultValue = "2") long size) {
        return studentService.pageStudents(current, size);
    }

    /**
     * 只要姓名这一列
     */
    @GetMapping("/names")
    public List<String> names() {
        return studentService.listNames();
    }

    /**
     * 每个学生的选课门数、总分、平均分
     */
    @GetMapping("/statistics")
    public List<Map<String, Object>> statistics() {
        return studentService.scoreStat();
    }

    /**
     * 某个学生的成绩单。mode 传 steps 走分步查询，传 join 走一条 SQL，
     * 两种写法的返回结果一模一样，方便对照
     */
    @GetMapping("/{id}/scores")
    public List<ScoreSheetVO> scores(@PathVariable Long id,
                                     @RequestParam(defaultValue = "steps") String mode) {
        return "join".equals(mode)
                ? studentService.scoreSheetByJoin(id)
                : studentService.scoreSheetBySteps(id);
    }

    /**
     * 按 id 查一个学生，放在最后，避免把 /class、/page 这些路径抢走
     */
    @GetMapping("/{id}")
    public Student get(@PathVariable Long id) {
        return studentService.getStudent(id);
    }
}

```

## 运行

启动应用：

```bash
mvn -pl 05-mybatis spring-boot:run
```

端口 8005，接口按顺序试：

| 看什么 | 请求 |
| --- | --- |
| 全部学生（5 条） | `GET /students` |
| 按 id 查 | `GET /students/1` |
| 按班级查 | `GET /students/class?className=软件2301` |
| 姓名模糊查 | `GET /students/search?name=张` |
| 年龄区间 | `GET /students/age-range?minAge=19&maxAge=20` |
| 分页 | `GET /students/page?current=1&size=2` |
| 只要姓名 | `GET /students/names` |
| 分组统计 | `GET /students/statistics` |
| 成绩单（分步查） | `GET /students/1/scores` |
| 成绩单（join 查） | `GET /students/1/scores?mode=join` |

最后两个请求的返回结果应该完全一样，可以自己对比。

到这里，单表查询、条件构造、分组统计、多表关联就都实践过了。

## 动手练习

1. 查每门课的平均分，要带上课程名和选课人数。提示：`t_score` 按 `course_id` 分组算出平均分，再把 `t_course` join 进来取名字。
2. 给成绩单加分页。提示：Mapper 方法第一个参数写成 `IPage<ScoreSheetVO>`，返回 `IPage<ScoreSheetVO>`，Service 里传 `new Page<>(current, size)`。
3. 统计每个班的平均分。提示：成绩表 join 学生表，按 `class_name` 分组。注意已经被逻辑删除的赵雪不应该参与统计。

第 3 题有个坑值得留意：只 join 成绩表和学生表还不够，得确认 `st.deleted = 0` 写上了，否则班级平均分会被一条不该存在的成绩拉偏。
