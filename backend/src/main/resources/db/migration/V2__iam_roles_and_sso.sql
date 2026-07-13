CREATE TABLE sso_identity (
  id BIGINT NOT NULL AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  provider VARCHAR(64) NOT NULL,
  subject VARCHAR(191) NOT NULL,
  email VARCHAR(160) NULL,
  created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  CONSTRAINT fk_sso_user FOREIGN KEY (user_id) REFERENCES app_user(id),
  CONSTRAINT uk_sso_provider_subject UNIQUE (provider, subject)
);

INSERT INTO role(id,code,name,description,built_in) VALUES
  (1,'ADMIN','系统管理员','系统配置、账号和全部数据管理',TRUE),
  (2,'PMO','PMO','跨项目度量、标准化和资源统筹',TRUE),
  (3,'DELIVERY_MANAGER','交付负责人','负责项目的全生命周期管理',TRUE),
  (4,'DELIVERY_ENGINEER','交付工程师','参与项目的日常交付操作',TRUE),
  (5,'TECH_MANAGER','技术经理','二开审核、技术风险和代码知识',TRUE),
  (6,'PRODUCT_MANAGER','产品经理','产品能力卡与标准化管理',TRUE);

INSERT INTO permission(id,code,name,module) VALUES
  (1,'system:manage','系统管理','system'),
  (2,'dashboard:read','查看驾驶舱','dashboard'),
  (3,'project:read','查看项目','project'),
  (4,'project:write','维护项目','project'),
  (5,'requirement:read','查看需求','requirement'),
  (6,'requirement:write','维护需求','requirement'),
  (7,'requirement:classify','确认需求分类','requirement'),
  (8,'standardization:read','查看标准化','standardization'),
  (9,'standardization:write','维护标准化','standardization'),
  (10,'knowledge:read','查看知识库','knowledge'),
  (11,'knowledge:write','维护知识库','knowledge'),
  (12,'resource:read','查看资源','resource'),
  (13,'resource:write','维护资源','resource'),
  (14,'agent:execute','执行Agent任务','automation'),
  (15,'file:write','上传与更新文件','storage'),
  (16,'audit:read','查看审计日志','audit');

INSERT INTO role_permission(role_id,permission_id)
SELECT 1,id FROM permission;

INSERT INTO role_permission(role_id,permission_id) VALUES
  (2,2),(2,3),(2,5),(2,8),(2,9),(2,10),(2,12),(2,13),(2,16),
  (3,2),(3,3),(3,4),(3,5),(3,10),(3,12),(3,13),(3,14),(3,15),
  (4,3),(4,4),(4,5),(4,6),(4,7),(4,10),(4,14),(4,15),
  (5,3),(5,5),(5,7),(5,8),(5,10),(5,11),(5,14),(5,15),
  (6,2),(6,3),(6,5),(6,8),(6,9),(6,10);

