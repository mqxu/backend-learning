-- ============================================================
-- 知乎案例：演示数据
-- 执行顺序：先 schema.sql，再 data.sql
--
-- 密码统一是 Zhihu#2026（teacher 是 Teacher#2026），存的是 BCrypt 密文。
-- 时间用 NOW() 减若干天生成，跑起来永远是"最近几天"的内容。
-- ============================================================

SET NAMES utf8mb4;

DELETE FROM z_vote;
DELETE FROM z_follow;
DELETE FROM z_comment;
DELETE FROM z_answer;
DELETE FROM z_question;
DELETE FROM z_topic;
DELETE FROM z_user;
DELETE FROM z_special;

INSERT INTO z_user (id, username, password, nickname, avatar, bio, created_at)
VALUES (1, 'alice', '$2a$10$BjPedYBiGVCj59wQH4gzwuTkZWaJU6p3WXp2rufyy9SivZ3o.liHq', '林知远',
        'https://pic1.zhimg.com/80/v2-8f7a6f0b6b1e0b3f2c7d8e9a0b1c2d3e_720w.png', '写了六年后端，还在补基础', NOW() - INTERVAL 400 DAY),
       (2, 'bob', '$2a$10$BjPedYBiGVCj59wQH4gzwuTkZWaJU6p3WXp2rufyy9SivZ3o.liHq', '周可欣',
        'https://pic2.zhimg.com/80/v2-1a2b3c4d5e6f708192a3b4c5d6e7f809_720w.png', '前端工程师，关注体验与可维护性', NOW() - INTERVAL 300 DAY),
       (3, 'carol', '$2a$10$BjPedYBiGVCj59wQH4gzwuTkZWaJU6p3WXp2rufyy9SivZ3o.liHq', '陈默',
        NULL, '软件工程大三，正在找实习', NOW() - INTERVAL 120 DAY),
       (4, 'dave', '$2a$10$BjPedYBiGVCj59wQH4gzwuTkZWaJU6p3WXp2rufyy9SivZ3o.liHq', '苏航',
        NULL, '职业本科在读，喜欢折腾数据库', NOW() - INTERVAL 90 DAY),
       (5, 'teacher', '$2a$10$YQw5f2mn.jiE10BfaFskseWzW4jZYDWRH4dGWLR01HVUffS3MeA56', '陶老师',
        NULL, '教后端工程化，也带毕业设计', NOW() - INTERVAL 600 DAY);

INSERT INTO z_topic (id, name, description, followers_count, question_count, sort)
VALUES (1, '后端开发', '服务端语言、框架、中间件与工程实践', 1284, 2, 1),
       (2, '前端开发', '浏览器、框架、构建工具与交互实现', 962, 1, 2),
       (3, '数据库', 'MySQL、Redis、索引与事务', 741, 2, 3),
       (4, '算法与数据结构', '面试常考题型与解题思路', 1530, 1, 4),
       (5, '职业发展', '实习、求职、技术成长路径', 486, 1, 5),
       (6, '校园生活', '课程、竞赛、实验室与项目', 233, 1, 6);

INSERT INTO z_question (id, title, content, author_id, topic_id, answer_count, view_count, vote_count, status, created_at, updated_at)
VALUES (1, 'Spring Boot 4 升级后 @MockBean 找不到，该怎么办？',
        '照着 Boot 3 的教程写测试，编译直接报找不到符号。项目是 Spring Boot 4.1.1，JDK 21。', 3, 1, 2, 328, 12, 0,
        NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 5 DAY),
       (2, 'MyBatis-Plus 分页插件为什么必须单独引 jsqlparser？',
        '只引 mybatis-plus-spring-boot4-starter，启动时 PaginationInnerInterceptor 报类找不到。版本 3.5.17。', 4, 1, 2, 215, 8, 0,
        NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 4 DAY),
       (3, 'Vue3 的 ref 和 reactive 到底该用哪个？',
        '看官方文档说都能用，实际写起来感觉随便选也行。有没有判断标准，团队里怎么统一？', 3, 2, 3, 512, 21, 0,
        NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 2 DAY),
       (4, 'MySQL 索引失效的常见场景有哪些？',
        '线上有条查询明明建了索引却走全表扫描，EXPLAIN 里 type 是 ALL。想系统性整理一下。', 4, 3, 1, 689, 15, 0,
        NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),
       (5, '校招面试手写算法，链表和二叉树哪个考得多？',
        '时间有限，想先抓高频的。目标是中小厂后端岗。', 1, 4, 2, 1204, 33, 0,
        NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 7 DAY),
       (6, '职业本科学生该怎么准备后端实习？',
        '学校课程偏实践，理论课少。投了几家都没消息，不知道差在哪。', 2, 5, 2, 876, 27, 0,
        NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 6 DAY),
       (7, '校园里有哪些适合练手的真实项目？',
        '不想再做增删改查的图书管理了，想找有真实用户、能被使用的场景。', 3, 6, 1, 402, 9, 0,
        NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 1 DAY),
       (8, 'Redis 缓存和数据库不一致，先删缓存还是先更新库？',
        '看了几种说法，越看越乱。想知道背后的推导过程，而不是记住结论。', 1, 3, 1, 933, 18, 0,
        NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);

INSERT INTO z_answer (id, question_id, author_id, content, vote_up, vote_down, comment_count, accepted, created_at)
VALUES (1, 1, 1,
        'Boot 4 把测试注解整体搬了包。@WebMvcTest 现在在 org.springframework.boot.webmvc.test.autoconfigure，@SpringBootTest 还在老地方。另外 @MockBean 已经被删掉，换成 @MockitoBean，包是 org.springframework.test.context.bean.override.mockito。把 import 改掉就能编译。',
        46, 1, 2, 0, NOW() - INTERVAL 6 DAY),
       (2, 1, 5,
        '补一句原因：Boot 4 把测试相关的自动配置拆成了独立模块，包名跟着模块走，所以不能只改版本号。升级时建议先把测试依赖换成 spring-boot-starter-webmvc-test，再逐个改 import。',
        18, 0, 0, 0, NOW() - INTERVAL 5 DAY),
       (3, 2, 1,
        '3.5.9 起 MyBatis-Plus 把 JSqlParser 拆成了独立构件，分页插件要用它解析 SQL 才能拼 LIMIT。加一个 mybatis-plus-jsqlparser 依赖，版本和 starter 保持一致即可。',
        31, 0, 2, 1, NOW() - INTERVAL 5 DAY),
       (4, 2, 4,
        '踩过这个坑。只加 jsqlparser 还不够，如果用了自定义 XML 里的复杂 SQL，最好把 PaginationInnerInterceptor 的 optimizeJoin 关掉，否则 JOIN 会被改写。',
        7, 0, 0, 0, NOW() - INTERVAL 4 DAY),
       (5, 3, 2,
        '经验判断：需要整体替换的对象用 ref，比如 ref(null) 换成一个新对象；一组互相关联的字段用 reactive，比如表单。最容易出错的是 reactive 解构会丢响应性，团队里我们干脆约定业务状态一律 ref，只有表单聚合对象用 reactive。',
        88, 2, 2, 1, NOW() - INTERVAL 4 DAY),
       (6, 3, 1,
        '补一个判断标准：这个值会不会被整体赋值。会的话 ref 更省心，reactive 整体赋值必须改属性，不能直接替换引用，一不小心就断开响应。',
        42, 0, 0, 0, NOW() - INTERVAL 3 DAY),
       (7, 3, 3,
        '我是新手，看完两位的回答理解成：先无脑 ref，等确实需要一个对象聚合再换 reactive。这样理解对吗？',
        6, 0, 0, 0, NOW() - INTERVAL 2 DAY),
       (8, 4, 5,
        '高频的有六类：字段上做函数或运算、隐式类型转换、前导模糊匹配、OR 连接了无索引列、联合索引不满足最左前缀、范围查询后面的列失效。用 EXPLAIN 看 key 和 rows，比背结论快。',
        54, 1, 1, 0, NOW() - INTERVAL 3 DAY),
       (9, 5, 1,
        '链表考的是指针操作，反转、合并、判环、找中点，写熟这四类基本够用。二叉树集中在遍历的变形，层序、路径和、最近公共祖先。两边都要手写，别只看。',
        61, 0, 0, 0, NOW() - INTERVAL 9 DAY),
       (10, 5, 2,
        '补充：中小厂更爱考链表，因为代码短、好判分；大厂二叉树多一点。但真正拉开差距的是边界处理，空指针和单节点一定要先想清楚。',
        25, 0, 0, 0, NOW() - INTERVAL 7 DAY),
       (11, 6, 5,
        '课程偏实践不是短板，短板是没作品。把一门课的实训做成能跑、有 README、有接口文档的完整项目，比刷十道八股有用。简历上写清楚你解决了什么问题，用了什么方案，遇到什么坑。',
        73, 3, 2, 0, NOW() - INTERVAL 8 DAY),
       (12, 6, 1,
        '再具体一点：挑一个真实场景，比如实验室的预约、社团的报名，做完之后让真的人用。有真实反馈的项目，面试时能聊二十分钟。',
        39, 1, 0, 0, NOW() - INTERVAL 6 DAY),
       (13, 7, 2,
        '推荐三个方向：一是把学院的通知、讲座信息做成聚合页；二是实验室设备预约；三是社团活动报名与签到。共同点是有明确的使用者，数据量小但流程完整，够练权限、状态机和事务。',
        22, 0, 1, 0, NOW() - INTERVAL 2 DAY),
       (14, 8, 1,
        '推导一下：如果先删缓存再更新库，删完到更新完这段时间里，任何一次读都会把旧值重新写回缓存，之后就一直旧到过期。反过来先更新库再删缓存，不一致窗口只有"删缓存"这一瞬间，而且下一次读会拿到新值。所以选后者，并且缓存要设过期时间兜底。',
        67, 2, 1, 0, NOW() - INTERVAL 1 DAY);

INSERT INTO z_comment (id, answer_id, author_id, content, parent_id, reply_to_user_id, created_at)
VALUES (1, 1, 3, '改完 import 果然好了，谢谢', 0, NULL, NOW() - INTERVAL 5 DAY),
       (2, 1, 4, '补充一下，@MockitoBean 的包和 @MockBean 完全不一样，别按老路径找', 1, 3, NOW() - INTERVAL 5 DAY),
       (3, 3, 4, '优化 JOIN 这个提醒很关键，我这边分页数字一直不对就是它', 0, NULL, NOW() - INTERVAL 4 DAY),
       (4, 3, 3, '确认了，加 jsqlparser 之后确实正常了', 0, NULL, NOW() - INTERVAL 4 DAY),
       (5, 5, 3, '“先无脑 ref”这个说法太实用了', 0, NULL, NOW() - INTERVAL 3 DAY),
       (6, 5, 1, '对，等真的需要聚合再换，不要一开始就纠结', 5, 3, NOW() - INTERVAL 3 DAY),
       (7, 8, 4, '最左前缀那条我踩过，联合索引顺序写反了', 0, NULL, NOW() - INTERVAL 2 DAY),
       (8, 13, 3, '第二个方向我们实验室正好需要，准备试试', 0, NULL, NOW() - INTERVAL 1 DAY),
       (9, 11, 3, '“有真实反馈的项目”这句话点醒我了', 0, NULL, NOW() - INTERVAL 6 DAY),
       (10, 14, 2, '所以关键是缩短不一致窗口，而不是追求强一致', 0, NULL, NOW() - INTERVAL 1 DAY);

INSERT INTO z_vote (user_id, target_type, target_id, value)
VALUES (2, 'ANSWER', 1, 1),
       (3, 'ANSWER', 1, 1),
       (4, 'ANSWER', 1, 1),
       (5, 'ANSWER', 1, 1),
       (1, 'ANSWER', 5, 1),
       (3, 'ANSWER', 5, 1),
       (4, 'ANSWER', 5, 1),
       (5, 'ANSWER', 5, 1),
       (1, 'ANSWER', 8, 1),
       (2, 'ANSWER', 8, 1),
       (3, 'ANSWER', 8, 1),
       (1, 'ANSWER', 11, 1),
       (2, 'ANSWER', 11, 1),
       (3, 'ANSWER', 11, 1),
       (4, 'ANSWER', 14, 1),
       (5, 'ANSWER', 14, 1),
       (2, 'ANSWER', 5, -1),
       (3, 'ANSWER', 6, -1),
       (3, 'QUESTION', 1, 1),
       (4, 'QUESTION', 3, 1),
       (5, 'QUESTION', 8, 1);

INSERT INTO z_follow (user_id, target_type, target_id)
VALUES (1, 'USER', 5),
       (2, 'USER', 1),
       (3, 'USER', 1),
       (4, 'USER', 5),
       (3, 'TOPIC', 1),
       (4, 'TOPIC', 3),
       (1, 'TOPIC', 1),
       (4, 'QUESTION', 3),
       (1, 'QUESTION', 6),
       (2, 'QUESTION', 8);

INSERT INTO z_special (id, title, introduction, banner, view_count, followers_count, is_following, updated)
VALUES (2011090301901624555, '骏马闹元宵 京彩中国节', '花灯映月，喜乐相聚，共享中国年味。',
        'https://pic2.zhimg.com/100/v2-61cbf102b5defbe8858631984a43a069_hd.png', 3312205, 2, 'FALSE', 1775109100),
       (2023074101875492733, '2026清明节丨清风寄念 哀思绵长', '山河清朗，缅怀遥寄，常念往昔温情',
        'https://pic3.zhimg.com/100/v2-85becf1d600f8753fd556d7dc5337ba2_hd.png', 1092382, 14, 'FALSE', 1775184924),
       (2025242081262675533, '4·15全民国家安全教育日丨国安于心 守护同行', '山河安澜，初心坚守，共筑平安家国',
        'https://pic1.zhimg.com/100/v2-ee615d4eef835dd1603c03f9659346fc_hd.png', 23137646, 5, 'TRUE', 1775635871),
       (2030234245487138378, '2026全民阅读活动周丨墨香致远 阅见美好', '书页传情，同心共读，涵养文明新风',
        'https://pic4.zhimg.com/100/v2-59202e897209571e4fb28b13ee73fb5b_hd.png', 6009065, 16, 'FALSE', 1776826493),
       (2037528715861698118, '2026 民法典月丨学法明理 法治同行', '普法润心，守法相伴，共筑法治新风',
        'https://pic1.zhimg.com/100/v2-d23eb125648529e6ae74a0728106cf78_hd.png', 3718712, 10, 'FALSE', 1778827816),
       (2040843602801202710, '2026 互联护苗丨清朗筑梦 守护童心', '净网护航，向阳成长，共筑少年美好晴空',
        'https://pic1.zhimg.com/100/v2-d23eb125648529e6ae74a0728106cf78_hd.png', 1705935, 8, 'FALSE', 1779360593),
       (2050893149845956359, '2026端午节｜网络中国节·端午', '粽香迎端午，千年寄安康',
        'https://pic4.zhimg.com/100/v2-ef5d5c2550fd5cff894223d5d8301d2b_hd.png', 181584, 10, 'FALSE', 1781753428),
       (2071605888083929052, '2026「京彩向尚」｜培育京彩好网民', '弘扬网络主旋律，争做京彩好网民',
        'https://pic4.zhimg.com/100/v2-a526f17e73201f76fe99ee9b3b7c349b_hd.png', 646395, 5, 'FALSE', 1786694934),
       (2072630688474518071, '网络中国节·七夕', '览千年乞巧古韵，赴人间七夕真情',
        'https://pic2.zhimg.com/100/v2-ef323e8437820770cf51aa01788643f1_hd.png', 1451027, 9, 'FALSE', 1786935255),
       (2086116797393257830, '2026中秋｜秋空皓月 人间相思', '一轮秋月溯文脉，一席饼食蕴烟火，一缕清光寄团圆',
        'https://pic2.zhimg.com/100/v2-0b76aceb29d84a748a44ac749e317d65_hd.png', 8477079, 3, 'FALSE', 1790151017);
